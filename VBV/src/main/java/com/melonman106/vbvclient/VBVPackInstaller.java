package com.melonman106.vbvclient;

import net.lax1dude.eaglercraft.v1_8.internal.FileChooserResult;

/**
 * Hands a downloaded resource-pack ZIP to Eagler's own pack importer.
 *
 * <p>{@code patch_pack_installer_hook.py} makes {@code PackSelectionScreen.tick()} call
 * {@link #takePending()}, so a pack queued here goes through exactly the same unpack, validation,
 * progress and discovery code as a ZIP chosen with the "Import Pack..." button.</p>
 */
public final class VBVPackInstaller {
    private static FileChooserResult pending;

    private VBVPackInstaller() {}

    public static synchronized void queue(String fileName, byte[] zipData) {
        if (zipData == null || zipData.length == 0) return;
        String name = fileName == null || fileName.isEmpty() ? "pack.zip" : fileName;
        pending = new FileChooserResult(name, zipData);
    }

    public static synchronized boolean hasPending() {
        return pending != null;
    }

    /** Returns the queued pack once, then null. */
    public static synchronized FileChooserResult takePending() {
        FileChooserResult result = pending;
        pending = null;
        return result;
    }
}
