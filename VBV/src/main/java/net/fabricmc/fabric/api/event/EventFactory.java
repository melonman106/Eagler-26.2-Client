package net.fabricmc.fabric.api.event;

import java.lang.reflect.Array;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** Array-backed event factory compatible with the common Fabric registration pattern. */
public final class EventFactory {
    private EventFactory() {}

    public static <T> Event<T> createArrayBacked(Class<T> type, Function<T[], T> invokerFactory) {
        return createArrayBacked(type, null, invokerFactory);
    }

    public static <T> Event<T> createArrayBacked(Class<T> type, T defaultInvoker, Function<T[], T> invokerFactory) {
        if (type == null || invokerFactory == null) throw new IllegalArgumentException("Event type and invoker factory are required");
        return new ArrayBackedEvent<>(type, defaultInvoker, invokerFactory);
    }

    private static final class ArrayBackedEvent<T> extends Event<T> {
        private final Class<T> type;
        private final T defaultInvoker;
        private final Function<T[], T> invokerFactory;
        private final CopyOnWriteArrayList<T> listeners = new CopyOnWriteArrayList<>();
        private volatile T invoker;

        private ArrayBackedEvent(Class<T> type, T defaultInvoker, Function<T[], T> invokerFactory) {
            this.type = type;
            this.defaultInvoker = defaultInvoker;
            this.invokerFactory = invokerFactory;
            this.invoker = buildInvoker();
        }

        @Override public T invoker() { return invoker; }

        @Override public void register(T listener) {
            if (listener == null || !type.isInstance(listener)) throw new IllegalArgumentException("Invalid event listener");
            listeners.addIfAbsent(listener);
            invoker = buildInvoker();
        }

        @Override public int listenerCount() { return listeners.size(); }

        @SuppressWarnings("unchecked")
        private T[] snapshot() {
            T[] copy = (T[]) Array.newInstance(type, listeners.size());
            return listeners.toArray(copy);
        }

        private T buildInvoker() {
            if (listeners.isEmpty() && defaultInvoker != null) return defaultInvoker;
            return invokerFactory.apply(snapshot());
        }
    }
}
