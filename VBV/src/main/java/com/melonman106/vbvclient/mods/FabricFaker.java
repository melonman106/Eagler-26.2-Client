package com.melonman106.vbvclient.mods;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Pretends the Fabric Loader and Fabric API are installed.
 *
 * <p>This client is not Fabric: nothing here loads jars or runs Java mods. But importable mods (and real
 * fabric.mod.json files inside them) say things like "depends on fabricloader" or "depends on fabric-api", and a
 * mod that cannot start because of that would be pointless. So the loader hands out a set of virtual mods that
 * answer "is it loaded" and "which version" and accept every version range, and it is honest about how much is
 * really behind each one:</p>
 * <ul>
 *   <li>{@link Backing#REAL}: importable mods really can use this part (for example item groups, item settings
 *       and mod assets);</li>
 *   <li>{@link Backing#PARTIAL}: part of it works, the note says which;</li>
 *   <li>{@link Backing#FAKED}: accepted so the mod loads, but nothing happens behind it.</li>
 * </ul>
 * No game classes are used here, so this runs and is tested on a plain JVM.
 */
public final class FabricFaker {
    public static final String LOADER_VERSION = "0.16.14";
    public static final String API_VERSION = "0.121.0+26.2";
    public static final String MINECRAFT_VERSION = "26.2";
    public static final String JAVA_VERSION = "21";

    public enum Backing { REAL, PARTIAL, FAKED }

    /** A virtual mod. */
    public record Provided(String id, String name, String version, Backing backing, String note) {}

    public enum Kind { REQUIRES, RECOMMENDS, CONFLICTS, BREAKS }

    /** One line of "depends"/"requires". */
    public record Dependency(String id, ModVersion.Range range, Kind kind) {
        public boolean optional() { return kind == Kind.RECOMMENDS; }
    }

    /** The outcome of checking a mod's dependencies. {@code faked} lists the virtual Fabric mods that were faked or only partly real. */
    public record Resolution(List<String> errors, List<String> warnings, List<String> faked) {
        public boolean ok() { return errors.isEmpty(); }
    }

    private static final Map<String, Provided> PROVIDED = new LinkedHashMap<>();
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    private static void add(String id, String name, String version, Backing backing, String note) {
        PROVIDED.put(id, new Provided(id, name, version, backing, note));
    }

    static {
        ALIASES.put("fabric-loader", "fabricloader");
        ALIASES.put("fabric_loader", "fabricloader");
        ALIASES.put("fabric", "fabric-api");
        ALIASES.put("fabric_api", "fabric-api");

        add("fabricloader", "Fabric Loader (faked)", LOADER_VERSION, Backing.FAKED,
            "Not the real Fabric Loader. It answers \"is it loaded\" and version checks so mods that depend on it can load; "
                + "nothing is discovered, no entrypoints run and no mixins are applied.");
        add("fabric-api", "Fabric API (faked)", API_VERSION, Backing.PARTIAL,
            "Not the real Fabric API. The modules below say how much of each is really backed.");
        add("minecraft", "Minecraft", MINECRAFT_VERSION, Backing.REAL, "The game version this client runs. A mismatch is only a warning.");
        add("java", "Java", JAVA_VERSION, Backing.REAL, "Reported as 21 whatever the browser runs. A mismatch is only a warning.");

        // What importable (zip) mods can really use.
        add("fabric-api-base", "Fabric API Base", API_VERSION, Backing.REAL, "Nothing for a data mod to call, always satisfied.");
        add("fabric-object-builder-api-v1", "Object Builder API", API_VERSION, Backing.PARTIAL,
            "Items: stack size, durability, rarity, attack damage and speed, repair item, enchantability, fire resistance. Blocks and entities come later.");
        add("fabric-item-api-v1", "Item API", API_VERSION, Backing.PARTIAL,
            "Item settings from items.json. Custom item behaviour (use/attack callbacks) needs Java and is not available.");
        add("fabric-item-group-api-v1", "Item Group API", API_VERSION, Backing.PARTIAL,
            "\"creative_tab\" puts an item into a vanilla creative tab. Custom tabs are not available yet.");
        add("fabric-resource-loader-v0", "Resource Loader", API_VERSION, Backing.REAL,
            "The assets/ folder of an imported mod is served to the game as a resource pack (textures, models, languages, sounds).");
        add("fabric-registry-sync-v0", "Registry Sync", API_VERSION, Backing.PARTIAL,
            "Items are registered in the game registry before it freezes. There is no id sync with servers, so a multiplayer server needs the same mod.");

        // Present as Java classes in this client for ported mods, but not drivable from a zip mod yet.
        add("fabric-lifecycle-events-v1", "Lifecycle Events", API_VERSION, Backing.PARTIAL,
            "Real for mods ported to Java into this client; a zip mod cannot hook events yet.");
        add("fabric-key-binding-api-v1", "Key Binding API", API_VERSION, Backing.PARTIAL,
            "Real for mods ported to Java into this client; a zip mod cannot add key bindings yet.");
        add("fabric-rendering-v1", "Rendering API", API_VERSION, Backing.PARTIAL,
            "HUD rendering is real for mods ported to Java into this client; zip mod HUD elements come in a later phase.");
        add("fabric-screen-api-v1", "Screen API", API_VERSION, Backing.PARTIAL,
            "Real for mods ported to Java into this client; a zip mod cannot open or change screens.");

        // Accepted, nothing behind them.
        String[][] faked = {
            {"fabric-api-lookup-api-v1", "API Lookup API"}, {"fabric-biome-api-v1", "Biome API"},
            {"fabric-block-api-v1", "Block API"}, {"fabric-block-view-api-v2", "Block View API"},
            {"fabric-client-gametest-api-v1", "Client Game Test API"}, {"fabric-client-tags-api-v1", "Client Tags API"},
            {"fabric-command-api-v2", "Command API"}, {"fabric-content-registries-v0", "Content Registries (fuel, compost, flammable)"},
            {"fabric-convention-tags-v1", "Convention Tags v1"}, {"fabric-convention-tags-v2", "Convention Tags v2"},
            {"fabric-crash-report-info-v1", "Crash Report Info"}, {"fabric-data-attachment-api-v1", "Data Attachment API"},
            {"fabric-data-generation-api-v1", "Data Generation API"}, {"fabric-dimensions-v1", "Dimensions API"},
            {"fabric-entity-events-v1", "Entity Events"}, {"fabric-events-interaction-v0", "Interaction Events"},
            {"fabric-game-rule-api-v1", "Game Rule API"}, {"fabric-gametest-api-v1", "Game Test API"},
            {"fabric-loot-api-v2", "Loot API v2"}, {"fabric-loot-api-v3", "Loot API v3"},
            {"fabric-message-api-v1", "Message API"}, {"fabric-model-loading-api-v1", "Model Loading API"},
            {"fabric-networking-api-v1", "Networking API"}, {"fabric-particles-v1", "Particles API"},
            {"fabric-recipe-api-v1", "Recipe API"},
            {"fabric-renderer-api-v1", "Renderer API"}, {"fabric-renderer-indigo", "Indigo Renderer"},
            {"fabric-rendering-data-attachment-v1", "Rendering Data Attachment"}, {"fabric-rendering-fluids-v1", "Fluid Rendering"},
            {"fabric-resource-conditions-api-v1", "Resource Conditions"}, {"fabric-screen-handler-api-v1", "Screen Handler API"},
            {"fabric-sound-api-v1", "Sound API"}, {"fabric-tag-api-v1", "Tag API"},
            {"fabric-transfer-api-v1", "Transfer API"}, {"fabric-transitive-access-wideners-v1", "Transitive Access Wideners"},
        };
        for (String[] f : faked) {
            add(f[0], f[1], API_VERSION, Backing.FAKED, "Accepted so the mod can load. Nothing behind it works in this client.");
        }
    }

    private FabricFaker() {}

    /** Maps the alternative spellings ("fabric-loader", "fabric") to the id this faker uses. */
    public static String normalise(String id) {
        String lower = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        return ALIASES.getOrDefault(lower, lower);
    }

    /** The virtual mod behind an id, or null. {@code minecraft} and {@code java} count; the client's own mods do not. */
    public static Provided find(String id) {
        return PROVIDED.get(normalise(id));
    }

    public static boolean isReserved(String id) {
        String n = normalise(id);
        return PROVIDED.containsKey(n) || n.startsWith("fabric-") || n.equals("c") || n.equals("vbv")
            || n.equals("vbvclient") || n.equals("eaglercraft") || n.equals("realms");
    }

    public static List<Provided> all() {
        List<Provided> list = new ArrayList<>(PROVIDED.values());
        Collections.sort(list, (a, b) -> a.id().compareTo(b.id()));
        return list;
    }

    /**
     * Checks a mod's dependencies against the virtual Fabric environment and whatever else is installed.
     *
     * @param installedVersion version of an installed and enabled mod (the client's own or another imported one) for an
     *                         id, or null when there is none. Virtual mods never reach this.
     */
    public static Resolution resolve(List<Dependency> dependencies, Function<String, String> installedVersion) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> faked = new ArrayList<>();
        for (Dependency dep : dependencies) {
            String id = normalise(dep.id());
            Provided virtual = PROVIDED.get(id);
            String have = virtual != null ? virtual.version() : installedVersion.apply(id);
            boolean matches = have != null && dep.range().matches(ModVersion.parse(have));

            if (dep.kind() == Kind.CONFLICTS || dep.kind() == Kind.BREAKS) {
                if (virtual == null && matches) {
                    if (dep.kind() == Kind.BREAKS) errors.add("cannot be used together with " + id + " " + have + " (it declares that it breaks)");
                    else warnings.add("conflicts with " + id + " " + have);
                }
                continue; // the Fabric pieces cannot conflict with anything: they are not real
            }

            if (virtual != null) {
                if (id.equals("minecraft") || id.equals("java")) {
                    if (!matches) warnings.add("was made for " + id + " " + dep.range() + " but this client has " + have + "; loading it anyway");
                    continue;
                }
                if (!matches) {
                    warnings.add("asked for " + id + " " + dep.range() + "; the faked " + id + " says " + have + " and is accepted anyway");
                }
                if (virtual.backing() != Backing.REAL && !faked.contains(id)) faked.add(id);
                continue;
            }

            if (have == null) {
                if (dep.optional()) warnings.add("works better with " + id + " " + dep.range() + " (not installed)");
                else errors.add("needs " + id + " " + dep.range() + " (not installed or turned off)");
            } else if (!matches) {
                if (dep.optional()) warnings.add("works better with " + id + " " + dep.range() + " (found " + have + ")");
                else errors.add("needs " + id + " " + dep.range() + " but found " + have);
            }
        }
        return new Resolution(errors, warnings, faked);
    }
}
