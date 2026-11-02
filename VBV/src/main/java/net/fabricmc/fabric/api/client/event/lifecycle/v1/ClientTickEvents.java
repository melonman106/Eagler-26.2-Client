package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.Minecraft;

/** Fabric-shaped client tick events backed by the Eagler client tick bridge. */
public final class ClientTickEvents {
    @FunctionalInterface public interface StartTick { void onStartTick(Minecraft client); }
    @FunctionalInterface public interface EndTick { void onEndTick(Minecraft client); }

    public static final Event<StartTick> START_CLIENT_TICK = EventFactory.createArrayBacked(
        StartTick.class, callbacks -> client -> {
            for (StartTick callback : callbacks) callback.onStartTick(client);
        });
    public static final Event<EndTick> END_CLIENT_TICK = EventFactory.createArrayBacked(
        EndTick.class, callbacks -> client -> {
            for (EndTick callback : callbacks) callback.onEndTick(client);
        });

    private ClientTickEvents() {}

    public static void fireStart(Minecraft client) { START_CLIENT_TICK.invoker().onStartTick(client); }
    public static void fireEnd(Minecraft client) { END_CLIENT_TICK.invoker().onEndTick(client); }
}
