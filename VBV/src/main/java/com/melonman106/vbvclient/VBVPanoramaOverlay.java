package com.melonman106.vbvclient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Animated title-screen overlay frames generated from panorama_overlay.gif or .mp4. */
public final class VBVPanoramaOverlay {
    private static final Identifier[] FRAMES = new Identifier[] {
        Identifier.parse("vbvclient:textures/panorama_overlay/frame_0001.png")
    };

    private VBVPanoramaOverlay() {}

    public static void render(GuiGraphicsExtractor graphics, int width, int height) {
        if (FRAMES.length == 0) return;
        int frame = (int)((System.currentTimeMillis() / 83L) % FRAMES.length);
        graphics.blit(RenderPipelines.GUI_TEXTURED, FRAMES[frame], 0, 0, 0, 0,
            width, height, width, height, width, height);
    }
}
