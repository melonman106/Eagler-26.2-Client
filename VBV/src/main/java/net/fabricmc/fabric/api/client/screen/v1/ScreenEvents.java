package net.fabricmc.fabric.api.client.screen.v1;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/** Screen lifecycle and rendering callbacks for manually ported client mods. */
public final class ScreenEvents {
    @FunctionalInterface public interface Init { void onInit(Screen screen, int width, int height); }
    @FunctionalInterface public interface Render { void onRender(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta); }
    @FunctionalInterface public interface Tick { void onTick(Screen screen); }
    @FunctionalInterface public interface Removed { void onRemoved(Screen screen); }

    public static final Event<Init> BEFORE_INIT = EventFactory.createArrayBacked(Init.class, listeners -> (screen, width, height) -> {
        for (Init listener : listeners) listener.onInit(screen, width, height);
    });
    public static final Event<Init> AFTER_INIT = EventFactory.createArrayBacked(Init.class, listeners -> (screen, width, height) -> {
        for (Init listener : listeners) listener.onInit(screen, width, height);
    });
    public static final Event<Render> BEFORE_RENDER = EventFactory.createArrayBacked(Render.class, listeners -> (screen, graphics, mouseX, mouseY, delta) -> {
        for (Render listener : listeners) listener.onRender(screen, graphics, mouseX, mouseY, delta);
    });
    public static final Event<Render> AFTER_RENDER = EventFactory.createArrayBacked(Render.class, listeners -> (screen, graphics, mouseX, mouseY, delta) -> {
        for (Render listener : listeners) listener.onRender(screen, graphics, mouseX, mouseY, delta);
    });
    public static final Event<Tick> AFTER_TICK = EventFactory.createArrayBacked(Tick.class, listeners -> screen -> {
        for (Tick listener : listeners) listener.onTick(screen);
    });
    public static final Event<Removed> REMOVE = EventFactory.createArrayBacked(Removed.class, listeners -> screen -> {
        for (Removed listener : listeners) listener.onRemoved(screen);
    });

    private ScreenEvents() {}
    public static void fireBeforeInit(Screen screen, int width, int height) { BEFORE_INIT.invoker().onInit(screen, width, height); }
    public static void fireAfterInit(Screen screen, int width, int height) { AFTER_INIT.invoker().onInit(screen, width, height); }
    public static void fireBeforeRender(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) { BEFORE_RENDER.invoker().onRender(screen, graphics, mouseX, mouseY, delta); }
    public static void fireAfterRender(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) { AFTER_RENDER.invoker().onRender(screen, graphics, mouseX, mouseY, delta); }
    public static void fireAfterTick(Screen screen) { AFTER_TICK.invoker().onTick(screen); }
    public static void fireRemove(Screen screen) { REMOVE.invoker().onRemoved(screen); }
}
