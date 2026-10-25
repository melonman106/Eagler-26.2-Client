package com.melonman106.vbvclient;

import com.melonman106.vbvclient.fabriccompat.FabricCompatClient;

/** 26.2 client lifecycle entry point for the Herz and Fabric-compatibility adapters. */
public final class HerzClient {
    private static boolean initialized;

    private HerzClient() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        FabricCompatClient.initialize();
        for (HerzMod mod : HerzModRegistry.all()) {
            mod.onInitialize();
        }
    }

    public static void tick() {
        FabricCompatClient.tick();
        for (HerzMod mod : HerzModRegistry.all()) {
            mod.onTick();
        }
    }

    public static void stop() {
        FabricCompatClient.stop();
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
