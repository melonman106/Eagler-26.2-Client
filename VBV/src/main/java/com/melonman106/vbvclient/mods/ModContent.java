package com.melonman106.vbvclient.mods;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The non-item content of an imported mod, read from its optional {@code content.json}.
 *
 * <p>Items stay in {@code items.json} so a small item-only mod needs no new file. A mod that wants an effect, potion,
 * block, crop or HUD puts them here. The file is plain data: the client still owns the code that registers a
 * {@code MobEffect}, {@code Block}, {@code CropBlock}, {@code Potion} or HUD panel, exactly as it already owns the
 * code that turns an item definition into an {@code Item}.</p>
 *
 * <p>No game classes are used here, so the file is parsed by the same plain-JVM checks as the rest of the importer.</p>
 */
public final class ModContent {
    public static final int MAX_EFFECTS = 64;
    public static final int MAX_POTIONS = 64;
    public static final int MAX_BLOCKS = 128;
    public static final int MAX_CROPS = 64;
    public static final int MAX_RECIPES = 256;
    public static final int MAX_POTION_EFFECTS = 8;
    public static final List<String> CATEGORIES = List.of("beneficial", "harmful", "neutral");
    public static final List<String> SOUNDS = List.of("stone", "amethyst", "glass", "crop", "grass", "wood", "metal",
        "wool", "gravel", "sand", "snow", "ladder", "slime", "honey", "netherite", "deepslate", "copper", "bone");

    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,64}");
    private static final Pattern LOCATION = Pattern.compile("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+");
    private static final Set<String> KNOWN = Set.of("effects", "potions", "blocks", "crops", "hud", "recipes");

    /** A custom status effect. The client heals {@code healAmount} every {@code healInterval} ticks. */
    public record EffectDef(String id, String name, int color, String category, int healInterval, float healAmount) {}

    /** One effect on a potion; {@code effect} is a full namespace:path id. */
    public record PotionEffectDef(String effect, int duration, int amplifier) {}

    public record PotionDef(String id, String name, List<PotionEffectDef> effects) {}

    /**
     * A block and its matching block item. {@code texture} is a resource path such as {@code block/ruby_block}; the
     * block model, blockstate and item model are supplied by the mod's assets (or by the generator that built the zip).
     */
    public record BlockDef(String id, String name, String texture, float hardness, float resistance, int light,
                           String sound, List<String> creativeTabs) {}

    /** A crop block plus the seed item that plants it and the item it drops when mature. */
    public record CropDef(String id, String name, String block, String seed, String drop, String texture,
                          String seedTexture, int stages, List<String> creativeTabs) {}

    /** One HUD panel. It is shown while {@code effect} is active, or always when {@code alwaysShow} is true. */
    public record HudDef(String modId, String id, String title, String effect, boolean showTimer,
                         String nearbyEntity, boolean alwaysShow) {}

    public final List<EffectDef> effects;
    public final List<PotionDef> potions;
    public final List<BlockDef> blocks;
    public final List<CropDef> crops;
    public final HudDef hud;
    public final JsonArray recipes;

    private ModContent(List<EffectDef> effects, List<PotionDef> potions, List<BlockDef> blocks, List<CropDef> crops,
                       HudDef hud, JsonArray recipes) {
        this.effects = Collections.unmodifiableList(effects);
        this.potions = Collections.unmodifiableList(potions);
        this.blocks = Collections.unmodifiableList(blocks);
        this.crops = Collections.unmodifiableList(crops);
        this.hud = hud;
        this.recipes = recipes == null ? new JsonArray() : recipes;
    }

    public record Result(ModContent content, List<String> problems, List<String> warnings) {}

    public boolean hasContent() {
        return !effects.isEmpty() || !potions.isEmpty() || !blocks.isEmpty() || !crops.isEmpty() || hud != null
            || recipes.size() > 0;
    }

    /** Content kinds this file really provides, in the same words {@code info.json} uses. */
    public List<String> kinds() {
        List<String> out = new ArrayList<>();
        if (!effects.isEmpty()) out.add("effect");
        if (!potions.isEmpty()) out.add("potion");
        if (!blocks.isEmpty()) out.add("block");
        if (!crops.isEmpty()) out.add("crop");
        if (hud != null) out.add("hud");
        if (recipes.size() > 0) out.add("recipe");
        return out;
    }

    public static Result parse(String modId, String json) {
        List<String> problems = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                problems.add("content.json must be a JSON object ({ ... }).");
                return new Result(empty(), problems, warnings);
            }
            root = parsed.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException ex) {
            problems.add("content.json is not valid JSON: " + firstLine(ex.getMessage()));
            return new Result(empty(), problems, warnings);
        }
        for (String key : root.keySet()) {
            if (!KNOWN.contains(key)) warnings.add("content.json: unknown field \"" + key + "\" is ignored.");
        }

        List<EffectDef> effects = effects(modId, root.get("effects"), problems, warnings);
        List<PotionDef> potions = potions(modId, root.get("potions"), problems, warnings);
        List<BlockDef> blocks = blocks(modId, root.get("blocks"), problems, warnings);
        List<CropDef> crops = crops(modId, root.get("crops"), problems, warnings);
        HudDef hud = hud(modId, root.get("hud"), problems, warnings);

        JsonArray recipes = new JsonArray();
        JsonElement recipeElement = root.get("recipes");
        if (recipeElement != null && !recipeElement.isJsonNull()) {
            if (!recipeElement.isJsonArray()) {
                problems.add("content.json: \"recipes\" must be a list.");
            } else {
                JsonArray array = recipeElement.getAsJsonArray();
                if (array.size() > MAX_RECIPES) {
                    problems.add("content.json lists " + array.size() + " recipes; the limit is " + MAX_RECIPES + ".");
                } else {
                    for (int i = 0; i < array.size(); i++) {
                        JsonElement entry = array.get(i);
                        if (!entry.isJsonObject()) {
                            problems.add("content.json recipe " + (i + 1) + " must be an object ({ ... }).");
                        } else if (!entry.getAsJsonObject().has("type")) {
                            problems.add("content.json recipe " + (i + 1) + " needs a \"type\" such as minecraft:crafting_shaped.");
                        } else {
                            recipes.add(entry);
                        }
                    }
                }
            }
        }

        ModContent content = new ModContent(effects, potions, blocks, crops, hud, recipes);
        return new Result(content, problems, warnings);
    }

    private static ModContent empty() {
        return new ModContent(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), null, new JsonArray());
    }

    // ---- effects ----

    private static List<EffectDef> effects(String modId, JsonElement element, List<String> problems, List<String> warnings) {
        List<EffectDef> out = new ArrayList<>();
        if (element == null || element.isJsonNull()) return out;
        if (!element.isJsonArray()) {
            problems.add("content.json: \"effects\" must be a list.");
            return out;
        }
        JsonArray list = element.getAsJsonArray();
        if (list.size() > MAX_EFFECTS) {
            problems.add("content.json lists " + list.size() + " effects; the limit is " + MAX_EFFECTS + ".");
            return out;
        }
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < list.size(); index++) {
            String where = "content.json effect " + (index + 1);
            if (!list.get(index).isJsonObject()) {
                problems.add(where + " must be an object ({ ... }).");
                continue;
            }
            JsonObject object = list.get(index).getAsJsonObject();
            String id = text(object, "id", where, problems);
            if (id == null) continue;
            where = "effect \"" + id + "\"";
            if (!ID.matcher(id).matches()) {
                problems.add(where + ": the id must be 1-64 characters of lowercase letters, digits and _.");
                continue;
            }
            if (!seen.add(id)) {
                problems.add(where + ": this id is listed twice.");
                continue;
            }
            int before = problems.size();
            String name = name(object, id, where, warnings, problems);
            int color = color(object, where, problems);
            String category = text(object, "category", where, problems);
            if (category == null) category = "beneficial";
            else if (!CATEGORIES.contains(category)) problems.add(where + ": category must be one of " + String.join(", ", CATEGORIES) + ".");
            int interval = integer(object, "heal_interval", where, 40, 1, 20 * 60, problems);
            Double amount = number(object, "heal_amount", where, 0, 20, problems);
            float heal = amount == null ? 1.0F : amount.floatValue();
            if (problems.size() == before) out.add(new EffectDef(id, name, color, category, interval, heal));
        }
        return out;
    }

    // ---- potions ----

    private static List<PotionDef> potions(String modId, JsonElement element, List<String> problems, List<String> warnings) {
        List<PotionDef> out = new ArrayList<>();
        if (element == null || element.isJsonNull()) return out;
        if (!element.isJsonArray()) {
            problems.add("content.json: \"potions\" must be a list.");
            return out;
        }
        JsonArray list = element.getAsJsonArray();
        if (list.size() > MAX_POTIONS) {
            problems.add("content.json lists " + list.size() + " potions; the limit is " + MAX_POTIONS + ".");
            return out;
        }
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < list.size(); index++) {
            String where = "content.json potion " + (index + 1);
            if (!list.get(index).isJsonObject()) {
                problems.add(where + " must be an object ({ ... }).");
                continue;
            }
            JsonObject object = list.get(index).getAsJsonObject();
            String id = text(object, "id", where, problems);
            if (id == null) continue;
            where = "potion \"" + id + "\"";
            if (!ID.matcher(id).matches()) {
                problems.add(where + ": the id must be 1-64 characters of lowercase letters, digits and _.");
                continue;
            }
            if (!seen.add(id)) {
                problems.add(where + ": this id is listed twice.");
                continue;
            }
            int before = problems.size();
            String name = name(object, id, where, warnings, problems);
            List<PotionEffectDef> effects = new ArrayList<>();
            JsonElement effectsElement = object.get("effects");
            if (effectsElement == null || !effectsElement.isJsonArray()) {
                problems.add(where + ": \"effects\" must be a list of at least one effect.");
            } else {
                JsonArray array = effectsElement.getAsJsonArray();
                if (array.isEmpty() || array.size() > MAX_POTION_EFFECTS) {
                    problems.add(where + ": effects must contain 1-" + MAX_POTION_EFFECTS + " entries.");
                } else {
                    for (int i = 0; i < array.size(); i++) {
                        String effectWhere = where + " effect " + (i + 1);
                        if (!array.get(i).isJsonObject()) {
                            problems.add(effectWhere + " must be an object ({ ... }).");
                            continue;
                        }
                        JsonObject effect = array.get(i).getAsJsonObject();
                        String effectId = location(effect, "effect", effectWhere, modId, problems);
                        if (effectId == null) continue;
                        int duration = integer(effect, "duration", effectWhere, 900, 1, 1_000_000, problems);
                        int amplifier = integer(effect, "amplifier", effectWhere, 0, 0, 10, problems);
                        effects.add(new PotionEffectDef(effectId, duration, amplifier));
                    }
                }
            }
            if (problems.size() == before) out.add(new PotionDef(id, name, effects));
        }
        return out;
    }

    // ---- blocks ----

    private static List<BlockDef> blocks(String modId, JsonElement element, List<String> problems, List<String> warnings) {
        List<BlockDef> out = new ArrayList<>();
        if (element == null || element.isJsonNull()) return out;
        if (!element.isJsonArray()) {
            problems.add("content.json: \"blocks\" must be a list.");
            return out;
        }
        JsonArray list = element.getAsJsonArray();
        if (list.size() > MAX_BLOCKS) {
            problems.add("content.json lists " + list.size() + " blocks; the limit is " + MAX_BLOCKS + ".");
            return out;
        }
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < list.size(); index++) {
            String where = "content.json block " + (index + 1);
            if (!list.get(index).isJsonObject()) {
                problems.add(where + " must be an object ({ ... }).");
                continue;
            }
            JsonObject object = list.get(index).getAsJsonObject();
            String id = text(object, "id", where, problems);
            if (id == null) continue;
            where = "block \"" + id + "\"";
            if (!ID.matcher(id).matches()) {
                problems.add(where + ": the id must be 1-64 characters of lowercase letters, digits and _.");
                continue;
            }
            if (!seen.add(id)) {
                problems.add(where + ": this id is listed twice.");
                continue;
            }
            int before = problems.size();
            String name = name(object, id, where, warnings, problems);
            String texture = texture(object, "texture", "block/" + id, where, modId, problems);
            Double hardness = number(object, "hardness", where, 0, 100, problems);
            float destroy = hardness == null ? 0.6F : hardness.floatValue();
            Double resistance = number(object, "resistance", where, 0, 100, problems);
            float blast = resistance == null ? destroy : resistance.floatValue();
            int light = integer(object, "light", where, 0, 0, 15, problems);
            String sound = text(object, "sound", where, problems);
            if (sound == null) sound = "stone";
            else if (!SOUNDS.contains(sound)) problems.add(where + ": sound must be one of " + String.join(", ", SOUNDS) + ".");
            List<String> tabs = tabs(object.get("creative_tab"), where, false, false, false, problems);
            if (problems.size() == before) {
                out.add(new BlockDef(id, name, texture, destroy, blast, light, sound, tabs));
            }
        }
        return out;
    }

    // ---- crops ----

    private static List<CropDef> crops(String modId, JsonElement element, List<String> problems, List<String> warnings) {
        List<CropDef> out = new ArrayList<>();
        if (element == null || element.isJsonNull()) return out;
        if (!element.isJsonArray()) {
            problems.add("content.json: \"crops\" must be a list.");
            return out;
        }
        JsonArray list = element.getAsJsonArray();
        if (list.size() > MAX_CROPS) {
            problems.add("content.json lists " + list.size() + " crops; the limit is " + MAX_CROPS + ".");
            return out;
        }
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < list.size(); index++) {
            String where = "content.json crop " + (index + 1);
            if (!list.get(index).isJsonObject()) {
                problems.add(where + " must be an object ({ ... }).");
                continue;
            }
            JsonObject object = list.get(index).getAsJsonObject();
            String id = text(object, "id", where, problems);
            if (id == null) continue;
            where = "crop \"" + id + "\"";
            if (!ID.matcher(id).matches()) {
                problems.add(where + ": the id must be 1-64 characters of lowercase letters, digits and _.");
                continue;
            }
            if (!seen.add(id)) {
                problems.add(where + ": this id is listed twice.");
                continue;
            }
            int before = problems.size();
            String name = name(object, id, where, warnings, problems);
            String block = localId(object, "block", id + "_crop", where, problems);
            String seed = localId(object, "seed", id + "_seeds", where, problems);
            String drop = localId(object, "drop", id, where, problems);
            int stages = integer(object, "stages", where, 4, 1, 7, problems);
            String texture = texture(object, "texture", "block/" + id, where, modId, problems);
            String seedTexture = texture(object, "seed_texture", "item/" + seed, where, modId, problems);
            List<String> tabs = tabs(object.get("creative_tab"), where, false, false, false, problems);
            if (problems.size() == before) {
                out.add(new CropDef(id, name, block, seed, drop, texture, seedTexture, stages, tabs));
            }
        }
        return out;
    }

    // ---- HUD ----

    private static HudDef hud(String modId, JsonElement element, List<String> problems, List<String> warnings) {
        if (element == null || element.isJsonNull()) return null;
        String where = "content.json hud";
        if (!element.isJsonObject()) {
            problems.add(where + " must be an object ({ ... }).");
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        String id = text(object, "id", where, problems);
        if (id == null) return null;
        where = "hud \"" + id + "\"";
        if (!ID.matcher(id).matches()) {
            problems.add(where + ": the id must be 1-64 characters of lowercase letters, digits and _.");
            return null;
        }
        String title = text(object, "title", where, problems);
        if (title == null || title.isBlank()) title = ModItems.prettify(id);
        if (title.length() > 64) {
            warnings.add(where + ": the title was shortened to 64 characters.");
            title = title.substring(0, 64);
        }
        String effect = location(object, "effect", where, modId, problems);
        if (effect == null) return null;
        boolean showTimer = flag(object, "show_timer", true, where, problems);
        String nearby = location(object, "nearby_entity", where, "minecraft", problems);
        boolean always = flag(object, "always_show", false, where, problems);
        return new HudDef(modId, id, title, effect, showTimer, nearby, always);
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

    private static String name(JsonObject object, String id, String where, List<String> warnings, List<String> problems) {
        String name = text(object, "name", where, problems);
        if (name == null || name.isBlank()) name = ModItems.prettify(id);
        if (name.length() > 64) {
            warnings.add(where + ": the name was shortened to 64 characters.");
            name = name.substring(0, 64);
        }
        return name;
    }

    private static String localId(JsonObject object, String key, String fallback, String where, List<String> problems) {
        String value = text(object, key, where, problems);
        if (value == null) return fallback;
        if (!ID.matcher(value).matches()) {
            problems.add(where + ": \"" + key + "\" must be 1-64 characters of lowercase letters, digits and _.");
            return fallback;
        }
        return value;
    }

    private static String location(JsonObject object, String key, String where, String defaultNamespace, List<String> problems) {
        String value = text(object, key, where, problems);
        if (value == null) {
            if (!key.equals("nearby_entity")) problems.add(where + ": \"" + key + "\" is required.");
            return null;
        }
        if (!LOCATION.matcher(value).matches()) {
            problems.add(where + ": \"" + key + "\" must be an id such as namespace:path (lowercase letters, digits, _ . - /).");
            return null;
        }
        return value.contains(":") ? value : defaultNamespace + ":" + value;
    }

    private static String texture(JsonObject object, String key, String fallback, String where, String modId, List<String> problems) {
        String value = location(object, key, where, modId, problems);
        if (value == null) value = modId + ":" + fallback;
        int colon = value.indexOf(':');
        String path = colon >= 0 ? value.substring(colon + 1) : value;
        if (!path.startsWith("block/") && !path.startsWith("item/")) {
            problems.add(where + ": \"" + key + "\" must be inside block/ or item/.");
        }
        return value;
    }

    private static int color(JsonObject object, String where, List<String> problems) {
        JsonElement element = object.get("color");
        if (element == null || element.isJsonNull()) return 0x66D9FF;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            double value = element.getAsDouble();
            if (value >= 0 && value <= 0xFFFFFF) return (int) value;
            problems.add(where + ": color must be between 0 and 16777215.");
            return 0x66D9FF;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            problems.add(where + ": \"color\" must be text such as \"#66D9FF\" or a number.");
            return 0x66D9FF;
        }
        String value = element.getAsString().trim();
        if (value.startsWith("#")) value = value.substring(1);
        if (value.startsWith("0x") || value.startsWith("0X")) value = value.substring(2);
        if (!value.matches("[0-9a-fA-F]{6}")) {
            problems.add(where + ": color must be six hexadecimal digits, such as \"#66D9FF\".");
            return 0x66D9FF;
        }
        return Integer.parseInt(value, 16);
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

    private static boolean flag(JsonObject object, String key, boolean fallback, String where, List<String> problems) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) return fallback;
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            problems.add(where + ": \"" + key + "\" must be true or false.");
            return fallback;
        }
        return element.getAsBoolean();
    }

    private static List<String> tabs(JsonElement element, String where, boolean weapon, boolean tool, boolean food, List<String> problems) {
        List<String> tabs = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            tabs.add(food ? "food_and_drinks" : weapon ? "combat" : tool ? "tools_and_utilities" : "natural_blocks");
            return tabs;
        }
        List<JsonElement> values = new ArrayList<>();
        if (element.isJsonArray()) element.getAsJsonArray().forEach(values::add);
        else values.add(element);
        for (JsonElement value : values) {
            String tab = value.isJsonPrimitive() ? value.getAsString().trim().toLowerCase(Locale.ROOT) : "";
            if (tab.startsWith("minecraft:")) tab = tab.substring("minecraft:".length());
            if (tab.equals(ModItems.NO_TAB) || ModItems.TABS.contains(tab)) {
                if (!tabs.contains(tab)) tabs.add(tab);
            } else {
                problems.add(where + ": unknown creative_tab " + value + ". Use one of: " + String.join(", ", ModItems.TABS) + ", or \"none\".");
            }
        }
        if (tabs.size() > 1 && tabs.contains(ModItems.NO_TAB)) {
            problems.add(where + ": creative_tab cannot mix \"none\" with real tabs.");
        }
        return tabs;
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
}
