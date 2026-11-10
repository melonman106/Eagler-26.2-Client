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
    private static final int MAX_QUADS = 160_000;
    private static final int REBUILD_DISTANCE = 48;
    private static final long MIN_REBUILD_MS = 400L;
    private static final long CACHE_SETTLE_MS = 1500L;
    private static final int INITIAL_SCAN_RADIUS = 40;

    private static GpuBuffer vertexBuffer;
    private static long vertexBufferSize;
    private static int quadCount;
    private static long vertexBytes;
    private static double originX;
    private static double originZ;

    private static boolean built;
    private static ClientLevel builtLevel;
    private static ClientLevel scannedLevel;
    private static int builtMode = -1;
    private static int builtRenderDistance = -1;
    private static int builtCacheVersion = -1;
    private static double builtCamX;
    private static double builtCamZ;
    private static long builtAtMs;

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
        // Underground, the real rock hides the surface; the LOD would show through gaps in it.
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, camBlockX, camBlockZ);
        if (camera.y < surface - 14.0) {
            return;
        }
        if (scannedLevel != level) {
            scannedLevel = level;
            VBVLodCache.scanLoaded(level, camBlockX >> 4, camBlockZ >> 4, INITIAL_SCAN_RADIUS);
        }

        long now = System.currentTimeMillis();
        double moveX = camera.x - builtCamX;
        double moveZ = camera.z - builtCamZ;
        boolean settingsChanged = !built || builtLevel != level || builtMode != VBVLod.getLevel() || builtRenderDistance != renderDistance;
        boolean moved = moveX * moveX + moveZ * moveZ > (double) REBUILD_DISTANCE * REBUILD_DISTANCE;
        boolean cacheChanged = builtCacheVersion != VBVLodCache.version();
        if (settingsChanged || ((moved || (cacheChanged && now - builtAtMs > CACHE_SETTLE_MS)) && now - builtAtMs > MIN_REBUILD_MS)) {
            rebuild(level, camera, renderDistance, now);
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

    private static void rebuild(ClientLevel level, Vec3 camera, int renderDistance, long now) {
        built = true;
        builtLevel = level;
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
            Mesher mesher = new Mesher(builder, camera, renderDistance);
            mesher.run();
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

    private static void releaseBuffer() {
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
            vertexBufferSize = 0L;
        }
        quadCount = 0;
        vertexBytes = 0L;
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
        int quads;

        Mesher(BufferBuilder out, Vec3 camera, int renderDistance) {
            this.out = out;
            this.camX = camera.x;
            this.camY = camera.y;
            this.camZ = camera.z;
            this.renderDistance = renderDistance;
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
            for (int t = tiers.length - 1; t >= 0 && quads < MAX_QUADS; t--) {
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
                for (int cellZ = minCellZ; cellZ <= maxCellZ && quads < MAX_QUADS; cellZ++) {
                    for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                        int centerX = cellX * size + half;
                        int centerZ = cellZ * size + half;
                        int distance = Math.max(Math.abs((centerX >> 4) - camChunkX), Math.abs((centerZ >> 4) - camChunkZ));
                        if (distance <= inner || distance > outer) {
                            continue;
                        }
                        cell(cellX * size, cellZ * size, size, centerX, centerZ);
                    }
                }
            }
        }

        private void cell(int minX, int minZ, int size, int centerX, int centerZ) {
            VBVLodCache.Column column = VBVLodCache.get(centerX >> 4, centerZ >> 4);
            if (column == null) {
                return;
            }
            int index = sampleIndex(centerX, centerZ);
            int height = column.height[index];
            if (height == VBVLodCache.NO_DATA) {
                return;
            }
            int rgb = column.rgb[index];
            // Sit one block under the real terrain so it never fights the chunks it overlaps.
            double top = height - 1.0;
            double maxX = minX + size;
            double maxZ = minZ + size;
            quad(minX, top, minZ, maxX, top, minZ, maxX, top, maxZ, minX, top, maxZ, rgb, 1.0F);

            // Walls to the east and south neighbours so cliffs are not see-through.
            wall(true, maxX, minZ, maxZ, height, rgb, centerX + size, centerZ);
            wall(false, maxZ, minX, maxX, height, rgb, centerX, centerZ + size);
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
