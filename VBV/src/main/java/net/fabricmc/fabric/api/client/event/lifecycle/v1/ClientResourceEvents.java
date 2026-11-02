package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.Minecraft;

/** Client resource reload notification; ports register listeners before reload. */
public final class ClientResourceEvents {
    @FunctionalInterface public interface Reload { void onResourceReload(Minecraft client); }

    public static final Event<Reload> RELOAD = EventFactory.createArrayBacked(Reload.class, listeners -> client -> {
        for (Reload listener : listeners) listener.onResourceReload(client);
    });

    private ClientResourceEvents() {}
    public static void fireReload(Minecraft client) { RELOAD.invoker().onResourceReload(client); }
}
