package net.lax1dude.eaglercraft.v1_8.teavmcompat.inject;

import java.util.Objects;
import java.util.function.Consumer;

/** Preserves an element buffered by hasNext() when draining a stream iterator. */
public abstract class SimpleStreamIteratorInject<T> {
    public abstract boolean hasNext();

    public abstract T next();

    public void forEachRemaining(Consumer<? super T> action) {
        Objects.requireNonNull(action);
        while (hasNext()) {
            action.accept(next());
        }
    }
}
