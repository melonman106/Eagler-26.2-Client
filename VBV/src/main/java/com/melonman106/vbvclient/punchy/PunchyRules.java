package com.melonman106.vbvclient.punchy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The pack's "compat" rules: which animation plays for which action, for which held item.
 *
 * <p>A compat file has any of: {@code customGroups} (named item lists), {@code item} (rules per category such as
 * {@code SWORD}), {@code itemSpecific} (rules per item id), {@code emptyHand} and {@code default_animations}.
 * Every rule is {@code {"type": "attack", ...}}; the other keys depend on the type (for example
 * {@code attack} has {@code var_1..var_3} and {@code critical_hit}, {@code mining_v2} has
 * {@code start/looping/end}).
 *
 * <p>Rules are layered, least specific first: defaults, then each matching category, then the item's own entry.
 * A more specific layer replaces every rule of the same {@code type} from the layers below it.
 */
public final class PunchyRules {
    /** One rule object. {@code type} is the action it answers; the rest is read on demand. */
    public static final class Rule {
        public final String type;
        private final JsonObject json;

        Rule(String type, JsonObject json) {
            this.type = type;
            this.json = json;
        }

        /** A string field, or {@code null}. */
        public String str(String key) {
            JsonElement e = json.get(key);
            return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
        }

        public boolean bool(String key) {
            JsonElement e = json.get(key);
            return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
        }

        /** The first of the given keys that is set (e.g. {@code name} then {@code var_1}). */
        public String firstOf(String... keys) {
            for (String key : keys) {
                String v = str(key);
                if (v != null) return v;
            }
            return null;
        }

        /** Item ids of an {@code interaction} rule's {@code items} array (empty when absent). */
        public List<String> strings(String key) {
            List<String> out = new ArrayList<>();
            JsonElement e = json.get(key);
            if (e != null && e.isJsonArray()) {
                for (JsonElement x : e.getAsJsonArray()) if (x.isJsonPrimitive()) out.add(x.getAsString());
            }
            return out;
        }

        /** {@code variations: [{"state": "crouching", "name": "use_bow_down"}]} -> the name for a state, else null. */
        public String variation(String state) {
            JsonElement e = json.get("variations");
            if (e == null || !e.isJsonArray()) return null;
            for (JsonElement v : e.getAsJsonArray()) {
                if (!v.isJsonObject()) continue;
                JsonObject o = v.getAsJsonObject();
                if (o.has("state") && o.has("name") && state.equals(o.get("state").getAsString())) {
                    return o.get("name").getAsString();
                }
            }
            return null;
        }
    }

    /** The layered rules for one held item. */
    public static final class Resolved {
        private final Map<String, List<Rule>> byType = new LinkedHashMap<>();
        /** The item entry's {@code disable_mining_animation} (swords: swinging is an attack, not mining). */
        public boolean disableMining;

        void overlay(List<Rule> layer) {
            Map<String, List<Rule>> incoming = new LinkedHashMap<>();
            for (Rule r : layer) incoming.computeIfAbsent(r.type, k -> new ArrayList<>()).add(r);
            byType.putAll(incoming);
        }

        public Rule first(String type) {
            List<Rule> list = byType.get(type);
            return list == null || list.isEmpty() ? null : list.get(0);
        }

        public List<Rule> all(String type) {
            List<Rule> list = byType.get(type);
            return list == null ? List.of() : list;
        }

        public boolean isEmpty() {
            return byType.isEmpty();
        }
    }

    private static final class Entry {
        final List<Rule> rules = new ArrayList<>();
        boolean disableMining;
    }

    private final List<Rule> defaults = new ArrayList<>();
    private final Map<String, Entry> categories = new LinkedHashMap<>();
    private final Map<String, Entry> items = new LinkedHashMap<>();
    private final Map<String, List<String>> groups = new LinkedHashMap<>();
    private final Map<String, String> problems = new LinkedHashMap<>();

    /**
     * Adds one compat file. Files added later override earlier entries with the same category or item id,
     * so add {@code default_tuning.json} first.
     */
    public void add(String fileName, String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                problems.put(fileName, "not an object");
                return;
            }
            JsonObject o = root.getAsJsonObject();
            JsonElement g = o.get("customGroups");
            if (g != null && g.isJsonArray()) {
                for (JsonElement e : g.getAsJsonArray()) {
                    if (!e.isJsonObject() || !e.getAsJsonObject().has("name")) continue;
                    List<String> ids = new ArrayList<>();
                    JsonElement list = e.getAsJsonObject().get("items");
                    if (list != null && list.isJsonArray()) {
                        for (JsonElement id : list.getAsJsonArray()) if (id.isJsonPrimitive()) ids.add(id.getAsString());
                    }
                    groups.put(e.getAsJsonObject().get("name").getAsString(), ids);
                }
            }
            JsonElement d = o.get("default_animations");
            if (d != null && d.isJsonArray()) {
                defaults.clear();
                defaults.addAll(rules(d.getAsJsonArray()));
            }
            section(o.get("item"), categories);
            section(o.get("itemSpecific"), items);
            JsonElement hand = o.get("emptyHand");
            if (hand != null && hand.isJsonObject()) categories.put("EMPTY_HAND", entry(hand.getAsJsonObject()));
        } catch (RuntimeException e) {
            problems.put(fileName, String.valueOf(e.getMessage()));
        }
    }

    private static void section(JsonElement element, Map<String, Entry> into) {
        if (element == null || !element.isJsonObject()) return;
        for (Map.Entry<String, JsonElement> e : element.getAsJsonObject().entrySet()) {
            if (e.getValue().isJsonObject()) into.put(e.getKey(), entry(e.getValue().getAsJsonObject()));
        }
    }

    private static Entry entry(JsonObject o) {
        Entry entry = new Entry();
        JsonElement list = o.get("customAnimation");
        if (list != null && list.isJsonArray()) entry.rules.addAll(rules(list.getAsJsonArray()));
        JsonElement dm = o.get("disable_mining_animation");
        entry.disableMining = dm != null && dm.isJsonPrimitive() && dm.getAsJsonPrimitive().isBoolean() && dm.getAsBoolean();
        return entry;
    }

    private static List<Rule> rules(JsonArray array) {
        List<Rule> out = new ArrayList<>();
        for (JsonElement e : array) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            JsonElement type = o.get("type");
            if (type != null && type.isJsonPrimitive()) out.add(new Rule(type.getAsString(), o));
        }
        return out;
    }

    /**
     * The rules for one held item.
     *
     * @param itemId     e.g. {@code minecraft:iron_sword}; {@code null} or empty for an empty hand
     * @param categories the categories it belongs to, least specific first (e.g. {@code SWORD}); the empty hand
     *                   is {@code EMPTY_HAND}. Custom groups containing the item are added automatically.
     */
    public Resolved resolve(String itemId, Collection<String> categories) {
        Resolved out = new Resolved();
        out.overlay(defaults);
        List<String> keys = new ArrayList<>(categories);
        if (itemId != null) {
            for (Map.Entry<String, List<String>> g : groups.entrySet()) {
                if (g.getValue().contains(itemId) && !keys.contains(g.getKey())) keys.add(g.getKey());
            }
        }
        for (String key : keys) apply(out, this.categories.get(key));
        if (itemId != null) apply(out, items.get(itemId));
        return out;
    }

    private static void apply(Resolved out, Entry e) {
        if (e == null) return;
        out.overlay(e.rules);
        if (e.disableMining) out.disableMining = true;
    }

    public boolean isEmpty() {
        return defaults.isEmpty() && categories.isEmpty() && items.isEmpty();
    }

    public int entryCount() {
        return categories.size() + items.size();
    }

    public Map<String, String> problems() {
        return java.util.Collections.unmodifiableMap(problems);
    }
}
