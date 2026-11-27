package com.melonman106.vbvclient.mods;

import static com.melonman106.vbvclient.mods.TestKit.any;
import static com.melonman106.vbvclient.mods.TestKit.bytes;
import static com.melonman106.vbvclient.mods.TestKit.check;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Checks for events.json: the command guard, parsing, the dispatcher and reading it from a mod zip. */
final class EventTests {
    private EventTests() {}

    static void run() {
        guard();
        parsing();
        parsingProblems();
        conditions();
        dispatching();
        limits();
        inPackage();
    }

    // ---- the command guard ----

    static void guard() {
        check(CommandGuard.check("effect give @s minecraft:speed 5 1") == null, "an ordinary command passes");
        check(CommandGuard.check("/effect give @s minecraft:speed 5 1") == null, "a leading slash is fine");
        check(CommandGuard.check("say hello world") == null, "say passes");
        check(CommandGuard.check("execute as @a at @s run particle minecraft:flame ~ ~1 ~") == null, "execute ... run <safe> passes");
        for (String denied : List.of("op Steve", "/OP Steve", "minecraft:op Steve", "deop Steve", "stop", "ban Steve", "ban-ip 1.2.3.4",
            "banlist", "pardon Steve", "whitelist add Steve", "save-all", "save-off", "setidletimeout 5", "publish", "debug start",
            "perf start", "jfr start", "transfer a.b 25565", "reload", "kick Steve")) {
            check(CommandGuard.check(denied) != null, "refused: " + denied);
        }
        check(CommandGuard.check("execute as @a run op @s") != null, "a denied command after run is refused");
        check(CommandGuard.check("execute as @a at @s run execute if entity @s run stop") != null, "a denied command after a second run is refused");
        check(CommandGuard.check("execute if entity @s run return run ban Steve") != null, "return run <denied> is refused");
        check(CommandGuard.check("execute as @a run say run op") != null, "the word run before a denied word is refused, even in plain text");
        check(CommandGuard.check("say please stop") == null, "a denied word as a plain argument is fine");
        check(CommandGuard.check(null) != null && CommandGuard.check("") != null && CommandGuard.check("  ") != null && CommandGuard.check("/") != null,
            "missing or empty commands are refused");
        check(CommandGuard.check("say " + "a".repeat(CommandGuard.MAX_LENGTH - 4)) == null, "exactly the length limit passes");
        check(CommandGuard.check("say " + "a".repeat(CommandGuard.MAX_LENGTH - 3)) != null, "one past the length limit is refused");
        check(CommandGuard.check("say a\nop b") != null, "a line break is refused");
        check(CommandGuard.check("say a\u0000b") != null, "a control character is refused");
        check(CommandGuard.clampLevel(-3) == 0 && CommandGuard.clampLevel(1) == 1 && CommandGuard.clampLevel(2) == 2 && CommandGuard.clampLevel(9) == 2,
            "levels are clamped into 0..2");
    }

    // ---- parsing ----

    private static final String GOOD = "{\"events\":["
        + "{\"on\":\"item_use\",\"target\":\"ruby_wand\",\"cooldown\":40,"
        + " \"if\":{\"sneaking\":false,\"chance\":0.5,\"dimension\":\"overworld\",\"biome\":[\"plains\",\"#is_forest\"],\"time\":\"day\","
        + "\"weather\":\"clear\",\"y_min\":0,\"y_max\":100,\"holding\":\"ruby_wand\",\"health_below\":0.5},"
        + " \"do\":[{\"command\":\"/effect give @s minecraft:speed 10 1\"},{\"effect\":\"ruby_haste\",\"duration\":60,\"amplifier\":2},"
        + "{\"give\":\"ruby\",\"count\":3},{\"message\":\"Zap!\",\"actionbar\":true},{\"sound\":\"entity.player.levelup\",\"volume\":2,\"pitch\":1.5},"
        + "{\"particle\":\"flame\",\"count\":20,\"spread\":1},{\"damage_item\":2},{\"consume_item\":1}]},"
        + "{\"on\":\"block_use\",\"target\":\"minecraft:dirt\",\"do\":[{\"command\":\"summon minecraft:chicken {x} {y} {z}\"}]},"
        + "{\"on\":\"mob_tick\",\"target\":\"#minecraft:zombies\",\"interval\":30,\"do\":[{\"command\":\"say hi\"}]},"
        + "{\"on\":\"player_join\",\"do\":[{\"message\":\"Welcome\"}]}]}";

    static void parsing() {
        ModEvents.Result result = ModEvents.parse("ruby_tools", GOOD);
        check(result.problems().isEmpty(), "a good file has no problems: " + result.problems());
        check(result.warnings().isEmpty(), "a good file has no warnings: " + result.warnings());
        check(result.bindings().size() == 4, "four bindings: " + result.bindings().size());
        ModEvents.Binding wand = result.bindings().get(0);
        check(wand.event() == ModEvents.Event.ITEM_USE && wand.modId().equals("ruby_tools") && wand.index() == 0, "event, mod and index");
        check("ruby_tools:ruby_wand".equals(wand.target()), "a bare target gets the mod's namespace: " + wand.target());
        check(wand.cooldown() == 40 && wand.interval() == ModEvents.DEFAULT_INTERVAL, "cooldown read, interval defaults");
        ModEvents.Condition c = wand.condition();
        check(c.chance() == 0.5 && Boolean.FALSE.equals(c.sneaking()), "chance and sneaking");
        check(c.dimensions().equals(List.of("minecraft:overworld")), "a bare dimension is minecraft's: " + c.dimensions());
        check(c.biomes().equals(List.of("minecraft:plains", "#minecraft:is_forest")), "biomes and a tag get minecraft: " + c.biomes());
        check("day".equals(c.time()) && "clear".equals(c.weather()) && c.yMin() == 0 && c.yMax() == 100, "time, weather and heights");
        check("ruby_tools:ruby_wand".equals(c.holding()) && c.healthBelow() == 0.5, "holding gets the mod's namespace, health_below read");
        List<ModEvents.Action> actions = wand.actions();
        check(actions.size() == 8, "eight actions: " + actions.size());
        check(actions.get(0).kind() == ModEvents.Kind.COMMAND && actions.get(0).text().equals("effect give @s minecraft:speed 10 1"),
            "a command loses its leading slash: " + actions.get(0).text());
        check("ruby_tools:ruby_haste".equals(actions.get(1).id()) && actions.get(1).duration() == 60 && actions.get(1).amplifier() == 2
            && "actor".equals(actions.get(1).to()), "effect: mod namespace, duration, amplifier, default target");
        check("ruby_tools:ruby".equals(actions.get(2).id()) && actions.get(2).amount() == 3, "give: mod namespace and count");
        check("Zap!".equals(actions.get(3).text()) && actions.get(3).flag(), "message with the action bar");
        check("minecraft:entity.player.levelup".equals(actions.get(4).id()) && actions.get(4).volume() == 2f && actions.get(4).pitch() == 1.5f,
            "sound: minecraft namespace, volume, pitch");
        check("minecraft:flame".equals(actions.get(5).id()) && actions.get(5).amount() == 20 && actions.get(5).volume() == 1f, "particle");
        check(actions.get(6).kind() == ModEvents.Kind.DAMAGE_ITEM && actions.get(6).amount() == 2, "damage_item");
        check(actions.get(7).kind() == ModEvents.Kind.CONSUME_ITEM && actions.get(7).amount() == 1, "consume_item");
        ModEvents.Binding dirt = result.bindings().get(1);
        check("minecraft:dirt".equals(dirt.target()) && dirt.condition() == ModEvents.Condition.NONE && dirt.cooldown() == 0, "full ids stay as written; no condition");
        ModEvents.Binding tick = result.bindings().get(2);
        check(tick.targetIsTag() && "#minecraft:zombies".equals(tick.target()) && tick.interval() == 30, "tag target and interval");
        check(result.bindings().get(3).target() == null && result.bindings().get(3).index() == 3, "player_join has no target");
        check(ModEvents.commandCount(result.bindings()) == 3, "three command actions in all");
        check(ModEvents.commandTexts(result.bindings()).equals(List.of("effect give @s minecraft:speed 10 1", "say hi",
            "summon minecraft:chicken {x} {y} {z}")), "distinct sorted command texts: " + ModEvents.commandTexts(result.bindings()));
        check(ModEvents.parse("m", "{}").bindings().isEmpty() && ModEvents.parse("m", "{\"events\":[]}").problems().isEmpty(), "empty is fine");
    }

    static void parsingProblems() {
        check(!ModEvents.parse("m", "not json").problems().isEmpty(), "invalid JSON is a problem");
        check(!ModEvents.parse("m", "[]").problems().isEmpty(), "a list at the top is a problem");
        check(!ModEvents.parse("m", "{\"events\":{}}").problems().isEmpty(), "events must be a list");
        StringBuilder many = new StringBuilder("{\"events\":[");
        for (int i = 0; i <= ModEvents.MAX_BINDINGS; i++) many.append(i == 0 ? "" : ",").append("{}");
        check(any(ModEvents.parse("m", many + "]}").problems(), "the limit is"), "too many events is a problem");

        // A bad entry is dropped with a warning; the good one beside it stays.
        ModEvents.Result mixed = ModEvents.parse("m", "{\"events\":["
            + "{\"on\":\"nonsense\",\"target\":\"a\",\"do\":[{\"message\":\"x\"}]},"
            + "{\"on\":\"item_use\",\"do\":[{\"message\":\"x\"}]},"
            + "{\"on\":\"item_use\",\"target\":\"BAD ID\",\"do\":[{\"message\":\"x\"}]},"
            + "{\"on\":\"item_use\",\"target\":\"a\",\"do\":[]},"
            + "{\"on\":\"item_use\",\"target\":\"a\"},"
            + "{\"on\":\"item_use\",\"target\":\"a\",\"cooldown\":-1,\"do\":[{\"message\":\"x\"}]},"
            + "{\"on\":\"item_use\",\"target\":\"a\",\"interval\":20,\"do\":[{\"message\":\"x\"}]},"
            + "{\"on\":\"mob_tick\",\"target\":\"a\",\"interval\":3,\"do\":[{\"message\":\"x\"}]},"
            + "{\"on\":\"item_use\",\"target\":\"a\",\"if\":{\"sneakin\":true},\"do\":[{\"message\":\"x\"}]},"
            + "{\"on\":\"item_use\",\"target\":\"a\",\"if\":{\"chance\":2},\"do\":[{\"message\":\"x\"}]},"
            + "{\"on\":\"item_use\",\"target\":\"a\",\"if\":{\"time\":\"dusk\"},\"do\":[{\"message\":\"x\"}]},"
            + "{\"on\":\"item_use\",\"target\":\"a\",\"do\":[{\"command\":\"op Steve\"}]},"
            + "{\"on\":\"item_use\",\"target\":\"a\",\"do\":[{\"command\":\"say a\",\"message\":\"b\"}]},"
            + "{\"on\":\"item_use\",\"target\":\"a\",\"do\":[{\"explode\":1}]},"
            + "{\"on\":\"item_use\",\"target\":\"a\",\"do\":[\"say hi\"]},"
            + "{\"on\":\"item_use\",\"target\":\"a\",\"do\":[{\"give\":\"x\",\"count\":65}]},"
            + "5,"
            + "{\"on\":\"item_use\",\"target\":\"good\",\"do\":[{\"message\":\"ok\"}]}]}");
        check(mixed.problems().isEmpty(), "bad entries are not problems: " + mixed.problems());
        check(mixed.bindings().size() == 2, "only the good entry and the one that ignored a stray interval stay: " + mixed.bindings().size());
        check("m:good".equals(mixed.bindings().get(mixed.bindings().size() - 1).target()), "the good entry stays");
        check(mixed.bindings().get(0).target().equals("m:a") && mixed.bindings().get(0).interval() == ModEvents.DEFAULT_INTERVAL,
            "interval on a non-tick event is ignored, not fatal");
        check(any(mixed.warnings(), "must be one of"), "unknown event warns");
        check(any(mixed.warnings(), "\"target\" must be an id"), "missing or bad target warns");
        check(any(mixed.warnings(), "\"do\" must be a list of at least one action"), "empty or missing do warns");
        check(any(mixed.warnings(), "\"cooldown\" must be a whole number"), "bad cooldown warns");
        check(any(mixed.warnings(), "\"interval\" only applies"), "stray interval warns");
        check(any(mixed.warnings(), "\"interval\" must be a whole number of ticks from 10"), "too-short interval warns");
        check(any(mixed.warnings(), "unknown condition \"sneakin\""), "a misspelt condition drops the entry rather than loosening it");
        check(any(mixed.warnings(), "condition \"chance\" must be"), "chance out of range warns");
        check(any(mixed.warnings(), "condition \"time\" must be"), "bad time warns");
        check(any(mixed.warnings(), "\"op\" is not allowed in mods"), "a denied command is dropped with the reason");
        check(any(mixed.warnings(), "use one action per entry"), "two action keys in one entry warn");
        check(any(mixed.warnings(), "needs one of:"), "an unknown action warns");
        check(any(mixed.warnings(), "must be an object such as"), "an action that is not an object warns");
        check(any(mixed.warnings(), "a count of 1-64"), "give count out of range warns");
        check(any(mixed.warnings(), "events.json entry 17 must be an object"), "an entry that is not an object warns");

        ModEvents.Result partial = ModEvents.parse("m", "{\"events\":[{\"on\":\"item_use\",\"target\":\"a\",\"do\":["
            + "{\"command\":\"stop\"},{\"message\":\"stays\"}]}]}");
        check(partial.bindings().size() == 1 && partial.bindings().get(0).actions().size() == 1 && partial.bindings().get(0).commandCount() == 0,
            "a refused command is dropped but the other actions of the entry stay");
        ModEvents.Result tooMany = ModEvents.parse("m", "{\"events\":[{\"on\":\"item_use\",\"target\":\"a\",\"do\":["
            + "{\"damage_item\":1},{\"damage_item\":1},{\"damage_item\":1},{\"damage_item\":1},{\"damage_item\":1},"
            + "{\"damage_item\":1},{\"damage_item\":1},{\"damage_item\":1},{\"damage_item\":1}]}]}");
        check(tooMany.bindings().isEmpty() && any(tooMany.warnings(), "the limit is " + ModEvents.MAX_ACTIONS), "more than 8 actions drops the entry");
        ModEvents.Result noEffect = ModEvents.parse("m", "{\"events\":[{\"on\":\"player_join\",\"target\":\"x\",\"do\":[{\"message\":\"a\\nb\"}]}]}");
        check(any(noEffect.warnings(), "has no target"), "a target on player_join is ignored with a warning");
        check("a b".equals(noEffect.bindings().get(0).actions().get(0).text()), "line breaks in a message become spaces");
        check(any(ModEvents.parse("m", "{\"stuff\":1}").warnings(), "unknown field \"stuff\""), "unknown top-level field warns");
    }

    // ---- conditions ----

    static final class FakeEnv implements EventDispatcher.Env {
        double random = 0.0;
        boolean sneaking = false;
        String dimension = "minecraft:overworld";
        List<String> biomes = new ArrayList<>(List.of("minecraft:plains", "#minecraft:is_overworld"));
        boolean day = true;
        String weather = "clear";
        int y = 64;
        List<String> holding = new ArrayList<>(List.of("ruby_tools:ruby_wand"));
        double health = 1.0;
        List<String> subjectTags = new ArrayList<>();

        @Override public double random() { return random; }
        @Override public boolean sneaking() { return sneaking; }
        @Override public String dimension() { return dimension; }
        @Override public boolean biomeIs(String idOrTag) { return biomes.contains(idOrTag); }
        @Override public boolean isDay() { return day; }
        @Override public String weather() { return weather; }
        @Override public int blockY() { return y; }
        @Override public boolean holdingIs(String idOrTag) { return holding.contains(idOrTag); }
        @Override public double healthFraction() { return health; }
        @Override public boolean subjectInTag(String tag) { return subjectTags.contains(tag); }
    }

    private static ModEvents.Condition cond(String json) {
        ModEvents.Result result = ModEvents.parse("m", "{\"events\":[{\"on\":\"item_use\",\"target\":\"a\",\"if\":" + json
            + ",\"do\":[{\"message\":\"x\"}]}]}");
        check(result.bindings().size() == 1, "condition parses: " + json + " " + result.warnings());
        return result.bindings().isEmpty() ? ModEvents.Condition.NONE : result.bindings().get(0).condition();
    }

    static void conditions() {
        FakeEnv env = new FakeEnv();
        check(EventDispatcher.holds(ModEvents.Condition.NONE, env), "no condition holds");
        check(EventDispatcher.holds(cond("{}"), env), "an empty if holds");
        env.sneaking = true;
        check(!EventDispatcher.holds(cond("{\"sneaking\":false}"), env) && EventDispatcher.holds(cond("{\"sneaking\":true}"), env), "sneaking");
        check(EventDispatcher.holds(cond("{\"dimension\":[\"minecraft:the_nether\",\"overworld\"]}"), env), "dimension in a list");
        check(!EventDispatcher.holds(cond("{\"dimension\":\"the_nether\"}"), env), "dimension not matching");
        check(EventDispatcher.holds(cond("{\"biome\":[\"desert\",\"plains\"]}"), env) && EventDispatcher.holds(cond("{\"biome\":\"#is_overworld\"}"), env), "biome by id or tag");
        check(!EventDispatcher.holds(cond("{\"biome\":\"desert\"}"), env), "biome not matching");
        check(EventDispatcher.holds(cond("{\"time\":\"day\"}"), env) && !EventDispatcher.holds(cond("{\"time\":\"night\"}"), env), "day");
        env.day = false;
        check(EventDispatcher.holds(cond("{\"time\":\"night\"}"), env) && !EventDispatcher.holds(cond("{\"time\":\"day\"}"), env), "night");
        check(EventDispatcher.holds(cond("{\"weather\":\"clear\"}"), env) && !EventDispatcher.holds(cond("{\"weather\":\"rain\"}"), env), "weather");
        check(EventDispatcher.holds(cond("{\"y_min\":64,\"y_max\":64}"), env), "height limits are inclusive");
        check(!EventDispatcher.holds(cond("{\"y_min\":65}"), env) && !EventDispatcher.holds(cond("{\"y_max\":63}"), env), "height outside the limits");
        env.holding.add("minecraft:stick");
        check(EventDispatcher.holds(cond("{\"holding\":\"stick\"}"), env) == false, "a bare holding id means the mod's own item, so minecraft:stick needs its full id");
        check(EventDispatcher.holds(cond("{\"holding\":\"minecraft:stick\"}"), env), "holding by full id");
        env.health = 0.4;
        check(EventDispatcher.holds(cond("{\"health_below\":0.5}"), env) && !EventDispatcher.holds(cond("{\"health_below\":0.4}"), env), "health_below is strictly below");
        env.random = 0.49;
        check(EventDispatcher.holds(cond("{\"chance\":0.5}"), env), "chance passes under the roll");
        env.random = 0.5;
        check(!EventDispatcher.holds(cond("{\"chance\":0.5}"), env), "chance fails on the roll");
        check(EventDispatcher.holds(cond("{\"chance\":1}"), env), "chance 1 always passes");
        env.random = 0.0;
        check(!EventDispatcher.holds(cond("{\"chance\":0}"), env), "chance 0 never passes");
        // All conditions together.
        ModEvents.Condition both = cond("{\"time\":\"night\",\"y_max\":100,\"biome\":\"plains\"}");
        check(EventDispatcher.holds(both, env), "several conditions that all hold");
        env.y = 200;
        check(!EventDispatcher.holds(both, env), "one failing condition fails them all");
    }

    // ---- the dispatcher ----

    static final class RecordingSink implements EventDispatcher.Sink {
        final List<String> log = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        Runnable onCommand;

        @Override public void command(String modId, String command, int level) {
            log.add("command:" + modId + ":" + level + ":" + command);
            if (onCommand != null) onCommand.run();
        }
        @Override public void effect(ModEvents.Action a) { log.add("effect:" + a.id()); }
        @Override public void give(ModEvents.Action a) { log.add("give:" + a.id() + "x" + a.amount()); }
        @Override public void message(ModEvents.Action a) { log.add("message:" + a.text()); }
        @Override public void sound(ModEvents.Action a) { log.add("sound:" + a.id()); }
        @Override public void particle(ModEvents.Action a) { log.add("particle:" + a.id()); }
        @Override public void damageItem(int amount) { log.add("damage:" + amount); }
        @Override public void consumeItem(int amount) { log.add("consume:" + amount); }
        @Override public void warn(String message) { warnings.add(message); }
    }

    private static EventDispatcher.ModRules rules(String mod, boolean allowed, int level, String eventsJson) {
        ModEvents.Result result = ModEvents.parse(mod, eventsJson);
        check(result.problems().isEmpty() && result.warnings().isEmpty(), "test events parse cleanly: " + result.problems() + result.warnings());
        return new EventDispatcher.ModRules(mod, allowed, level, result.bindings());
    }

    private static String one(String on, String target, String extra, String actions) {
        return "{\"events\":[{\"on\":\"" + on + "\"" + (target == null ? "" : ",\"target\":\"" + target + "\"") + extra + ",\"do\":" + actions + "}]}";
    }

    static void dispatching() {
        FakeEnv env = new FakeEnv();
        RecordingSink sink = new RecordingSink();
        String say = "[{\"command\":\"say {x} {y} {z}\"},{\"message\":\"hi\"},{\"damage_item\":1}]";

        // Target matching by id.
        EventDispatcher d = new EventDispatcher(List.of(rules("m", true, 2, one("item_use", "wand", "", say))));
        check(d.handles(ModEvents.Event.ITEM_USE) && !d.handles(ModEvents.Event.BLOCK_BREAK), "handles only the events that have bindings");
        d.fire(ModEvents.Event.ITEM_USE, "m:other", "p1", 0, 1, 2, 3, env, sink);
        check(sink.log.isEmpty(), "a different item does not fire");
        d.fire(ModEvents.Event.ITEM_USE, "m:wand", "p1", 0, 1, 2, 3, env, sink);
        check(sink.log.equals(List.of("command:m:2:say 1 2 3", "message:hi", "damage:1")), "actions run in order with {x}{y}{z} filled in: " + sink.log);
        d.fire(ModEvents.Event.BLOCK_BREAK, "m:wand", "p1", 0, 1, 2, 3, env, sink);
        check(sink.log.size() == 3, "an event nobody listens for does nothing");

        // Target matching by tag.
        sink.log.clear();
        d = new EventDispatcher(List.of(rules("m", true, 2, one("block_break", "#minecraft:logs", "", "[{\"message\":\"log\"}]"))));
        d.fire(ModEvents.Event.BLOCK_BREAK, "minecraft:oak_log", "p1", 0, 0, 0, 0, env, sink);
        check(sink.log.isEmpty(), "a tag target needs the subject to be in the tag");
        env.subjectTags.add("minecraft:logs");
        d.fire(ModEvents.Event.BLOCK_BREAK, "minecraft:oak_log", "p1", 0, 0, 0, 0, env, sink);
        check(sink.log.equals(List.of("message:log")), "a tag target fires when the subject is in the tag");
        env.subjectTags.clear();

        // No target at all.
        sink.log.clear();
        d = new EventDispatcher(List.of(rules("m", true, 2, one("player_join", null, "", "[{\"message\":\"welcome\"}]"))));
        d.fire(ModEvents.Event.PLAYER_JOIN, null, "p1", 0, 0, 0, 0, env, sink);
        check(sink.log.equals(List.of("message:welcome")), "an event with no target fires");

        // Commands switched off: only the other actions run.
        sink.log.clear();
        d = new EventDispatcher(List.of(rules("m", false, 2, one("item_use", "wand", "", say))));
        d.fire(ModEvents.Event.ITEM_USE, "m:wand", "p1", 0, 1, 2, 3, env, sink);
        check(sink.log.equals(List.of("message:hi", "damage:1")), "commands off skips only the command actions: " + sink.log);
        check(sink.warnings.isEmpty(), "commands being off is not a warning (the details page says it)");

        // Command level is clamped.
        sink.log.clear();
        d = new EventDispatcher(List.of(rules("m", true, 9, one("item_use", "wand", "", "[{\"command\":\"say a\"}]"))));
        d.fire(ModEvents.Event.ITEM_USE, "m:wand", "p1", 0, 0, 0, 0, env, sink);
        check(sink.log.equals(List.of("command:m:2:say a")), "level above 2 runs at 2: " + sink.log);
        d = new EventDispatcher(List.of(rules("m", true, 0, one("item_use", "wand", "", "[{\"command\":\"say a\"}]"))));
        sink.log.clear();
        d.fire(ModEvents.Event.ITEM_USE, "m:wand", "p1", 0, 0, 0, 0, env, sink);
        check(sink.log.equals(List.of("command:m:0:say a")), "level 0 stays 0");

        // Cooldown is per actor and per binding.
        sink.log.clear();
        d = new EventDispatcher(List.of(rules("m", true, 2, one("item_use", "wand", ",\"cooldown\":40", "[{\"message\":\"zap\"}]"))));
        d.fire(ModEvents.Event.ITEM_USE, "m:wand", "p1", 100, 0, 0, 0, env, sink);
        d.fire(ModEvents.Event.ITEM_USE, "m:wand", "p1", 120, 0, 0, 0, env, sink);
        check(sink.log.size() == 1, "inside the cooldown the same actor does not fire again");
        d.fire(ModEvents.Event.ITEM_USE, "m:wand", "p2", 120, 0, 0, 0, env, sink);
        check(sink.log.size() == 2, "another actor has its own cooldown");
        d.fire(ModEvents.Event.ITEM_USE, "m:wand", "p1", 140, 0, 0, 0, env, sink);
        check(sink.log.size() == 3, "after the cooldown it fires again");
        d.fire(ModEvents.Event.ITEM_USE, "m:wand", "p1", 5, 0, 0, 0, env, sink);
        check(sink.log.size() == 4, "a tick count that went backwards (a new world) counts as ready");
        d.reset();
        d.fire(ModEvents.Event.ITEM_USE, "m:wand", "p1", 6, 0, 0, 0, env, sink);
        check(sink.log.size() == 5, "reset forgets cooldowns");

        // A failed condition does not start the cooldown.
        sink.log.clear();
        d = new EventDispatcher(List.of(rules("m", true, 2, one("item_use", "wand", ",\"cooldown\":100,\"if\":{\"sneaking\":true}", "[{\"message\":\"x\"}]"))));
        env.sneaking = false;
        d.fire(ModEvents.Event.ITEM_USE, "m:wand", "p1", 0, 0, 0, 0, env, sink);
        env.sneaking = true;
        d.fire(ModEvents.Event.ITEM_USE, "m:wand", "p1", 1, 0, 0, 0, env, sink);
        check(sink.log.size() == 1, "failing the condition first does not use up the cooldown");
        env.sneaking = false;

        // Tick events are spaced out by interval, per actor.
        sink.log.clear();
        d = new EventDispatcher(List.of(rules("m", true, 2, one("mob_tick", "minecraft:zombie", ",\"interval\":20", "[{\"message\":\"t\"}]"))));
        for (long tick = 0; tick < 100; tick++) d.fire(ModEvents.Event.MOB_TICK, "minecraft:zombie", "z1", tick, 0, 0, 0, env, sink);
        check(sink.log.size() == 5, "interval 20 over 100 ticks fires 5 times: " + sink.log.size());

        // Several mods, load order kept.
        sink.log.clear();
        d = new EventDispatcher(List.of(
            rules("a", true, 2, one("item_use", "minecraft:stick", "", "[{\"message\":\"first\"}]")),
            rules("b", true, 2, one("item_use", "minecraft:stick", "", "[{\"message\":\"second\"}]"))));
        d.fire(ModEvents.Event.ITEM_USE, "minecraft:stick", "p1", 0, 0, 0, 0, env, sink);
        check(sink.log.equals(List.of("message:first", "message:second")), "mods run in load order");
        check(d.bindingsFor(ModEvents.Event.ITEM_USE).size() == 2 && d.bindingsFor(ModEvents.Event.MOB_DEATH).isEmpty(), "bindingsFor lists what listens");
    }

    // ---- limits ----

    static void limits() {
        FakeEnv env = new FakeEnv();

        // Per-tick command budget.
        RecordingSink sink = new RecordingSink();
        StringBuilder cmds = new StringBuilder("[");
        for (int i = 0; i < 8; i++) cmds.append(i == 0 ? "" : ",").append("{\"command\":\"say ").append(i).append("\"}");
        cmds.append("]");
        EventDispatcher d = new EventDispatcher(List.of(rules("m", true, 2, one("item_use", "wand", "", cmds.toString()))));
        for (int i = 0; i < 5; i++) d.fire(ModEvents.Event.ITEM_USE, "m:wand", "p" + i, 10, 0, 0, 0, env, sink);
        check(sink.log.size() == EventDispatcher.COMMANDS_PER_TICK, "20 commands per tick, the rest dropped: " + sink.log.size());
        check(sink.warnings.size() == 1 && sink.warnings.get(0).contains("too many commands"), "one warning, not one per command: " + sink.warnings);
        sink.log.clear();
        d.fire(ModEvents.Event.ITEM_USE, "m:wand", "p0", 11, 0, 0, 0, env, sink);
        check(sink.log.size() == 8, "the next tick has a fresh budget: " + sink.log.size());

        // Per-second command budget.
        sink = new RecordingSink();
        d = new EventDispatcher(List.of(rules("m", true, 2, one("item_use", "wand", "", cmds.toString()))));
        int fired = 0;
        for (long tick = 0; tick < 20; tick++) {
            for (int i = 0; i < 3; i++) d.fire(ModEvents.Event.ITEM_USE, "m:wand", "p" + i, tick, 0, 0, 0, env, sink);
        }
        fired = sink.log.size();
        check(fired == EventDispatcher.COMMANDS_PER_SECOND, "no more than 200 commands in a second (20 ticks): " + fired);
        sink.log.clear();
        d.fire(ModEvents.Event.ITEM_USE, "m:wand", "p0", 20, 0, 0, 0, env, sink);
        check(sink.log.size() == 8, "the next second starts fresh");

        // The budget is per mod: one noisy mod does not use up another's.
        sink = new RecordingSink();
        d = new EventDispatcher(List.of(
            rules("noisy", true, 2, one("item_use", "minecraft:stick", "", cmds.toString())),
            rules("quiet", true, 2, one("item_use", "minecraft:stick", "", "[{\"command\":\"say q\"}]"))));
        for (int i = 0; i < 5; i++) d.fire(ModEvents.Event.ITEM_USE, "minecraft:stick", "p" + i, 3, 0, 0, 0, env, sink);
        long quiet = sink.log.stream().filter(line -> line.startsWith("command:quiet:")).count();
        check(quiet == 5, "the quiet mod ran all five: " + quiet);

        // Non-command actions have a (large) budget too.
        sink = new RecordingSink();
        StringBuilder msgs = new StringBuilder("[");
        for (int i = 0; i < 8; i++) msgs.append(i == 0 ? "" : ",").append("{\"damage_item\":1}");
        msgs.append("]");
        d = new EventDispatcher(List.of(rules("m", true, 2, one("item_use", "wand", "", msgs.toString()))));
        for (int i = 0; i < 40; i++) d.fire(ModEvents.Event.ITEM_USE, "m:wand", "p" + i, 7, 0, 0, 0, env, sink);
        check(sink.log.size() == EventDispatcher.ACTIONS_PER_TICK, "200 actions per mod per tick: " + sink.log.size());

        // Nesting: a command that raises the same event again is cut off at the depth limit.
        sink = new RecordingSink();
        final EventDispatcher[] holder = new EventDispatcher[1];
        final RecordingSink nested = sink;
        final int[] calls = {0};
        d = new EventDispatcher(List.of(rules("m", true, 2, one("item_use", "wand", "", "[{\"command\":\"say again\"}]"))));
        holder[0] = d;
        sink.onCommand = () -> {
            calls[0]++;
            holder[0].fire(ModEvents.Event.ITEM_USE, "m:wand", "p1", 50, 0, 0, 0, env, nested);
        };
        d.fire(ModEvents.Event.ITEM_USE, "m:wand", "p1", 50, 0, 0, 0, env, sink);
        check(calls[0] == EventDispatcher.MAX_DEPTH, "a command that raises its own event runs " + EventDispatcher.MAX_DEPTH + " deep and no more: " + calls[0]);
        sink.onCommand = null;
        sink.log.clear();
        d.fire(ModEvents.Event.ITEM_USE, "m:wand", "p1", 51, 0, 0, 0, env, sink);
        check(sink.log.size() == 1, "after the cut-off the depth counter is back to zero");

        // A command that slips past a denied word at run time is still refused.
        sink = new RecordingSink();
        ModEvents.Binding bad = new ModEvents.Binding("m", 0, ModEvents.Event.ITEM_USE, "m:wand", 0, 20, ModEvents.Condition.NONE,
            List.of(new ModEvents.Action(ModEvents.Kind.COMMAND, "op Steve", null, 0, 0, 0, 0, 0, false, null)));
        d = new EventDispatcher(List.of(new EventDispatcher.ModRules("m", true, 2, List.of(bad))));
        d.fire(ModEvents.Event.ITEM_USE, "m:wand", "p1", 0, 0, 0, 0, env, sink);
        check(sink.log.isEmpty() && sink.warnings.size() == 1 && sink.warnings.get(0).contains("refused"), "run-time check refuses a denied command: " + sink.warnings);
    }

    // ---- inside a mod zip ----

    static void inPackage() {
        Map<String, byte[]> files = TestKit.goodMod("ruby_tools");
        files.put("events.json", bytes(one("item_use", "ruby_sword", ",\"cooldown\":20", "[{\"command\":\"say hi\"},{\"message\":\"hi\"}]")));
        ModPackage pkg = TestKit.read(files);
        check(pkg.ok(), "a mod with events.json imports: " + pkg.problems);
        check(pkg.events.size() == 1 && pkg.events.get(0).modId().equals("ruby_tools"), "the package carries its bindings");
        check(pkg.kinds.contains("event") && pkg.kinds.contains("item"), "kinds include event: " + pkg.kinds);
        check(pkg.files.containsKey("events.json"), "events.json is kept");
        check(pkg.warnings.isEmpty(), "no warnings: " + pkg.warnings);

        Map<String, byte[]> declared = TestKit.goodMod("ruby_tools");
        declared.put("info.json", bytes("{\"schema\":1,\"id\":\"ruby_tools\",\"name\":\"Ruby Tools\",\"version\":\"1.0.0\",\"provides\":[\"item\",\"event\"],"
            + "\"command_level\":1}"));
        declared.put("events.json", bytes(one("item_use", "ruby_sword", "", "[{\"message\":\"hi\"}]")));
        ModPackage withLevel = TestKit.read(declared);
        check(withLevel.ok() && withLevel.manifest.commandLevel == 1 && withLevel.manifest.effectiveCommandLevel() == 1, "command_level is read: " + withLevel.problems);
        check(withLevel.warnings.isEmpty(), "declaring \"event\" with events present gives no warning: " + withLevel.warnings);
        check(pkg.manifest.commandLevel == -1 && pkg.manifest.effectiveCommandLevel() == 2, "no command_level means 2 when it is needed");

        Map<String, byte[]> high = TestKit.goodMod("ruby_tools");
        high.put("info.json", bytes("{\"schema\":1,\"id\":\"ruby_tools\",\"name\":\"Ruby Tools\",\"version\":\"1.0.0\",\"command_level\":4}"));
        ModPackage clamped = TestKit.read(high);
        check(clamped.ok() && clamped.manifest.commandLevel == 2 && any(clamped.warnings, "command_level"), "command_level above 2 is lowered with a warning: " + clamped.warnings);
        Map<String, byte[]> text = TestKit.goodMod("ruby_tools");
        text.put("info.json", bytes("{\"schema\":1,\"id\":\"ruby_tools\",\"name\":\"Ruby Tools\",\"version\":\"1.0.0\",\"command_level\":\"high\"}"));
        check(!TestKit.read(text).ok(), "a command_level that is not a number rejects the mod");

        Map<String, byte[]> missing = TestKit.goodMod("ruby_tools");
        missing.put("info.json", bytes("{\"schema\":1,\"id\":\"ruby_tools\",\"name\":\"Ruby Tools\",\"version\":\"1.0.0\",\"provides\":[\"item\",\"event\"]}"));
        ModPackage noFile = TestKit.read(missing);
        check(noFile.ok() && any(noFile.warnings, "events.json was not found"), "declaring event without the file warns: " + noFile.warnings);

        Map<String, byte[]> broken = TestKit.goodMod("ruby_tools");
        broken.put("events.json", bytes("{ nope"));
        ModPackage bad = TestKit.read(broken);
        check(!bad.ok() && any(bad.problems, "events.json is not valid JSON"), "a broken events.json rejects the mod: " + bad.problems);

        Map<String, byte[]> softBroken = TestKit.goodMod("ruby_tools");
        softBroken.put("events.json", bytes("{\"events\":[{\"on\":\"item_use\",\"target\":\"a\",\"do\":[{\"command\":\"op x\"}]}]}"));
        ModPackage soft = TestKit.read(softBroken);
        check(soft.ok() && soft.events.isEmpty() && !soft.kinds.contains("event") && any(soft.warnings, "not allowed in mods"),
            "a refused command only drops its entry; the mod still imports: " + soft.warnings);
    }
}
