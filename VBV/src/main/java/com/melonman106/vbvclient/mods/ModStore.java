package com.melonman106.vbvclient.mods;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * The imported mods: stored as the original {@code .zip} files in a {@code mods/} folder, plus one small
 * {@code state.json} (which mods are turned off, and whether safe mode is on). Both the client and the
 * integrated-server worker read the same store and compute the same {@link Plan}, so they register the same content
 * in the same order.
 *
 * <p>No game classes are used here; {@link ModFiles} is the only thing that touches storage.</p>
 */
public final class ModStore {
    public static final String FOLDER = "mods";
    private static final String STATE = FOLDER + "/state.json";

    private final ModFiles files;
    private final Function<String, String> nativeVersions;

    /**
     * @param nativeVersions version of a built-in mod of this client by id, or null (used for "requires" and to refuse
     *                       imported mods that reuse a built-in id)
     */
    public ModStore(ModFiles files, Function<String, String> nativeVersions) {
        this.files = files;
        this.nativeVersions = nativeVersions;
    }

    /** What happened to an import. */
    public record InstallResult(boolean ok, String id, String replacedVersion, List<String> problems, List<String> warnings) {}

    /** One stored mod. {@code pkg} is null when the stored zip could not be read any more. */
    public record Installed(String id, ModPackage pkg, boolean enabled, List<String> problems) {}

    /** The mods that will load, why the others will not, and what the faked Fabric pieces mean for each. */
    public record Plan(List<ModPackage> active, Map<String, List<String>> blocked, Map<String, FabricFaker.Resolution> resolutions,
                       boolean safeMode, String safeModeReason) {
        public boolean isActive(String id) {
            for (ModPackage pkg : active) if (pkg.manifest.id.equals(id)) return true;
            return false;
        }
    }

    // ---- state ----

    private JsonObject state() {
        byte[] bytes = files.read(STATE);
        if (bytes != null) {
            try {
                JsonElement parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
                if (parsed.isJsonObject()) return parsed.getAsJsonObject();
            } catch (JsonParseException | IllegalStateException ignored) {
                // a damaged state file just means "everything on, safe mode off"
            }
        }
        return new JsonObject();
    }

    private boolean saveState(JsonObject state) {
        return files.write(STATE, state.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Set<String> disabled(JsonObject state) {
        Set<String> out = new HashSet<>();
        JsonElement list = state.get("disabled");
        if (list != null && list.isJsonArray()) {
            for (JsonElement entry : list.getAsJsonArray()) if (entry.isJsonPrimitive()) out.add(entry.getAsString());
        }
        return out;
    }

    public boolean safeMode() {
        JsonElement value = state().get("safe_mode");
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
    }

    /** Turning safe mode off also clears a tripped boot guard. */
    public boolean setSafeMode(boolean on) {
        return setSafeMode(on, "Safe mode was turned on.");
    }

    /** As {@link #setSafeMode(boolean)}; the reason is only used when safe mode was not already on with one. */
    public boolean setSafeMode(boolean on, String reason) {
        JsonObject state = state();
        state.addProperty("safe_mode", on);
        if (!on) {
            state.remove("registering");
            state.remove("safe_reason");
        } else if (!state.has("safe_reason")) {
            state.addProperty("safe_reason", reason);
        }
        return saveState(state);
    }

    // ---- boot guard ----

    /**
     * Called just before mods are registered. If the previous start never reached {@link #endRegistration()}, something
     * crashed while registering mods, so safe mode is switched on (and stays on until the player turns it off).
     *
     * @return true when the guard tripped just now
     */
    public boolean beginRegistration() {
        JsonObject state = state();
        boolean tripped = state.has("registering") && state.get("registering").isJsonPrimitive() && state.get("registering").getAsBoolean();
        if (tripped) {
            state.addProperty("safe_mode", true);
            state.addProperty("safe_reason", "The last start stopped while loading mods, so mods were turned off.");
            state.remove("registering");
        } else if (!safeModeFrom(state)) {
            state.addProperty("registering", true);
        }
        saveState(state);
        return tripped;
    }

    public void endRegistration() {
        JsonObject state = state();
        if (state.has("registering")) {
            state.remove("registering");
            saveState(state);
        }
    }

    private static boolean safeModeFrom(JsonObject state) {
        JsonElement value = state.get("safe_mode");
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
    }

    // ---- listing ----

    public List<Installed> installed() {
        Set<String> off = disabled(state());
        Map<String, Installed> sorted = new TreeMap<>();
        for (String name : files.list(FOLDER)) {
            if (!name.endsWith(".zip")) continue;
            String id = name.substring(0, name.length() - 4);
            if (!ModManifest.ID.matcher(id).matches()) continue;
            byte[] bytes = files.read(FOLDER + "/" + name);
            if (bytes == null) {
                sorted.put(id, new Installed(id, null, !off.contains(id), List.of("The stored file could not be read.")));
                continue;
            }
            ModPackage pkg = ModPackage.read(bytes);
            if (pkg.manifest == null || !pkg.manifest.id.equals(id)) {
                List<String> problems = new ArrayList<>(pkg.problems);
                if (pkg.manifest != null) problems.add("The stored file is named " + id + ".zip but contains the mod \"" + pkg.manifest.id + "\".");
                if (problems.isEmpty()) problems.add("The stored file is not a valid mod.");
                sorted.put(id, new Installed(id, null, !off.contains(id), problems));
            } else {
                sorted.put(id, new Installed(id, pkg, !off.contains(id), pkg.problems));
            }
        }
        return new ArrayList<>(sorted.values());
    }

    // ---- changes ----

    public InstallResult install(byte[] zip) {
        ModPackage pkg = ModPackage.read(zip);
        List<String> problems = new ArrayList<>(pkg.problems);
        if (pkg.manifest == null || !problems.isEmpty()) {
            return new InstallResult(false, pkg.manifest == null ? null : pkg.manifest.id, null, problems, new ArrayList<>(pkg.warnings));
        }
        String id = pkg.manifest.id;
        if (nativeVersions.apply(id) != null) {
            problems.add("The id \"" + id + "\" is already used by a built-in mod of this client. Change the id in the mod.");
            return new InstallResult(false, id, null, problems, new ArrayList<>(pkg.warnings));
        }
        String replaced = null;
        byte[] old = files.read(FOLDER + "/" + id + ".zip");
        if (old != null) {
            ModPackage previous = ModPackage.read(old);
            replaced = previous.manifest != null ? previous.manifest.version : "unknown";
        }
        if (!files.write(FOLDER + "/" + id + ".zip", zip)) {
            problems.add("The mod could not be saved (the browser storage may be full or blocked).");
            return new InstallResult(false, id, null, problems, new ArrayList<>(pkg.warnings));
        }
        return new InstallResult(true, id, replaced, problems, new ArrayList<>(pkg.warnings));
    }

    public boolean remove(String id) {
        if (!ModManifest.ID.matcher(id).matches()) return false;
        files.delete(FOLDER + "/" + id + ".zip");
        JsonObject state = state();
        Set<String> off = disabled(state);
        if (off.remove(id)) {
            writeDisabled(state, off);
            saveState(state);
        }
        return true;
    }

    public boolean setEnabled(String id, boolean enabled) {
        JsonObject state = state();
        Set<String> off = disabled(state);
        if (enabled) off.remove(id);
        else off.add(id);
        writeDisabled(state, off);
        return saveState(state);
    }

    private static void writeDisabled(JsonObject state, Set<String> off) {
        JsonArray array = new JsonArray();
        List<String> sorted = new ArrayList<>(off);
        Collections.sort(sorted);
        for (String id : sorted) array.add(id);
        state.add("disabled", array);
    }

    // ---- applied snapshot ----
    //
    // The client registers mods once, at start-up. The integrated-server worker starts later, possibly after the
    // player imported or changed a mod in the meantime. If the worker simply planned again it could register
    // different items, and the two sides would then disagree about item ids. So the client publishes exactly what it
    // registered: copies of those zips named by content (applied/<id>-<crc>.zip) and a list (applied.json). The
    // worker only ever reads that list, so both sides always see the very same mods.

    public static final String APPLIED_JSON = FOLDER + "/applied.json";
    public static final String APPLIED_FOLDER = FOLDER + "/applied";

    /** The mods a realm registers. {@code problems} is not empty when the snapshot could not be reproduced exactly. */
    public record Applied(List<ModPackage> mods, List<String> problems, boolean safeMode) {}

    private static String hex(long crc) { return String.format("%08x", crc & 0xFFFFFFFFL); }

    private static String appliedName(String id, long crc) { return id + "-" + hex(crc) + ".zip"; }

    /**
     * Client start-up: publish the plan and return the mods to register. A mod whose copy cannot be saved is left
     * out (so the worker, which reads the copies, stays in step); if the list itself cannot be saved nothing is
     * registered.
     */
    public Applied apply(Plan plan) {
        List<String> problems = new ArrayList<>();
        List<ModPackage> loaded = new ArrayList<>();
        JsonArray list = new JsonArray();
        Set<String> keep = new HashSet<>();
        for (ModPackage pkg : plan.active()) {
            String name = appliedName(pkg.manifest.id, pkg.crc);
            byte[] existing = files.read(APPLIED_FOLDER + "/" + name);
            if (existing == null || ModPackage.crc32(existing) != pkg.crc) {
                if (!files.write(APPLIED_FOLDER + "/" + name, pkg.source)) {
                    problems.add(pkg.manifest.id + " was left out: its copy could not be saved (browser storage may be full).");
                    continue;
                }
            }
            keep.add(name);
            JsonObject entry = new JsonObject();
            entry.addProperty("id", pkg.manifest.id);
            entry.addProperty("version", pkg.manifest.version);
            entry.addProperty("crc", hex(pkg.crc));
            list.add(entry);
            loaded.add(pkg);
        }
        JsonObject root = new JsonObject();
        root.addProperty("safe", plan.safeMode());
        root.add("mods", list);
        if (!files.write(APPLIED_JSON, root.toString().getBytes(StandardCharsets.UTF_8))) {
            files.delete(APPLIED_JSON);
            problems.add("The list of loaded mods could not be saved, so no mods were loaded this time.");
            return new Applied(new ArrayList<>(), problems, plan.safeMode());
        }
        for (String name : files.list(APPLIED_FOLDER)) {
            if (!keep.contains(name)) files.delete(APPLIED_FOLDER + "/" + name);
        }
        return new Applied(loaded, problems, plan.safeMode());
    }

    /** Integrated-server worker start-up: load exactly what the client registered. */
    public Applied readApplied() {
        List<String> problems = new ArrayList<>();
        List<ModPackage> mods = new ArrayList<>();
        byte[] raw = files.read(APPLIED_JSON);
        if (raw == null) return new Applied(mods, problems, false);
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new JsonParseException("not an object");
            root = parsed.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException ex) {
            problems.add("The list of loaded mods is damaged.");
            return new Applied(mods, problems, false);
        }
        boolean safe = root.has("safe") && root.get("safe").isJsonPrimitive() && root.get("safe").getAsJsonPrimitive().isBoolean() && root.get("safe").getAsBoolean();
        JsonElement list = root.get("mods");
        if (list == null || !list.isJsonArray()) return new Applied(mods, problems, safe);
        for (JsonElement element : list.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject entry = element.getAsJsonObject();
            String id = entry.has("id") && entry.get("id").isJsonPrimitive() ? entry.get("id").getAsString() : "";
            String crcText = entry.has("crc") && entry.get("crc").isJsonPrimitive() ? entry.get("crc").getAsString() : "";
            if (!ModManifest.ID.matcher(id).matches() || !crcText.matches("[0-9a-f]{8}")) {
                problems.add("An entry in the list of loaded mods is damaged.");
                continue;
            }
            long crc = Long.parseLong(crcText, 16);
            byte[] bytes = files.read(APPLIED_FOLDER + "/" + appliedName(id, crc));
            if (bytes == null || ModPackage.crc32(bytes) != crc) {
                problems.add(id + ": the copy the game loaded at start-up is missing or damaged.");
                continue;
            }
            ModPackage pkg = ModPackage.read(bytes);
            if (pkg.manifest == null || !pkg.manifest.id.equals(id) || !pkg.problems.isEmpty()) {
                problems.add(id + ": the copy the game loaded at start-up can no longer be read.");
                continue;
            }
            mods.add(pkg);
        }
        return new Applied(mods, problems, safe);
    }

    /** True when what the plan would load now differs from what the game loaded at start-up (so a restart is needed). */
    public boolean restartNeeded(Plan plan) {
        Set<String> now = new HashSet<>();
        for (ModPackage pkg : plan.active()) now.add(pkg.manifest.id + ":" + hex(pkg.crc));
        Set<String> then = new HashSet<>();
        byte[] raw = files.read(APPLIED_JSON);
        if (raw != null) {
            try {
                JsonElement parsed = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8));
                if (parsed.isJsonObject() && parsed.getAsJsonObject().has("mods") && parsed.getAsJsonObject().get("mods").isJsonArray()) {
                    for (JsonElement element : parsed.getAsJsonObject().getAsJsonArray("mods")) {
                        if (!element.isJsonObject()) continue;
                        JsonObject entry = element.getAsJsonObject();
                        if (entry.has("id") && entry.has("crc")) then.add(entry.get("id").getAsString() + ":" + entry.get("crc").getAsString());
                    }
                }
            } catch (JsonParseException | IllegalStateException | UnsupportedOperationException ignored) {
                return true;
            }
        }
        return !now.equals(then);
    }

    // ---- planning ----

    /**
     * Works out which stored mods will load. Mods are ordered by id so every realm registers them identically.
     * A mod is blocked when it is turned off, unreadable, or needs something that is not there; blocking one can
     * block the mods that need it, so this repeats until nothing changes. The faked Fabric Loader and Fabric API
     * satisfy everything that asks for them.
     */
    public Plan plan() {
        List<Installed> all = installed();
        JsonObject state = state();
        if (safeModeFrom(state)) {
            String reason = state.has("safe_reason") && state.get("safe_reason").isJsonPrimitive() ? state.get("safe_reason").getAsString() : "Safe mode is on.";
            return new Plan(new ArrayList<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), true, reason);
        }

        Map<String, List<String>> blocked = new LinkedHashMap<>();
        Map<String, ModPackage> candidates = new TreeMap<>();
        for (Installed mod : all) {
            if (mod.pkg() == null || !mod.problems().isEmpty()) {
                blocked.put(mod.id(), new ArrayList<>(mod.problems()));
            } else if (!mod.enabled()) {
                blocked.put(mod.id(), new ArrayList<>(List.of("Turned off.")));
            } else {
                candidates.put(mod.id(), mod.pkg());
            }
        }

        Map<String, FabricFaker.Resolution> resolutions = new HashMap<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Map.Entry<String, ModPackage> entry : new ArrayList<>(candidates.entrySet())) {
                String self = entry.getKey();
                FabricFaker.Resolution resolution = FabricFaker.resolve(entry.getValue().manifest.dependencies, id -> {
                    if (id.equals(self)) return null;
                    ModPackage other = candidates.get(id);
                    return other != null ? other.manifest.version : nativeVersions.apply(id);
                });
                if (!resolution.ok()) {
                    blocked.put(self, new ArrayList<>(resolution.errors()));
                    candidates.remove(self);
                    resolutions.remove(self);
                    changed = true;
                } else {
                    resolutions.put(self, resolution);
                }
            }
        }
        return new Plan(new ArrayList<>(candidates.values()), blocked, resolutions, false, "");
    }
}
