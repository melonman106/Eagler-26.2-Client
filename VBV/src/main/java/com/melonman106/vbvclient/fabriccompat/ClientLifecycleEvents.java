package com.melonman106.vbvclient.fabriccompat;

import java.util.concurrent.CopyOnWriteArrayList;

/** Minimal client lifecycle callbacks for manually ported mods. */
public final class ClientLifecycleEvents {
    @FunctionalInterface public interface ClientStarted { void onClientStarted(); }
    @FunctionalInterface public interface ClientStopping { void onClientStopping(); }

    public static final Event<ClientStarted> CLIENT_STARTED = new Event<>();
    public static final Event<ClientStopping> CLIENT_STOPPING = new Event<>();

    private ClientLifecycleEvents() {}

    public static final class Event<T> {
        private final CopyOnWriteArrayList<T> listeners = new CopyOnWriteArrayList<>();
        public void register(T listener) {
            if (listener == null) throw new IllegalArgumentException("Listener must not be null");
            listeners.addIfAbsent(listener);
        }
        public void fire(java.util.function.Consumer<T> call) {
            for (T listener : listeners) call.accept(listener);
        }
        public int listenerCount() { return listeners.size(); }
    }
}
