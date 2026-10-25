package com.melonman106.vbvclient.fabriccompat;

import com.melonman106.vbvclient.VBVClient;
import com.melonman106.vbvclient.VBVModInfo;
import java.util.Collection;

/**
 * Bootstrap and event bridge for the small Eagler-native Fabric compatibility API.
 * This is an API for ports, not a binary-compatible replacement for Fabric Loader.
 */
public final class FabricCompatClient {
    private static boolean initialized;
    private static boolean started;

    private FabricCompatClient() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        // Make existing native mods visible through the familiar metadata lookup API.
        VBVClient.init();
        for (VBVModInfo info : VBVClient.getMods()) {
            registerMod(new ModMetadata(info.id(), info.name(), info.version(),
                info.description(), info.authors()));
        }
        ClientLifecycleEvents.CLIENT_STARTED.fire(ClientLifecycleEvents.ClientStarted::onClientStarted);
        started = true;
    }

    public static synchronized void tick() {
        if (!initialized) initialize();
        ClientTickEvents.START_CLIENT_TICK.invoker(ClientTickEvents.StartTick::onStartTick);
        ClientTickEvents.END_CLIENT_TICK.invoker(ClientTickEvents.EndTick::onEndTick);
    }

    public static synchronized void stop() {
        if (!started) return;
        ClientLifecycleEvents.CLIENT_STOPPING.fire(ClientLifecycleEvents.ClientStopping::onClientStopping);
        started = false;
    }

    public static void registerMod(ModMetadata metadata) {
        FabricLoader.getInstance().registerMod(metadata);
    }

    public static Collection<ModContainer> getMods() {
        return FabricLoader.getInstance().getAllMods();
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
