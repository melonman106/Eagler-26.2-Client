package com.melonman106.vbvclient.fabriccompat;

/** Metadata handle for a mod known to the native compatibility registry. */
public final class ModContainer {
    private final ModMetadata metadata;

    ModContainer(ModMetadata metadata) {
        this.metadata = metadata;
    }

    public ModMetadata getMetadata() {
        return metadata;
    }
}
