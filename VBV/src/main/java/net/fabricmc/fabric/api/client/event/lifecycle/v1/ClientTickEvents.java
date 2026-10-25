package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;

/** Small Fabric-shaped client tick event subset for Eagler ports. */
public final class ClientTickEvents {
    @FunctionalInterface public interface StartTick { void onStartTick(Minecraft client); }
    @FunctionalInterface public interface EndTick { void onEndTick(Minecraft client); }

    public static final Event<StartTick> START_CLIENT_TICK = new Event<>();
    public static final Event<EndTick> END_CLIENT_TICK = new Event<>();

    private ClientTickEvents() {}

    public static final class Event<T> {
        private final CopyOnWriteArrayList<T> listeners = new CopyOnWriteArrayList<>();
        public void register(T listener) {
            if (listener == null) throw new IllegalArgumentException("Listener must not be null");
            listeners.addIfAbsent(listener);
        }
        public int listenerCount() { return listeners.size(); }
        public void fire(java.util.function.Consumer<T> invocation) {
            for (T listener : listeners) invocation.accept(listener);
        }
    }

    public static void fireStart(Minecraft client) {
        START_CLIENT_TICK.fire(listener -> listener.onStartTick(client));
    }
    public static void fireEnd(Minecraft client) {
        END_CLIENT_TICK.fire(listener -> listener.onEndTick(client));
    }
}
