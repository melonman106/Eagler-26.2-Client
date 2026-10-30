package com.melonman106.vbvclient;

import java.util.Random;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Dungeons II-inspired multiplayer connection/loading screen. */
public final class VBVServerLoadingScreen extends Screen {
    private static final Identifier[] BACKDROPS = {
        Identifier.parse("vbvclient:textures/loadingscreen/loadingscreen1.png"),
        Identifier.parse("vbvclient:textures/loadingscreen/loadingscreen2.png"),
        Identifier.parse("vbvclient:textures/loadingscreen/loadingscreen3.png")
    };
    private static final Identifier[] WALK_FRAMES = buildWalkFrames();
    private final Identifier backdrop;
    private int ticks;

    public VBVServerLoadingScreen() {
        super(Component.literal("Loading..."));
        backdrop = BACKDROPS[new Random().nextInt(BACKDROPS.length)];
    }

    private static Identifier[] buildWalkFrames() {
        Identifier[] frames = new Identifier[12];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = Identifier.parse("vbvclient:textures/loadingscreen/walking/frame_" + i + ".png");
        }
        return frames;
    }

    @Override
    public void tick() {
        ticks++;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, backdrop, 0, 0, 0, 0,
            width, height, width, height, width, height);

        String label = "Loading...";
        int frame = (ticks / 3) % WALK_FRAMES.length;
        int size = Math.min(42, Math.max(28, height / 18));
        int x = width - size - 16;
        int y = height - size - 14;
        try {
            graphics.blit(RenderPipelines.GUI_TEXTURED, WALK_FRAMES[frame], x, y, 0, 0,
                size, size, size, size, size, size);
        } catch (RuntimeException ignored) {
            // Keep the loading label visible if the converted GIF frames are absent.
        }
        graphics.text(font, label, x - font.width(label) - 8, y + size - font.lineHeight,
            0xFFFFFFFF, true);
    }
}
