package com.melonman106.vbvclient.mods;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The calls the patched game makes when something happens. Each one first asks {@link ModEventRuntime#handles} so a
 * game with no event rules does one comparison and nothing else, then works out who did what and hands over to
 * {@link ModEventRuntime#raise}. They run after the game's own action and never change its outcome.
 *
 * <p>All of them are meant for the server; a call from a client world is ignored.</p>
 */
public final class ModEventHooks {
    /** How often a creature standing on a block raises block_step_on again. */
    private static final int STEP_SPACING = 10;
    private static final int STEP_TABLE_MAX = 512;
    private static final Map<Integer, Long> LAST_STEP = new HashMap<>();

    private ModEventHooks() {}

    private static String item(Item item) {
        return ModEventRuntime.idOf(item, BuiltInRegistries.ITEM.getKey(item));
    }

    private static String block(Block block) {
        return ModEventRuntime.idOf(block, BuiltInRegistries.BLOCK.getKey(block));
    }

    private static String mobId(Entity entity) {
        return ModEventRuntime.idOf(entity.getType(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    /** A right-click that the item handled ({@code Item.use}). {@code item} is read before the use, as the stack may be used up. */
    public static void itemUse(ServerPlayer player, Item item, ItemStack stack, InteractionHand hand) {
        if (!ModEventRuntime.handles(ModEvents.Event.ITEM_USE)) return;
        ModEventRuntime.raise(ModEvents.Event.ITEM_USE, item, item(item), player.level(), player, null, null, stack, hand);
    }

    /** An item that placed or did something to the block it was used on ({@code Item.useOn}). */
    public static void itemUseOnBlock(ServerPlayer player, Item item, ItemStack stack, InteractionHand hand, BlockPos pos) {
        if (!ModEventRuntime.handles(ModEvents.Event.ITEM_USE_ON_BLOCK)) return;
        ModEventRuntime.raise(ModEvents.Event.ITEM_USE_ON_BLOCK, item, item(item), player.level(), player, null, pos, stack, hand);
    }

    /** A player hit a living thing with an item in hand. */
    public static void itemHitEntity(Player player, ItemStack stack, LivingEntity target) {
        if (!(player instanceof ServerPlayer serverPlayer) || !ModEventRuntime.handles(ModEvents.Event.ITEM_HIT_ENTITY)) return;
        Item item = stack.getItem();
        ModEventRuntime.raise(ModEvents.Event.ITEM_HIT_ENTITY, item, item(item), serverPlayer.level(), serverPlayer, target, null,
            stack, InteractionHand.MAIN_HAND);
    }

    /** A player finished eating, drinking or otherwise using an item that takes time. */
    public static void itemFinishUsing(LivingEntity user, Item item, ItemStack stack, InteractionHand hand) {
        if (!(user instanceof ServerPlayer player) || !ModEventRuntime.handles(ModEvents.Event.ITEM_FINISH_USING)) return;
        ModEventRuntime.raise(ModEvents.Event.ITEM_FINISH_USING, item, item(item), player.level(), player, null, null, stack, hand);
    }

    /** Called every tick for every player: the "holding" tick event, and the place that tidies up after commands. */
    public static void playerTick(ServerPlayer player) {
        ModEventRuntime.flushUntag();
        if (!ModEventRuntime.handles(ModEvents.Event.ITEM_HOLD_TICK)) return;
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return;
        Item item = held.getItem();
        ModEventRuntime.raise(ModEvents.Event.ITEM_HOLD_TICK, item, item(item), player.level(), player, null, null, held, InteractionHand.MAIN_HAND);
    }

    /** A player broke a block (it is already gone; {@code state} is what it was). */
    public static void blockBreak(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (!ModEventRuntime.handles(ModEvents.Event.BLOCK_BREAK)) return;
        Block block = state.getBlock();
        ModEventRuntime.raise(ModEvents.Event.BLOCK_BREAK, block, block(block), level, player, null, pos, null, InteractionHand.MAIN_HAND);
    }

    /** A player put a block in the world. Dispensers and other non-players are not reported. */
    public static void blockPlace(Level level, BlockPos pos, BlockState state, Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)
            || !ModEventRuntime.handles(ModEvents.Event.BLOCK_PLACE)) return;
        Block block = state.getBlock();
        ModEventRuntime.raise(ModEvents.Event.BLOCK_PLACE, block, block(block), serverLevel, serverPlayer, null, pos, null, InteractionHand.MAIN_HAND);
    }

    /** A player right-clicked a block and the block handled it. */
    public static void blockUse(ServerPlayer player, Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel serverLevel) || !ModEventRuntime.handles(ModEvents.Event.BLOCK_USE)) return;
        Block block = state.getBlock();
        ModEventRuntime.raise(ModEvents.Event.BLOCK_USE, block, block(block), serverLevel, player, null, pos, null, InteractionHand.MAIN_HAND);
    }

    /** A creature on the ground stands on this block. Raised at most every {@value #STEP_SPACING} ticks per creature. */
    public static void blockStepOn(Entity entity, BlockPos pos, BlockState state) {
        if (!(entity.level() instanceof ServerLevel level) || !ModEventRuntime.handles(ModEvents.Event.BLOCK_STEP_ON)) return;
        if (!(entity instanceof ServerPlayer) && !(entity instanceof Mob)) return;
        long tick = level.getServer().getTickCount();
        Long last = LAST_STEP.get(entity.getId());
        if (last != null && tick >= last && tick - last < STEP_SPACING) return;
        if (LAST_STEP.size() >= STEP_TABLE_MAX) LAST_STEP.clear();
        LAST_STEP.put(entity.getId(), tick);
        Block block = state.getBlock();
        ModEventRuntime.raise(ModEvents.Event.BLOCK_STEP_ON, block, block(block), level, entity, null, pos, null, InteractionHand.MAIN_HAND);
    }

    /** A block was given a random tick by the game. Nobody caused it, so commands run as the server at the block. */
    public static void blockRandomTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (!ModEventRuntime.handles(ModEvents.Event.BLOCK_RANDOM_TICK)) return;
        Block block = state.getBlock();
        ModEventRuntime.raise(ModEvents.Event.BLOCK_RANDOM_TICK, block, block(block), level, null, null, pos, null, null);
    }

    /** A mob has just appeared in the world (spawned, hatched, summoned, born). */
    public static void mobSpawn(Entity entity) {
        if (!(entity instanceof Mob mob) || mob.tickCount != 0 || !(mob.level() instanceof ServerLevel level)
            || !ModEventRuntime.handles(ModEvents.Event.MOB_SPAWN)) return;
        ModEventRuntime.raise(ModEvents.Event.MOB_SPAWN, mob.getType(), mobId(mob), level, mob, null, null, null, null);
    }

    /** A mob took damage; {@code source} says who or what did it. */
    public static void mobHurt(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof Mob mob) || !(mob.level() instanceof ServerLevel level) || !ModEventRuntime.handles(ModEvents.Event.MOB_HURT)) return;
        ModEventRuntime.raise(ModEvents.Event.MOB_HURT, mob.getType(), mobId(mob), level, mob, source.getEntity(), null, null, null);
    }

    /** A mob died. */
    public static void mobDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof Mob mob) || !(mob.level() instanceof ServerLevel level) || !ModEventRuntime.handles(ModEvents.Event.MOB_DEATH)) return;
        ModEventRuntime.raise(ModEvents.Event.MOB_DEATH, mob.getType(), mobId(mob), level, mob, source.getEntity(), null, null, null);
    }

    /** A mob hurt something with its own attack. */
    public static void mobAttack(Mob attacker, Entity target) {
        if (!(attacker.level() instanceof ServerLevel level) || !ModEventRuntime.handles(ModEvents.Event.MOB_ATTACK)) return;
        ModEventRuntime.raise(ModEvents.Event.MOB_ATTACK, attacker.getType(), mobId(attacker), level, attacker, target, null, null, null);
    }

    /** Called every tick for every mob; the dispatcher spaces the rules out by their interval. */
    public static void mobTick(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level) || !ModEventRuntime.handles(ModEvents.Event.MOB_TICK)) return;
        ModEventRuntime.raise(ModEvents.Event.MOB_TICK, mob.getType(), mobId(mob), level, mob, null, null, null, null);
    }

    /** A player has joined the server (a new connection, not a respawn). */
    public static void playerJoin(ServerPlayer player) {
        if (!ModEventRuntime.handles(ModEvents.Event.PLAYER_JOIN)) return;
        ModEventRuntime.raise(ModEvents.Event.PLAYER_JOIN, null, null, player.level(), player, null, null, null, null);
    }
}
