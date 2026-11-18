package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * Animated menu backdrops. {@code apply_dungeons_home.py} turns every video (or GIF / PNG) in
 * {@code VBV/panorama/} into numbered PNG frames and rewrites the four tables below, so adding a
 * file to that folder adds an entry to Extras > Panoramas.
 *
 * <p>Only the frames around the playhead are kept as textures. A 15 second clip is 150 frames of
 * 1280x720 pixels, about 550 MB of GPU memory if every frame stayed loaded. Only the frame being
 * shown and the one being faded in are ever sampled, so the ring holds {@link #RESIDENT} of them
 * (about 15 MB) and each frame is simply loaded again on the next loop. The <em>Motion</em> button
 * on the Panorama tab freezes the backdrop on its first frame and removes the per-frame decode,
 * the texture upload and the second full-screen blend entirely.</p>
 *
 * <p>The frames are stored at a low rate (5 per second by default) to keep the download small: every
 * panorama frame is a PNG that has to be downloaded, unpacked and, when shown, decoded. Between two stored
 * frames the next one is faded in over the current one, which for slow camera moves is within a few percent
 * of the frame that was dropped (measured on the bundled clips: 34-42 dB against the original 10 fps
 * frames), so the picture still looks smooth at the screen's refresh rate.</p>
 */
public final class VBVPanoramaOverlay {
    /** The panorama used until the player picks another one. */
    public static final String DEFAULT_ID = "panorama_overlay";
    /** Saved value meaning "no overlay, show Minecraft's own panorama". */
    public static final String NONE_ID = "none";

    // Rewritten at build time by apply_dungeons_home.py (one entry per file in VBV/panorama/).
    private static final String[] IDS = new String[] { "panorama_overlay" };
    private static final String[] NAMES = new String[] { "Panorama Overlay" };
    private static final int[] COUNTS = new int[] { 1 };
    private static final long[] FRAME_MS = new long[] { 100L };

    private static final String FRAME_DIR = "vbvclient:textures/panorama_overlay/";
    /** Current frame plus the one being faded in, with a little slack for a resize mid-fade. */
    private static final int RESIDENT = 4;

    private static final Identifier[] resident = new Identifier[RESIDENT];
    private static int residentPos;
    private static int selected = 0;
    private static int cachedPanorama = -1;
    private static int cachedFrame = -1;
    private static Identifier cachedId;
    private static Identifier cachedNextId;
    /** When false the first frame is held: no per-frame decode, upload or blend. */
    private static boolean motion = true;

    private VBVPanoramaOverlay() {}

    /** Number of panoramas found in the panorama folder at build time. */
    public static int count() {
        return IDS.length;
    }

    public static String name(int index) {
        return NAMES[index];
    }

    public static int frameCount(int index) {
        return COUNTS[index];
    }

    /** Index of the active panorama, or -1 when the vanilla panorama is used. */
    public static int selectedIndex() {
        return selected;
    }

    public static String selectedId() {
        return selected >= 0 && selected < IDS.length ? IDS[selected] : NONE_ID;
    }

    public static void selectById(String id) {
        if (NONE_ID.equals(id)) {
            select(-1);
            return;
        }
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i].equals(id)) {
                select(i);
                return;
            }
        }
        select(IDS.length > 0 ? 0 : -1);
    }

    public static boolean isMotion() {
        return motion;
    }

    public static void setMotion(boolean value) {
        motion = value;
    }

    public static void toggleMotion() {
        motion = !motion;
    }

    public static void select(int index) {
        int next = index >= 0 && index < IDS.length ? index : -1;
        if (next == selected) {
            return;
        }
        releaseResident();
        selected = next;
        cachedPanorama = -1;
    }

    /** First frame of a panorama, used as its thumbnail in the Extras screen. */
    public static Identifier preview(int index) {
        return frame(index, 0);
    }

    /** Frees the thumbnails of panoramas that are not playing. */
    public static void releasePreviews() {
        for (int i = 0; i < IDS.length; i++) {
            if (i != selected) {
                release(frame(i, 0));
            }
        }
    }

    public static void render(GuiGraphicsExtractor graphics, int width, int height) {
        if (selected < 0 || selected >= IDS.length || COUNTS[selected] <= 0) {
            return;
        }
        int count = COUNTS[selected];
        long frameMs = Math.max(1L, FRAME_MS[selected]);
        long now = System.currentTimeMillis();
        int frame = motion ? (int) ((now / frameMs) % count) : 0;
        if (selected != cachedPanorama || frame != cachedFrame || cachedId == null) {
            cachedId = frame(selected, frame);
            cachedNextId = motion && count > 1 ? frame(selected, (frame + 1) % count) : null;
            cachedPanorama = selected;
            cachedFrame = frame;
            remember(cachedId);
            if (cachedNextId != null) {
                remember(cachedNextId);
            }
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, cachedId, 0, 0, 0, 0,
            width, height, width, height, width, height);
        if (cachedNextId != null) {
            // Fade the next stored frame in over this one (0 at the start of the interval, 1 at the end).
            float blend = (float) (now % frameMs) / (float) frameMs;
            graphics.blit(RenderPipelines.GUI_TEXTURED, cachedNextId, 0, 0, 0, 0,
                width, height, width, height, width, height, ARGB.white(blend));
        }
    }

    private static Identifier frame(int index, int frame) {
        String number = Integer.toString(frame + 1);
        while (number.length() < 4) {
            number = "0" + number;
        }
        return Identifier.parse(FRAME_DIR + IDS[index] + "/frame_" + number + ".png");
    }

    /** Notes that a frame is now loaded and unloads the oldest one once the ring is full. */
    private static void remember(Identifier id) {
        for (int i = 0; i < RESIDENT; i++) {
            if (id.equals(resident[i])) {
                return;
            }
        }
        Identifier old = resident[residentPos];
        if (old != null) {
            release(old);
        }
        resident[residentPos] = id;
        residentPos = (residentPos + 1) % RESIDENT;
    }

    private static void releaseResident() {
        for (int i = 0; i < RESIDENT; i++) {
            if (resident[i] != null) {
                release(resident[i]);
                resident[i] = null;
            }
        }
        residentPos = 0;
        cachedId = null;
        cachedNextId = null;
    }

    private static void release(Identifier id) {
        try {
            Minecraft.getInstance().getTextureManager().release(id);
        } catch (RuntimeException ignored) {
            // A texture that was never loaded, or is already gone, needs no cleanup.
        }
    }
}
