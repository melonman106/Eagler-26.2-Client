package net.fabricmc.fabric.api.client.rendering.v1;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Eagler-native HUD callback, fired after vanilla HUD extraction. */
@FunctionalInterface
public interface HudRenderCallback {
    Event<HudRenderCallback> EVENT = EventFactory.createArrayBacked(
        HudRenderCallback.class, callbacks -> (client, graphics, tickCounter) -> {
            for (HudRenderCallback callback : callbacks) callback.onHudRender(client, graphics, tickCounter);
        });

    void onHudRender(Minecraft client, GuiGraphicsExtractor graphics, DeltaTracker tickCounter);

    static void fire(Minecraft client, GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
        EVENT.invoker().onHudRender(client, graphics, tickCounter);
    }
}
