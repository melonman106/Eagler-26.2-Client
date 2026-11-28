package com.melonman106.vbvclient.mods;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Runs the event bindings of the loaded mods on the server. {@link EventDispatcher} decides what fires; this class gives
 * it the facts it asks for ({@link Ctx} as {@code Env}) and carries out each action ({@code Ctx} as {@code Sink}).
 *
 * <p>Nothing here throws into the game: an action that cannot run is logged once and skipped, because a mod's rule must
 * never be able to break the vanilla code path that raised the event.</p>
 */
public final class ModEventRuntime {
    /** Given to the other party (the one hit, the killer ...) for as long as a command of the mod runs. */
    public static final String OTHER_TAG = "vbv_other";

    private static boolean built;
    private static EventDispatcher dispatcher;
    private static final boolean[] LISTENING = new boolean[ModEvents.Event.values().length];
    private static final Set<String> WARNED = new HashSet<>();
    private static final List<Entity> UNTAG = new ArrayList<>();
    private static final Map<Object, String> IDS = new IdentityHashMap<>();

    private ModEventRuntime() {}

    private static void build() {
        built = true;
        List<EventDispatcher.ModRules> rules = new ArrayList<>();
        for (ModPackage pkg : ModBootstrap.loaded()) {
            if (pkg.events.isEmpty()) continue;
            rules.add(new EventDispatcher.ModRules(pkg.manifest.id, ModBootstrap.commandsAllowed(pkg.manifest.id),
                pkg.manifest.effectiveCommandLevel(), pkg.events));
        }
        if (rules.isEmpty()) return;
        dispatcher = new EventDispatcher(rules);
        for (ModEvents.Event event : ModEvents.Event.values()) LISTENING[event.ordinal()] = dispatcher.handles(event);
    }

    /** True when any loaded mod listens for the event. This is the only work a hook does when no mod does. */
    public static boolean handles(ModEvents.Event event) {
        if (!built) build();
        return LISTENING[event.ordinal()];
    }

    static void warnOnce(String message) {
        if (WARNED.size() < 200 && WARNED.add(message)) System.err.println("[VBV mods] " + message);
    }

    /** The full id of an item, block or entity type, cached because tick events ask for it constantly. */
    static String idOf(Object thing, Identifier key) {
        String id = IDS.get(thing);
        if (id == null) {
            id = key.toString();
            IDS.put(thing, id);
        }
        return id;
    }

    /** Removes the other-party tag from entities that were still inside a running command when their event finished. */
    static void flushUntag() {
        if (UNTAG.isEmpty() || Commands.vbvInCommand()) return;
        for (Entity entity : UNTAG) entity.removeTag(OTHER_TAG);
        UNTAG.clear();
    }

    /**
     * Raises one event.
     *
     * @param subject  what the event is about (for tag tests): an Item, a Block or an EntityType, or null
     * @param subjectId the full id of that
     * @param actor    who did it (a player or a mob); the {@code @s} of commands. Null for events nobody caused
     *                 (a random tick): commands then run as the server at the block
     * @param other    the other party, tagged {@link #OTHER_TAG} while commands run, or null
     * @param pos      the block involved, or null
     * @param stack    the item involved (the one used, or what is held), or null
     */
    static void raise(ModEvents.Event event, Object subject, String subjectId, ServerLevel level, Entity actor, Entity other,
                      BlockPos pos, ItemStack stack, InteractionHand hand) {
        if (dispatcher == null || (actor == null && pos == null)) return;
        try {
            flushUntag();
            Ctx ctx = new Ctx(level, actor, other, pos, stack, hand, subject);
            BlockPos at = pos != null ? pos : actor.blockPosition();
            String actorKey = actor != null ? actor.getUUID().toString() : "block:" + pos.asLong();
            dispatcher.fire(event, subjectId, actorKey, level.getServer().getTickCount(),
                at.getX(), at.getY(), at.getZ(), ctx, ctx);
        } catch (Throwable t) {
            warnOnce("an event rule failed (" + event.id + "): " + t);
        }
    }

    private static final class Ctx implements EventDispatcher.Env, EventDispatcher.Sink {
        private final ServerLevel level;
        private final Entity actor;
        private final Entity other;
        private final BlockPos pos;
        private final ItemStack stack;
        private final InteractionHand hand;
        private final Object subject;

        Ctx(ServerLevel level, Entity actor, Entity other, BlockPos pos, ItemStack stack, InteractionHand hand, Object subject) {
            this.level = level;
            this.actor = actor;
            this.other = other;
            this.pos = pos;
            this.hand = hand;
            this.subject = subject;
            this.stack = stack != null ? stack : (actor instanceof LivingEntity living ? living.getMainHandItem() : ItemStack.EMPTY);
        }

        private BlockPos where() {
            return pos != null ? pos : actor.blockPosition();
        }

        // ---- Env ----

        @Override
        public double random() {
            return level.getRandom().nextDouble();
        }

        @Override
        public boolean sneaking() {
            return actor != null && actor.isShiftKeyDown();
        }

        @Override
        public String dimension() {
            return level.dimension().identifier().toString();
        }

        @Override
        public boolean biomeIs(String idOrTag) {
            try {
                Holder<net.minecraft.world.level.biome.Biome> biome = level.getBiome(where());
                if (idOrTag.startsWith("#")) return biome.is(TagKey.create(Registries.BIOME, Identifier.parse(idOrTag.substring(1))));
                return biome.is(Identifier.parse(idOrTag));
            } catch (RuntimeException ex) {
                return false;
            }
        }

        @Override
        public boolean isDay() {
            return level.isBrightOutside();
        }

        @Override
        public String weather() {
            return level.isThundering() ? "thunder" : level.isRaining() ? "rain" : "clear";
        }

        @Override
        public int blockY() {
            return where().getY();
        }

        @Override
        public boolean holdingIs(String idOrTag) {
            if (!(actor instanceof LivingEntity living)) return false;
            ItemStack held = living.getMainHandItem();
            if (held.isEmpty()) return false;
            try {
                if (idOrTag.startsWith("#")) return held.is(TagKey.create(Registries.ITEM, Identifier.parse(idOrTag.substring(1))));
                return idOf(held.getItem(), BuiltInRegistries.ITEM.getKey(held.getItem())).equals(idOrTag);
            } catch (RuntimeException ex) {
                return false;
            }
        }

        @Override
        public double healthFraction() {
            if (!(actor instanceof LivingEntity living)) return 1.0;
            float max = living.getMaxHealth();
            return max <= 0 ? 1.0 : Math.max(0.0, Math.min(1.0, living.getHealth() / max));
        }

        @Override
        public boolean subjectInTag(String tag) {
            try {
                Identifier id = Identifier.parse(tag);
                if (subject instanceof Item item) return BuiltInRegistries.ITEM.wrapAsHolder(item).is(TagKey.create(Registries.ITEM, id));
                if (subject instanceof net.minecraft.world.level.block.Block block) {
                    return BuiltInRegistries.BLOCK.wrapAsHolder(block).is(TagKey.create(Registries.BLOCK, id));
                }
                if (subject instanceof net.minecraft.world.entity.EntityType<?> type) {
                    return BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).is(TagKey.create(Registries.ENTITY_TYPE, id));
                }
            } catch (RuntimeException ex) {
                // fall through: an unreadable tag matches nothing
            }
            return false;
        }

        // ---- Sink ----

        @Override
        public void command(String modId, String command, int permissionLevel) {
            CommandSourceStack source;
            if (actor instanceof ServerPlayer player) source = player.createCommandSourceStack();
            else if (actor != null) source = actor.createCommandSourceStackForNameResolution(level);
            else source = level.getServer().createCommandSourceStack().withLevel(level).withPosition(Vec3.atCenterOf(where()));
            source = source.withPermission(LevelBasedPermissionSet.forLevel(PermissionLevel.byId(permissionLevel))).withSuppressedOutput();
            boolean tagged = other != null && other != actor && other.addTag(OTHER_TAG);
            try {
                level.getServer().getCommands().performPrefixedCommand(source, command);
            } catch (Throwable t) {
                warnOnce("Mod " + modId + ": a command failed (" + t + ").");
            } finally {
                if (tagged) {
                    // A command that is already running only queues ours, so the tag has to outlive this call.
                    if (Commands.vbvInCommand()) UNTAG.add(other);
                    else other.removeTag(OTHER_TAG);
                }
            }
        }

        private LivingEntity living(String to) {
            Entity target = "other".equals(to) ? other : actor;
            return target instanceof LivingEntity living ? living : null;
        }

        private ServerPlayer player() {
            if (actor instanceof ServerPlayer p) return p;
            if (other instanceof ServerPlayer p) return p;
            return null;
        }

        @Override
        public void effect(ModEvents.Action action) {
            LivingEntity target = living(action.to());
            if (target == null) return;
            Optional<? extends Holder<MobEffect>> effect = lookup(BuiltInRegistries.MOB_EFFECT, action.id());
            if (effect.isEmpty()) {
                warnOnce("an effect action names \"" + action.id() + "\", which is not an effect.");
                return;
            }
            target.addEffect(new MobEffectInstance(effect.get(), action.duration(), action.amplifier()));
        }

        @Override
        public void give(ModEvents.Action action) {
            ServerPlayer player = player();
            if (player == null) return;
            Optional<? extends Holder<Item>> item = lookup(BuiltInRegistries.ITEM, action.id());
            if (item.isEmpty() || item.get().value() == Items.AIR) {
                warnOnce("a give action names \"" + action.id() + "\", which is not an item.");
                return;
            }
            ItemStack given = new ItemStack(item.get(), action.amount());
            if (!player.getInventory().add(given) && !given.isEmpty()) player.drop(given, false);
        }

        @Override
        public void message(ModEvents.Action action) {
            ServerPlayer player = player();
            if (player != null) player.sendSystemMessage(Component.literal(action.text()), action.flag());
        }

        private double[] centre() {
            if (pos != null) return new double[] {pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5};
            return new double[] {actor.getX(), actor.getY() + actor.getBbHeight() * 0.5, actor.getZ()};
        }

        @Override
        public void sound(ModEvents.Action action) {
            Optional<? extends Holder<SoundEvent>> sound = lookup(BuiltInRegistries.SOUND_EVENT, action.id());
            if (sound.isEmpty()) {
                warnOnce("a sound action names \"" + action.id() + "\", which is not a sound.");
                return;
            }
            double[] c = centre();
            level.playSound(null, c[0], c[1], c[2], sound.get().value(), SoundSource.PLAYERS, action.volume(), action.pitch());
        }

        @Override
        public void particle(ModEvents.Action action) {
            Optional<? extends Holder<ParticleType<?>>> particle = lookup(BuiltInRegistries.PARTICLE_TYPE, action.id());
            if (particle.isEmpty() || !(particle.get().value() instanceof SimpleParticleType simple)) {
                warnOnce("a particle action names \"" + action.id() + "\", which is not a particle without extra settings.");
                return;
            }
            double[] c = centre();
            double spread = action.volume();
            level.sendParticles(simple, c[0], c[1], c[2], action.amount(), spread, spread, spread, 0.02);
        }

        @Override
        public void damageItem(int amount) {
            if (!(actor instanceof ServerPlayer player) || stack.isEmpty() || !stack.isDamageableItem()) return;
            stack.hurtAndBreak(amount, player, hand != null ? hand : InteractionHand.MAIN_HAND);
        }

        @Override
        public void consumeItem(int amount) {
            if (!(actor instanceof ServerPlayer player) || stack.isEmpty() || player.hasInfiniteMaterials()) return;
            stack.shrink(amount);
        }

        @Override
        public void warn(String message) {
            warnOnce(message);
        }

        private static <T> Optional<? extends Holder<T>> lookup(Registry<T> registry, String id) {
            try {
                return registry.get(Identifier.parse(id));
            } catch (RuntimeException ex) {
                return Optional.empty();
            }
        }
    }
}
