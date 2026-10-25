package com.melonman106.vbvclient.fabriccompat;

/** Backwards-compatible alias for the Fabric-shaped initializer contract. */
@FunctionalInterface
public interface ModInitializer extends net.fabricmc.api.ModInitializer {
    @Override
    void onInitialize();
}
