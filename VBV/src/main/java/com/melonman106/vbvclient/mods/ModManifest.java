package com.melonman106.vbvclient.mods;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * What a mod says about itself, read from {@code info.json} (this client's format) or from a real
 * {@code fabric.mod.json} (so a mod made for Fabric can be imported for its data and assets).
 *
 * <p>Reading never throws: anything wrong is reported in {@link Result#problems()} (the mod is rejected) or
 * {@link Result#warnings()} (the mod still loads). No game classes are used, so this runs on a plain JVM.</p>
 */
public final class ModManifest {
    public static final int SCHEMA = 1;
    public static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]{1,63}");
    /** Kinds of content a mod can declare. Items, blocks, effects, potions, crops, HUDs, recipes and events are loaded. */
    public static final List<String> KINDS = List.of("item", "block", "entity", "effect", "potion", "crop", "hud", "recipe", "event");

    public enum Format { VBV, FABRIC }

    public final Format format;
    public final String id;
    public final String name;
    public final String version;
    public final String description;
    public final List<String> authors;
    public final boolean survival;
    public final boolean multiplayer;
    /** "*", "client" or "server" (Fabric's {@code environment}). */
    public final String environment;
    public final List<FabricFaker.Dependency> dependencies;
    /** Declared content kinds from info.json; empty means "work it out from the files". */
    public final List<String> declaredKinds;
    /** Path of the icon inside the zip, or null. */
    public final String icon;
    /** True when a fabric.mod.json lists Java entrypoints, mixins or jars, none of which can run here. */
    public final boolean hasJavaCode;
    /** The permission level the mod asks for its commands (0-2, never more), or -1 when info.json does not say. */
    public final int commandLevel;

    private ModManifest(Format format, String id, String name, String version, String description, List<String> authors,
                        boolean survival, boolean multiplayer, String environment, List<FabricFaker.Dependency> dependencies,
                        List<String> declaredKinds, String icon, boolean hasJavaCode, int commandLevel) {
        this.commandLevel = commandLevel;
        this.format = format;
        this.id = id;
        this.name = name;
        this.version = version;
        this.description = description;
        this.authors = Collections.unmodifiableList(authors);
        this.survival = survival;
        this.multiplayer = multiplayer;
        this.environment = environment;
        this.dependencies = Collections.unmodifiableList(dependencies);
        this.declaredKinds = Collections.unmodifiableList(declaredKinds);
        this.icon = icon;
        this.hasJavaCode = hasJavaCode;
    }

    /** The outcome of reading a manifest: a manifest when {@code problems} is empty. */
    public record Result(ModManifest manifest, List<String> problems, List<String> warnings) {
        public boolean ok() { return manifest != null && problems.isEmpty(); }
    }

    public static Result parse(Format format, String json) {
        String file = format == Format.FABRIC ? "fabric.mod.json" : "info.json";
        List<String> problems = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                problems.add(file + " must be a JSON object ({ ... }).");
                return new Result(null, problems, warnings);
            }
            root = parsed.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException ex) {
            problems.add(file + " is not valid JSON: " + firstLine(ex.getMessage()));
            return new Result(null, problems, warnings);
        }

        if (format == Format.VBV && root.has("schema")) {
            Integer schema = integer(root.get("schema"));
            if (schema == null) problems.add(file + ": \"schema\" must be a whole number.");
            else if (schema > SCHEMA) problems.add(file + ": this mod uses schema " + schema + " but this client understands up to " + SCHEMA + ". Update the client.");
        }
        if (format == Format.FABRIC && root.has("schemaVersion")) {
            Integer schema = integer(root.get("schemaVersion"));
            if (schema != null && schema != 1) warnings.add(file + ": schemaVersion " + schema + " is unfamiliar; reading it as version 1.");
        }

        String id = string(root, "id", file, problems, true);
        if (id != null) {
            if (!ID.matcher(id).matches()) {
                problems.add(file + ": id \"" + id + "\" must be 2-64 characters: start with a lowercase letter, then lowercase letters, digits, _ or -.");
            } else if (FabricFaker.isReserved(id)) {
                problems.add(file + ": id \"" + id + "\" is reserved (it names the game, this client or a Fabric API module). Pick another id.");
            }
        }
        String version = string(root, "version", file, problems, true);
        String name = string(root, "name", file, problems, format == Format.VBV);
        if (name == null || name.isBlank()) name = id == null ? "" : id;
        if (name.length() > 48) {
            warnings.add(file + ": the name is longer than 48 characters and was shortened.");
            name = name.substring(0, 48);
        }
        String description = string(root, "description", file, problems, false);
        if (description == null) description = "";
        if (description.length() > 400) {
            warnings.add(file + ": the description is longer than 400 characters and was shortened.");
            description = description.substring(0, 400);
        }

        List<String> authors = authors(root.get("authors"));
        boolean survival = bool(root, "survival", true, file, problems);
        boolean multiplayer = bool(root, "multiplayer", false, file, problems);

        int commandLevel = -1;
        if (format == Format.VBV && root.has("command_level")) {
            Integer level = integer(root.get("command_level"));
            if (level == null) {
                problems.add(file + ": \"command_level\" must be a whole number from 0 to " + CommandGuard.MAX_LEVEL + ".");
            } else {
                commandLevel = CommandGuard.clampLevel(level);
                if (commandLevel != level) {
                    warnings.add(file + ": \"command_level\" " + level + " was changed to " + commandLevel + " (a mod's commands never run above level " + CommandGuard.MAX_LEVEL + ").");
                }
            }
        }

        String environment = "*";
        if (root.has("environment")) {
            String value = root.get("environment").isJsonPrimitive() ? root.get("environment").getAsString() : "";
            if (value.equals("*") || value.equals("client") || value.equals("server")) environment = value;
            else problems.add(file + ": \"environment\" must be \"*\", \"client\" or \"server\".");
        }

        List<String> kinds = new ArrayList<>();
        if (format == Format.VBV && root.has("provides")) {
            JsonElement provides = root.get("provides");
            if (!provides.isJsonArray()) {
                problems.add(file + ": \"provides\" must be a list such as [\"item\"].");
            } else {
                for (JsonElement entry : provides.getAsJsonArray()) {
                    String kind = entry.isJsonPrimitive() ? entry.getAsString().toLowerCase(Locale.ROOT) : "";
                    if (!KINDS.contains(kind)) problems.add(file + ": unknown content kind " + entry + " in \"provides\". Use: " + String.join(", ", KINDS) + ".");
                    else if (!kinds.contains(kind)) kinds.add(kind);
                }
            }
        }

        List<FabricFaker.Dependency> dependencies = new ArrayList<>();
        if (format == Format.VBV) {
            dependencies(root.get("requires"), FabricFaker.Kind.REQUIRES, "requires", file, dependencies, problems);
        } else {
            dependencies(root.get("depends"), FabricFaker.Kind.REQUIRES, "depends", file, dependencies, problems);
        }
        dependencies(root.get("recommends"), FabricFaker.Kind.RECOMMENDS, "recommends", file, dependencies, problems);
        dependencies(root.get("conflicts"), FabricFaker.Kind.CONFLICTS, "conflicts", file, dependencies, problems);
        dependencies(root.get("breaks"), FabricFaker.Kind.BREAKS, "breaks", file, dependencies, problems);

        String icon = icon(root.get("icon"));
        if (icon == null && format == Format.VBV) icon = "pack.png";

        boolean hasJavaCode = false;
        if (format == Format.FABRIC) {
            hasJavaCode = nonEmpty(root.get("entrypoints")) || nonEmpty(root.get("mixins")) || nonEmpty(root.get("jars"));
            if (hasJavaCode) {
                warnings.add("This mod also contains Java code (entrypoints, mixins or jars). Java cannot run in the browser, so only its data and assets are used.");
            }
        }

        if (!problems.isEmpty()) return new Result(null, problems, warnings);
        ModManifest manifest = new ModManifest(format, id, name, version, description, authors, survival, multiplayer,
            environment, dependencies, kinds, icon, hasJavaCode, commandLevel);
        return new Result(manifest, problems, warnings);
    }

    /** The permission level this mod's commands run at: what info.json asked for, else {@link CommandGuard#MAX_LEVEL}. */
    public int effectiveCommandLevel() {
        return commandLevel < 0 ? CommandGuard.MAX_LEVEL : commandLevel;
    }

    // ---- helpers ----

    private static String firstLine(String message) {
        if (message == null) return "unreadable";
        int end = message.indexOf('\n');
        String line = end >= 0 ? message.substring(0, end) : message;
        return line.length() > 160 ? line.substring(0, 160) + "..." : line;
    }

    private static Integer integer(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return null;
        double value = element.getAsDouble();
        return value == Math.rint(value) && Math.abs(value) < 1e9 ? (int) value : null;
    }

    private static String string(JsonObject root, String key, String file, List<String> problems, boolean required) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            if (required) problems.add(file + ": \"" + key + "\" is required.");
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            problems.add(file + ": \"" + key + "\" must be text.");
            return null;
        }
        String value = element.getAsString().trim();
        if (required && value.isEmpty()) {
            problems.add(file + ": \"" + key + "\" must not be empty.");
            return null;
        }
        return value;
    }

    private static boolean bool(JsonObject root, String key, boolean fallback, String file, List<String> problems) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) return fallback;
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            problems.add(file + ": \"" + key + "\" must be true or false.");
            return fallback;
        }
        return element.getAsBoolean();
    }

    private static List<String> authors(JsonElement element) {
        List<String> out = new ArrayList<>();
        if (element == null || element.isJsonNull()) return out;
        List<JsonElement> list = new ArrayList<>();
        if (element.isJsonArray()) element.getAsJsonArray().forEach(list::add);
        else list.add(element);
        for (JsonElement entry : list) {
            String name = null;
            if (entry.isJsonPrimitive()) name = entry.getAsString();
            else if (entry.isJsonObject() && entry.getAsJsonObject().has("name") && entry.getAsJsonObject().get("name").isJsonPrimitive()) {
                name = entry.getAsJsonObject().get("name").getAsString();
            }
            if (name != null && !name.isBlank() && out.size() < 16) out.add(name.trim());
        }
        return out;
    }

    private static boolean nonEmpty(JsonElement element) {
        if (element == null || element.isJsonNull()) return false;
        if (element.isJsonArray()) return element.getAsJsonArray().size() > 0;
        if (element.isJsonObject()) return element.getAsJsonObject().size() > 0;
        return element.isJsonPrimitive();
    }

    /** Fabric allows {@code "icon": "a.png"} or {@code "icon": {"32": "a.png", "128": "b.png"}}; the biggest wins. */
    private static String icon(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonPrimitive()) return element.getAsString();
        if (element.isJsonObject()) {
            String best = null;
            int bestSize = -1;
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                if (!entry.getValue().isJsonPrimitive()) continue;
                int size;
                try { size = Integer.parseInt(entry.getKey().trim()); } catch (NumberFormatException ex) { size = 0; }
                if (size > bestSize) {
                    bestSize = size;
                    best = entry.getValue().getAsString();
                }
            }
            return best;
        }
        return null;
    }

    /**
     * Reads a dependency list in any of the shapes mods use: Fabric's object ({@code {"id": ">=1.0"}} or
     * {@code {"id": [">=1", "2.x"]}}), a list of {@code {"id": ..., "version": ...}} objects, or a list of strings
     * ({@code "id"} or {@code "id >=1.0"}).
     */
    private static void dependencies(JsonElement element, FabricFaker.Kind kind, String field, String file,
                                     List<FabricFaker.Dependency> out, List<String> problems) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                ModVersion.Range range = range(entry.getValue());
                if (range == null) {
                    problems.add(file + ": \"" + field + "\" entry \"" + entry.getKey() + "\" needs a version range as text, such as \">=1.0\" or \"*\".");
                    continue;
                }
                out.add(new FabricFaker.Dependency(FabricFaker.normalise(entry.getKey()), range, kind));
            }
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement entry : array) {
                if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                    String[] pieces = entry.getAsString().trim().split("\\s+", 2);
                    if (pieces[0].isEmpty()) continue;
                    out.add(new FabricFaker.Dependency(FabricFaker.normalise(pieces[0]),
                        pieces.length > 1 ? ModVersion.Range.parse(pieces[1]) : ModVersion.Range.any(), kind));
                } else if (entry.isJsonObject() && entry.getAsJsonObject().has("id") && entry.getAsJsonObject().get("id").isJsonPrimitive()) {
                    JsonObject object = entry.getAsJsonObject();
                    JsonElement version = object.has("version") ? object.get("version") : null;
                    ModVersion.Range range = version == null ? ModVersion.Range.any() : range(version);
                    if (range == null) {
                        problems.add(file + ": \"" + field + "\" entry \"" + object.get("id").getAsString() + "\" has a version that is not text.");
                        continue;
                    }
                    out.add(new FabricFaker.Dependency(FabricFaker.normalise(object.get("id").getAsString()), range, kind));
                } else {
                    problems.add(file + ": \"" + field + "\" entries must be an id, or {\"id\": ..., \"version\": ...}.");
                }
            }
        } else {
            problems.add(file + ": \"" + field + "\" must be a list or an object.");
        }
    }

    private static ModVersion.Range range(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) return ModVersion.Range.parse(element.getAsString());
        if (element.isJsonArray()) {
            List<String> ranges = new ArrayList<>();
            for (JsonElement entry : element.getAsJsonArray()) {
                if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) return null;
                ranges.add(entry.getAsString());
            }
            return ModVersion.Range.parseAny(ranges);
        }
        return null;
    }
}
