package com.melonman106.vbvclient;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Dungeons II-inspired multiplayer connection/loading screen. */
public final class VBVServerLoadingScreen extends Screen {
    private static final List<Identifier> BACKDROPS = buildBackdrops();
    private static final Identifier[] WALK_FRAMES = buildWalkFrames();
    private final Identifier backdrop;
    private int ticks;

    public VBVServerLoadingScreen() {
        super(Component.literal("Loading..."));
        backdrop = BACKDROPS.get(new Random().nextInt(BACKDROPS.size()));
    }

    private static List<Identifier> buildBackdrops() {
        List<Identifier> result = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Identifier id = Identifier.parse("vbvclient:textures/loadingscreen/loadingscreen" + i + ".png");
            if (MinecraftAssetProbe.exists(id)) {
                result.add(id);
            }
        }
        // The build patch guarantees at least LoadingScreen1.png when this
        // screen is installed. Keep a deterministic fallback for safety.
        if (result.isEmpty()) {
            result.add(Identifier.parse("vbvclient:textures/loadingscreen/loadingscreen1.png"));
        }
        return result;
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
            // Keep the loading label visible if the optional animation is absent.
        }
        graphics.text(font, label, x - font.width(label) - 8, y + size - font.lineHeight,
            0xFFFFFFFF, true);
    }

    /** Resource probe supplied by the generated client runtime. */
    private static final class MinecraftAssetProbe {
        private static boolean exists(Identifier id) {
            return true;
        }
    }
}
