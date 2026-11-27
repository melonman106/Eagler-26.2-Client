package com.melonman106.vbvclient.mods;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Event bindings read from a mod's {@code events.json}: "when this happens to this thing, if these conditions hold, do
 * these actions". The file is plain data; the client owns the code that performs the actions (see
 * {@link EventDispatcher}). No game classes are used, so the file is parsed by the same plain-JVM checks as the rest of
 * the importer.
 *
 * <p>A file that is not valid JSON, or lists far too much, rejects the mod like the other mod files. A single bad entry
 * is dropped with a message and the rest of the mod still loads.</p>
 */
public final class ModEvents {
    private ModEvents() {}

    public static final int MAX_BINDINGS = 256;
    public static final int MAX_ACTIONS = 8;
    public static final int MAX_MESSAGE = 256;
    public static final int MAX_COOLDOWN = 20 * 60 * 60;
    public static final int MIN_INTERVAL = 10;
    public static final int DEFAULT_INTERVAL = 20;

    private static final Pattern LOCATION = Pattern.compile("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+");

    /** What an event's {@code target} names. */
    public enum Subject { ITEM, BLOCK, MOB, NONE }

    public enum Event {
        ITEM_USE("item_use", Subject.ITEM), ITEM_USE_ON_BLOCK("item_use_on_block", Subject.ITEM),
        ITEM_HIT_ENTITY("item_hit_entity", Subject.ITEM), ITEM_FINISH_USING("item_finish_using", Subject.ITEM),
        ITEM_HOLD_TICK("item_hold_tick", Subject.ITEM),
        BLOCK_BREAK("block_break", Subject.BLOCK), BLOCK_PLACE("block_place", Subject.BLOCK),
        BLOCK_USE("block_use", Subject.BLOCK), BLOCK_STEP_ON("block_step_on", Subject.BLOCK),
        BLOCK_RANDOM_TICK("block_random_tick", Subject.BLOCK),
        MOB_SPAWN("mob_spawn", Subject.MOB), MOB_DEATH("mob_death", Subject.MOB), MOB_HURT("mob_hurt", Subject.MOB),
        MOB_ATTACK("mob_attack", Subject.MOB), MOB_TICK("mob_tick", Subject.MOB),
        PLAYER_JOIN("player_join", Subject.NONE);

        public final String id;
        public final Subject subject;

        Event(String id, Subject subject) {
            this.id = id;
            this.subject = subject;
        }

        /** True for the events the game raises every tick; a binding's {@code interval} spaces them out. */
        public boolean ticking() { return this == ITEM_HOLD_TICK || this == MOB_TICK; }

        public static Event find(String id) {
            if (id == null) return null;
            for (Event event : values()) if (event.id.equals(id)) return event;
            return null;
        }

        public static String names() {
            List<String> names = new ArrayList<>();
            for (Event event : values()) names.add(event.id);
            return String.join(", ", names);
        }
    }

    /** All conditions in an {@code if} block must hold. A field left out is not checked. */
    public record Condition(double chance, Boolean sneaking, List<String> dimensions, List<String> biomes, String time,
                            String weather, Integer yMin, Integer yMax, String holding, double healthBelow) {
        public static final Condition NONE = new Condition(1.0, null, List.of(), List.of(), null, null, null, null, null, -1);
    }

    public enum Kind { COMMAND, EFFECT, GIVE, MESSAGE, SOUND, PARTICLE, DAMAGE_ITEM, CONSUME_ITEM }

    /**
     * One thing to do. Which fields mean what depends on {@code kind}: COMMAND {@code text}; EFFECT {@code id},
     * {@code duration}, {@code amplifier}, {@code to}; GIVE {@code id}, {@code amount}; MESSAGE {@code text},
     * {@code flag} (action bar); SOUND {@code id}, {@code volume}, {@code pitch}; PARTICLE {@code id}, {@code amount}
     * (how many), {@code volume} (spread); DAMAGE_ITEM / CONSUME_ITEM {@code amount}.
     */
    public record Action(Kind kind, String text, String id, int amount, int duration, int amplifier, float volume,
                         float pitch, boolean flag, String to) {}

    /**
     * One rule. {@code target} is a full id, a {@code #tag}, or null for events with no target; {@code index} is the
     * entry's position in the file, which stays stable so cooldowns can be keyed by it.
     */
    public record Binding(String modId, int index, Event event, String target, int cooldown, int interval,
                          Condition condition, List<Action> actions) {
        public boolean targetIsTag() { return target != null && target.startsWith("#"); }

        public int commandCount() {
            int count = 0;
            for (Action action : actions) if (action.kind() == Kind.COMMAND) count++;
            return count;
        }
    }

    public record Result(List<Binding> bindings, List<String> problems, List<String> warnings) {}

    /** How many command actions the bindings hold. */
    public static int commandCount(List<Binding> bindings) {
        int count = 0;
        for (Binding binding : bindings) count += binding.commandCount();
        return count;
    }

    /** The distinct command texts in the bindings, sorted: what an "Allow commands" switch is tied to. */
    public static List<String> commandTexts(List<Binding> bindings) {
        Set<String> texts = new TreeSet<>();
        for (Binding binding : bindings) {
            for (Action action : binding.actions()) if (action.kind() == Kind.COMMAND) texts.add(action.text());
        }
        return new ArrayList<>(texts);
    }

    public static Result parse(String modId, String json) {
        List<String> problems = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<Binding> bindings = new ArrayList<>();
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                problems.add("events.json must be a JSON object ({ ... }).");
                return new Result(bindings, problems, warnings);
            }
            root = parsed.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException ex) {
            problems.add("events.json is not valid JSON: " + firstLine(ex.getMessage()));
            return new Result(bindings, problems, warnings);
        }
        for (String key : root.keySet()) {
            if (!key.equals("events")) warnings.add("events.json: unknown field \"" + key + "\" is ignored.");
        }
        JsonElement list = root.get("events");
        if (list == null || list.isJsonNull()) return new Result(bindings, problems, warnings);
        if (!list.isJsonArray()) {
            problems.add("events.json: \"events\" must be a list.");
            return new Result(bindings, problems, warnings);
        }
        JsonArray array = list.getAsJsonArray();
        if (array.size() > MAX_BINDINGS) {
            problems.add("events.json lists " + array.size() + " events; the limit is " + MAX_BINDINGS + ".");
            return new Result(bindings, problems, warnings);
        }
        for (int index = 0; index < array.size(); index++) {
            String where = "events.json entry " + (index + 1);
            if (!array.get(index).isJsonObject()) {
                warnings.add(where + " must be an object ({ ... }); it was dropped.");
                continue;
            }
            Binding binding = binding(modId, index, where, array.get(index).getAsJsonObject(), warnings);
            if (binding != null) bindings.add(binding);
        }
        return new Result(bindings, problems, warnings);
    }

    // ---- one entry ----

    private static Binding binding(String modId, int index, String where, JsonObject object, List<String> warnings) {
        Event event = Event.find(string(object, "on"));
        if (event == null) {
            warnings.add(where + ": \"on\" must be one of " + Event.names() + "; the entry was dropped.");
            return null;
        }
        where = where + " (" + event.id + ")";
        for (String key : object.keySet()) {
            if (!Set.of("on", "target", "cooldown", "interval", "if", "do").contains(key)) {
                warnings.add(where + ": unknown field \"" + key + "\" is ignored.");
            }
        }

        String target = null;
        if (event.subject == Subject.NONE) {
            if (object.has("target")) warnings.add(where + ": this event has no target, so \"target\" is ignored.");
        } else {
            target = qualify(location(string(object, "target"), true), modId);
            if (target == null) {
                warnings.add(where + ": \"target\" must be an id such as namespace:name or a #tag; the entry was dropped.");
                return null;
            }
        }

        Integer cooldown = whole(object, "cooldown", 0, MAX_COOLDOWN);
        if (object.has("cooldown") && cooldown == null) {
            warnings.add(where + ": \"cooldown\" must be a whole number of ticks from 0 to " + MAX_COOLDOWN + "; the entry was dropped.");
            return null;
        }
        Integer interval = whole(object, "interval", MIN_INTERVAL, MAX_COOLDOWN);
        if (object.has("interval")) {
            if (!event.ticking()) {
                warnings.add(where + ": \"interval\" only applies to item_hold_tick and mob_tick, so it is ignored.");
                interval = null;
            } else if (interval == null) {
                warnings.add(where + ": \"interval\" must be a whole number of ticks from " + MIN_INTERVAL + " to " + MAX_COOLDOWN + "; the entry was dropped.");
                return null;
            }
        }

        Condition condition = Condition.NONE;
        if (object.has("if")) {
            condition = condition(modId, where, object.get("if"), warnings);
            if (condition == null) return null;
        }

        List<Action> actions = new ArrayList<>();
        JsonElement todo = object.get("do");
        if (todo == null || !todo.isJsonArray() || todo.getAsJsonArray().isEmpty()) {
            warnings.add(where + ": \"do\" must be a list of at least one action; the entry was dropped.");
            return null;
        }
        JsonArray list = todo.getAsJsonArray();
        if (list.size() > MAX_ACTIONS) {
            warnings.add(where + ": \"do\" lists " + list.size() + " actions; the limit is " + MAX_ACTIONS + "; the entry was dropped.");
            return null;
        }
        for (int i = 0; i < list.size(); i++) {
            Action action = action(modId, where + " action " + (i + 1), list.get(i), warnings);
            if (action != null) actions.add(action);
        }
        if (actions.isEmpty()) {
            warnings.add(where + ": none of its actions could be used; the entry was dropped.");
            return null;
        }
        return new Binding(modId, index, event, target, cooldown == null ? 0 : cooldown,
            interval == null ? DEFAULT_INTERVAL : interval, condition, Collections.unmodifiableList(actions));
    }

    // ---- conditions ----

    /** The parsed condition, or null when it is not usable (the whole entry is then dropped rather than fire too often). */
    private static Condition condition(String modId, String where, JsonElement element, List<String> warnings) {
        if (!element.isJsonObject()) {
            warnings.add(where + ": \"if\" must be an object ({ ... }); the entry was dropped.");
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        Set<String> known = Set.of("chance", "sneaking", "dimension", "biome", "time", "weather", "y_min", "y_max", "holding", "health_below");
        for (String key : object.keySet()) {
            if (!known.contains(key)) {
                warnings.add(where + ": unknown condition \"" + key + "\"; the entry was dropped so it cannot fire more often than intended.");
                return null;
            }
        }
        double chance = 1.0;
        if (object.has("chance")) {
            Double value = number(object, "chance", 0, 1);
            if (value == null) return bad(where, "chance", "a number from 0 to 1", warnings);
            chance = value;
        }
        Boolean sneaking = null;
        if (object.has("sneaking")) {
            JsonElement value = object.get("sneaking");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) return bad(where, "sneaking", "true or false", warnings);
            sneaking = value.getAsBoolean();
        }
        List<String> dimensions = locations(object, "dimension", false, "minecraft");
        if (dimensions == null) return bad(where, "dimension", "an id or a list of ids", warnings);
        List<String> biomes = locations(object, "biome", true, "minecraft");
        if (biomes == null) return bad(where, "biome", "an id, a #tag, or a list of them", warnings);
        String time = null;
        if (object.has("time")) {
            time = string(object, "time");
            if (!"day".equals(time) && !"night".equals(time)) return bad(where, "time", "\"day\" or \"night\"", warnings);
        }
        String weather = null;
        if (object.has("weather")) {
            weather = string(object, "weather");
            if (!"clear".equals(weather) && !"rain".equals(weather) && !"thunder".equals(weather)) {
                return bad(where, "weather", "\"clear\", \"rain\" or \"thunder\"", warnings);
            }
        }
        Integer yMin = null;
        if (object.has("y_min") && (yMin = whole(object, "y_min", -2048, 2048)) == null) return bad(where, "y_min", "a whole number from -2048 to 2048", warnings);
        Integer yMax = null;
        if (object.has("y_max") && (yMax = whole(object, "y_max", -2048, 2048)) == null) return bad(where, "y_max", "a whole number from -2048 to 2048", warnings);
        String holding = null;
        if (object.has("holding")) {
            holding = qualify(location(string(object, "holding"), true), modId);
            if (holding == null) return bad(where, "holding", "an item id or #tag", warnings);
        }
        double healthBelow = -1;
        if (object.has("health_below")) {
            Double value = number(object, "health_below", 0, 1);
            if (value == null) return bad(where, "health_below", "a fraction from 0 to 1", warnings);
            healthBelow = value;
        }
        return new Condition(chance, sneaking, dimensions, biomes, time, weather, yMin, yMax, holding, healthBelow);
    }

    private static Condition bad(String where, String key, String expected, List<String> warnings) {
        warnings.add(where + ": condition \"" + key + "\" must be " + expected + "; the entry was dropped.");
        return null;
    }

    /** A list from one id or an array of ids; empty when the key is absent; null when it is present and wrong. */
    private static List<String> locations(JsonObject object, String key, boolean allowTag, String namespace) {
        List<String> out = new ArrayList<>();
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) return out;
        List<JsonElement> values = new ArrayList<>();
        if (element.isJsonArray()) element.getAsJsonArray().forEach(values::add);
        else values.add(element);
        if (values.isEmpty() || values.size() > 32) return null;
        for (JsonElement value : values) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return null;
            String id = qualify(location(value.getAsString(), allowTag), namespace);
            if (id == null) return null;
            out.add(id);
        }
        return Collections.unmodifiableList(out);
    }

    // ---- actions ----

    private static final List<String> ACTION_KEYS = List.of("command", "effect", "give", "message", "sound", "particle",
        "damage_item", "consume_item");

    private static Action action(String modId, String where, JsonElement element, List<String> warnings) {
        if (!element.isJsonObject()) {
            warnings.add(where + " must be an object such as {\"command\": \"...\"}; it was dropped.");
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        String key = null;
        for (String candidate : ACTION_KEYS) {
            if (!object.has(candidate)) continue;
            if (key != null) {
                warnings.add(where + " has both \"" + key + "\" and \"" + candidate + "\"; use one action per entry. It was dropped.");
                return null;
            }
            key = candidate;
        }
        if (key == null) {
            warnings.add(where + " needs one of: " + String.join(", ", ACTION_KEYS) + ". It was dropped.");
            return null;
        }
        Set<String> params;
        switch (key) {
            case "effect": params = Set.of("effect", "duration", "amplifier", "to"); break;
            case "give": params = Set.of("give", "count"); break;
            case "message": params = Set.of("message", "actionbar"); break;
            case "sound": params = Set.of("sound", "volume", "pitch"); break;
            case "particle": params = Set.of("particle", "count", "spread"); break;
            default: params = Set.of(key);
        }
        for (String name : object.keySet()) {
            if (!params.contains(name)) warnings.add(where + ": \"" + name + "\" is not used by a \"" + key + "\" action and is ignored.");
        }
        where = where + " (" + key + ")";
        switch (key) {
            case "command": {
                String text = string(object, "command");
                String reason = CommandGuard.check(text);
                if (reason != null) {
                    warnings.add(where + ": " + reason + "; the action was dropped.");
                    return null;
                }
                String clean = text.trim();
                if (clean.startsWith("/")) clean = clean.substring(1).trim();
                return new Action(Kind.COMMAND, clean, null, 0, 0, 0, 0, 0, false, null);
            }
            case "effect": {
                String id = location(string(object, "effect"), false);
                Integer duration = object.has("duration") ? whole(object, "duration", 1, 1_000_000) : Integer.valueOf(200);
                Integer amplifier = object.has("amplifier") ? whole(object, "amplifier", 0, 255) : Integer.valueOf(0);
                String to = object.has("to") ? string(object, "to") : "actor";
                if (id == null || duration == null || amplifier == null || (!"actor".equals(to) && !"other".equals(to))) {
                    warnings.add(where + ": needs an effect id, a duration of 1-1000000 ticks, an amplifier of 0-255 and \"to\" of \"actor\" or \"other\"; the action was dropped.");
                    return null;
                }
                return new Action(Kind.EFFECT, null, qualify(id, modId), 0, duration, amplifier, 0, 0, false, to);
            }
            case "give": {
                String id = location(string(object, "give"), false);
                Integer count = object.has("count") ? whole(object, "count", 1, 64) : Integer.valueOf(1);
                if (id == null || count == null) {
                    warnings.add(where + ": needs an item id and a count of 1-64; the action was dropped.");
                    return null;
                }
                return new Action(Kind.GIVE, null, qualify(id, modId), count, 0, 0, 0, 0, false, null);
            }
            case "message": {
                String text = string(object, "message");
                boolean actionbar = false;
                if (object.has("actionbar")) {
                    JsonElement value = object.get("actionbar");
                    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
                        warnings.add(where + ": \"actionbar\" must be true or false; the action was dropped.");
                        return null;
                    }
                    actionbar = value.getAsBoolean();
                }
                if (text == null || text.isBlank()) {
                    warnings.add(where + ": the message must be text; the action was dropped.");
                    return null;
                }
                StringBuilder clean = new StringBuilder();
                for (int i = 0; i < text.length() && clean.length() < MAX_MESSAGE; i++) {
                    char c = text.charAt(i);
                    clean.append(c < 32 ? ' ' : c);
                }
                return new Action(Kind.MESSAGE, clean.toString(), null, 0, 0, 0, 0, 0, actionbar, null);
            }
            case "sound": {
                String id = location(string(object, "sound"), false);
                Double volume = object.has("volume") ? number(object, "volume", 0, 4) : Double.valueOf(1);
                Double pitch = object.has("pitch") ? number(object, "pitch", 0.5, 2) : Double.valueOf(1);
                if (id == null || volume == null || pitch == null) {
                    warnings.add(where + ": needs a sound id, a volume of 0-4 and a pitch of 0.5-2; the action was dropped.");
                    return null;
                }
                return new Action(Kind.SOUND, null, qualify(id, "minecraft"), 0, 0, 0, volume.floatValue(), pitch.floatValue(), false, null);
            }
            case "particle": {
                String id = location(string(object, "particle"), false);
                Integer count = object.has("count") ? whole(object, "count", 1, 100) : Integer.valueOf(10);
                Double spread = object.has("spread") ? number(object, "spread", 0, 5) : Double.valueOf(0.3);
                if (id == null || count == null || spread == null) {
                    warnings.add(where + ": needs a particle id, a count of 1-100 and a spread of 0-5; the action was dropped.");
                    return null;
                }
                return new Action(Kind.PARTICLE, null, qualify(id, "minecraft"), count, 0, 0, spread.floatValue(), 0, false, null);
            }
            case "damage_item": {
                Integer amount = whole(object, "damage_item", 1, 1000);
                if (amount == null) {
                    warnings.add(where + ": must be a whole number from 1 to 1000; the action was dropped.");
                    return null;
                }
                return new Action(Kind.DAMAGE_ITEM, null, null, amount, 0, 0, 0, 0, false, null);
            }
            default: {
                Integer amount = whole(object, "consume_item", 1, 64);
                if (amount == null) {
                    warnings.add(where + ": must be a whole number from 1 to 64; the action was dropped.");
                    return null;
                }
                return new Action(Kind.CONSUME_ITEM, null, null, amount, 0, 0, 0, 0, false, null);
            }
        }
    }

    // ---- helpers ----

    /** Adds the namespace to a bare id (a #tag already has one). Passes null through. */
    private static String qualify(String id, String namespace) {
        if (id == null || id.startsWith("#") || id.contains(":")) return id;
        return namespace + ":" + id;
    }

    /** A valid id or (when allowed) {@code #tag}, lowercase, or null. A tag with no namespace gets minecraft:. */
    private static String location(String value, boolean allowTag) {
        if (value == null) return null;
        String text = value.trim();
        boolean tag = text.startsWith("#");
        if (tag) {
            if (!allowTag) return null;
            text = text.substring(1);
        }
        if (!LOCATION.matcher(text).matches()) return null;
        if (tag && !text.contains(":")) text = "minecraft:" + text;
        return tag ? "#" + text : text;
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) return null;
        return element.getAsString().trim();
    }

    private static Double number(JsonObject object, String key, double min, double max) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return null;
        double value = element.getAsDouble();
        if (Double.isNaN(value) || value < min || value > max) return null;
        return value;
    }

    private static Integer whole(JsonObject object, String key, int min, int max) {
        Double value = number(object, key, min, max);
        if (value == null || value != Math.rint(value)) return null;
        return value.intValue();
    }

    private static String firstLine(String message) {
        if (message == null) return "unreadable";
        int end = message.indexOf('\n');
        String line = end >= 0 ? message.substring(0, end) : message;
        return line.length() > 160 ? line.substring(0, 160) + "..." : line;
    }
}
