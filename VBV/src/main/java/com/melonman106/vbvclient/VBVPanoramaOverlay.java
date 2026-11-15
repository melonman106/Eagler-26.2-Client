package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Animated menu backdrops. {@code apply_dungeons_home.py} turns every video (or GIF / PNG) in
 * {@code VBV/panorama/} into numbered PNG frames and rewrites the four tables below, so adding a
 * file to that folder adds an entry to Extras > Panoramas.
 *
 * <p>Only the frames around the playhead are kept as textures. A 15 second clip is 150 frames of
 * 854x480 pixels, about 250 MB of GPU memory if every frame stayed loaded; a short ring of
 * {@link #RESIDENT} frames needs about 20 MB and each frame is simply loaded again on the next loop.</p>
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
    private static final int RESIDENT = 12;

    private static final Identifier[] resident = new Identifier[RESIDENT];
    private static int residentPos;
    private static int selected = 0;
    private static int cachedPanorama = -1;
    private static int cachedFrame = -1;
    private static Identifier cachedId;

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
        int frame = (int) ((System.currentTimeMillis() / FRAME_MS[selected]) % COUNTS[selected]);
        Identifier id;
        if (selected == cachedPanorama && frame == cachedFrame && cachedId != null) {
            id = cachedId;
        } else {
            id = frame(selected, frame);
            cachedPanorama = selected;
            cachedFrame = frame;
            cachedId = id;
            remember(id);
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0, 0,
            width, height, width, height, width, height);
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
    }

    private static void release(Identifier id) {
        try {
            Minecraft.getInstance().getTextureManager().release(id);
        } catch (RuntimeException ignored) {
            // A texture that was never loaded, or is already gone, needs no cleanup.
        }
    }
}
