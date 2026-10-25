package net.fabricmc.loader.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * Minimal FabricLoader facade backed by the Eagler-native registry.
 * It does not discover JARs, resolve dependencies, or run Mixins.
 */
public final class FabricLoader {
    private static final FabricLoader INSTANCE = new FabricLoader();
    private FabricLoader() {}

    public static FabricLoader getInstance() { return INSTANCE; }

    public boolean isModLoaded(String id) {
        return com.melonman106.vbvclient.fabriccompat.FabricLoader.getInstance().isModLoaded(id);
    }

    public Optional<ModContainer> getModContainer(String id) {
        return com.melonman106.vbvclient.fabriccompat.FabricLoader.getInstance()
            .getModContainer(id).map(ModContainer::new);
    }

    public Collection<ModContainer> getAllMods() {
        Collection<com.melonman106.vbvclient.fabriccompat.ModContainer> source =
            com.melonman106.vbvclient.fabriccompat.FabricLoader.getInstance().getAllMods();
        ArrayList<ModContainer> result = new ArrayList<>();
        for (com.melonman106.vbvclient.fabriccompat.ModContainer container : source) {
            result.add(new ModContainer(container));
        }
        return Collections.unmodifiableList(result);
    }

    public net.fabricmc.api.EnvType getEnvironmentType() {
        return net.fabricmc.api.EnvType.CLIENT;
    }
}
