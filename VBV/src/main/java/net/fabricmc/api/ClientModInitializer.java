package net.fabricmc.api;

/** Fabric-shaped client initializer used by native Eagler mod ports. */
@FunctionalInterface
public interface ClientModInitializer {
    void onInitializeClient();
}
