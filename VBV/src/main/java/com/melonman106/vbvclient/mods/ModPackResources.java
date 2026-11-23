package com.melonman106.vbvclient.mods;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * One imported mod as a resource pack that lives in memory. It serves exactly the files {@link ModAssets#build} made, so
 * the mod's textures, models, item definitions and language files reach the game the same way a pack from disk would.
 * It never offers server data: imported mods have no data pack in this version.
 */
final class ModPackResources extends AbstractPackResources {
    private final NavigableMap<String, byte[]> files;
    private final byte[] packMeta;
    private final byte[] packIcon;

    ModPackResources(PackLocationInfo location, Map<String, byte[]> assets, byte[] packMeta, byte[] packIcon) {
        super(location);
        this.files = new TreeMap<>(assets);
        this.packMeta = packMeta;
        this.packIcon = packIcon;
    }

    private static IoSupplier<InputStream> supply(byte[] bytes) {
        return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        String joined = String.join("/", path);
        if ("pack.mcmeta".equals(joined)) return supply(packMeta);
        if ("pack.png".equals(joined)) return supply(packIcon);
        return null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier location) {
        if (type != PackType.CLIENT_RESOURCES) return null;
        return supply(files.get(location.getNamespace() + "/" + location.getPath()));
    }

    @Override
    public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES) return;
        String prefix = namespace + "/" + (directory.isEmpty() ? "" : directory + "/");
        for (Map.Entry<String, byte[]> entry : files.subMap(prefix, true, prefix + '￿', false).entrySet()) {
            Identifier id = Identifier.tryBuild(namespace, entry.getKey().substring(namespace.length() + 1));
            if (id != null) output.accept(id, supply(entry.getValue()));
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        if (type != PackType.CLIENT_RESOURCES) return Collections.emptySet();
        Set<String> namespaces = new LinkedHashSet<>();
        for (String key : files.keySet()) {
            int slash = key.indexOf('/');
            if (slash > 0) namespaces.add(key.substring(0, slash));
        }
        return namespaces;
    }

    @Override
    public void close() {
        // nothing is held open
    }
}
