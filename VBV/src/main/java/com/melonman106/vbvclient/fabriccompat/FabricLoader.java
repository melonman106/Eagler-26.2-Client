package com.melonman106.vbvclient.fabriccompat;

import com.melonman106.vbvclient.mods.FabricFaker;
import com.melonman106.vbvclient.mods.ModBootstrap;
import com.melonman106.vbvclient.mods.ModPackage;
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
 *
 * It also answers for the pieces of the Fabric environment that importable mods
 * may depend on: "fabricloader", "fabric-api" and its modules, "minecraft" and
 * "java" are reported as present (see {@link FabricFaker}), and so is every
 * imported mod that was loaded at startup.
 */
public final class FabricLoader {
    private static final FabricLoader INSTANCE = new FabricLoader();
    private final Map<String, ModContainer> mods = new LinkedHashMap<>();
    private final Map<String, ModContainer> virtualMods = new LinkedHashMap<>();

    private FabricLoader() {
        // The version is the one the dependency check pretends to be, so a mod that asks for fabricloader >=0.14 and a
        // port that looks at the container agree with each other.
        registerMod(new ModMetadata("fabricloader", "Eagler Fabric Compatibility", FabricFaker.LOADER_VERSION,
            "Native compatibility APIs for manually ported client mods.", "melonman106"));
    }

    public static FabricLoader getInstance() {
        return INSTANCE;
    }

    public synchronized boolean isModLoaded(String id) {
        return lookup(id) != null;
    }

    public synchronized Optional<ModContainer> getModContainer(String id) {
        return Optional.ofNullable(lookup(id));
    }

    public synchronized Collection<ModContainer> getAllMods() {
        Map<String, ModContainer> all = new LinkedHashMap<>(mods);
        for (ModPackage pkg : ModBootstrap.loaded()) {
            all.putIfAbsent(pkg.manifest.id, importedContainer(pkg));
        }
        for (FabricFaker.Provided provided : FabricFaker.all()) {
            all.putIfAbsent(provided.id(), virtualContainer(provided));
        }
        return Collections.unmodifiableList(new ArrayList<>(all.values()));
    }

    /** Registered ports first, then imported mods, then the virtual Fabric environment. */
    private ModContainer lookup(String id) {
        if (id == null) return null;
        ModContainer registered = mods.get(id);
        if (registered != null) return registered;
        for (ModPackage pkg : ModBootstrap.loaded()) {
            if (pkg.manifest.id.equals(id)) return importedContainer(pkg);
        }
        FabricFaker.Provided provided = FabricFaker.find(id);
        return provided == null ? null : virtualContainer(provided);
    }

    private ModContainer virtualContainer(FabricFaker.Provided provided) {
        return virtualMods.computeIfAbsent(provided.id(), key -> new ModContainer(new ModMetadata(
            provided.id(), provided.name(), provided.version(), provided.note(), "FabricMC")));
    }

    private static ModContainer importedContainer(ModPackage pkg) {
        return new ModContainer(new ModMetadata(pkg.manifest.id, pkg.manifest.name, pkg.manifest.version,
            pkg.manifest.description, String.join(", ", pkg.manifest.authors)));
    }

    synchronized void registerMod(ModMetadata metadata) {
        mods.put(metadata.getId(), new ModContainer(metadata));
    }

    public net.fabricmc.api.EnvType getEnvironmentType() {
        return net.fabricmc.api.EnvType.CLIENT;
    }
}
