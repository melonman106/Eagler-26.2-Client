package net.fabricmc.loader.api;

import java.util.Collections;
import net.fabricmc.loader.api.metadata.ModMetadata;

/** Public Fabric-shaped view of a registered Eagler client mod. */
public final class ModContainer {
    private final ModMetadata metadata;

    ModContainer(com.melonman106.vbvclient.fabriccompat.ModContainer source) {
        com.melonman106.vbvclient.fabriccompat.ModMetadata data = source.getMetadata();
        this.metadata = new ModMetadata() {
            @Override public String getId() { return data.getId(); }
            @Override public String getName() { return data.getName(); }
            @Override public String getDescription() { return data.getDescription(); }
            @Override public Version getVersion() { return new SimpleVersion(data.getVersion()); }
            @Override public Iterable<String> getAuthors() {
                String authors = data.getAuthors();
                return authors == null || authors.isBlank()
                    ? Collections.emptyList() : Collections.singletonList(authors);
            }
        };
    }

    public ModMetadata getMetadata() { return metadata; }
    public String getId() { return metadata.getId(); }
}
