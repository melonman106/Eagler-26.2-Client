package com.melonman106.vbvclient.fabriccompat;

/** Fabric-shaped entrypoint contract for manually ported client mods. */
@FunctionalInterface
public interface ModInitializer {
    void onInitialize();
}
