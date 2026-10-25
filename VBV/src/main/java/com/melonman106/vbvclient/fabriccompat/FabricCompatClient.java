package com.melonman106.vbvclient.fabriccompat;

import com.melonman106.vbvclient.VBVClient;
import com.melonman106.vbvclient.VBVModInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bootstrap and event bridge for the small Eagler-native Fabric compatibility API.
 * This is an API for ports, not a binary-compatible replacement for Fabric Loader.
 */
public final class FabricCompatClient {
    private static boolean initialized;
    private static boolean started;
    private static final Map<String, List<net.fabricmc.api.ModInitializer>> INITIALIZERS = new LinkedHashMap<>();
    private static final List<net.fabricmc.api.ModInitializer> INVOKED_INITIALIZERS = new ArrayList<>();

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

        List<net.fabricmc.api.ModInitializer> pendingInitializers = new ArrayList<>();
        for (List<net.fabricmc.api.ModInitializer> entrypoints : INITIALIZERS.values()) {
            pendingInitializers.addAll(entrypoints);
        }
        for (net.fabricmc.api.ModInitializer entrypoint : pendingInitializers) {
            invokeInitializer(entrypoint);
        }
    }

    public static synchronized void tick() {
        tick(null);
    }

    public static synchronized void tick(net.minecraft.client.Minecraft client) {
        if (!initialized) initialize();
        if (!started) {
            started = true;
            ClientLifecycleEvents.CLIENT_STARTED.fire(ClientLifecycleEvents.ClientStarted::onClientStarted);
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.fireStarted(client);
        }
        ClientTickEvents.START_CLIENT_TICK.invoker(ClientTickEvents.StartTick::onStartTick);
        ClientTickEvents.END_CLIENT_TICK.invoker(ClientTickEvents.EndTick::onEndTick);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.fireStart(client);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.fireEnd(client);
    }

    public static synchronized void stop() {
        stop(null);
    }

    public static synchronized void stop(net.minecraft.client.Minecraft client) {
        if (!started) return;
        ClientLifecycleEvents.CLIENT_STOPPING.fire(ClientLifecycleEvents.ClientStopping::onClientStopping);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.fireStopping(client);
        started = false;
    }

    public static void registerMod(ModMetadata metadata) {
        FabricLoader.getInstance().registerMod(metadata);
    }

    /**
     * Register a manually ported mod's initializer. Register before initialize()
     * for startup ordering; late registrations are invoked immediately.
     */
    public static synchronized void registerInitializer(String modId, net.fabricmc.api.ModInitializer initializer) {
        if (modId == null || modId.isBlank()) throw new IllegalArgumentException("Mod id must not be blank");
        if (initializer == null) throw new IllegalArgumentException("Initializer must not be null");
        INITIALIZERS.computeIfAbsent(modId, ignored -> new ArrayList<>()).add(initializer);
        if (initialized) invokeInitializer(initializer);
    }

    private static void invokeInitializer(net.fabricmc.api.ModInitializer initializer) {
        if (INVOKED_INITIALIZERS.contains(initializer)) return;
        INVOKED_INITIALIZERS.add(initializer);
        initializer.onInitialize();
    }

    public static Collection<ModContainer> getMods() {
        return FabricLoader.getInstance().getAllMods();
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
