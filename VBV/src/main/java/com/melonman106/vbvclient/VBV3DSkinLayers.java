package com.melonman106.vbvclient;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.core.Direction;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import org.joml.Vector3f;

/**
 * 3D Skin Layers: pushes every drawn pixel of the outer layer out into a real cube.
 *
 * <p>Inflating the layer box (vanilla does it by 0.25 px, this class used to do it by 1-3 px)
 * only ever moves the whole texture outward: the faces stay flat and nothing about the layer
 * gains volume. A real 3D skin layer is built from voxels instead. The skin image is read once
 * when it is uploaded, and every pixel of the jacket, sleeves, pants and hat that has some
 * alpha becomes a 1x1xt cube standing on the body surface. Because the mask is per skin, a
 * pixel that the artist left transparent simply has no cube, so cut-outs, belts, hair and
 * sleeve ends get their own visible side walls.</p>
 *
 * <p>Only the outer face of a cube and the four walls that border a transparent neighbour are
 * emitted, which is far cheaper than drawing a box per pixel: a full skin is a few hundred
 * quads, not thousands. The quads are cached per (skin, slim/wide, thickness) and rendered in
 * the layer part's own pose, so animation and armour behave exactly as before.</p>
 *
 * <p>The buffered image is not available while the model is baked, so the model keeps the old
 * six-slab shell as a fallback. The moment a skin's mask is known its voxel mesh replaces the
 * shell for that player only; the model itself is shared and never modified.</p>
 */
public final class VBV3DSkinLayers {
    /** Vanilla's own outer-layer growth, used when the feature is off. */
    private static final float VANILLA_THICKNESS = 0.25F;
    /** Vanilla's hat shell is thicker than the body's. */
    public static final float VANILLA_HAT_THICKNESS = 0.5F;
    /** Whole skin pixels, so the slab edges land on texture pixel boundaries. */
    private static final float[] THICKNESS = {1.0F, 2.0F, 3.0F};

    /** Skin images are 64 x 64; legacy 64 x 32 images use the same top half. */
    private static final int SKIN = 64;
    /** Below this alpha a pixel is treated as transparent, like the resource pack cut-outs. */
    private static final int SOLID_ALPHA = 24;
    /** x, y, z, u, v for four corners, then the quad's normal. */
    private static final int STRIDE = 23;
    private static final int MAX_MASKS = 128;
    private static final int MAX_SKINS = 24;
    private static final float PIXEL = 1.0F / 16.0F;

    /**
     * The player model is drawn with culling off, so a slab's wall that faces the body would be drawn too. It lies
     * exactly on the body surface and samples a different part of the skin than the face in front of it, so wherever
     * the outer pixel is transparent it would paint the wrong texel (or flicker against the body). Each fallback slab
     * is therefore built without its inner wall. Model-space names: NORTH is -Z, SOUTH +Z, WEST -X, EAST +X, DOWN -Y
     * (the top of the body, since model Y points down) and UP +Y.
     */
    private static final Set<Direction> NO_SOUTH = allBut(Direction.SOUTH);
    private static final Set<Direction> NO_NORTH = allBut(Direction.NORTH);
    private static final Set<Direction> NO_EAST = allBut(Direction.EAST);
    private static final Set<Direction> NO_WEST = allBut(Direction.WEST);
    private static final Set<Direction> NO_UP = allBut(Direction.UP);
    private static final Set<Direction> NO_DOWN = allBut(Direction.DOWN);

    public static boolean enabled = true;
    public static int thickness;

    /** Per-skin alpha masks, keyed by the skin texture's identifier. */
    private static final Map<String, byte[]> MASKS = new LinkedHashMap<String, byte[]>(32, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<String, byte[]> eldest) {
            return this.size() > MAX_MASKS;
        }
    };
    /** Built voxel meshes, keyed by skin + model + thickness. */
    private static final Map<String, Skin> SKINS = new LinkedHashMap<String, Skin>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<String, Skin> eldest) {
            return this.size() > MAX_SKINS;
        }
    };

    /** The layer parts of the player whose model is being rendered right now, or null. */
    private static ModelPart[] activeParts;
    private static Mesh[] activeMeshes;
    private static final Vector3f NORMAL_IN = new Vector3f();
    private static final Vector3f NORMAL_OUT = new Vector3f();
    private static final Vector3f POSITION_OUT = new Vector3f();

    private VBV3DSkinLayers() {}

    private static Set<Direction> allBut(Direction hidden) {
        Set<Direction> sides = EnumSet.allOf(Direction.class);
        sides.remove(hidden);
        return Collections.unmodifiableSet(sides);
    }

    /** Outer-layer growth in model pixels, exposed for the config screen. */
    public static float overlayThickness() {
        if (!enabled) return VANILLA_THICKNESS;
        return THICKNESS[Math.max(0, Math.min(THICKNESS.length - 1, thickness))];
    }

    public static void cycleThickness() {
        thickness = (thickness + 1) % THICKNESS.length;
        SKINS.clear();
    }

    public static String thicknessLabel() {
        return String.format(java.util.Locale.ROOT, "%.0f", overlayThickness()) + " px";
    }

    public static void loadSettings(boolean on, int level) {
        enabled = on;
        thickness = Math.max(0, Math.min(THICKNESS.length - 1, level));
        SKINS.clear();
    }

    // ------------------------------------------------------------ fallback shell --

    /**
     * Adds one outer skin layer to {@code builder}.
     *
     * <p>The fallback used while a skin's pixel mask is still unknown: six slabs that keep the
     * texture's own size and stick out by the configured thickness. Once the mask exists the
     * voxel mesh replaces this layer for that player.</p>
     *
     * @param x,y,z     the layer box's minimum corner, exactly as vanilla passes it to addBox
     * @param w,h,d     the layer box's size, exactly as vanilla passes it to addBox
     * @param u,v       the layer texture's texOffs, exactly as vanilla passes it
     * @param base      the body deformation the model was built with (vanilla's {@code scale})
     * @param vanilla   the growth vanilla used for this layer (0.25, or 0.5 for the hat)
     */
    public static CubeListBuilder layer(
        CubeListBuilder builder,
        float x, float y, float z,
        float w, float h, float d,
        int u, int v,
        CubeDeformation base,
        float vanilla
    ) {
        if (!enabled) {
            return builder.texOffs(u, v).addBox(x, y, z, w, h, d, base.extend(vanilla));
        }

        float t = overlayThickness();
        int ti = Math.round(t);
        int iw = Math.round(w);
        int ih = Math.round(h);
        int id = Math.round(d);

        // Where each face of the box lives in the skin texture, from Minecraft's own box layout.
        int frontU = u + id;
        int frontV = v + id;
        int sideU = u;
        int sideV = v + id;
        int rightU = u + id + iw;
        int backU = u + 2 * id + iw;

        // Each slab's texOffs is chosen so that its outward face maps onto that exact region; the
        // neighbouring pixels it samples on its narrow walls are the layer's own side texture.
        // Each slab leaves out the wall that faces the body (see NO_SOUTH and friends).
        // -Z (front)
        builder.texOffs(frontU - ti, frontV - ti).addBox(x, y, z - t, w, h, t, NO_SOUTH);
        // +Z (back)
        builder.texOffs(backU - iw - 2 * ti, frontV - ti).addBox(x, y, z + d, w, h, t, NO_NORTH);
        // -X and +X (sides)
        builder.texOffs(sideU, sideV - id).addBox(x - t, y, z, t, h, d, NO_EAST);
        builder.texOffs(rightU - id - ti, sideV - id).addBox(x + w, y, z, t, h, d, NO_WEST);
        // -Y and +Y (top and bottom in the model's down-is-positive space)
        builder.texOffs(frontU - id, v).addBox(x, y - t, z, w, t, d, NO_UP);
        builder.texOffs(u, v).addBox(x, y + h, z, w, t, d, NO_DOWN);
        return builder;
    }

    // ---------------------------------------------------------------- skin masks --

    /** Remembers a skin's alpha mask while its image is still readable. */
    public static void capture(final Identifier texture, final NativeImage image) {
        if (texture == null || image == null) return;
        int w = image.getWidth();
        int h = image.getHeight();
        if (w != SKIN || (h != SKIN && h != 32)) return;

        byte[] alpha = new byte[SKIN * SKIN];
        try {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    alpha[y * SKIN + x] = image.getLuminanceOrAlpha(x, y);
                }
            }
        } catch (RuntimeException unsupportedFormat) {
            return;
        }

        String key = texture.toString();
        MASKS.put(key, alpha);
        SKINS.keySet().removeIf(entry -> entry.startsWith(key + '|'));
    }

    /**
     * Remembers the mask of an Eaglercraft skin as it is registered. Eaglercraft's multiplayer skins
     * ({@code eagler:multiplayer/skin_N}) and custom skins ({@code eagler:skins/custom/tex_N}) are plain
     * DynamicTextures, so they never pass through {@link #capture} via ReloadableTexture or the skin downloader,
     * and without this hook only the built-in Steve and Alex skins would ever get the voxel layer. The pixels are
     * already filled in when the texture is registered. Capes, favicons, map and lightmap textures are ignored.
     */
    public static void captureDynamic(final Identifier location, final AbstractTexture texture) {
        if (location == null || !(texture instanceof DynamicTexture)) return;
        if (!"eagler".equals(location.getNamespace())) return;
        String path = location.getPath();
        if (!path.startsWith("multiplayer/skin_") && !path.startsWith("skins/")) return;
        capture(location, ((DynamicTexture)texture).getPixels());
    }

    // ---------------------------------------------------------------- voxel mesh --

    /** The six layer boxes of the player model, in each part's own pixel space. */
    private static final Part HAT = new Part(-4, -8, -4, 8, 8, 8, 32, 0);
    private static final Part JACKET = new Part(-4, 0, -2, 8, 12, 4, 16, 32);
    private static final Part LEFT_PANTS = new Part(-2, 0, -2, 4, 12, 4, 0, 48);
    private static final Part RIGHT_PANTS = new Part(-2, 0, -2, 4, 12, 4, 0, 32);
    private static final Part WIDE_LEFT_SLEEVE = new Part(-1, -2, -2, 4, 12, 4, 48, 48);
    private static final Part WIDE_RIGHT_SLEEVE = new Part(-3, -2, -2, 4, 12, 4, 40, 32);
    private static final Part SLIM_LEFT_SLEEVE = new Part(-1, -2, -2, 3, 12, 4, 48, 48);
    private static final Part SLIM_RIGHT_SLEEVE = new Part(-2, -2, -2, 3, 12, 4, 40, 32);

    private static final Part[] WIDE = {HAT, JACKET, WIDE_LEFT_SLEEVE, WIDE_RIGHT_SLEEVE, LEFT_PANTS, RIGHT_PANTS};
    private static final Part[] SLIM = {HAT, JACKET, SLIM_LEFT_SLEEVE, SLIM_RIGHT_SLEEVE, LEFT_PANTS, RIGHT_PANTS};

    private static final class Part {
        final float x;
        final float y;
        final float z;
        final int w;
        final int h;
        final int d;
        final int u;
        final int v;

        Part(final float x, final float y, final float z, final int w, final int h, final int d, final int u, final int v) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.w = w;
            this.h = h;
            this.d = d;
            this.u = u;
            this.v = v;
        }
    }

    private static final class Mesh {
        final float[] data;

        Mesh(final float[] data) {
            this.data = data;
        }
    }

    /** The voxel layer of one skin, split into the six model parts it renders with. */
    private static final class Skin {
        final Mesh hat;
        final Mesh jacket;
        final Mesh leftSleeve;
        final Mesh rightSleeve;
        final Mesh leftPants;
        final Mesh rightPants;

        Skin(final Mesh hat, final Mesh jacket, final Mesh leftSleeve, final Mesh rightSleeve, final Mesh leftPants, final Mesh rightPants) {
            this.hat = hat;
            this.jacket = jacket;
            this.leftSleeve = leftSleeve;
            this.rightSleeve = rightSleeve;
            this.leftPants = leftPants;
            this.rightPants = rightPants;
        }
    }

    /** Collects the quads of one part. */
    private static final class QuadSink {
        private float[] data = new float[STRIDE * 256];
        private int count;

        void quad(
            final float x0, final float y0, final float z0,
            final float x1, final float y1, final float z1,
            final float x2, final float y2, final float z2,
            final float x3, final float y3, final float z3,
            final float u0, final float v0,
            final float u1, final float v1,
            final float u2, final float v2,
            final float u3, final float v3,
            final float nx, final float ny, final float nz
        ) {
            int at = this.count * STRIDE;
            if (at + STRIDE > this.data.length) {
                this.data = Arrays.copyOf(this.data, this.data.length * 2);
            }
            float[] d = this.data;
            d[at] = x0;
            d[at + 1] = y0;
            d[at + 2] = z0;
            d[at + 3] = u0;
            d[at + 4] = v0;
            d[at + 5] = x1;
            d[at + 6] = y1;
            d[at + 7] = z1;
            d[at + 8] = u1;
            d[at + 9] = v1;
            d[at + 10] = x2;
            d[at + 11] = y2;
            d[at + 12] = z2;
            d[at + 13] = u2;
            d[at + 14] = v2;
            d[at + 15] = x3;
            d[at + 16] = y3;
            d[at + 17] = z3;
            d[at + 18] = u3;
            d[at + 19] = v3;
            d[at + 20] = nx;
            d[at + 21] = ny;
            d[at + 22] = nz;
            this.count++;
        }

        float[] toArray() {
            int used = this.count * STRIDE;
            return used == this.data.length ? this.data : Arrays.copyOf(this.data, used);
        }
    }

    private static boolean solid(final byte[] alpha, final int tu, final int tv) {
        return tu >= 0 && tv >= 0 && tu < SKIN && tv < SKIN && (alpha[tv * SKIN + tu] & 0xFF) >= SOLID_ALPHA;
    }

    /**
     * Emits one quad with the winding Minecraft's model cubes use, so back-face culling and the
     * light normal behave exactly like the vanilla boxes.
     */
    private static void plane(
        final QuadSink out,
        final float px, final float py, final float pz,
        final float ux, final float uy, final float uz,
        final float vx, final float vy, final float vz,
        final float nx, final float ny, final float nz,
        final int tu, final int tv
    ) {
        float cx = uy * vz - uz * vy;
        float cy = uz * vx - ux * vz;
        float cz = ux * vy - uy * vx;
        float u0 = tu / (float)SKIN;
        float v0 = tv / (float)SKIN;
        float u1 = (tu + 1) / (float)SKIN;
        float v1 = (tv + 1) / (float)SKIN;
        if (cx * nx + cy * ny + cz * nz > 0.0F) {
            out.quad(
                px, py, pz,
                px + ux, py + uy, pz + uz,
                px + ux + vx, py + uy + vy, pz + uz + vz,
                px + vx, py + vy, pz + vz,
                u0, v0, u1, v0, u1, v1, u0, v1,
                nx, ny, nz
            );
        } else {
            out.quad(
                px, py, pz,
                px + vx, py + vy, pz + vz,
                px + ux + vx, py + uy + vy, pz + uz + vz,
                px + ux, py + uy, pz + uz,
                u0, v0, u0, v1, u1, v1, u1, v0,
                nx, ny, nz
            );
        }
    }

    /**
     * Extrudes one face of a layer box.
     *
     * <p>{@code tu0/tv0} is the texel at {@code (i = 0, j = 0)}, the four step values map texels
     * to the two tangent axes, {@code p} is that texel's outer corner and {@code u}/{@code v}
     * are the tangent axes. Each opaque texel becomes one outer quad plus a wall for every edge
     * whose neighbour is not part of the layer.</p>
     */
    private static void face(
        final QuadSink out,
        final byte[] alpha,
        final int tu0, final int tv0,
        final int tuI, final int tvI,
        final int tuJ, final int tvJ,
        final int tw, final int th,
        final float px, final float py, final float pz,
        final float ux, final float uy, final float uz,
        final float vx, final float vy, final float vz,
        final float nx, final float ny, final float nz,
        final int t
    ) {
        float bx = -nx * t;
        float by = -ny * t;
        float bz = -nz * t;
        for (int j = 0; j < th; j++) {
            for (int i = 0; i < tw; i++) {
                int tu = tu0 + i * tuI + j * tuJ;
                int tv = tv0 + i * tvI + j * tvJ;
                if (!solid(alpha, tu, tv)) continue;

                float x = px + i * ux + j * vx;
                float y = py + i * uy + j * vy;
                float z = pz + i * uz + j * vz;
                plane(out, x, y, z, ux, uy, uz, vx, vy, vz, nx, ny, nz, tu, tv);

                if (i == 0 || !solid(alpha, tu - tuI, tv - tvI)) {
                    plane(out, x, y, z, vx, vy, vz, bx, by, bz, -ux, -uy, -uz, tu, tv);
                }
                if (i + 1 >= tw || !solid(alpha, tu + tuI, tv + tvI)) {
                    plane(out, x + ux, y + uy, z + uz, vx, vy, vz, bx, by, bz, ux, uy, uz, tu, tv);
                }
                if (j == 0 || !solid(alpha, tu - tuJ, tv - tvJ)) {
                    plane(out, x, y, z, ux, uy, uz, bx, by, bz, -vx, -vy, -vz, tu, tv);
                }
                if (j + 1 >= th || !solid(alpha, tu + tuJ, tv + tvJ)) {
                    plane(out, x + vx, y + vy, z + vz, ux, uy, uz, bx, by, bz, vx, vy, vz, tu, tv);
                }
            }
        }
    }

    private static Mesh build(final byte[] alpha, final Part part, final int t) {
        QuadSink out = new QuadSink();
        int x = Math.round(part.x);
        int y = Math.round(part.y);
        int z = Math.round(part.z);
        int u = part.u;
        int v = part.v;
        int w = part.w;
        int h = part.h;
        int d = part.d;

        // -Z (front of the body)
        face(out, alpha, u + d, v + d, 1, 0, 0, 1, w, h,
            x, y, z - t, 1, 0, 0, 0, 1, 0, 0, 0, -1, t);
        // +Z (back); like the -X side, its texture's u axis runs against the tangent axis (+X), so the first
        // texel in model order is the last one of the strip. Vanilla's SOUTH polygon maps minX to the high u.
        face(out, alpha, u + 2 * d + 2 * w - 1, v + d, -1, 0, 0, 1, w, h,
            x, y, z + d + t, 1, 0, 0, 0, 1, 0, 0, 0, 1, t);
        // -X (left side); the side texture's u axis runs against +Z
        face(out, alpha, u + d - 1, v + d, -1, 0, 0, 1, d, h,
            x - t, y, z, 0, 0, 1, 0, 1, 0, -1, 0, 0, t);
        // +X (right side)
        face(out, alpha, u + d + w, v + d, 1, 0, 0, 1, d, h,
            x + w + t, y, z, 0, 0, 1, 0, 1, 0, 1, 0, 0, t);
        // -Y (top of the part); the top strip's v axis runs against +Z
        face(out, alpha, u + d, v + d - 1, 1, 0, 0, -1, w, d,
            x, y - t, z, 1, 0, 0, 0, 0, 1, 0, -1, 0, t);
        // +Y (bottom of the part); its strip runs against +Z, exactly like the top one
        face(out, alpha, u + d + w, v + d - 1, 1, 0, 0, -1, w, d,
            x, y + h + t, z, 1, 0, 0, 0, 0, 1, 0, 1, 0, t);
        return new Mesh(out.toArray());
    }

    private static Skin skin(final String key, final boolean slim, final int t) {
        String cacheKey = key + '|' + (slim ? 's' : 'w') + '|' + t;
        Skin cached = SKINS.get(cacheKey);
        if (cached != null) return cached;
        byte[] alpha = MASKS.get(key);
        if (alpha == null) return null;

        Part[] parts = slim ? SLIM : WIDE;
        Skin skin = new Skin(
            build(alpha, parts[0], t),
            build(alpha, parts[1], t),
            build(alpha, parts[2], t),
            build(alpha, parts[3], t),
            build(alpha, parts[4], t),
            build(alpha, parts[5], t)
        );
        SKINS.put(cacheKey, skin);
        return skin;
    }

    // ------------------------------------------------------------- render hooks --

    /** Selects the voxel layer of the player whose model is about to be drawn. */
    public static void beginModel(final Object model, final Object state) {
        activeParts = null;
        activeMeshes = null;
        if (!enabled) return;
        if (!(model instanceof PlayerModel) || !(state instanceof AvatarRenderState)) return;
        PlayerModel player = (PlayerModel)model;
        AvatarRenderState avatar = (AvatarRenderState)state;
        try {
            Identifier texture = avatar.skin.body().texturePath();
            if (texture == null) return;
            Skin skin = skin(texture.toString(), avatar.skin.model() == PlayerModelType.SLIM, Math.round(overlayThickness()));
            if (skin == null) return;
            activeParts = new ModelPart[] {
                player.hat, player.jacket, player.leftSleeve, player.rightSleeve, player.leftPants, player.rightPants
            };
            activeMeshes = new Mesh[] {
                skin.hat, skin.jacket, skin.leftSleeve, skin.rightSleeve, skin.leftPants, skin.rightPants
            };
        } catch (RuntimeException ignored) {
            activeParts = null;
            activeMeshes = null;
        }
    }

    /** Forgets the per-player layer again, so the shared model is never left pointing at a skin. */
    public static void endModel() {
        activeParts = null;
        activeMeshes = null;
    }

    /**
     * Draws the active player's voxel layer in place of this part's baked fallback shell.
     *
     * @return true when this part was rendered here, false to let the baked cubes draw
     */
    public static boolean renderVoxelLayer(
        final ModelPart part,
        final PoseStack.Pose pose,
        final VertexConsumer buffer,
        final int lightCoords,
        final int overlayCoords,
        final int color
    ) {
        ModelPart[] parts = activeParts;
        if (parts == null) return false;
        Mesh mesh = null;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] == part) {
                mesh = activeMeshes[i];
                break;
            }
        }
        if (mesh == null) return false;

        float[] data = mesh.data;
        for (int at = 0; at < data.length; at += STRIDE) {
            NORMAL_IN.set(data[at + 20], data[at + 21], data[at + 22]);
            Vector3f normal = pose.transformNormal(NORMAL_IN, NORMAL_OUT);
            float nx = normal.x();
            float ny = normal.y();
            float nz = normal.z();
            for (int corner = 0; corner < 4; corner++) {
                int o = at + corner * 5;
                pose.pose().transformPosition(data[o] * PIXEL, data[o + 1] * PIXEL, data[o + 2] * PIXEL, POSITION_OUT);
                buffer.addVertex(
                    POSITION_OUT.x(), POSITION_OUT.y(), POSITION_OUT.z(),
                    color, data[o + 3], data[o + 4], overlayCoords, lightCoords, nx, ny, nz
                );
            }
        }
        return true;
    }
}
