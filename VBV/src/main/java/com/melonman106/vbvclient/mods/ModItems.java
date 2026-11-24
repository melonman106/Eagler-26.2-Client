package com.melonman106.vbvclient.mods;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The item definitions in a mod's {@code items.json}, read and checked without any game classes.
 *
 * <p>Fields (all but "id" optional):
 * name, texture, model ("generated" or "handheld"), stack_size, durability, attack_damage, attack_speed
 * (the final swings-per-second a player sees, not the game's offset), rarity, repair_with, enchantability,
 * fire_resistant, glint, food, creative_tab, tags. The names follow what Fabric API's item settings and item
 * group events let a Java mod say, so a mod author used to Fabric can guess them.</p>
 */
public final class ModItems {
    public static final int MAX_ITEMS = 256;
    public static final int MAX_STACK = 99;
    public static final int MAX_DURABILITY = 1_000_000;
    public static final List<String> RARITIES = List.of("common", "uncommon", "rare", "epic");
    public static final List<String> MODELS = List.of("generated", "handheld");
    /** The creative tabs of the base game that a mod item can be added to (the ids of {@code CreativeModeTabs}). */
    public static final List<String> TABS = List.of("building_blocks", "colored_blocks", "natural_blocks", "functional_blocks",
        "redstone_blocks", "tools_and_utilities", "combat", "food_and_drinks", "ingredients", "spawn_eggs", "op_blocks");
    public static final String NO_TAB = "none";

    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_]{1,64}");
    private static final Pattern LOCATION = Pattern.compile("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+");
    private static final Set<String> KNOWN = Set.of("id", "name", "texture", "model", "stack_size", "durability", "attack_damage",
        "attack_speed", "rarity", "repair_with", "enchantability", "fire_resistant", "glint", "food", "creative_tab", "tags");

    /** Food values, as in Fabric's food settings. */
    public record Food(int nutrition, float saturation, boolean alwaysEdible) {}

    /**
     * One item. {@code attackDamage}, {@code attackSpeed}, {@code repairWith} and {@code food} are null when not set;
     * {@code texture} and {@code repairWith} are full "namespace:path" ids.
     */
    public record ItemDef(String id, String name, String texture, String model, int stackSize, int durability,
                          Double attackDamage, Double attackSpeed, String rarity, String repairWith, int enchantability,
                          boolean fireResistant, boolean glint, Food food, List<String> creativeTabs, List<String> tags) {
        public String descriptionKey(String modId) { return "item." + modId + "." + id; }
    }

    /** The result of reading items.json. {@code items} holds every item that was valid; the mod is rejected when {@code problems} is not empty. */
    public record Result(List<ItemDef> items, List<String> problems, List<String> warnings) {}

    public static Result parse(String modId, String json) {
        List<ItemDef> items = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (JsonParseException | IllegalStateException ex) {
            problems.add("items.json is not valid JSON: " + firstLine(ex.getMessage()));
            return new Result(items, problems, warnings);
        }
        JsonArray list;
        if (root.isJsonObject() && root.getAsJsonObject().has("items") && root.getAsJsonObject().get("items").isJsonArray()) {
            list = root.getAsJsonObject().getAsJsonArray("items");
        } else if (root.isJsonArray()) {
            list = root.getAsJsonArray();
        } else {
            problems.add("items.json must look like {\"items\": [ ... ]}.");
            return new Result(items, problems, warnings);
        }
        if (list.size() > MAX_ITEMS) {
            problems.add("items.json lists " + list.size() + " items; the limit is " + MAX_ITEMS + ".");
            return new Result(items, problems, warnings);
        }

        Set<String> seen = new HashSet<>();
        boolean tagsNoticed = false;
        for (int index = 0; index < list.size(); index++) {
            JsonElement entry = list.get(index);
            String where = "items.json entry " + (index + 1);
            if (!entry.isJsonObject()) {
                problems.add(where + " must be an object ({ ... }).");
                continue;
            }
            JsonObject object = entry.getAsJsonObject();
            String id = text(object, "id", where, problems);
            if (id == null) continue;
            where = "item \"" + id + "\"";
            if (!ITEM_ID.matcher(id).matches()) {
                problems.add(where + ": the id must be 1-64 characters of lowercase letters, digits and _.");
                continue;
            }
            if (!seen.add(id)) {
                problems.add(where + ": this id is listed twice.");
                continue;
            }
            int before = problems.size();

            for (String key : object.keySet()) {
                if (!KNOWN.contains(key)) warnings.add(where + ": unknown field \"" + key + "\" is ignored.");
            }

            String name = text(object, "name", where, problems);
            if (name == null || name.isBlank()) name = prettify(id);
            if (name.length() > 64) {
                warnings.add(where + ": the name was shortened to 64 characters.");
                name = name.substring(0, 64);
            }

            String texture = location(object, "texture", where, modId, problems);
            if (texture == null) texture = modId + ":item/" + id;

            Double attackDamage = number(object, "attack_damage", where, 0, 1024, problems);
            Double attackSpeed = number(object, "attack_speed", where, 0, 100, problems);
            if (attackSpeed != null && attackDamage == null) {
                warnings.add(where + ": attack_speed has no effect without attack_damage.");
                attackSpeed = null;
            }

            String model = text(object, "model", where, problems);
            if (model == null) model = attackDamage != null ? "handheld" : "generated";
            else if (!MODELS.contains(model)) problems.add(where + ": model must be one of " + String.join(", ", MODELS) + ".");

            int durability = integer(object, "durability", where, 0, 0, MAX_DURABILITY, problems);
            int stack = integer(object, "stack_size", where, durability > 0 ? 1 : 64, 1, MAX_STACK, problems);
            if (durability > 0 && stack != 1) problems.add(where + ": an item with durability cannot stack; remove stack_size or set it to 1.");

            String rarity = text(object, "rarity", where, problems);
            if (rarity == null) rarity = "common";
            else if (!RARITIES.contains(rarity)) problems.add(where + ": rarity must be one of " + String.join(", ", RARITIES) + ".");

            String repairWith = location(object, "repair_with", where, "minecraft", problems);
            if (repairWith != null && durability == 0) {
                warnings.add(where + ": repair_with has no effect on an item without durability.");
                repairWith = null;
            }
            int enchantability = integer(object, "enchantability", where, 0, 0, 100, problems);
            boolean fire = flag(object, "fire_resistant", where, problems);
            boolean glint = flag(object, "glint", where, problems);

            Food food = null;
            if (object.has("food")) {
                if (!object.get("food").isJsonObject()) {
                    problems.add(where + ": food must look like {\"nutrition\": 4, \"saturation\": 2.4}.");
                } else {
                    JsonObject f = object.getAsJsonObject("food");
                    int nutrition = integer(f, "nutrition", where + " food", 1, 0, 20, problems);
                    Double saturation = number(f, "saturation", where + " food", 0, 20, problems);
                    food = new Food(nutrition, saturation == null ? 0.6f : saturation.floatValue(), flag(f, "always_edible", where + " food", problems));
                    if (durability > 0) problems.add(where + ": an item cannot be food and have durability.");
                }
            }

            List<String> tabs = tabs(object.get("creative_tab"), where, attackDamage != null, durability > 0, food != null, problems);
            List<String> tags = new ArrayList<>();
            if (object.has("tags")) {
                if (!object.get("tags").isJsonArray()) {
                    problems.add(where + ": tags must be a list.");
                } else {
                    for (JsonElement tag : object.getAsJsonArray("tags")) {
                        String value = tag.isJsonPrimitive() ? tag.getAsString().trim() : "";
                        if (!LOCATION.matcher(value).matches()) problems.add(where + ": " + tag + " is not a tag id such as minecraft:swords.");
                        else tags.add(value.contains(":") ? value : "minecraft:" + value);
                    }
                    if (!tags.isEmpty() && !tagsNoticed) {
                        tagsNoticed = true;
                        warnings.add("Item tags are read but not applied in this version (they need data-pack support).");
                    }
                }
            }

            if (problems.size() == before) {
                items.add(new ItemDef(id, name, texture, model, stack, durability, attackDamage, attackSpeed, rarity, repairWith,
                    enchantability, fire, glint, food, tabs, tags));
            }
        }
        return new Result(items, problems, warnings);
    }

    /** "ruby_sword" becomes "Ruby Sword". */
    public static String prettify(String id) {
        StringBuilder out = new StringBuilder();
        boolean start = true;
        for (char c : id.toCharArray()) {
            if (c == '_') {
                out.append(' ');
                start = true;
            } else {
                out.append(start ? Character.toUpperCase(c) : c);
                start = false;
            }
        }
        return out.toString();
    }

    // ---- helpers ----

    private static String firstLine(String message) {
        if (message == null) return "unreadable";
        int end = message.indexOf('\n');
        String line = end >= 0 ? message.substring(0, end) : message;
        return line.length() > 160 ? line.substring(0, 160) + "..." : line;
    }

    private static String text(JsonObject object, String key, String where, List<String> problems) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            if (key.equals("id")) problems.add(where + ": \"id\" is required.");
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            problems.add(where + ": \"" + key + "\" must be text.");
            return null;
        }
        return element.getAsString().trim();
    }

    private static String location(JsonObject object, String key, String where, String defaultNamespace, List<String> problems) {
        String value = text(object, key, where, problems);
        if (value == null) return null;
        if (!LOCATION.matcher(value).matches()) {
            problems.add(where + ": \"" + key + "\" must be an id such as namespace:path (lowercase letters, digits, _ . - /).");
            return null;
        }
        return value.contains(":") ? value : defaultNamespace + ":" + value;
    }

    private static Double number(JsonObject object, String key, String where, double min, double max, List<String> problems) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) return null;
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            problems.add(where + ": \"" + key + "\" must be a number.");
            return null;
        }
        double value = element.getAsDouble();
        if (Double.isNaN(value) || value < min || value > max) {
            problems.add(where + ": \"" + key + "\" must be between " + trim(min) + " and " + trim(max) + ".");
            return null;
        }
        return value;
    }

    private static int integer(JsonObject object, String key, String where, int fallback, int min, int max, List<String> problems) {
        Double value = number(object, key, where, min, max, problems);
        if (value == null) return fallback;
        if (value != Math.rint(value)) {
            problems.add(where + ": \"" + key + "\" must be a whole number.");
            return fallback;
        }
        return value.intValue();
    }

    private static boolean flag(JsonObject object, String key, String where, List<String> problems) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) return false;
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            problems.add(where + ": \"" + key + "\" must be true or false.");
            return false;
        }
        return element.getAsBoolean();
    }

    /** "creative_tab" is one tab id or a list of them; "none" hides the item from the creative inventory. */
    private static List<String> tabs(JsonElement element, String where, boolean weapon, boolean tool, boolean food, List<String> problems) {
        List<String> tabs = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            tabs.add(food ? "food_and_drinks" : weapon ? "combat" : tool ? "tools_and_utilities" : "ingredients");
            return tabs;
        }
        List<JsonElement> values = new ArrayList<>();
        if (element.isJsonArray()) element.getAsJsonArray().forEach(values::add);
        else values.add(element);
        for (JsonElement value : values) {
            String tab = value.isJsonPrimitive() ? value.getAsString().trim().toLowerCase(Locale.ROOT) : "";
            if (tab.startsWith("minecraft:")) tab = tab.substring("minecraft:".length());
            if (tab.equals(NO_TAB) || TABS.contains(tab)) {
                if (!tabs.contains(tab)) tabs.add(tab);
            } else {
                problems.add(where + ": unknown creative_tab " + value + ". Use one of: " + String.join(", ", TABS) + ", or \"none\".");
            }
        }
        if (tabs.size() > 1 && tabs.contains(NO_TAB)) {
            problems.add(where + ": creative_tab cannot mix \"none\" with real tabs.");
        }
        return tabs;
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    /** Used by tests and the UI to know every key an item may have. */
    public static Set<String> knownFields() { return KNOWN; }
}
