package com.melonman106.vbvclient.mods;

import java.util.List;

/**
 * Where imported mods are kept. In the game this is the browser's virtual filesystem (shared by the client and the
 * integrated-server worker); the tests use a map in memory. Paths use "/" and never start with one.
 */
public interface ModFiles {
    /** The bytes of a file, or null when it does not exist (or cannot be read). */
    byte[] read(String path);

    /** Writes a file, replacing any older one. Returns false when it could not be written. */
    boolean write(String path, byte[] data);

    /** Removes a file if present. */
    void delete(String path);

    /** The names (not paths) of the files directly inside a folder; empty when there are none. */
    List<String> list(String folder);
}
