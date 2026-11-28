#!/usr/bin/env python3
"""Wire the importable mods' event rules (events.json) into the generated Eagler 26.2 sources.

Every hook is one extra call (or two) placed AFTER the game's own action, so a rule can never change what vanilla
did. The calls go to com.melonman106.vbvclient.mods.ModEventHooks, which first asks whether any loaded mod listens
for that event; with no event rules the whole cost is one array lookup.

Each edit has its own exact marker, so a re-run never applies one twice. Fail closed: every anchor must match
exactly once, and nothing is written unless all of them do.
"""
from pathlib import Path
import sys

if len(sys.argv) < 2:
    raise SystemExit("::error::Generated U2 workspace path was not supplied")

JAVA = Path(sys.argv[1]).resolve() / "game" / "src" / "main" / "java"
HOOKS = "com.melonman106.vbvclient.mods.ModEventHooks"

EDITS = [
    # --- items -----------------------------------------------------------------------------------------------
    (
        "net/minecraft/server/level/ServerPlayerGameMode.java",
        "item-use",
        "// VBV_MODS_EVENTS_HOOK: item-use",
        "      InteractionResult result = itemStack.use(level, player, hand);\n",
        "      // VBV_MODS_EVENTS_HOOK: item-use\n"
        "      net.minecraft.world.item.Item vbvUsedItem = itemStack.getItem();\n"
        "      InteractionResult result = itemStack.use(level, player, hand);\n"
        f"      if (result.consumesAction()) {HOOKS}.itemUse(player, vbvUsedItem, itemStack, hand);\n",
    ),
    (
        "net/minecraft/server/level/ServerPlayerGameMode.java",
        "item-use-on-block",
        "// VBV_MODS_EVENTS_HOOK: item-use-on-block",
        "if (success.consumesAction()) {\n",
        "if (success.consumesAction()) {\n"
        "               // VBV_MODS_EVENTS_HOOK: item-use-on-block\n"
        f"               {HOOKS}.itemUseOnBlock(player, usedItemStack.getItem(), itemStack, hand, pos);\n",
    ),
    (
        "net/minecraft/world/entity/player/Player.java",
        "item-hit-entity",
        "// VBV_MODS_EVENTS_HOOK: item-hit-entity",
        "            EnchantmentHelper.doPostAttackEffectsWithItemSource(serverLevel, entity, damageSource, attackingItemStack);\n",
        "            EnchantmentHelper.doPostAttackEffectsWithItemSource(serverLevel, entity, damageSource, attackingItemStack);\n"
        "            // VBV_MODS_EVENTS_HOOK: item-hit-entity\n"
        f"            if (hurtTarget instanceof LivingEntity vbvHitTarget) {HOOKS}.itemHitEntity(this, attackingItemStack, vbvHitTarget);\n",
    ),
    (
        "net/minecraft/world/entity/LivingEntity.java",
        "item-finish-using",
        "// VBV_MODS_EVENTS_HOOK: item-finish-using",
        "               ItemStack result = this.useItem.finishUsingItem(this.level(), this);\n",
        "               // VBV_MODS_EVENTS_HOOK: item-finish-using\n"
        "               net.minecraft.world.item.Item vbvFinishedItem = this.useItem.getItem();\n"
        "               ItemStack vbvFinishedStack = this.useItem;\n"
        "               ItemStack result = this.useItem.finishUsingItem(this.level(), this);\n"
        f"               {HOOKS}.itemFinishUsing(this, vbvFinishedItem, vbvFinishedStack, hand);\n",
    ),
    (
        "net/minecraft/server/level/ServerPlayer.java",
        "player-tick",
        "// VBV_MODS_EVENTS_HOOK: player-tick",
        "      this.connection.tickClientLoadTimeout();\n",
        "      this.connection.tickClientLoadTimeout();\n"
        "      // VBV_MODS_EVENTS_HOOK: player-tick\n"
        f"      {HOOKS}.playerTick(this);\n",
    ),
    # --- blocks ----------------------------------------------------------------------------------------------
    (
        "net/minecraft/server/level/ServerPlayerGameMode.java",
        "block-break",
        "// VBV_MODS_EVENTS_HOOK: block-break",
        "         block.destroy(this.level, pos, adjustedState);\n",
        "         block.destroy(this.level, pos, adjustedState);\n"
        "         // VBV_MODS_EVENTS_HOOK: block-break\n"
        f"         {HOOKS}.blockBreak(this.player, this.level, pos, adjustedState);\n",
    ),
    (
        "net/minecraft/world/item/BlockItem.java",
        "block-place",
        "// VBV_MODS_EVENTS_HOOK: block-place",
        "      level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, placedState));\n",
        "      level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, placedState));\n"
        "      // VBV_MODS_EVENTS_HOOK: block-place\n"
        f"      {HOOKS}.blockPlace(level, pos, placedState, player);\n",
    ),
    (
        "net/minecraft/server/level/ServerPlayerGameMode.java",
        "block-use-with-item",
        "// VBV_MODS_EVENTS_HOOK: block-use-with-item",
        "               CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(player, pos, usedItemStack);\n               return itemUse;\n",
        "               CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(player, pos, usedItemStack);\n"
        "               // VBV_MODS_EVENTS_HOOK: block-use-with-item\n"
        f"               {HOOKS}.blockUse(player, level, pos, state);\n"
        "               return itemUse;\n",
    ),
    (
        "net/minecraft/server/level/ServerPlayerGameMode.java",
        "block-use-empty-hand",
        "// VBV_MODS_EVENTS_HOOK: block-use-empty-hand",
        "               CriteriaTriggers.DEFAULT_BLOCK_USE.trigger(player, pos);\n",
        "               CriteriaTriggers.DEFAULT_BLOCK_USE.trigger(player, pos);\n"
        "               // VBV_MODS_EVENTS_HOOK: block-use-empty-hand\n"
        f"               {HOOKS}.blockUse(player, level, pos, state);\n",
    ),
    (
        "net/minecraft/world/entity/Entity.java",
        "block-step-on",
        "// VBV_MODS_EVENTS_HOOK: block-step-on",
        "            effectState.getBlock().stepOn(this.level(), effectPos, effectState, this);\n",
        "            effectState.getBlock().stepOn(this.level(), effectPos, effectState, this);\n"
        "            // VBV_MODS_EVENTS_HOOK: block-step-on\n"
        f"            {HOOKS}.blockStepOn(this, effectPos, effectState);\n",
    ),
    (
        "net/minecraft/server/level/ServerLevel.java",
        "block-random-tick",
        "// VBV_MODS_EVENTS_HOOK: block-random-tick",
        "                     blockState.randomTick(this, pos, this.random);\n",
        "                     blockState.randomTick(this, pos, this.random);\n"
        "                     // VBV_MODS_EVENTS_HOOK: block-random-tick\n"
        f"                     {HOOKS}.blockRandomTick(this, pos, blockState);\n",
    ),
    # --- mobs ------------------------------------------------------------------------------------------------
    (
        "net/minecraft/server/level/ServerLevel.java",
        "mob-spawn",
        "// VBV_MODS_EVENTS_HOOK: mob-spawn",
        "         return this.entityManager.addNewEntity(entity);\n",
        "         // VBV_MODS_EVENTS_HOOK: mob-spawn\n"
        "         boolean vbvAdded = this.entityManager.addNewEntity(entity);\n"
        f"         if (vbvAdded) {HOOKS}.mobSpawn(entity);\n"
        "         return vbvAdded;\n",
    ),
    (
        "net/minecraft/world/entity/LivingEntity.java",
        "mob-hurt",
        "// VBV_MODS_EVENTS_HOOK: mob-hurt",
        "            this.gameEvent(GameEvent.ENTITY_DAMAGE);\n",
        "            this.gameEvent(GameEvent.ENTITY_DAMAGE);\n"
        "            // VBV_MODS_EVENTS_HOOK: mob-hurt\n"
        f"            {HOOKS}.mobHurt(this, source);\n",
    ),
    (
        "net/minecraft/world/entity/LivingEntity.java",
        "mob-death",
        "// VBV_MODS_EVENTS_HOOK: mob-death",
        "            this.level().broadcastEntityEvent(this, (byte)3);\n",
        "            this.level().broadcastEntityEvent(this, (byte)3);\n"
        "            // VBV_MODS_EVENTS_HOOK: mob-death\n"
        f"            {HOOKS}.mobDeath(this, source);\n",
    ),
    (
        "net/minecraft/world/entity/Mob.java",
        "mob-attack",
        "// VBV_MODS_EVENTS_HOOK: mob-attack",
        "         this.setLastHurtMob(target);\n         this.playAttackSound();\n",
        "         this.setLastHurtMob(target);\n"
        "         this.playAttackSound();\n"
        "         // VBV_MODS_EVENTS_HOOK: mob-attack\n"
        f"         {HOOKS}.mobAttack(this, target);\n",
    ),
    (
        "net/minecraft/world/entity/Mob.java",
        "mob-tick",
        "// VBV_MODS_EVENTS_HOOK: mob-tick",
        "      super.tick();\n      if (!this.level().isClientSide() && this.tickCount % 5 == 0) {\n",
        "      super.tick();\n"
        "      // VBV_MODS_EVENTS_HOOK: mob-tick\n"
        f"      {HOOKS}.mobTick(this);\n"
        "      if (!this.level().isClientSide() && this.tickCount % 5 == 0) {\n",
    ),
    # --- players and commands --------------------------------------------------------------------------------
    (
        "net/minecraft/server/players/PlayerList.java",
        "player-join",
        "// VBV_MODS_EVENTS_HOOK: player-join",
        "      net.lax1dude.eaglercraft.v1_8.profile.LANPlayerSkinBroker26.playerJoined(this.server, connection, player);\n",
        "      net.lax1dude.eaglercraft.v1_8.profile.LANPlayerSkinBroker26.playerJoined(this.server, connection, player);\n"
        "      // VBV_MODS_EVENTS_HOOK: player-join\n"
        f"      {HOOKS}.playerJoin(player);\n",
    ),
    (
        "net/minecraft/commands/Commands.java",
        "in-command",
        "// VBV_MODS_EVENTS_HOOK: in-command",
        "   private static final ThreadLocal<@Nullable ExecutionContext<CommandSourceStack>> CURRENT_EXECUTION_CONTEXT = new ThreadLocal<>();\n",
        "   private static final ThreadLocal<@Nullable ExecutionContext<CommandSourceStack>> CURRENT_EXECUTION_CONTEXT = new ThreadLocal<>();\n"
        "\n"
        "   // VBV_MODS_EVENTS_HOOK: in-command\n"
        "   /** True while a command is being run; a command started now is queued behind it instead of running at once. */\n"
        "   public static boolean vbvInCommand() {\n"
        "      return CURRENT_EXECUTION_CONTEXT.get() != null;\n"
        "   }\n",
    ),
]

# Validate and stage every edit before writing any file.
staged = {}
for rel, label, marker, anchor, replacement in EDITS:
    path = JAVA / rel
    if not path.is_file():
        raise SystemExit(f"::error::{rel} is missing")

    text = staged.get(path)
    if text is None:
        text = path.read_text(encoding="utf-8")

    if (marker + "\n") in text:  # the newline keeps 'item-use' from matching 'item-use-on-block'
        staged[path] = text
        continue

    count = text.count(anchor)
    if count != 1:
        raise SystemExit(
            f"::error::VBV mod events hook '{label}' anchor matched {count} time(s) "
            f"in {rel}; expected exactly 1"
        )

    staged[path] = text.replace(anchor, replacement, 1)

changed = []
for path, text in staged.items():
    if text != path.read_text(encoding="utf-8"):
        changed.append(path)

for path in changed:
    path.write_text(staged[path], encoding="utf-8")

print(
    f"[VBV mod events] verified {len(EDITS)} distinct hooks "
    f"({len(changed)} file(s) updated)"
)
