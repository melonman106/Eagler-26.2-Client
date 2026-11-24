package com.melonman106.vbvclient.mods;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds the resource-pack view of a mod: the files from its {@code assets/} folder plus the small files the game
 * needs for each item (the item definition, the item model and the display-name language entry) which the mod
 * author does not have to write by hand. Anything the author supplies themselves is never overwritten.
 *
 * <p>Keys are resource-pack paths without the leading "assets/", for example {@code ruby/models/item/ruby_sword.json}.</p>
 */
public final class ModAssets {
    /** Where the mod icon is served from, so the Mods screen can draw it as {@code <id>:textures/vbv/icon.png}. */
    public static String iconLocation(String modId) { return modId + ":textures/vbv/icon.png"; }

    private ModAssets() {}

    public static Map<String, byte[]> build(ModPackage pkg) {
        Map<String, byte[]> out = new TreeMap<>(pkg.assets());
        String id = pkg.manifest.id;

        for (ModItems.ItemDef item : pkg.items) {
            putIfAbsent(out, id + "/items/" + item.id() + ".json", itemDefinition(id, item));
            putIfAbsent(out, id + "/models/item/" + item.id() + ".json", itemModel(item));
        }

        if (!pkg.items.isEmpty()) {
            String langKey = id + "/lang/en_us.json";
            JsonObject lang = new JsonObject();
            byte[] existing = out.get(langKey);
            if (existing != null) {
                try {
                    JsonElement parsed = JsonParser.parseString(new String(existing, StandardCharsets.UTF_8));
                    if (parsed.isJsonObject()) lang = parsed.getAsJsonObject();
                } catch (JsonParseException | IllegalStateException ignored) {
                    // an unreadable language file was already dropped by ModPackage; start fresh if it somehow survived
                }
            }
            for (ModItems.ItemDef item : pkg.items) {
                String key = item.descriptionKey(id);
                if (!lang.has(key)) lang.addProperty(key, item.name());
            }
            out.put(langKey, lang.toString().getBytes(StandardCharsets.UTF_8));
        }

        if (pkg.icon != null) putIfAbsent(out, id + "/textures/vbv/icon.png", pkg.icon);
        return out;
    }

    /** The first resource-pack format that uses {@code min_format}/{@code max_format} (formats up to 64 need the legacy fields). */
    public static final int FIRST_RANGE_FORMAT = 65;

    /**
     * The {@code pack.mcmeta} the game reads to decide whether the mod's pack fits this version. The range runs from the
     * first ranged format to the running game's format, so it stays compatible when the game's own format moves on.
     */
    public static byte[] packMeta(ModPackage pkg, int gameFormatMajor) {
        JsonObject pack = new JsonObject();
        String name = pkg.manifest.name;
        pack.addProperty("description", name + " " + pkg.manifest.version + " (imported mod)");
        pack.addProperty("min_format", FIRST_RANGE_FORMAT);
        pack.addProperty("max_format", Math.max(FIRST_RANGE_FORMAT, gameFormatMajor));
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    static byte[] itemDefinition(String modId, ModItems.ItemDef item) {
        JsonObject model = new JsonObject();
        model.addProperty("type", "minecraft:model");
        model.addProperty("model", modId + ":item/" + item.id());
        JsonObject root = new JsonObject();
        root.add("model", model);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    static byte[] itemModel(ModItems.ItemDef item) {
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", item.texture());
        JsonObject root = new JsonObject();
        root.addProperty("parent", "minecraft:item/" + item.model());
        root.add("textures", textures);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void putIfAbsent(Map<String, byte[]> map, String key, byte[] value) {
        map.putIfAbsent(key, value);
    }
}
