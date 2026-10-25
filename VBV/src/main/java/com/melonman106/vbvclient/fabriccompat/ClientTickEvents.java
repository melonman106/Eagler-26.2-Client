package com.melonman106.vbvclient.fabriccompat;

import java.util.concurrent.CopyOnWriteArrayList;

/** Browser-safe client tick event hooks inspired by Fabric API. */
public final class ClientTickEvents {
    @FunctionalInterface
    public interface StartTick { void onStartTick(); }
    @FunctionalInterface
    public interface EndTick { void onEndTick(); }

    public static final Event<StartTick> START_CLIENT_TICK = new Event<>();
    public static final Event<EndTick> END_CLIENT_TICK = new Event<>();

    private ClientTickEvents() {}

    public static final class Event<T> {
        private final CopyOnWriteArrayList<T> listeners = new CopyOnWriteArrayList<>();
        public void register(T listener) {
            if (listener == null) throw new IllegalArgumentException("Listener must not be null");
            listeners.addIfAbsent(listener);
        }
        public void invoker(java.util.function.Consumer<T> call) {
            for (T listener : listeners) call.accept(listener);
        }
        public int listenerCount() { return listeners.size(); }
    }
}
