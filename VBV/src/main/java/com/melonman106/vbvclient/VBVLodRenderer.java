package com.melonman106.vbvclient;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Draws the remembered distant terrain from {@link VBVLodCache} as flat-coloured columns.
 *
 * <p>It is called from the start of the level renderer's main pass (before the real terrain), so
 * real chunks always win the depth test where they exist. The mesh is rebuilt only when the camera
 * has moved a fair distance, the settings changed, or newly captured terrain has settled, and the
 * distance fade is baked into vertex alpha so the columns dissolve into the sky at the far end.
 * Any exception switches LOD off (see {@link VBVLod#failure()}) instead of breaking the frame.</p>
 */
public final class VBVLodRenderer {
    private static final int VERTEX_SIZE = DefaultVertexFormat.POSITION_COLOR.getVertexSize();
    /**
     * The far-terrain mesh is capped by a quad budget that follows the frame rate. A low frame rate
     * shrinks the budget and rebuilds at once, so the smaller mesh is used now; when the frame rate
     * recovers the budget grows again and the next natural rebuild picks it up, so the good frames
     * are not spent rebuilding. Far terrain therefore costs least exactly when the machine can least
     * afford it, which is what keeps LOD usable at a 30 chunk view.
     */
    private static final int BUDGET_MAX = 160_000;
    private static final int BUDGET_HIGH = 110_000;
    private static final int BUDGET_MID = 70_000;
    private static final int BUDGET_LOW = 40_000;
    private static int quadBudget = BUDGET_MAX;
    private static final int REBUILD_DISTANCE = 48;
    /** While the budget is small the camera has to move further before another rebuild is worth it. */
    private static final int REBUILD_DISTANCE_SLOW = 96;
    private static final long MIN_REBUILD_MS = 400L;
    private static final long CACHE_SETTLE_MS = 1500L;
    /**
     * Cos(~26 degrees). The mesh only contains the terrain in front of the camera, so the remembered
     * direction has to follow a turn; without this, turning around showed no far terrain until the
     * player walked far enough to trigger a rebuild.
     */
    private static final double TURN_COS = 0.9;
    private static final int INITIAL_SCAN_RADIUS = 40;
    /**
     * Chunks remembered per frame while the already loaded area is scanned. Doing all 81x81 of them in
     * the first frame that draws the LOD was a visible stall right after a world loaded.
     */
    private static final int SCAN_CHUNKS_PER_FRAME = 128;

    private static GpuBuffer vertexBuffer;
    private static long vertexBufferSize;
    private static int quadCount;
    private static long vertexBytes;
    private static double originX;
    private static double originZ;

    private static boolean built;
    private static ClientLevel builtLevel;
    private static ClientLevel scannedLevel;
    private static ClientLevel scanLevel;
    private static int scanCenterX;
    private static int scanCenterZ;
    private static int scanCursorX;
    private static int scanCursorZ;
    private static int builtMode = -1;
    private static int builtRenderDistance = -1;
    private static int builtCacheVersion = -1;
    private static double builtCamX;
    private static double builtCamZ;
    private static double builtViewX;
    private static double builtViewZ;
    private static boolean builtCull;
    private static long builtAtMs;
    /** Last mesh size and rebuild cost, shown by the FPS meter and the performance report. */
    private static long lastBuildMs;

    // Fog as the real terrain sees it this frame (set by the FogRenderer hook).
    private static boolean fogValid;
    private static float fogR = 0.75F;
    private static float fogG = 0.85F;
    private static float fogB = 1.0F;
    private static float envStart;
    private static float envEnd = Float.MAX_VALUE;
    private static float renderStart;
    private static float renderEnd;

    private VBVLodRenderer() {}

    /** Called by the FogRenderer hook every frame the real fog is computed. */
    public static void setFog(float r, float g, float b, float environmentalStart, float environmentalEnd, float renderDistanceStart, float renderDistanceEnd) {
        fogR = r;
        fogG = g;
        fogB = b;
        envStart = environmentalStart;
        envEnd = environmentalEnd;
        renderStart = renderDistanceStart;
        renderEnd = renderDistanceEnd;
        fogValid = true;
    }

    /** Forces a rebuild and a fresh scan after the level or the LOD choice changes. */
    public static void resetAfterSettingChange() {
        built = false;
        scannedLevel = null;
    }

    /**
     * Called about four times a second with the smoothed FPS. Shrinking the budget drops the mesh
     * immediately; growing it waits for the next natural rebuild.
     */
    public static void updateBudget(int fps) {
        if (fps <= 0) {
            return;
        }
        int next = fps < 30 ? BUDGET_LOW : (fps < 45 ? BUDGET_MID : (fps < 55 ? BUDGET_HIGH : BUDGET_MAX));
        if (next == quadBudget) {
            return;
        }
        boolean smaller = next < quadBudget;
        quadBudget = next;
        if (smaller) {
            built = false;
        }
    }

    public static void render(Vec3 camera, RenderTarget target, boolean atmospheric) {
        if (!VBVLod.isActive() || !atmospheric || camera == null || target == null) {
            return;
        }
        try {
            draw(camera, target);
        } catch (Throwable t) {
            String message = String.valueOf(t);
            VBVLod.fail(message);
            System.err.println("[VBV LOD] disabled after an error: " + message);
            built = false;
            releaseBuffer();
        }
    }

    private static void draw(Vec3 camera, RenderTarget target) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || !VBVLodCache.usable(level)) {
            return;
        }
        int renderDistance = mc.options.getEffectiveRenderDistance();
        if (renderDistance >= VBVLod.rangeChunks()) {
            return;
        }
        int camBlockX = Mth.floor(camera.x);
        int camBlockZ = Mth.floor(camera.z);
        // Restores this dimension's saved samples on first use and writes new ones back now and then.
        VBVLodCache.maintain(level, camBlockX >> 4, camBlockZ >> 4);
        // Underground, the real rock hides the surface; the LOD would show through gaps in it.
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, camBlockX, camBlockZ);
        if (camera.y < surface - 14.0) {
            return;
        }
        if (scannedLevel != level) {
            scannedLevel = level;
            beginScan(camBlockX >> 4, camBlockZ >> 4);
        }
        scanStep(level);

        // The far terrain behind the camera can never be on screen, so it is not meshed at all: half of
        // every ring is behind the player, which halves the quads and the vertices they cost.
        double viewX = 0.0;
        double viewZ = 0.0;
        boolean cull = false;
        if (VBVFreecam.isActive()) {
            // The free camera is the one being rendered, so its facing decides what is behind it.
            viewX = VBVFreecam.forwardX();
            viewZ = VBVFreecam.forwardZ();
            cull = true;
        } else if (mc.player != null) {
            Vec3 look = mc.player.getViewVector(1.0F);
            double length = Math.sqrt(look.x * look.x + look.z * look.z);
            if (length > 0.1) {
                viewX = look.x / length;
                viewZ = look.z / length;
                cull = true;
            }
        }

        long now = System.currentTimeMillis();
        double moveX = camera.x - builtCamX;
        double moveZ = camera.z - builtCamZ;
        boolean settingsChanged = !built || builtLevel != level || builtMode != VBVLod.getLevel() || builtRenderDistance != renderDistance;
        int rebuildDistance = quadBudget <= BUDGET_MID ? REBUILD_DISTANCE_SLOW : REBUILD_DISTANCE;
        boolean moved = moveX * moveX + moveZ * moveZ > (double) rebuildDistance * rebuildDistance;
        boolean cacheChanged = builtCacheVersion != VBVLodCache.version();
        boolean turned = built && builtCull && cull && viewX * builtViewX + viewZ * builtViewZ < TURN_COS;
        if (settingsChanged || turned
            || ((moved || (cacheChanged && now - builtAtMs > CACHE_SETTLE_MS)) && now - builtAtMs > MIN_REBUILD_MS)) {
            rebuild(level, camera, renderDistance, now, viewX, viewZ, cull);
        }
        if (quadCount <= 0 || vertexBuffer == null) {
            return;
        }

        float lum = 0.2126F * fogR + 0.7152F * fogG + 0.0722F * fogB;
        float brightness = Mth.clamp(lum / 0.8F, 0.18F, 1.0F);
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
            .writeTransform(
                RenderSystem.getModelViewMatrixCopy(),
                new Vector4f(brightness, brightness, brightness, 1.0F),
                new Vector3f((float) (originX - camera.x), (float) -camera.y, (float) (originZ - camera.z)),
                new Matrix4f()
            );
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indexBuffer = indices.getBuffer(6 * quadCount);
        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "VBV LOD", target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(RenderPipelines.VBV_LOD);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, vertexBuffer.slice(0L, vertexBytes));
            pass.setIndexBuffer(indexBuffer, indices.type());
            pass.drawIndexed(6 * quadCount, 1, 0, 0, 0);
        }
    }

    private static void rebuild(ClientLevel level, Vec3 camera, int renderDistance, long now, double viewX, double viewZ, boolean cull) {
        built = true;
        builtLevel = level;
        builtViewX = viewX;
        builtViewZ = viewZ;
        builtCull = cull;
        builtMode = VBVLod.getLevel();
        builtRenderDistance = renderDistance;
        builtCacheVersion = VBVLodCache.version();
        builtCamX = camera.x;
        builtCamZ = camera.z;
        builtAtMs = now;
        originX = Mth.floor(camera.x / 16.0) * 16.0;
        originZ = Mth.floor(camera.z / 16.0) * 16.0;

        try (ByteBufferBuilder bytes = new ByteBufferBuilder(1 << 20)) {
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
            Mesher mesher = new Mesher(builder, camera, renderDistance, viewX, viewZ, cull);
            long started = System.nanoTime();
            mesher.run();
            lastBuildMs = (System.nanoTime() - started) / 1_000_000L;
            quadCount = mesher.quads;
            MeshData mesh = builder.build();
            if (mesh == null) {
                quadCount = 0;
                vertexBytes = 0L;
                return;
            }
            try (MeshData ignored = mesh) {
                ByteBuffer data = mesh.vertexBuffer();
                vertexBytes = data.remaining();
                ensureCapacity(vertexBytes);
                RenderSystem.getDevice().createCommandEncoder().writeToBuffer(vertexBuffer.slice(0L, vertexBytes), data);
            }
        }
    }

    private static void ensureCapacity(long bytes) {
        if (vertexBuffer != null && vertexBufferSize >= bytes) {
            return;
        }
        releaseBuffer();
        long size = Math.max(1L << 16, Long.highestOneBit(Math.max(1L, bytes - 1L)) << 1);
        vertexBuffer = RenderSystem.getDevice()
            .createBuffer(() -> "VBV LOD vertex buffer", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, size);
        vertexBufferSize = size;
    }

    /**
     * One line for the FPS meter: how large the far-terrain mesh is and what the last rebuild cost.
     * Null while LOD is off, so the meter does not show an empty row.
     */
    public static String statsLine() {
        if (!VBVLod.isActive() || quadCount <= 0) {
            return null;
        }
        return "LOD: " + quadCount + "/" + quadBudget + " quads, last build " + lastBuildMs + " ms";
    }

    /** Multi-line state for the Diagnostics screen. */
    public static String diagnostics() {
        StringBuilder out = new StringBuilder();
        out.append("LOD: ").append(VBVLod.name());
        String failure = VBVLod.failure();
        if (!failure.isEmpty()) {
            out.append(" (switched off: ").append(failure).append(')');
        }
        out.append('\n');
        out.append("Mesh: ").append(quadCount).append("/").append(quadBudget).append(" quads, last rebuild ").append(lastBuildMs).append(" ms");
        out.append(VBVLod.isActive() && builtCull ? " | back-of-camera culling on" : "").append('\n');
        out.append("Terrain samples: ").append(VBVLodCache.size()).append(" chunks remembered, ")
            .append(VBVLodCache.persistedChunks()).append(" restored from storage");
        return out.toString();
    }

    private static void releaseBuffer() {
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
            vertexBufferSize = 0L;
        }
        quadCount = 0;
        vertexBytes = 0L;
    }

    /** Starts remembering the chunks the client already holds, a slice per frame (see {@link #scanStep}). */
    private static void beginScan(int centerChunkX, int centerChunkZ) {
        scanLevel = scannedLevel;
        scanCenterX = centerChunkX;
        scanCenterZ = centerChunkZ;
        scanCursorX = -INITIAL_SCAN_RADIUS;
        scanCursorZ = -INITIAL_SCAN_RADIUS;
    }

    /**
     * Remembers up to {@link #SCAN_CHUNKS_PER_FRAME} chunks of that area per frame, so switching LOD on
     * mid-game or arriving in a new world costs a few frames of small work instead of one long stall.
     */
    private static void scanStep(ClientLevel level) {
        if (scanLevel != level || scanCursorZ > INITIAL_SCAN_RADIUS) {
            return;
        }
        for (int i = 0; i < SCAN_CHUNKS_PER_FRAME; i++) {
            if (scanCursorZ > INITIAL_SCAN_RADIUS) {
                return;
            }
            int dx = scanCursorX;
            int dz = scanCursorZ;
            if (++scanCursorX > INITIAL_SCAN_RADIUS) {
                scanCursorX = -INITIAL_SCAN_RADIUS;
                scanCursorZ++;
            }
            LevelChunk chunk = level.getChunkSource().getChunk(scanCenterX + dx, scanCenterZ + dz, false);
            if (chunk != null) {
                VBVLodCache.capture(level, chunk);
            }
        }
    }

    /** Turns the remembered columns into quads, outermost ring first so alpha blending layers correctly. */
    private static final class Mesher {
        private final BufferBuilder out;
        private final double camX;
        private final double camY;
        private final double camZ;
        private final int renderDistance;
        private final boolean useEnvironment;
        private final float rStart;
        private final float rEnd;
        /** Camera forward on the ground plane, and whether it is steady enough to cull behind it. */
        private final double viewX;
        private final double viewZ;
        private final boolean cull;
        int quads;
        /** Colour of the last {@link #heightAt} probe, so a run can compare it without a second lookup. */
        private int probeRgb;

        Mesher(BufferBuilder out, Vec3 camera, int renderDistance, double viewX, double viewZ, boolean cull) {
            this.out = out;
            this.camX = camera.x;
            this.camY = camera.y;
            this.camZ = camera.z;
            this.renderDistance = renderDistance;
            this.viewX = viewX;
            this.viewZ = viewZ;
            this.cull = cull;
            this.useEnvironment = fogValid && envEnd > envStart && envEnd < 1.0E7F;
            this.rStart = fogValid ? renderStart : VBVLod.fogStartBlocks();
            this.rEnd = fogValid ? Math.max(renderEnd, 1.0F) : VBVLod.fogEndBlocks();
        }

        void run() {
            int[][] tiers = VBVLod.tiers();
            int camChunkX = Mth.floor(camX) >> 4;
            int camChunkZ = Mth.floor(camZ) >> 4;
            // Chunks this close are drawn by the real renderer; keep a one chunk overlap.
            int innerEdge = Math.max(0, renderDistance - 2);
            for (int t = tiers.length - 1; t >= 0 && quads < quadBudget; t--) {
                int outer = tiers[t][0];
                int size = tiers[t][1];
                int inner = t == 0 ? innerEdge : Math.max(tiers[t - 1][0], innerEdge);
                if (outer <= inner) {
                    continue;
                }
                int half = size / 2;
                int minCellX = Math.floorDiv(Mth.floor(camX) - outer * 16, size);
                int maxCellX = Math.floorDiv(Mth.floor(camX) + outer * 16, size);
                int minCellZ = Math.floorDiv(Mth.floor(camZ) - outer * 16, size);
                int maxCellZ = Math.floorDiv(Mth.floor(camZ) + outer * 16, size);
                for (int cellZ = minCellZ; cellZ <= maxCellZ && quads < quadBudget; cellZ++) {
                    for (int cellX = minCellX; cellX <= maxCellX && quads < quadBudget; cellX++) {
                        int centerX = cellX * size + half;
                        int centerZ = cellZ * size + half;
                        if (!select(centerX, centerZ, size, camChunkX, camChunkZ, inner, outer)) {
                            continue;
                        }
                        int height = heightAt(centerX, centerZ);
                        if (height == VBVLodCache.NO_DATA) {
                            continue;
                        }
                        int rgb = probeRgb;
                        // Flat ground is the common case, so the rest of the row is merged into one
                        // quad while height, colour and distance band stay the same. Walls are still
                        // emitted where a neighbour really steps, so nothing is lost.
                        int run = 1;
                        while (cellX + run <= maxCellX) {
                            int nextX = (cellX + run) * size + half;
                            int nextHeight = heightAt(nextX, centerZ);
                            if (nextHeight != height || probeRgb != rgb) {
                                break;
                            }
                            if (!select(nextX, centerZ, size, camChunkX, camChunkZ, inner, outer)) {
                                break;
                            }
                            run++;
                        }
                        emitRun(cellX * size, cellZ * size, size, run, height, rgb, centerX, centerZ);
                        cellX += run - 1;
                    }
                }
            }
        }

        /**
         * True when the whole cell is behind the camera. {@code 0.75} is above the cell's half diagonal
         * ({@code 0.71}), so a cell that is only partly behind the camera is always kept: culling can
         * only ever remove terrain that cannot be on screen.
         */
        private boolean behind(int centerX, int centerZ, int size) {
            if (!cull) {
                return false;
            }
            double dx = centerX - camX;
            double dz = centerZ - camZ;
            return dx * viewX + dz * viewZ < -size * 0.75;
        }

        /** True when this cell belongs in this ring and is not entirely behind the camera. */
        private boolean select(int centerX, int centerZ, int size, int camChunkX, int camChunkZ, int inner, int outer) {
            int distance = Math.max(Math.abs((centerX >> 4) - camChunkX), Math.abs((centerZ >> 4) - camChunkZ));
            return distance > inner && distance <= outer && !behind(centerX, centerZ, size);
        }

        /** Surface height of the cell whose centre is the given block, or NO_DATA; sets {@link #probeRgb}. */
        private int heightAt(int blockX, int blockZ) {
            VBVLodCache.Column column = VBVLodCache.get(blockX >> 4, blockZ >> 4);
            if (column == null) {
                return VBVLodCache.NO_DATA;
            }
            int index = sampleIndex(blockX, blockZ);
            probeRgb = column.rgb[index];
            return column.height[index];
        }

        /**
         * One merged strip of {@code run} cells: a single top quad, the wall at the far end of the
         * strip and one south wall per cell (the row below can step at any column).
         */
        private void emitRun(int minX, int minZ, int size, int run, int height, int rgb, int centerX, int centerZ) {
            // Sit one block under the real terrain so it never fights the chunks it overlaps.
            double top = height - 1.0;
            double maxX = minX + (double) size * run;
            double maxZ = minZ + size;
            quad(minX, top, minZ, maxX, top, minZ, maxX, top, maxZ, minX, top, maxZ, rgb, 1.0F);

            // Only the far end of the strip faces a cell outside it; inside the strip the heights
            // matched, so there is no step to draw.
            wall(true, maxX, minZ, maxZ, height, rgb, centerX + size * run, centerZ);
            for (int i = 0; i < run && quads < quadBudget; i++) {
                double edgeZ = minZ + size;
                double from = minX + (double) size * i;
                wall(false, edgeZ, from, from + size, height, rgb, centerX + size * i, centerZ + size);
            }
        }

        /** Vertical face on the shared edge with a neighbour that has a different height. */
        private void wall(boolean alongZ, double edge, double from, double to, int height, int rgb, int neighbourX, int neighbourZ) {
            VBVLodCache.Column other = VBVLodCache.get(neighbourX >> 4, neighbourZ >> 4);
            if (other == null) {
                return;
            }
            int otherIndex = sampleIndex(neighbourX, neighbourZ);
            int otherHeight = other.height[otherIndex];
            if (otherHeight == VBVLodCache.NO_DATA || Math.abs(otherHeight - height) < 1) {
                return;
            }
            boolean thisHigher = height > otherHeight;
            double low = Math.min(height, otherHeight) - 1.0;
            double high = Math.max(height, otherHeight) - 1.0;
            int color = thisHigher ? rgb : other.rgb[otherIndex];
            if (alongZ) {
                quad(edge, low, from, edge, low, to, edge, high, to, edge, high, from, color, 0.78F);
            } else {
                quad(from, low, edge, to, low, edge, to, high, edge, from, high, edge, color, 0.86F);
            }
        }

        private static int sampleIndex(int blockX, int blockZ) {
            return ((blockX & 15) >> 2) + (((blockZ & 15) >> 2) << 2);
        }

        private void quad(
            double x0, double y0, double z0, double x1, double y1, double z1,
            double x2, double y2, double z2, double x3, double y3, double z3, int rgb, float shade
        ) {
            float a0 = alpha(x0, y0, z0);
            float a1 = alpha(x1, y1, z1);
            float a2 = alpha(x2, y2, z2);
            float a3 = alpha(x3, y3, z3);
            if (a0 <= 0.01F && a1 <= 0.01F && a2 <= 0.01F && a3 <= 0.01F) {
                return;
            }
            int r = (int) (((rgb >> 16) & 0xFF) * shade);
            int g = (int) (((rgb >> 8) & 0xFF) * shade);
            int b = (int) ((rgb & 0xFF) * shade);
            vertex(x0, y0, z0, r, g, b, a0);
            vertex(x1, y1, z1, r, g, b, a1);
            vertex(x2, y2, z2, r, g, b, a2);
            vertex(x3, y3, z3, r, g, b, a3);
            quads++;
        }

        private void vertex(double x, double y, double z, int r, int g, int b, float alpha) {
            out.addVertex((float) (x - originX), (float) y, (float) (z - originZ)).setColor(r, g, b, (int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F));
        }

        /** Opacity after the same linear fog the real terrain uses (render-distance and environmental). */
        private float alpha(double x, double y, double z) {
            double dx = x - camX;
            double dy = y - camY;
            double dz = z - camZ;
            float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            float fog = linear(distance, rStart, rEnd);
            if (useEnvironment) {
                fog = Math.max(fog, linear(distance, envStart, envEnd));
            }
            return 1.0F - fog;
        }

        private static float linear(float distance, float start, float end) {
            if (end <= start) {
                return distance >= end ? 1.0F : 0.0F;
            }
            return Mth.clamp((distance - start) / (end - start), 0.0F, 1.0F);
        }
    }
}
