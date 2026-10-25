package com.melonman106.vbvclient.fabriccompat;

import java.util.Objects;

/** Minimal immutable metadata view; it does not parse Fabric mod JSON files. */
public final class ModMetadata {
    private final String id;
    private final String name;
    private final String version;
    private final String description;
    private final String authors;

    public ModMetadata(String id, String name, String version, String description, String authors) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Mod id must not be blank");
        this.id = id;
        this.name = Objects.requireNonNullElse(name, id);
        this.version = Objects.requireNonNullElse(version, "unknown");
        this.description = Objects.requireNonNullElse(description, "");
        this.authors = Objects.requireNonNullElse(authors, "");
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getDescription() { return description; }
    public String getAuthors() { return authors; }
}
