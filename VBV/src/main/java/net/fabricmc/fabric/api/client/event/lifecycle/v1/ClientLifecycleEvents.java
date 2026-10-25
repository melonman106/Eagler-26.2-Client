package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;

/** Small Fabric-shaped client started/stopping event subset for Eagler ports. */
public final class ClientLifecycleEvents {
    @FunctionalInterface public interface ClientStarted { void onClientStarted(Minecraft client); }
    @FunctionalInterface public interface ClientStopping { void onClientStopping(Minecraft client); }

    public static final Event<ClientStarted> CLIENT_STARTED = new Event<>();
    public static final Event<ClientStopping> CLIENT_STOPPING = new Event<>();

    private ClientLifecycleEvents() {}

    public static final class Event<T> {
        private final CopyOnWriteArrayList<T> listeners = new CopyOnWriteArrayList<>();
        public void register(T listener) {
            if (listener == null) throw new IllegalArgumentException("Listener must not be null");
            listeners.addIfAbsent(listener);
        }
        public void fire(java.util.function.Consumer<T> invocation) {
            for (T listener : listeners) invocation.accept(listener);
        }
    }

    public static void fireStarted(Minecraft client) {
        CLIENT_STARTED.fire(listener -> listener.onClientStarted(client));
    }
    public static void fireStopping(Minecraft client) {
        CLIENT_STOPPING.fire(listener -> listener.onClientStopping(client));
    }
}
