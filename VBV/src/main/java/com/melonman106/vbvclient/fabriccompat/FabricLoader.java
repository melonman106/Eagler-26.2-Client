package com.melonman106.vbvclient.fabriccompat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Small FabricLoader-shaped facade for Eaglercraft.
 *
 * This is not the upstream Fabric Loader: it does not discover JARs, resolve
 * dependencies, apply Mixins, or transform classes. Ports register metadata
 * explicitly through FabricCompatClient.registerMod.
 */
public final class FabricLoader {
    private static final FabricLoader INSTANCE = new FabricLoader();
    private final Map<String, ModContainer> mods = new LinkedHashMap<>();

    private FabricLoader() {
        registerMod(new ModMetadata("fabricloader", "Eagler Fabric Compatibility", "0.1.0",
            "Native compatibility APIs for manually ported client mods.", "melonman106"));
    }

    public static FabricLoader getInstance() {
        return INSTANCE;
    }

    public synchronized boolean isModLoaded(String id) {
        return mods.containsKey(id);
    }

    public synchronized Optional<ModContainer> getModContainer(String id) {
        return Optional.ofNullable(mods.get(id));
    }

    public synchronized Collection<ModContainer> getAllMods() {
        return Collections.unmodifiableList(new ArrayList<>(mods.values()));
    }

    synchronized void registerMod(ModMetadata metadata) {
        mods.put(metadata.getId(), new ModContainer(metadata));
    }

    public EnvType getEnvironmentType() {
        return EnvType.CLIENT;
    }
}
