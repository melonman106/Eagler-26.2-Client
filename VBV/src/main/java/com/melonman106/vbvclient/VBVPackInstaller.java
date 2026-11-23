package com.melonman106.vbvclient;

import java.util.ArrayDeque;

import net.lax1dude.eaglercraft.v1_8.internal.FileChooserResult;

/**
 * Hands downloaded resource-pack ZIPs to Eagler's own pack importer.
 *
 * <p>{@code patch_pack_installer_hook.py} makes {@code PackSelectionScreen.tick()} call
 * {@link #takePending()}, so a pack queued here goes through exactly the same unpack, validation,
 * progress and discovery code as a ZIP chosen with the "Import Pack..." button.</p>
 *
 * <p>A Modrinth install can bring several required dependency packs along with the chosen pack, so
 * this is a queue; the screen imports one pack per tick and the rest follow on the ticks after it.</p>
 */
public final class VBVPackInstaller {
    private static final ArrayDeque<FileChooserResult> PENDING = new ArrayDeque<>();

    private VBVPackInstaller() {}

    public static synchronized void queue(String fileName, byte[] zipData) {
        if (zipData == null || zipData.length == 0) return;
        String name = fileName == null || fileName.isEmpty() ? "pack.zip" : fileName;
        PENDING.addLast(new FileChooserResult(name, zipData));
        while (PENDING.size() > 16) PENDING.removeFirst();
    }

    public static synchronized boolean hasPending() {
        return !PENDING.isEmpty();
    }

    /** Returns the next queued pack once, then null. */
    public static synchronized FileChooserResult takePending() {
        return PENDING.pollFirst();
    }

    public static synchronized int count() {
        return PENDING.size();
    }

    public static synchronized void clear() {
        PENDING.clear();
    }
}
