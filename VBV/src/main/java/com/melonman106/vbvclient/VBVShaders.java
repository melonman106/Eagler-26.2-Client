package com.melonman106.vbvclient;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.FogType;

/**
 * VBV Shaders: one post-processing effect, <b>Sun Rays</b>.
 *
 * <p>The world is already on screen when this runs, so the effect never touches geometry or
 * lighting: it walks a handful of samples along the line from each pixel towards the sun, keeps
 * the ones that land on sky (the depth buffer says sky is exactly the far plane), and adds the
 * result back as a warm, saturated glow with a soft core around the sun. Everything the old
 * presets did - toon banding, vibrance, contrast, warmth, vignette, outlines and the screen-space
 * shadows - has been removed.</p>
 *
 * <p>Cost is one screen-sized pass with 6 / 10 / 14 depth taps depending on the quality setting,
 * so it is cheaper than the old Vibrant pass at Low and Medium. The FPS guard from before is still
 * here: a preset that cannot hold the frame rate switches itself off instead of costing frames.</p>
 *
 * <p>The shader reads its settings from a tiny 8x1 data texture that is rewritten every frame
 * (sun direction in view space, strength, quality, field of view). If anything fails, the effect
 * switches itself off and the game keeps rendering normally.</p>
 */
public final class VBVShaders {
    public static final int OFF = 0;
    public static final int SUN_RAYS = 1;

    private static final Identifier DATA_ID =
        Identifier.fromNamespaceAndPath("vbvclient", "textures/effect/vbv_data.png");
    private static final Identifier RAYS_CHAIN = Identifier.fromNamespaceAndPath("vbvclient", "vbv_sunrays");
    private static final int[] STRENGTH_STEPS = { 50, 75, 100, 125, 150 };

    private static int preset = OFF;
    private static int quality = 2;
    private static int strength = 100;

    private static boolean raysFailed;
    private static String lastError = "";

    /**
     * The performance guard. A post chain is at least one more full-screen pass over the frame.
     * Rather than slow the game down on a machine that cannot afford it, the guard watches the
     * smoothed FPS and, after a few seconds below {@link #GUARD_FPS}, turns the effect off for the
     * session (the player can switch it back on, which re-arms the guard).
     */
    private static final int GUARD_FPS = 40;
    private static final long GUARD_MS = 4000L;
    private static boolean guardEnabled = true;
    private static boolean guardTripped;
    private static long lowFpsSince;

    private static DynamicTexture dataTexture;
    private static float skyExposure = -1.0F;

    private VBVShaders() {}

    // ----- settings -----

    public static int getPreset() { return preset; }

    public static void setPreset(int value) {
        int clamped = value <= OFF ? OFF : SUN_RAYS;
        if (clamped != preset) {
            preset = clamped;
            // Give the effect another chance when the player picks it again.
            raysFailed = false;
            lastError = "";
            // Picking it by hand also re-arms the performance guard.
            guardTripped = false;
            lowFpsSince = 0L;
        }
    }

    public static void cyclePreset() { setPreset(preset == SUN_RAYS ? OFF : SUN_RAYS); }

    public static String presetName() {
        return preset == SUN_RAYS ? "Sun Rays" : "Off";
    }

    public static int getQuality() { return quality; }
    public static void setQuality(int value) { quality = Math.max(1, Math.min(3, value)); }
    public static void cycleQuality() { quality = quality >= 3 ? 1 : quality + 1; }
    public static String qualityName() {
        return switch (quality) {
            case 1 -> "Low (6 rays)";
            case 3 -> "High (14 rays)";
            default -> "Medium (10 rays)";
        };
    }

    public static int getStrength() { return strength; }

    public static void setStrength(int percent) {
        int best = STRENGTH_STEPS[0];
        for (int step : STRENGTH_STEPS) {
            if (Math.abs(step - percent) < Math.abs(best - percent)) best = step;
        }
        strength = best;
    }

    public static void cycleStrength() {
        for (int i = 0; i < STRENGTH_STEPS.length; i++) {
            if (STRENGTH_STEPS[i] == strength) {
                strength = STRENGTH_STEPS[(i + 1) % STRENGTH_STEPS.length];
                return;
            }
        }
        strength = 100;
    }

    /** Turns the FPS guard on or off; switching the preset re-arms a guard that has tripped. */
    public static boolean isGuardEnabled() { return guardEnabled; }

    public static void toggleGuard() {
        setGuard(!guardEnabled);
    }

    public static void setGuard(boolean value) {
        guardEnabled = value;
        guardTripped = false;
        lowFpsSince = 0L;
    }

    public static boolean guardTripped() { return guardTripped; }

    /**
     * Called about four times a second with the smoothed FPS. Below {@link #GUARD_FPS} for
     * {@link #GUARD_MS} the effect switches itself off once, so the shader can never be the reason
     * the game is unplayable.
     */
    public static void tickGuard(int fps) {
        if (preset == OFF || !guardEnabled || guardTripped || fps <= 0) {
            lowFpsSince = 0L;
            return;
        }
        long now = System.currentTimeMillis();
        if (fps >= GUARD_FPS) {
            lowFpsSince = 0L;
            return;
        }
        if (lowFpsSince == 0L) {
            lowFpsSince = now;
            return;
        }
        if (now - lowFpsSince >= GUARD_MS) {
            guardTripped = true;
            lowFpsSince = 0L;
            preset = OFF;
            System.err.println("[VBV Shaders] turned off: below " + GUARD_FPS + " FPS for " + GUARD_MS / 1000 + " s");
            VBVConfigStore.saveAll();
        }
    }

    /** Short note for the settings screens when the effect had to switch itself off, or "". */
    public static String statusNote() {
        if (guardTripped) return "Sun rays were turned off to protect the frame rate";
        if (preset == SUN_RAYS && raysFailed) return "Sun rays unavailable here (turned themselves off)";
        return "";
    }

    public static String lastError() { return lastError; }

    // ----- rendering -----

    /**
     * Called once per frame from GameRenderer.renderLevel, after the world is drawn and before
     * the hand. Never throws: any problem switches the effect off instead.
     */
    public static void apply(Minecraft mc, RenderTarget main, GraphicsResourceAllocator pool,
                             LevelRenderState levelState, boolean supported) {
        if (preset == OFF) {
            skyExposure = -1.0F;
            return;
        }
        if (raysFailed) return;
        try {
            if (mc.level == null) return;
            CameraRenderState camera = levelState.cameraRenderState;
            if (!camera.initialized || camera.isPanoramicMode) return;
            if (!supported) {
                fail("this graphics backend cannot run post effects");
                return;
            }
            if (!ensureDataTexture(mc)) return;
            writeData(mc, camera, levelState.skyRenderState);
            PostChain chain = mc.getShaderManager().getOptionalPostChain(RAYS_CHAIN, LevelTargetBundle.MAIN_TARGETS);
            if (chain == null) {
                fail("the effect did not compile on this graphics card");
                return;
            }
            chain.process(main, pool);
        } catch (Throwable error) {
            fail("renderer error: " + error);
        }
    }

    private static void fail(String reason) {
        raysFailed = true;
        lastError = reason;
        System.err.println("[VBV Shaders] Sun Rays: " + lastError + "; switched off.");
    }

    private static boolean ensureDataTexture(Minecraft mc) {
        if (dataTexture != null) {
            try {
                dataTexture.getTextureView();
                return true;
            } catch (IllegalStateException closed) {
                dataTexture = null;
            }
        }
        DynamicTexture created = new DynamicTexture("vbv_shader_data", 8, 1, true);
        // The post chain looks this id up by itself when it loads, so it has to be registered first.
        mc.getTextureManager().register(DATA_ID, created);
        dataTexture = created;
        return true;
    }

    private static void writeData(Minecraft mc, CameraRenderState camera, SkyRenderState sky) {
        boolean overworld = sky.skybox == DimensionType.Skybox.OVERWORLD;
        float[] sunWorld = VBVShaderMath.sunWorld(sky.sunAngle);
        float[] sunView = VBVShaderMath.toView(sunWorld, camera.yRot, camera.xRot);

        float rain = overworld ? VBVShaderMath.clamp01(1.0F - sky.rainBrightness) : 0.0F;
        float daylight = overworld ? VBVShaderMath.smoothstep(-0.02F, 0.25F, sunWorld[1]) : 0.0F;

        // The rays only make sense where the sky is actually visible: not in caves, not under water.
        float targetExposure = VBVShaderMath.clamp01(mc.level.getBrightness(LightLayer.SKY, camera.blockPos) / 15.0F);
        if (camera.fogType == FogType.WATER) targetExposure = 0.0F;
        skyExposure = skyExposure < 0.0F ? targetExposure : skyExposure + (targetExposure - skyExposure) * 0.06F;
        float sunStrength = daylight * (1.0F - 0.85F * rain) * skyExposure;

        float projection = camera.projectionMatrix.m11();
        float tanHalfFov = projection > 0.01F ? 1.0F / projection : 0.7F;

        NativeImage pixels = dataTexture.getPixels();
        // Texel 0: sun direction in view space, remapped to 0..1, with the strength in alpha.
        pixels.setPixelABGR(0, 0, VBVShaderMath.packAbgr(sunView[0] * 0.5F + 0.5F, sunView[1] * 0.5F + 0.5F,
            sunView[2] * 0.5F + 0.5F, sunStrength));
        // Texel 1: quality level as a 0..1 fraction.
        pixels.setPixelABGR(1, 0, VBVShaderMath.toByte(quality / 3.0F) << 24);
        // Texel 4: half the field of view's tangent, 16 bits over 0..4.
        int tan16 = Math.max(0, Math.min(65535, Math.round(tanHalfFov / 4.0F * 65535.0F)));
        pixels.setPixelABGR(4, 0, (((tan16 >> 8) & 255)) | ((tan16 & 255) << 8));
        // The last texel's alpha says "this data is real": a never-written texture passes the frame through.
        pixels.setPixelABGR(7, 0, VBVShaderMath.packAbgr(0.0F, 0.0F, 0.0F, 1.0F));
        dataTexture.upload();
    }
}
