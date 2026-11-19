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
 * VBV Shaders: two post-processing presets that run on the finished world image, before the hand
 * and the HUD are drawn.
 *
 * <ul>
 *   <li><b>Vibrant</b> - colour only: a cartoon toon band, strong vibrance, a gentle contrast
 *       curve, a little warmth and a vignette. Needs no depth data. At High quality it also draws
 *       a cheap four-tap outline on edges.</li>
 *   <li><b>Shadows</b> - Vibrant plus ambient occlusion and sun contact shadows. These are
 *       screen-space effects computed from the world depth buffer, not real shadow maps: they
 *       darken the ground and walls next to things that block the sun, but only for geometry that is
 *       on screen.</li>
 * </ul>
 *
 * The shaders read their settings (sun direction, field of view, strengths...) from a tiny 8x1
 * data texture that is rewritten every frame. If anything fails, that preset switches itself off
 * (Shadows falls back to Vibrant) and the game keeps rendering normally.
 */
public final class VBVShaders {
    public static final int OFF = 0;
    public static final int VIBRANT = 1;
    public static final int SHADOWS = 2;

    private static final Identifier DATA_ID = Identifier.fromNamespaceAndPath("vbvclient", "textures/effect/vbv_data.png");
    private static final Identifier VIBRANT_CHAIN = Identifier.fromNamespaceAndPath("vbvclient", "vbv_vibrant");
    private static final Identifier SHADOWS_CHAIN = Identifier.fromNamespaceAndPath("vbvclient", "vbv_shadows");
    private static final int[] STRENGTH_STEPS = { 50, 75, 100, 125, 150 };

    private static int preset = OFF;
    private static int quality = 2;
    private static int strength = 100;

    private static boolean shadowsFailed;
    private static boolean vibrantFailed;
    private static String lastError = "";

    /**
     * The performance guard. A post chain cannot be free: it is at least one more full-screen pass
     * over the frame. Rather than slow the game down on a machine that cannot afford it, the guard
     * watches the smoothed FPS and, after a few seconds below {@link #GUARD_FPS}, turns the preset
     * off for the session (the player can switch it back on, which re-arms the guard). It is on by
     * default and can be turned off in the shader options.
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
        int clamped = Math.max(OFF, Math.min(SHADOWS, value));
        if (clamped != preset) {
            preset = clamped;
            // Give a preset another chance when the player picks it again.
            shadowsFailed = false;
            vibrantFailed = false;
            lastError = "";
            // Picking a preset by hand also re-arms the performance guard.
            guardTripped = false;
            lowFpsSince = 0L;
        }
    }

    public static void cyclePreset() { setPreset(preset == SHADOWS ? OFF : preset + 1); }

    public static String presetName() {
        return switch (preset) { case VIBRANT -> "Vibrant"; case SHADOWS -> "Shadows"; default -> "Off"; };
    }

    public static int getQuality() { return quality; }
    public static void setQuality(int value) { quality = Math.max(1, Math.min(3, value)); }
    public static void cycleQuality() { quality = quality >= 3 ? 1 : quality + 1; }
    public static String qualityName() { return switch (quality) { case 1 -> "Low (colour)"; case 3 -> "High (outline)"; default -> "Medium"; }; }

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
     * {@link #GUARD_MS} the preset switches itself off once, so the shader can never be the reason
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

    /** Short note for the settings screens when a preset had to switch itself off, or "". */
    public static String statusNote() {
        if (guardTripped) return "Shaders were turned off to protect the frame rate";
        if (preset == OFF) return "";
        if (preset == SHADOWS && shadowsFailed && !vibrantFailed) return "Shadows unavailable here - using Vibrant";
        if ((preset == SHADOWS && shadowsFailed && vibrantFailed) || (preset == VIBRANT && vibrantFailed)) {
            return "Shaders unavailable here (turned themselves off)";
        }
        return "";
    }

    public static String lastError() { return lastError; }

    // ----- rendering -----

    /**
     * Called once per frame from GameRenderer.renderLevel, after the world is drawn and before
     * the hand. Never throws: any problem switches the preset off instead.
     */
    public static void apply(Minecraft mc, RenderTarget main, GraphicsResourceAllocator pool,
                             LevelRenderState levelState, boolean supported) {
        if (preset == OFF) {
            skyExposure = -1.0F;
            return;
        }
        int effective = preset;
        if (effective == SHADOWS && shadowsFailed) effective = VIBRANT;
        if (effective == VIBRANT && vibrantFailed) return;
        try {
            if (mc.level == null) return;
            CameraRenderState camera = levelState.cameraRenderState;
            if (!camera.initialized || camera.isPanoramicMode) return;
            if (!supported) {
                fail(effective, "this graphics backend cannot run post effects");
                return;
            }
            if (!ensureDataTexture(mc)) return;
            writeData(mc, camera, levelState.skyRenderState, effective);
            PostChain chain = mc.getShaderManager().getOptionalPostChain(
                effective == SHADOWS ? SHADOWS_CHAIN : VIBRANT_CHAIN, LevelTargetBundle.MAIN_TARGETS);
            if (chain == null) {
                fail(effective, "the effect did not compile on this graphics card");
                return;
            }
            chain.process(main, pool);
        } catch (Throwable error) {
            fail(effective, "renderer error: " + error);
        }
    }

    private static void fail(int which, String reason) {
        if (which == SHADOWS) shadowsFailed = true;
        else vibrantFailed = true;
        lastError = (which == SHADOWS ? "Shadows: " : "Vibrant: ") + reason;
        System.err.println("[VBV Shaders] " + lastError + "; switched off.");
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

    private static void writeData(Minecraft mc, CameraRenderState camera, SkyRenderState sky, int effective) {
        boolean overworld = sky.skybox == DimensionType.Skybox.OVERWORLD;
        float[] sunWorld = VBVShaderMath.sunWorld(sky.sunAngle);
        float[] sunView = VBVShaderMath.toView(sunWorld, camera.yRot, camera.xRot);

        float rain = overworld ? VBVShaderMath.clamp01(1.0F - sky.rainBrightness) : 0.0F;
        float daylight = overworld ? VBVShaderMath.smoothstep(-0.02F, 0.25F, sunWorld[1]) : 0.0F;

        // Sun shadows only make sense where the sun reaches: not in caves, not under water.
        float targetExposure = VBVShaderMath.clamp01(mc.level.getBrightness(LightLayer.SKY, camera.blockPos) / 15.0F);
        if (camera.fogType == FogType.WATER) targetExposure = 0.0F;
        skyExposure = skyExposure < 0.0F ? targetExposure : skyExposure + (targetExposure - skyExposure) * 0.06F;
        float sunStrength = daylight * (1.0F - 0.85F * rain) * skyExposure * skyExposure;

        float k = strength / 100.0F;
        boolean shadows = effective == SHADOWS;
        // The cartoon look: more saturation than before, a firm toon band, and a little less
        // contrast so the bands do not clip. Shadows trades a bit of banding for the occlusion.
        float saturation = 1.0F + (shadows ? 0.20F : 0.28F) * k;
        float contrast = (shadows ? 0.20F : 0.24F) * k;
        float vignette = (shadows ? 0.14F : 0.16F) * k;
        float toon = (shadows ? 0.60F : 0.75F) * k;
        float warmth = 0.035F * k;
        float ao = shadows ? Math.min(0.8F * k, 1.0F) : 0.0F;
        float shadow = shadows ? Math.min(0.85F * k, 1.0F) : 0.0F;

        float projection = camera.projectionMatrix.m11();
        float tanHalfFov = projection > 0.01F ? 1.0F / projection : 0.7F;
        int far = Math.max(8, Math.min(65535, Math.round(camera.depthFar)));

        NativeImage pixels = dataTexture.getPixels();
        pixels.setPixelABGR(0, 0, VBVShaderMath.packAbgr(sunView[0] * 0.5F + 0.5F, sunView[1] * 0.5F + 0.5F, sunView[2] * 0.5F + 0.5F, sunStrength));
        pixels.setPixelABGR(1, 0, (VBVShaderMath.toByte(quality / 3.0F) << 24) | ((far & 255) << 16) | (((far >> 8) & 255) << 8));
        pixels.setPixelABGR(2, 0, VBVShaderMath.packAbgr(saturation / 2.0F, contrast, vignette, toon));
        pixels.setPixelABGR(3, 0, VBVShaderMath.packAbgr(ao, shadow, shadows ? 1.0F : 0.0F, 0.5F + warmth * 2.0F));
        int tan16 = Math.max(0, Math.min(65535, Math.round(tanHalfFov / 4.0F * 65535.0F)));
        pixels.setPixelABGR(4, 0, (((tan16 >> 8) & 255)) | ((tan16 & 255) << 8));
        // The last texel's alpha says "this data is real": a never-written texture passes the frame through.
        pixels.setPixelABGR(7, 0, VBVShaderMath.packAbgr(0.0F, 0.0F, 0.0F, 1.0F));
        dataTexture.upload();
    }
}
