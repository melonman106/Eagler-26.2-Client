package com.melonman106.vbvclient;

import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * In-game FPS and lag-spike meter, plus a copyable performance report.
 *
 * <p>It times the gap between consecutive HUD frames, so any stall (chunk generation, server tick,
 * mesh work, garbage collection) shows up as a long frame regardless of its cause. Works without
 * browser dev tools, which managed school Chromebooks often block.</p>
 */
public final class VBVPerf {
    private static final int CAPACITY = 1024;
    private static final long GAP_RESET_NANOS = 1_500_000_000L;
    private static final long SPIKE_WINDOW_MS = 30_000L;
    private static final float SPIKE_MS = 50.0F;
    private static final float BIG_SPIKE_MS = 100.0F;
    private static final float HUGE_SPIKE_MS = 250.0F;
    private static final int GRAPH_FRAMES = 90;
    private static final int GRAPH_HEIGHT = 14;
    private static final int SPIKE_LOG = 96;

    private static boolean meter;

    /** Draggable screen offset, set from the HUD Editor. */
    public static int offsetX;
    public static int offsetY;

    /**
     * Frame phases timed by the engine hooks in {@code LevelRenderer}: building section meshes,
     * uploading them and drawing the opaque terrain. Together with the CEM and LOD costs they say
     * where a frame actually went, which the frame-time graph alone cannot.
     */
    public static final int PHASE_BUILD = 0, PHASE_UPLOAD = 1, PHASE_DRAW = 2;
    private static final String[] PHASE_NAMES = { "build", "upload", "draw" };
    private static final long[] PHASE_START = new long[3];
    private static final long[] PHASE_TOTAL = new long[3];
    private static long phaseBuildMs;
    private static long phaseUploadMs;
    private static long phaseDrawMs;

    private static final float[] FRAME_MS = new float[CAPACITY];
    private static int head;
    private static int count;
    private static long lastFrameNanos;

    private static final float[] SPIKE_VALUE = new float[SPIKE_LOG];
    private static final long[] SPIKE_AT = new long[SPIKE_LOG];
    private static int spikeHead;
    private static int spikeCount;

    // Cached summary, refreshed about four times a second.
    private static long summaryAtMs;
    private static int fps;
    private static float avgMs;
    private static float worstMs;
    private static int spikes50;
    private static int spikes100;

    // Custom-model (Fresh Animations) cost, smoothed over a few frames.
    private static float cemMs;
    private static int cemRuns;
    private static int cemFar;

    private VBVPerf() {}

    public static boolean isMeter() {
        return meter;
    }

    public static void setMeter(boolean value) {
        meter = value;
    }

    /** Called by the engine hook just before a timed phase starts. */
    public static void phaseStart(int phase) {
        if (phase >= 0 && phase < PHASE_START.length) {
            PHASE_START[phase] = System.nanoTime();
        }
    }

    /** Called by the engine hook right after a timed phase; a phase that never started is ignored. */
    public static void phaseEnd(int phase) {
        if (phase < 0 || phase >= PHASE_START.length) {
            return;
        }
        long started = PHASE_START[phase];
        if (started == 0L) {
            return;
        }
        PHASE_START[phase] = 0L;
        long elapsed = System.nanoTime() - started;
        if (elapsed > 0L) {
            PHASE_TOTAL[phase] += elapsed;
        }
    }

    /** Milliseconds the given phase took since the previous HUD frame; shown as a whole number. */
    public static long phaseMs(int phase) {
        return phase == PHASE_BUILD ? phaseBuildMs : (phase == PHASE_UPLOAD ? phaseUploadMs : phaseDrawMs);
    }

    public static String phaseLine() {
        return "Chunks " + one(phaseBuildMs) + "/" + one(phaseUploadMs) + "/" + one(phaseDrawMs) + " ms ("
            + PHASE_NAMES[PHASE_BUILD] + "/" + PHASE_NAMES[PHASE_UPLOAD] + "/" + PHASE_NAMES[PHASE_DRAW] + ")";
    }

    public static void toggleMeter() {
        meter = !meter;
    }

    /**
     * One-click low-end profile. Each item is a setting the player can still change afterwards:
     * fewer chunks to draw and simulate, far terrain from LOD instead of real chunks, the automatic
     * governor on, cheaper custom-entity animation, culling on, no shader pass, no world-map terrain
     * capture and a still menu backdrop, plus the FPS meter on so the difference is visible. Nothing
     * here is state a later click cannot undo from the same screens.
     */
    public static void applyPerformancePreset() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.options != null) {
            try {
                minecraft.options.renderDistance().set(Math.min(minecraft.options.renderDistance().get(), 8));
                minecraft.options.simulationDistance().set(Math.min(minecraft.options.simulationDistance().get(), 5));
                minecraft.options.save();
            } catch (Throwable ignored) {
                // A failed option write must not take the rest of the preset with it.
            }
        }
        // Far terrain is cheaper than real chunks at the same distance, so make sure LOD is on.
        if (VBVLod.getLevel() < VBVLod.LOW) VBVLod.setLevel(VBVLod.LOW);
        VBVAdaptive.setEnabled(true);
        VBVEntityCulling.setEnabled(true);
        if (VBVCem.isEnabled()) VBVCem.setAnimationRange(16);
        // Painting explored chunks into the world map is a per-chunk cost with no frame-rate payoff.
        VBVWorldMap.setTerrainCaptureEnabled(false);
        VBVExtraFeatures.setDebugHudEnabled(false);
        // A still backdrop removes the per-frame PNG decode and texture upload in the menu.
        VBVPanoramaOverlay.setMotion(false);
        meter = true;
    }

    /** Called once per HUD frame. Records the frame time and draws the meter if it is on. */
    public static void render(GuiGraphicsExtractor graphics) {
        long now = System.nanoTime();
        long previous = lastFrameNanos;
        lastFrameNanos = now;
        if (previous != 0L) {
            long gap = now - previous;
            // A long gap means the HUD was hidden (F1, a menu) rather than a real stall.
            if (gap > 0L && gap < GAP_RESET_NANOS) {
                record(gap / 1_000_000.0F, System.currentTimeMillis());
            }
        }
        // Phase totals always drain, so the meter starts from a clean window when it is switched on.
        phaseBuildMs = PHASE_TOTAL[PHASE_BUILD] / 1_000_000L;
        phaseUploadMs = PHASE_TOTAL[PHASE_UPLOAD] / 1_000_000L;
        phaseDrawMs = PHASE_TOTAL[PHASE_DRAW] / 1_000_000L;
        Arrays.fill(PHASE_TOTAL, 0L);
        // The governor and the watchdog work whether or not the meter is being drawn, so the FPS
        // summary they use has to be refreshed here rather than inside draw().
        refreshSummary(System.currentTimeMillis());
        VBVAdaptive.tick(fps);
        // The LOD mesh budget shrinks from the same smoothed FPS, so the far terrain gives way
        // before the real chunks do.
        VBVLodRenderer.updateBudget(fps);
        VBVMemory.tick();
        if (meter) {
            long[] cem = VBVCem.drainStats();
            if (cem[0] + cem[1] > 0L) {
                cemRuns = (int) cem[0];
                cemFar = (int) cem[1];
                cemMs = cemMs * 0.9F + (cem[2] / 1_000_000.0F) * 0.1F;
            } else {
                cemRuns = 0;
                cemFar = 0;
                cemMs *= 0.9F;
            }
            draw(graphics);
        }
    }

    private static void record(float ms, long wallMs) {
        FRAME_MS[head] = ms;
        head = (head + 1) % CAPACITY;
        if (count < CAPACITY) {
            count++;
        }
        if (ms >= SPIKE_MS) {
            SPIKE_VALUE[spikeHead] = ms;
            SPIKE_AT[spikeHead] = wallMs;
            spikeHead = (spikeHead + 1) % SPIKE_LOG;
            if (spikeCount < SPIKE_LOG) {
                spikeCount++;
            }
        }
    }

    private static void refreshSummary(long wallMs) {
        if (wallMs - summaryAtMs < 250L) {
            return;
        }
        summaryAtMs = wallMs;
        // Last ~5 seconds of frames.
        float total = 0.0F;
        float worst = 0.0F;
        int frames = 0;
        for (int i = 1; i <= count && total < 5000.0F; i++) {
            float ms = FRAME_MS[(head - i + CAPACITY) % CAPACITY];
            total += ms;
            worst = Math.max(worst, ms);
            frames++;
        }
        avgMs = frames == 0 ? 0.0F : total / frames;
        fps = total <= 0.0F ? 0 : Math.round(frames * 1000.0F / total);
        worstMs = worst;
        spikes50 = 0;
        spikes100 = 0;
        for (int i = 0; i < spikeCount; i++) {
            if (wallMs - SPIKE_AT[i] > SPIKE_WINDOW_MS) {
                continue;
            }
            spikes50++;
            if (SPIKE_VALUE[i] >= BIG_SPIKE_MS) {
                spikes100++;
            }
        }
    }

    private static void draw(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        refreshSummary(System.currentTimeMillis());
        int accent = VBVTheme.accentLight();
        String line1 = fps + " FPS   " + one(avgMs) + " ms   worst " + Math.round(worstMs) + " ms";
        String line2 = "Spikes (30s): " + spikes50 + " over 50ms, " + spikes100 + " over 100ms";
        String line3 = null;
        if (VBVEntityCulling.isEnabled() && VBVEntityCulling.lastEntitiesSeen() + VBVEntityCulling.lastBlockEntitiesSeen() > 0) {
            line3 = "Culled " + VBVEntityCulling.lastEntitiesCulled() + "/" + VBVEntityCulling.lastEntitiesSeen()
                + " entities, " + VBVEntityCulling.lastBlockEntitiesCulled() + "/" + VBVEntityCulling.lastBlockEntitiesSeen() + " blocks";
        }
        String line4 = cemRuns + cemFar > 0
            ? "Models: " + cemRuns + " animated, " + cemFar + " far, " + one(cemMs) + " ms" : null;
        String line5 = phaseLine();
        String line6 = VBVLodRenderer.statsLine();
        String line7 = VBVAdaptive.reportLine();
        String line8 = VBVMemory.pressureNote();
        int width = Math.max(Math.max(font.width(line1), font.width(line2)), Math.max(line3 == null ? 0 : font.width(line3), GRAPH_FRAMES));
        if (line4 != null) width = Math.max(width, font.width(line4));
        width = Math.max(width, font.width(line5));
        if (line6 != null) width = Math.max(width, font.width(line6));
        if (line7 != null) width = Math.max(width, font.width(line7));
        if (line8 != null) width = Math.max(width, font.width(line8));
        width += 8;
        int lineH = font.lineHeight + 1;
        int extra = (line3 == null ? 0 : 1) + (line4 == null ? 0 : 1) + (line6 == null ? 0 : 1)
            + (line7 == null ? 0 : 1) + (line8 == null ? 0 : 1);
        int height = 4 + lineH * (3 + extra) + GRAPH_HEIGHT + 6;
        int x = graphics.guiWidth() / 2 - width / 2 + offsetX;
        int y = 2 + offsetY;
        graphics.fill(x, y, x + width, y + height, 0xA0000000);
        graphics.fill(x, y, x + width, y + 1, VBVTheme.accentAlpha(0xC0));
        int color = fps >= 50 ? 0xFF8CFF8C : (fps >= 30 ? 0xFFFFE070 : 0xFFFF7070);
        int ty = y + 3;
        graphics.text(font, line1, x + 4, ty, color, true);
        ty += lineH;
        graphics.text(font, line2, x + 4, ty, spikes50 == 0 ? VBVUi.TEXT_DIM : 0xFFFFB070, true);
        ty += lineH;
        if (line3 != null) {
            graphics.text(font, line3, x + 4, ty, accent, true);
            ty += lineH;
        }
        if (line4 != null) {
            graphics.text(font, line4, x + 4, ty, accent, true);
            ty += lineH;
        }
        graphics.text(font, line5, x + 4, ty, accent, true);
        ty += lineH;
        if (line6 != null) {
            graphics.text(font, line6, x + 4, ty, accent, true);
            ty += lineH;
        }
        if (line7 != null) {
            graphics.text(font, line7, x + 4, ty, accent, true);
            ty += lineH;
        }
        if (line8 != null) {
            graphics.text(font, line8, x + 4, ty, 0xFFFFB070, true);
            ty += lineH;
        }
        // Frame-time bars: each bar is one frame, full height = 50 ms or worse.
        int gx = x + width / 2 - GRAPH_FRAMES / 2;
        int base = ty + GRAPH_HEIGHT;
        int shown = Math.min(GRAPH_FRAMES, count);
        for (int i = 0; i < shown; i++) {
            float ms = FRAME_MS[(head - 1 - i + CAPACITY) % CAPACITY];
            int h = Math.max(1, Math.min(GRAPH_HEIGHT, Math.round(ms / SPIKE_MS * GRAPH_HEIGHT)));
            int bar = ms >= BIG_SPIKE_MS ? 0xFFFF5050 : (ms >= SPIKE_MS ? 0xFFFFA040 : (ms > 25.0F ? 0xFFFFE070 : 0xFF70E070));
            int bx = gx + GRAPH_FRAMES - 1 - i;
            graphics.fill(bx, base - h, bx + 1, base, bar);
        }
    }

    /** Plain-text summary the player can paste somewhere. */
    public static String report() {
        Minecraft mc = Minecraft.getInstance();
        StringBuilder out = new StringBuilder("VBV performance report\n");
        out.append(VBVBoot.report());
        out.append(VBVSafeMode.describe());
        out.append(VBVHardware.describe());
        if (count == 0) {
            out.append("No in-game frames recorded yet. Play for a few seconds with the HUD visible, then copy again.\n");
        } else {
            float[] sorted = new float[count];
            float total = 0.0F;
            float worst = 0.0F;
            for (int i = 0; i < count; i++) {
                float ms = FRAME_MS[(head - 1 - i + CAPACITY) % CAPACITY];
                sorted[i] = ms;
                total += ms;
                worst = Math.max(worst, ms);
            }
            Arrays.sort(sorted);
            float p95 = sorted[Math.min(count - 1, (int) (count * 0.95F))];
            int over50 = 0;
            int over100 = 0;
            int over250 = 0;
            for (float ms : sorted) {
                if (ms >= SPIKE_MS) over50++;
                if (ms >= BIG_SPIKE_MS) over100++;
                if (ms >= HUGE_SPIKE_MS) over250++;
            }
            out.append("Frames sampled: ").append(count).append(" over ").append(one(total / 1000.0F)).append(" s\n");
            out.append("FPS avg ").append(Math.round(count * 1000.0F / Math.max(1.0F, total)))
                .append(" | frame avg ").append(one(total / count)).append(" ms | p95 ").append(one(p95))
                .append(" ms | worst ").append(Math.round(worst)).append(" ms\n");
            out.append("Frames over 50/100/250 ms: ").append(over50).append('/').append(over100).append('/').append(over250).append('\n');
        }
        out.append("Entity culling: ").append(VBVEntityCulling.isEnabled() ? "ON" : "OFF")
            .append(" (last frame ").append(VBVEntityCulling.lastEntitiesCulled()).append('/').append(VBVEntityCulling.lastEntitiesSeen())
            .append(" entities, ").append(VBVEntityCulling.lastBlockEntitiesCulled()).append('/').append(VBVEntityCulling.lastBlockEntitiesSeen())
            .append(" block entities culled)\n");
        out.append("Custom models: ").append(VBVCem.isEnabled() ? "ON" : "OFF").append(" | animations ")
            .append(VBVCem.animationsLabel()).append(" (last frames ").append(cemRuns).append(" animated, ")
            .append(cemFar).append(" beyond range, ").append(one(cemMs)).append(" ms)\n");
        out.append(VBVCem.diagnostics()).append('\n');
        out.append("Frame phases: ").append(phaseLine()).append('\n');
        out.append("LOD: ").append(VBVLod.name());
        if (VBVLod.getLevel() != VBVLod.OFF) {
            out.append(" (").append(VBVLod.rangeChunks()).append(" chunks, ").append(VBVLodCache.size()).append(" remembered")
                .append(VBVLodCache.persistedChunks() > 0 ? ", " + VBVLodCache.persistedChunks() + " restored" : "")
                .append(')');
        }
        out.append('\n');
        String lodStats = VBVLodRenderer.statsLine();
        if (lodStats != null) {
            out.append(lodStats).append('\n');
        }
        out.append(VBVLodRenderer.diagnostics()).append('\n');
        String adaptive = VBVAdaptive.reportLine();
        if (adaptive != null) {
            out.append(adaptive).append('\n');
        }
        out.append(VBVMemory.audit());
        if (mc.options != null) {
            out.append("Render distance ").append(mc.options.getEffectiveRenderDistance())
                .append(" | simulation ").append(mc.options.simulationDistance().get()).append('\n');
        }
        if (mc.getWindow() != null) {
            out.append("Window ").append(mc.getWindow().getWidth()).append('x').append(mc.getWindow().getHeight())
                .append(" | GUI scale ").append(mc.getWindow().getGuiScale()).append('\n');
        }
        out.append(VBVPackIndex.report());
        return out.toString();
    }

    /** Copies the report to the clipboard; returns it for display. */
    public static String copyReport() {
        String text = report();
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(text);
        } catch (Throwable t) {
            System.err.println("[VBV perf] could not copy the report: " + t);
        }
        return text;
    }

    private static String one(float value) {
        return Float.toString(Math.round(value * 10.0F) / 10.0F);
    }
}
