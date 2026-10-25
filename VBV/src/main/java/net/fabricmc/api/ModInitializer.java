package net.fabricmc.api;

/** Fabric-shaped initializer interface supported by the Eagler compatibility shim. */
@FunctionalInterface
public interface ModInitializer {
    void onInitialize();
}
