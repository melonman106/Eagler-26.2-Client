package com.melonman106.vbvclient;

import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;

/**
 * 3D Skin Layers: turns the player's second skin layer into a box with real thickness.
 *
 * <p>Vanilla draws the jacket, sleeves, pants and hat as one cube inflated by a few tenths of a
 * pixel. Because the whole cube grows, the skin texture is stretched over a larger face and the
 * layer just looks like it was pushed outward. Instead, {@link #layer} builds each layer out of
 * six thin slabs: every slab keeps the skin texture's own face region at exactly the size the
 * artist drew it, and sticks out of the body by the configured thickness. The result is a real
 * shell with visible walls on every silhouette.</p>
 *
 * <p>The player model reads {@link #overlayThickness()} while it is baked, so a change applies on
 * the next resource reload (the config screen has an Apply button).</p>
 */
public final class VBV3DSkinLayers {
    /** Vanilla's own outer-layer growth, used when the feature is off. */
    private static final float VANILLA_THICKNESS = 0.25F;
    /** Vanilla's hat shell is thicker than the body's. */
    public static final float VANILLA_HAT_THICKNESS = 0.5F;
    /** Whole skin pixels, so the slab edges land on texture pixel boundaries. */
    private static final float[] THICKNESS = {1.0F, 2.0F, 3.0F};

    public static boolean enabled = true;
    public static int thickness;

    private VBV3DSkinLayers() {}

    /** Outer-layer growth in model pixels, exposed for the config screen. */
    public static float overlayThickness() {
        if (!enabled) return VANILLA_THICKNESS;
        return THICKNESS[Math.max(0, Math.min(THICKNESS.length - 1, thickness))];
    }

    public static void cycleThickness() {
        thickness = (thickness + 1) % THICKNESS.length;
    }

    public static String thicknessLabel() {
        return String.format(java.util.Locale.ROOT, "%.0f", overlayThickness()) + " px";
    }

    public static void loadSettings(boolean on, int level) {
        enabled = on;
        thickness = Math.max(0, Math.min(THICKNESS.length - 1, level));
    }

    /**
     * Adds one outer skin layer to {@code builder}.
     *
     * <p>With the feature on, the layer becomes six slabs that sit exactly on the body surface and
     * grow outward, so the face textures keep their drawn size and the silhouette shows walls.
     * With it off, the vanilla single inflated box is added unchanged.</p>
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
        // -Z (front)
        builder.texOffs(frontU - ti, frontV - ti).addBox(x, y, z - t, w, h, t);
        // +Z (back)
        builder.texOffs(backU - iw - 2 * ti, frontV - ti).addBox(x, y, z + d, w, h, t);
        // -X and +X (sides)
        builder.texOffs(sideU, sideV - id).addBox(x - t, y, z, t, h, d);
        builder.texOffs(rightU - id - ti, sideV - id).addBox(x + w, y, z, t, h, d);
        // -Y and +Y (top and bottom in the model's down-is-positive space)
        builder.texOffs(frontU - id, v).addBox(x, y - t, z, w, t, d);
        builder.texOffs(u, v).addBox(x, y + h, z, w, t, d);
        return builder;
    }
}
