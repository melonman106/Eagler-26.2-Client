package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.Minecraft;

/** Fabric-shaped client lifecycle events backed by the Eagler lifecycle bridge. */
public final class ClientLifecycleEvents {
    @FunctionalInterface public interface ClientStarted { void onClientStarted(Minecraft client); }
    @FunctionalInterface public interface ClientStopping { void onClientStopping(Minecraft client); }

    public static final Event<ClientStarted> CLIENT_STARTED = EventFactory.createArrayBacked(
        ClientStarted.class, callbacks -> client -> {
            for (ClientStarted callback : callbacks) callback.onClientStarted(client);
        });
    public static final Event<ClientStopping> CLIENT_STOPPING = EventFactory.createArrayBacked(
        ClientStopping.class, callbacks -> client -> {
            for (ClientStopping callback : callbacks) callback.onClientStopping(client);
        });

    private ClientLifecycleEvents() {}

    public static void fireStarted(Minecraft client) { CLIENT_STARTED.invoker().onClientStarted(client); }
    public static void fireStopping(Minecraft client) { CLIENT_STOPPING.invoker().onClientStopping(client); }
}
