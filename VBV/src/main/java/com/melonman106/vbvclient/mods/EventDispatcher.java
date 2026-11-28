package com.melonman106.vbvclient.mods;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides which event bindings fire and in what order their actions run. The game side supplies the facts through
 * {@link Env} and performs the actions through {@link Sink}; everything in between (matching targets, spacing out tick
 * events, conditions, cooldowns, the command checks, the per-mod action budgets and the nesting limit) lives here so it
 * can be tested on a plain JVM. No game classes are used.
 *
 * <p>Bindings are notifications: they run after the game's own action and never change it. Everything is meant to run
 * on one thread (the server).</p>
 */
public final class EventDispatcher {
    /** An event caused by a command (a summon that raises mob_spawn, say) may nest this deep and no deeper. */
    public static final int MAX_DEPTH = 4;
    public static final int COMMANDS_PER_TICK = 20;
    public static final int COMMANDS_PER_SECOND = 200;
    public static final int ACTIONS_PER_TICK = 200;

    private static final int PRUNE_AT = 8192;

    /** One mod's rules, with what the player allowed. {@code commandLevel} is clamped to 0..2 before use. */
    public record ModRules(String modId, boolean commandsAllowed, int commandLevel, List<ModEvents.Binding> bindings) {}

    /** The facts about the moment an event happened. */
    public interface Env {
        /** A number from 0 (inclusive) to 1 (exclusive). */
        double random();
        boolean sneaking();
        String dimension();
        /** True when the biome at the event is {@code idOrTag} ("minecraft:plains" or "#minecraft:is_forest"). */
        boolean biomeIs(String idOrTag);
        boolean isDay();
        /** "clear", "rain" or "thunder". */
        String weather();
        int blockY();
        /** True when the actor's held item is {@code idOrTag}. */
        boolean holdingIs(String idOrTag);
        /** Current health as a fraction of the maximum (1 for something that has no health). */
        double healthFraction();
        /** True when the event's subject (item, block or mob) is in the tag. {@code tag} has no leading "#". */
        boolean subjectInTag(String tag);
    }

    /** What the game does for each kind of action. */
    public interface Sink {
        void command(String modId, String command, int level);
        void effect(ModEvents.Action action);
        void give(ModEvents.Action action);
        void message(ModEvents.Action action);
        void sound(ModEvents.Action action);
        void particle(ModEvents.Action action);
        void damageItem(int amount);
        void consumeItem(int amount);
        /** Something was refused or limited; shown once in the log. */
        void warn(String message);
    }

    private record Entry(ModRules rules, ModEvents.Binding binding) {}

    private static final class Budget {
        long tick = Long.MIN_VALUE;
        int actions;
        int commands;
        long window = Long.MIN_VALUE;
        int windowCommands;
        boolean warned;

        void roll(long now) {
            if (now != tick) {
                tick = now;
                actions = 0;
                commands = 0;
            }
            long currentWindow = Math.floorDiv(now, 20L);
            if (currentWindow != window) {
                window = currentWindow;
                windowCommands = 0;
            }
        }
    }

    private final Map<ModEvents.Event, List<Entry>> table = new EnumMap<>(ModEvents.Event.class);
    private final Map<String, Budget> budgets = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private int depth;

    public EventDispatcher(List<ModRules> mods) {
        for (ModRules rules : mods) {
            for (ModEvents.Binding binding : rules.bindings()) {
                table.computeIfAbsent(binding.event(), key -> new ArrayList<>()).add(new Entry(rules, binding));
            }
        }
    }

    /** True when at least one binding listens for the event; the game's hooks use this to skip all other work. */
    public boolean handles(ModEvents.Event event) {
        return table.containsKey(event);
    }

    /** Forgets cooldowns and budgets (a new world has started, so tick counts start again). */
    public void reset() {
        cooldowns.clear();
        budgets.clear();
        depth = 0;
    }

    /**
     * Raises an event.
     *
     * @param subjectId the item, block or mob the event is about, as a full id (null for events with no target)
     * @param actorKey  something that tells actors apart (an entity's UUID text), used for cooldowns and tick spacing
     * @param tick      the server's tick count
     * @param x         the block position for {@code {x}} in commands (see the design: the block for block events and
     *                  item_use_on_block, otherwise the actor's block)
     */
    public void fire(ModEvents.Event event, String subjectId, String actorKey, long tick, int x, int y, int z, Env env, Sink sink) {
        List<Entry> entries = table.get(event);
        if (entries == null || depth >= MAX_DEPTH) return;
        depth++;
        try {
            for (Entry entry : entries) run(entry, subjectId, actorKey, tick, x, y, z, env, sink);
        } finally {
            depth--;
        }
    }

    private void run(Entry entry, String subjectId, String actorKey, long tick, int x, int y, int z, Env env, Sink sink) {
        ModEvents.Binding binding = entry.binding();
        if (binding.target() != null) {
            if (binding.targetIsTag()) {
                if (!env.subjectInTag(binding.target().substring(1))) return;
            } else if (!binding.target().equals(subjectId)) {
                return;
            }
        }
        if (binding.event().ticking() && Math.floorMod(tick + actorKey.hashCode(), (long) binding.interval()) != 0) return;
        if (!holds(binding.condition(), env)) return;
        if (binding.cooldown() > 0 && !startCooldown(entry, actorKey, tick)) return;

        ModRules rules = entry.rules();
        Budget budget = budgets.computeIfAbsent(rules.modId(), key -> new Budget());
        for (ModEvents.Action action : binding.actions()) {
            budget.roll(tick);
            if (budget.actions >= ACTIONS_PER_TICK) {
                warnOnce(budget, rules, sink, "ran too many actions at once");
                return;
            }
            budget.actions++;
            switch (action.kind()) {
                case COMMAND: {
                    if (!rules.commandsAllowed()) break;
                    String reason = CommandGuard.check(action.text());
                    if (reason != null) {
                        sink.warn("Mod " + rules.modId() + ": a command was refused (" + reason + ").");
                        break;
                    }
                    if (budget.commands >= COMMANDS_PER_TICK || budget.windowCommands >= COMMANDS_PER_SECOND) {
                        warnOnce(budget, rules, sink, "ran too many commands at once");
                        break;
                    }
                    budget.commands++;
                    budget.windowCommands++;
                    String text = action.text().replace("{x}", Integer.toString(x)).replace("{y}", Integer.toString(y))
                        .replace("{z}", Integer.toString(z));
                    sink.command(rules.modId(), text, CommandGuard.clampLevel(rules.commandLevel()));
                    break;
                }
                case EFFECT: sink.effect(action); break;
                case GIVE: sink.give(action); break;
                case MESSAGE: sink.message(action); break;
                case SOUND: sink.sound(action); break;
                case PARTICLE: sink.particle(action); break;
                case DAMAGE_ITEM: sink.damageItem(action.amount()); break;
                case CONSUME_ITEM: sink.consumeItem(action.amount()); break;
                default: break;
            }
        }
    }

    private void warnOnce(Budget budget, ModRules rules, Sink sink, String what) {
        if (budget.warned) return;
        budget.warned = true;
        sink.warn("Mod " + rules.modId() + " " + what + "; the extra ones are skipped.");
    }

    /** True (and starts the cooldown) when the binding is ready for this actor. */
    private boolean startCooldown(Entry entry, String actorKey, long tick) {
        String key = entry.rules().modId() + "#" + entry.binding().index() + "|" + actorKey;
        Long last = cooldowns.get(key);
        // A tick count that went backwards means the world restarted: treat the binding as ready.
        if (last != null && tick >= last && tick - last < entry.binding().cooldown()) return false;
        if (cooldowns.size() >= PRUNE_AT) prune(tick);
        cooldowns.put(key, tick);
        return true;
    }

    private void prune(long tick) {
        Set<String> stale = new HashSet<>();
        for (Map.Entry<String, Long> entry : cooldowns.entrySet()) {
            if (tick < entry.getValue() || tick - entry.getValue() > ModEvents.MAX_COOLDOWN) stale.add(entry.getKey());
        }
        cooldowns.keySet().removeAll(stale);
        if (cooldowns.size() >= PRUNE_AT) cooldowns.clear();
    }

    /** True when every condition that is set holds. */
    public static boolean holds(ModEvents.Condition condition, Env env) {
        if (condition == ModEvents.Condition.NONE) return true;
        if (condition.sneaking() != null && condition.sneaking() != env.sneaking()) return false;
        if (!condition.dimensions().isEmpty() && !condition.dimensions().contains(env.dimension())) return false;
        if (condition.time() != null && ("day".equals(condition.time()) != env.isDay())) return false;
        if (condition.weather() != null && !condition.weather().equals(env.weather())) return false;
        if (condition.yMin() != null && env.blockY() < condition.yMin()) return false;
        if (condition.yMax() != null && env.blockY() > condition.yMax()) return false;
        if (condition.healthBelow() >= 0 && !(env.healthFraction() < condition.healthBelow())) return false;
        if (condition.holding() != null && !env.holdingIs(condition.holding())) return false;
        if (!condition.biomes().isEmpty()) {
            boolean any = false;
            for (String biome : condition.biomes()) {
                if (env.biomeIs(biome)) {
                    any = true;
                    break;
                }
            }
            if (!any) return false;
        }
        // The random roll goes last so a binding that fails a cheaper test does not spend a number.
        return condition.chance() >= 1.0 || env.random() < condition.chance();
    }

    /** The bindings that listen for an event, in load order (for tests and the details page). */
    public List<ModEvents.Binding> bindingsFor(ModEvents.Event event) {
        List<Entry> entries = table.get(event);
        if (entries == null) return List.of();
        List<ModEvents.Binding> out = new ArrayList<>();
        for (Entry entry : entries) out.add(entry.binding());
        return Collections.unmodifiableList(out);
    }
}
