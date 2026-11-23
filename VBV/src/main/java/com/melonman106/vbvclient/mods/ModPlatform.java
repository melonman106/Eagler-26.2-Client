package com.melonman106.vbvclient.mods;

import com.melonman106.vbvclient.VBVClient;
import java.util.ArrayList;
import java.util.List;
import net.lax1dude.eaglercraft.v1_8.Filesystem;
import net.lax1dude.eaglercraft.v1_8.internal.IEaglerFilesystem;
import net.lax1dude.eaglercraft.v1_8.internal.PlatformRuntime;
import net.lax1dude.eaglercraft.v1_8.internal.vfs2.VFile2;
import net.lax1dude.eaglercraft.v1_8.sp.server.internal.ServerPlatformSingleplayer;
import org.teavm.jso.JSBody;

/**
 * The game-facing half of the importable-mods store: which JavaScript realm this code is running in, and the browser
 * storage the mods live in.
 *
 * <p>Three realms run this code. The page (the client) and the integrated-server worker both load mods, from the same
 * database: the page opens the "worlds" database that the server worker reaches through the page's own handle. The
 * mesh worker also bootstraps the registries but has no storage and does not need item mods, so it gets none.</p>
 */
public final class ModPlatform {
    public enum Realm { PAGE, SERVER, OTHER }

    private static final String ROOT = "vbv";
    private static IEaglerFilesystem pageFilesystem;
    private static ModStore store;

    private ModPlatform() {}

    @JSBody(script = "try { return typeof window !== 'undefined' && typeof document !== 'undefined'; } catch (e) { return false; }")
    private static native boolean hasDocument();

    public static Realm realm() {
        try {
            if (ServerPlatformSingleplayer.getWorldsDatabase() != null) return Realm.SERVER;
        } catch (Throwable ignored) {
            // not the server worker
        }
        return hasDocument() ? Realm.PAGE : Realm.OTHER;
    }

    /** Version of a built-in mod of this client, or null. Constants only: this must not start the client's own classes. */
    public static String nativeVersion(String id) {
        if ("vbvclient".equals(id)) return VBVClient.VERSION;
        if ("eaglercraft".equals(id)) return FabricFaker.MINECRAFT_VERSION;
        return null;
    }

    /** The store for this realm, or null when there is no storage (the mesh worker, or storage that failed to open). */
    public static synchronized ModStore store() {
        if (store != null) return store;
        ModFiles files = files();
        if (files == null) return null;
        store = new ModStore(files, ModPlatform::nativeVersion);
        return store;
    }

    private static ModFiles files() {
        try {
            Realm realm = realm();
            if (realm == Realm.SERVER) return new VFileModFiles(ServerPlatformSingleplayer.getWorldsDatabase());
            if (realm == Realm.PAGE) {
                if (pageFilesystem == null) {
                    String name = PlatformRuntime.getClientConfigAdapter().getWorldsDB();
                    if (name == null || name.isEmpty()) return null;
                    pageFilesystem = Filesystem.getHandleFor(name);
                }
                return new VFileModFiles(pageFilesystem);
            }
        } catch (Throwable t) {
            System.err.println("[VBV mods] storage is not available: " + t);
        }
        return null;
    }

    /** {@link ModFiles} on top of the game's virtual filesystem; every path lives under "vbv/". */
    private static final class VFileModFiles implements ModFiles {
        private final IEaglerFilesystem fs;

        VFileModFiles(IEaglerFilesystem fs) {
            this.fs = fs;
        }

        private VFile2 file(String path) {
            return VFile2.create(fs, ROOT, path);
        }

        @Override
        public byte[] read(String path) {
            try {
                return file(path).getAllBytes();
            } catch (Throwable t) {
                return null;
            }
        }

        @Override
        public boolean write(String path, byte[] data) {
            try {
                file(path).setAllBytes(data);
                return true;
            } catch (Throwable t) {
                return false;
            }
        }

        @Override
        public void delete(String path) {
            try {
                file(path).delete();
            } catch (Throwable ignored) {
                // nothing to remove
            }
        }

        @Override
        public List<String> list(String folder) {
            List<String> names = new ArrayList<>();
            try {
                String prefix = ROOT + "/" + folder + "/";
                for (String key : VFile2.create(fs, ROOT, folder).listFilenames(true)) {
                    String clean = key.startsWith("/") ? key.substring(1) : key;
                    if (!clean.startsWith(prefix)) continue;
                    String rest = clean.substring(prefix.length());
                    if (!rest.isEmpty() && rest.indexOf('/') < 0) names.add(rest);
                }
            } catch (Throwable ignored) {
                // an unreadable folder looks empty
            }
            return names;
        }
    }
}
