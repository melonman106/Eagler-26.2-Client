package net.fabricmc.fabric.api.event;

/**
 * Fabric-compatible event contract for manually ported Eagler client mods.
 * This is a small source-compatibility layer, not Fabric's full runtime.
 */
public abstract class Event<T> {
    public abstract T invoker();
    public abstract void register(T listener);
    public abstract int listenerCount();
}
