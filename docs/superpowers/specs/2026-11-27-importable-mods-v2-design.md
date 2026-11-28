# Importable mods v2: events, commands, mobs, spawning, loot, world generation, render options

Status: **in progress.** Part 1 (events, commands, the Allow switch, the game hooks) is built; the rest is planned in the
order under "Update, Nov 28". Everything below about the game's code was read from the emulated Eaglercraft 26.2
sources; none of it has been run in the game.

## Why

Importable mods (`.zip`, see `docs/importable-mods.md`) can today add items, blocks, crops, effects, potions, HUD panels
and recipes. The project owner wants them to do much more: mobs and items that appear in the world on their own, items that run
commands, mods that change how existing mobs spawn and what blocks do, and some control over how the game is drawn.

The project owner's decisions (Nov 27):

* Mods stay **data only** ("events + commands, no code"). *Changed on Nov 28: see below.*
* The first pull request covers **all four** areas: events and commands, mobs and natural spawning, loot and world
  generation, render options and `.jem` models. *Changed on Nov 28: split into several pull requests.*

## Goals

1. **Events and commands.** Items, blocks and mobs (the mod's own and vanilla ones) can react to things happening and run
   actions, mainly ordinary game commands.
2. **Mod mobs** (`entities.json`): stats, look, behaviour, drops, a spawn egg.
3. **Natural spawning** of mod mobs, and rules that add, remove or re-weight the spawning of vanilla mobs per biome.
4. **Loot:** extra loot pools for vanilla and mod loot tables (chests, mob drops, block drops).
5. **World generation:** mod ores and plant patches placed in newly generated chunks.
6. **Render options:** block tint, sky and fog colour, and custom `.jem` models for mod mobs (and vanilla mobs and the
   player, the way a resource pack can).
7. **Safety:** importing a zip can never run code, read or write outside the game, or reach the network. Commands are the
   one strong tool and are behind a per-mod switch that is off by default.

## Non-goals

* Mixins and jars (Java code in a mod is still reported and not run). Mod code, if added, is sandboxed JavaScript in a
  later pull request (see "Update, Nov 28").
* Rewriting the renderer or chunk meshing. Mods choose from a fixed set of options only.
* Multiplayer id sync (still none), custom dimensions, custom GUIs or containers, structures, custom sounds (vanilla
  sound ids only), networking.
* Changing what a vanilla block or mob does beyond reacting to events and the properties listed below.

## Anti-goals (a failure even if it technically works)

* A broken or hostile mod stops the game from starting. Every piece is validated; an invalid piece is dropped with a
  message and the rest of the mod loads. Safe mode (`?nomods=1`, the Safe-mode button, the automatic switch-on after a
  failed start) turns **all** of it off.
* The two example mods, or any schema-1 mod already imported, stop working. Schema 1 stays valid; every new file and
  field is optional.
* An event loop that freezes the world (a command that triggers the same event again, forever).

## Constraints

* **Registry content is decided once at start-up** and must be identical on the page and in the integrated-server worker
  (the existing `applied.json` mechanism). Mob types, spawn eggs, attributes and renderers follow this rule. Events,
  spawn rules, loot and world generation are *server rules*: only the server worker needs them (read from the same applied
  copy, like recipes). Render options and models are page-side.
* **TeaVM / WASM-GC:** no reflection, no unsupported APIs; JSON through Gson as today.
* **Core logic uses no game classes** (like `ModContent` today) so it runs on a plain JVM in tests. The game side is thin.
* **Patch hooks fail closed:** each new hook has an exact-once anchor and a marker, is idempotent, and is registered in
  `apply_vbv_patch.py`.
* **No game available to run.** Verification is compile-checking against the emulated workspace, hook simulation
  (applies once, idempotent, no new compile errors against the unpatched baseline) and plain-JVM tests of the core.
  Results are reported as "not seen in the game".
* **Mesh workers** (opt-in with `?workers`) do not register mod content. Fallback decided here: if the applied mods
  contain blocks and mesh workers were requested, mesh workers are switched off for that session with a console and
  Mods-screen message. A real fix is only done if it turns out cheap.

## Approaches considered

| | What | Trade-off | Outcome |
| --- | --- | --- | --- |
| **A** | Declarative **events + commands** and rule files | Safe, small, testable on a plain JVM; limited to what commands, conditions and the fixed rule types can express | **Chosen** for part 1 |
| B | A, plus a sandboxed script language | More power (variables, loops, mob AI); a lot more to build and secure | **Chosen on Nov 28 for a later PR:** sandboxed JavaScript (QuickJS compiled to WebAssembly) |
| C | Real code in mods, running as ordinary page JavaScript or Java | Most power | **Rejected:** it would run inside the page with the player's browser access; the sandbox in B is the safe way to get code |

Inside A, events are dispatched from **a few central hooks** in the game (so vanilla blocks, items and mobs can be targets)
rather than only from subclasses of mod blocks and items. The mod's own blocks, items and mobs call the same dispatcher.

## Design

### New files in a mod

```text
my_mod.zip
├── entities.json        mobs                       (accepted-and-ignored today; becomes real)
├── events.json          event bindings, mainly for vanilla targets (mod items/blocks/mobs may also carry "events" inline)
├── spawns.json          natural spawn rules (mod and vanilla mobs)
├── loot.json            extra loot pools / new loot tables
├── worldgen.json        ores and plant patches
├── render.json          block tint, sky and fog colour
└── assets/<mod>/optifine/cem/<mob>.jem     custom mob models;  assets/minecraft/optifine/cem/ and
                                            assets/minecraft/textures/entity/ may override vanilla models and entity
                                            textures like a resource pack (the only paths a mod may place in the
                                            `minecraft` namespace)
```

Every list has a per-mod cap (256 entries, 8 actions per binding, 512 characters per command). `info.json` gains one
optional field: `"command_level": 0..2` (default 2 when any command action exists, otherwise 0; values above 2 are
clamped). `provides` may list the new kinds (`entity`, `event`, `spawn`, `loot`, `worldgen`, `render`).

### 1. Events and actions

```json
{ "events": [
  { "on": "item_use", "target": "ruby_tools:ruby_wand", "cooldown": 40,
    "if": { "sneaking": false, "chance": 1.0 },
    "do": [ { "command": "effect give @s minecraft:speed 10 1" },
            { "particle": "minecraft:flame", "count": 20 },
            { "damage_item": 1 } ] },
  { "on": "block_use", "target": "minecraft:dirt",
    "do": [ { "command": "summon minecraft:chicken ~ ~1 ~" } ] }
] }
```

* **Events:** items `item_use`, `item_use_on_block`, `item_hit_entity`, `item_finish_using` (eat/drink), `item_hold_tick`
  (every `interval` ticks while held); blocks `block_break`, `block_place`, `block_use`, `block_step_on`,
  `block_random_tick`; mobs `mob_spawn`, `mob_death`, `mob_hurt`, `mob_attack`, `mob_tick`; global `player_join`.
* **`target`:** an item, block or entity id, or a `#tag`. Omitted for global events.
* **`if` (all must hold):** `chance` (0–1), `sneaking`, `dimension` (list), `biome` (list or `#tag`), `time`
  (`day`, `night`), `weather` (`clear`, `rain`, `thunder`), `y_min`/`y_max`, `holding` (item id or `#tag`),
  `health_below` (fraction).
* **Actions without commands** (never need approval): `effect`, `give`, `message`, `sound`, `particle`, `damage_item`,
  `consume_item`. They are deliberately narrow so none of them can carry a command: `message` is **plain text only**
  (no JSON text, no click events, no translations), `give` is an **item id and a count only** (no components), `effect`,
  `sound` and `particle` take **ids and numbers only**. **`command`:** any game command, **needs the mod's "Allow
  commands" switch**.
* **Context:** `@s` is the **actor**. The command position is the actor, or the block centre for block events.
  `{x}`, `{y}`, `{z}` are the block position for every block event and for `item_use_on_block` (the clicked block), and
  the actor's block position for all other events. Block events with no entity (`block_random_tick`) have no `@s`; `@p`
  and `~ ~ ~` still work.

  | Event | `@s` (actor) | the other party |
  | --- | --- | --- |
  | `item_use`, `item_use_on_block`, `item_finish_using`, `item_hold_tick` | the player | none |
  | `item_hit_entity` | the player | the entity that was hit |
  | `block_break`, `block_place`, `block_use` | the player | none |
  | `block_step_on` | the entity that stepped | none |
  | `mob_hurt` | the mob that was hurt | the attacker, if any |
  | `mob_attack` | the attacking mob | the target |
  | `mob_spawn`, `mob_death`, `mob_tick` | the mob | none |
  | `player_join` | the player | none |

  The other party is reachable from a command as `@e[tag=vbv_other,limit=1]`: the dispatcher adds the tag `vbv_other` to
  it for exactly the duration of the command run and removes it afterwards (also on error). Nothing else is tagged.
* **Timing:** bindings are **notifications**. They run *after* the vanilla action has happened and can never cancel or
  replace it (no `cancel` action in this version). For `block_break` the block is already gone, so `target` matching and
  `{x}{y}{z}` use the broken block's captured state and position. For `mob_death` the mob still exists while the
  binding runs.
* **Safety rules for commands:**
  * They run on the server with permission level **`command_level` (at most 2, "like a data pack's functions")**, not the
    player's own level. (A player's own level is 0 in a world without cheats, which would make nearly every command a
    mod could want fail.) Output to chat is suppressed.
  * A fixed **deny-list** is refused whatever the level: `op`, `deop`, `stop`, `ban`, `ban-ip`, `banlist`, `pardon`,
    `pardon-ip`, `whitelist`, `save-all`, `save-on`, `save-off`, `setidletimeout`, `publish`, `debug`, `perf`, `jfr`,
    `transfer`.
  * **Per-mod switch:** the details page shows "Runs commands" with an **Allow commands** switch, **off by default**.
    The switch is tied to a hash of the mod's command text; if the mod is replaced with different commands the switch
    goes back off. While off, bindings with a `command` action skip that action (the rest still run) and the details page
    says how many were skipped.
  * **When the switch takes effect:** at the **next start-up**, like everything else a mod does (the screen already shows
    "Reload page"). The page stores the switch and the hash in the mod store (`vbv/mods/approvals.json`) and writes
    the result for each mod (`commands_allowed` true/false) into the applied copy it publishes at start-up; the server
    worker reads only that copy, so it never needs a live channel from the page. The details page shows the state as
    "Commands: allowed / not allowed / allowed after restart", and "skipped N" is computed from the mod's content and
    the stored switch.
  * **Limits:** at most 20 command actions per mod per tick (extra ones dropped, one warning logged); `mob_tick` and
    `item_hold_tick` intervals of at least 10 ticks; events caused by a command nest at most 4 deep.

### 2. Mobs (`entities.json`)

```json
{ "entities": [ {
  "id": "ember_hound", "name": "Ember Hound", "category": "monster",
  "body": "quadruped", "texture": "entity/ember_hound", "scale": 1.0,
  "size": { "width": 0.6, "height": 0.85 },
  "attributes": { "health": 20, "attack_damage": 4, "speed": 0.3, "follow_range": 24,
                  "armor": 0, "knockback_resistance": 0 },
  "goals": [ "float", { "melee_attack": { "speed": 1.2 } }, { "wander": { "speed": 0.8 } },
             "look_at_player", "random_look" ],
  "targets": [ "hurt_by", { "nearest": ["minecraft:player"] } ],
  "flags": { "fire_immune": true, "burns_in_daylight": false },
  "sounds": { "ambient": "minecraft:entity.wolf.growl", "hurt": "minecraft:entity.wolf.hurt",
              "death": "minecraft:entity.wolf.death" },
  "spawn_egg": { "base": "#7A2E1B", "spots": "#F2A33A" }
} ] }
```

* **Class:** a generic `ModMob` (one subclass of the game's monster base for `monster`, one of the pathfinder-mob base for
  `creature` and `ambient`), driven by the definition. Registered with `EntityType`, `DefaultAttributes`, a spawn egg item
  and a `SpawnPlacements` entry, from the same start-up code on page and worker.
* **Goals** (fixed list): `float`, `melee_attack`, `wander`, `look_at_player`, `random_look`, `panic`, `tempt`
  (`items`), `avoid` (`entities`, `distance`). **Targets:** `hurt_by`, `nearest` (entity ids). Unknown names are
  dropped with a message.
* **Look:** `body` picks one of the game's own model layers (preset list below) with the mod's texture. A mod can instead
  ship `assets/<mod>/optifine/cem/<id>.jem`, read by the client's existing custom-entity-model support (VBVCem).
* **Drops:** the mob's loot table is `<mod>:entities/<id>`, defined in `loot.json`. No table means no drops.
* `category`: `monster`, `creature` or `ambient`. Monsters do not spawn in peaceful.

### 3. Spawning (`spawns.json`)

```json
{ "spawns": [
  { "mob": "ruby_tools:ember_hound", "biomes": ["minecraft:desert", "#minecraft:is_badlands"],
    "weight": 8, "group": [1, 3],
    "if": { "light_max": 7, "time": "night", "y_min": 40, "block_below": ["minecraft:sand", "minecraft:red_sand"] } },
  { "mob": "minecraft:zombie", "mode": "remove", "biomes": ["minecraft:desert"] },
  { "mob": "minecraft:pig", "mode": "set_weight", "weight": 30, "biomes": ["#minecraft:is_forest"] }
] }
```

* `mode`: `add` (default), `remove`, `set_weight`. Works for mod and vanilla mobs.
* **Hook:** `ChunkGenerator.getMobsAt` (the single place natural spawning asks for a biome's spawn list). The adjusted list is
  cached per biome and category and cleared when the applied mods change.
* **Placement conditions:** the `if` block of the rules for that mob, evaluated in the mob's `SpawnPlacements` predicate
  (a position is allowed if at least one rule for the mob accepts it). Vanilla mobs keep their own placement rules.

### 4. Loot (`loot.json`)

```json
{ "loot": [
  { "table": "minecraft:chests/simple_dungeon",
    "pool": { "rolls": { "min": 1, "max": 2 }, "conditions": [ { "condition": "minecraft:random_chance", "chance": 0.3 } ],
              "entries": [ { "type": "minecraft:item", "name": "ruby_tools:ruby", "weight": 5 } ] } },
  { "table": "ruby_tools:entities/ember_hound", "pool": { "rolls": 1, "entries": [ { "type": "minecraft:item", "name": "minecraft:bone" } ] } }
] }
```

* `pool` is an ordinary loot-pool JSON object, parsed by the game's own codec, **after a whitelist check on the raw
  JSON** (done in the core, so it runs on a plain JVM). Loot functions can carry text components with click events, so
  they are whitelisted rather than all allowed:
  * entry types: `minecraft:item`, `minecraft:tag`, `minecraft:empty`;
  * functions: `minecraft:set_count`, `minecraft:set_damage`, `minecraft:enchant_randomly`,
    `minecraft:enchant_with_levels`, `minecraft:apply_bonus`, `minecraft:explosion_decay`, `minecraft:furnace_smelt`,
    `minecraft:limit_count`;
  * conditions: `minecraft:random_chance`, `minecraft:killed_by_player`, `minecraft:inverted`, `minecraft:any_of`,
    `minecraft:all_of`, `minecraft:survives_explosion`, `minecraft:match_tool` (item/tag only).
  A pool using anything else is dropped with a message naming the type. So loot never needs the commands switch and
  cannot carry a command.
* **Hook:** `ReloadableServerRegistries.getLootTable`, the one lookup every loot consumer uses. A table with extra pools is
  wrapped once and cached. A `table` that does not exist and is in the mod's own namespace is **created** from the pools.

### 5. World generation (`worldgen.json`)

```json
{ "ores": [ { "block": "ruby_tools:ruby_ore", "replace": ["#minecraft:stone_ore_replaceables"], "size": 6,
              "per_chunk": 4, "y": [-48, 32], "biomes": ["#minecraft:is_overworld"] } ],
  "patches": [ { "block": "ruby_tools:ruby_flower", "on": ["minecraft:grass_block"], "per_chunk": 0.15,
                 "tries": 24, "spread": 6, "biomes": ["minecraft:plains"] } ] }
```

* **Hook:** `ChunkGenerator.applyBiomeDecoration`, after vanilla decoration, for **newly generated chunks only** (existing
  chunks are never changed).
* Placement is deterministic from the world seed, the chunk position and the rule, and stays inside the chunk being
  decorated (veins are clamped to it).
* Dimension defaults to the overworld; `"dimension"` may name another.

### 6. Render options (`render.json`)

```json
{ "blocks": [ { "block": "ruby_tools:ruby_ore", "tint": "#E0115F" } ],
  "sky": [ { "dimension": "minecraft:overworld", "sky_color": "#6A5ACD", "fog_color": "#C8B6FF" } ] }
```

* **Block tint:** registered with the game's block colour handler for tint index 0 of the block's model. Page-side only.
* **Sky and fog colour:** applied where the client computes the fog colour and sky colour, for the named dimension.
* **Not included (not possible as data):** changing how chunks are meshed or lit, shaders per mod, new render pipelines.
* **Models:** the `.jem` / entity-texture allowance described above. The block **render layer** (solid / cutout /
  translucent) is only added if the game exposes it safely for a block; otherwise it stays a property of the block's
  model JSON as today.

### 7. UI, docs, example, tests

* **Mods screen / details page:** counts per new kind, "Runs commands" with the Allow switch, a list of dropped pieces.
* **Docs:** `docs/importable-mods.md` and the README are updated; the "Limits worth knowing" section is rewritten.
* **Example mod `ember_demo`** added to `build_example_mods.py`: one mob with a spawn rule, a loot pool, an ore, a wand
  that runs a command, a block tint. Built to a zip by the same script.
* **Tests (plain JVM):** schema parsing and validation of every new file; condition evaluation; event matching and
  cooldowns; command filter (deny-list, level clamp, per-tick budget, nesting depth); spawn-list transformation; the
  worldgen placement maths on a fake grid; approval hash. Workflow 07 runs them.
* **Game-side checks:** compile against the emulated workspace; hook simulation for every new anchor.

### Work order inside the one PR

Each step compiles and passes its tests before the next starts, so a step that runs out of road can be left out of the PR
rather than left broken:

1. Core schema for all new files; events, conditions, actions, command filter and approval; central event hooks.
2. Mobs and natural spawning (entity registration, goals, renderers, spawn rules).
3. Loot and world generation.
4. Render options, `.jem` allowance, example mod, UI, docs.

## Left to the builder

* The exact hook anchors for each event (candidates: the server-side player game mode for use and break, `Block.stepOn`,
  `BlockBehaviour.useWithoutItem` and `randomTick`, `Item.use`, `useOn` and `finishUsingItem`, `LivingEntity.hurtServer`
  and `die`, `Entity.tick`), each verified exact-once.
* The set of `body` presets: the game's own model layers that can be reused with a custom texture. Minimum: humanoid,
  quadruped, spider. Others only if they are easy; the doc lists exactly what shipped.
* The goal and target implementations, particle and sound action details, how `#tag` targets and biome tags are resolved
  on each side, and when renderer providers are registered relative to `EntityRenderers.createEntityRenderers`.
* Whether the existing custom-entity-model code can key a `.jem` by a mod mob id cheaply. If not, `.jem` for mod mobs is
  documented as "later" and body presets carry the look; vanilla-model overrides still work.
* Whether the mesh-worker fix is cheap (otherwise the fallback above stays).

## Facts checked in the game's source

* `ChunkGenerator.getMobsAt(...)` returns the `WeightedList<SpawnerData>` that `NaturalSpawner` uses.
* `ChunkGenerator.applyBiomeDecoration(WorldGenLevel, ChunkAccess, StructureManager)` exists.
* `ReloadableServerRegistries.getLootTable(key)` looks tables up in the loot-table registry.
* `Commands.performPrefixedCommand(CommandSourceStack, String)`; `CommandSourceStack.withPermission(PermissionSet)`;
  permission levels via `LevelBasedPermissionSet`.
* `DefaultAttributes.SUPPLIERS` is an immutable map (so a hook on `getSupplier` is needed);
  `EntityRenderers.PROVIDERS` is a private map filled by a private `register` (so a hook inside the class is needed);
  `SpawnPlacements.DATA_BY_TYPE` is a plain `HashMap`.
* `BlockColors.register(List<BlockTintSource>, Block...)` and a `computeFogColor` method exist.
* Mesh workers are off by default in this client.

## Calls made after the design review

A fresh reviewer read this document and asked three things. Each is settled above as my call, and none changes what the
mod author sees at the top level:

1. **Event context and timing:** one actor (`@s`), one optional other party reachable as `@e[tag=vbv_other,limit=1]`, and
   events that run after vanilla behaviour and never cancel it. (Cancelling can be added later without breaking mods.)
2. **No back door around the commands switch:** non-command actions are plain text / ids / numbers only, and loot pools
   are whitelisted by entry type, function and condition.
3. **The Allow switch applies at the next start-up**, stored in the mod store and copied into the applied list the server
   worker reads.

## Update, Nov 28: scope and order

The project owner changed two decisions and added requests. Part 1 above is unchanged and is still what the first PR
builds; the rest becomes separate pull requests, smallest and safest first.

1. **Events and commands** (this document's section 1, built): event rules, the Allow-commands switch, the game hooks, the
   `ember_demo` example.
2. **Mods folder:** the client reads a chosen folder at start-up, so mods can be added, removed or swapped without
   starting the game. It reuses the folder-picker handle the Storage feature already keeps; the browser may ask for one
   click to allow the folder again each session. The `.zip` import stays as the fallback.
3. **Per-server multiplayer and the mods list:** a mod's multiplayer setting becomes "everywhere" or "only these servers"
   (matched by server address). On joining a server the client sends the list of mods that are *enabled and allowed on
   that server*. The Fabric loader and Fabric API entries in that list are marked as VBV's emulation, because that is
   what they are; the client brand is not changed to claim a real Fabric install. Forge, NeoForge and Quilt join the
   fake-dependency table with the same honest "reported as present, does nothing" label (nothing is ported).
4. **Code engine:** optional `scripts/*.js` in a mod, run inside QuickJS compiled to WebAssembly (nothing outside the game
   functions the host hands in: no network, no page, no stored data). Scripts listen to the same events as part 1, can
   add mob behaviour and item behaviour, and sit behind an "Allow scripts" switch in the same style as the commands one.
5. **Mobs, spawning, loot, world generation, render options and `.jem`** (sections 2 to 6 above), built on parts 1 and 4.
6. **Server calls:** custom-payload messages from a real server reach the faked Fabric networking. Calls for things the
   client does not have are ignored; calls for things it has are carried out.

## As built in part 1 (differences from the text above)

* **Approvals** are stored in the mod store's `state.json` under `"commands"` as the exact command texts the player
  allowed, not as a hash in `approvals.json`. A mod is allowed when everything it asks to run has been approved, so
  removing a command keeps the approval and adding or changing one asks again. Mods with no commands never have a switch.
* The deny-list also refuses `reload` and `kick`, and looks inside `execute ... run` chains (including `return run`) so a
  denied command cannot hide behind them. A command is at most 512 characters. The per-mod budget is 20 commands per tick
  and, in addition, 200 per second and 200 actions per tick.
* `block_random_tick` has no actor: its commands run as the server at the block, and its non-command actions that need a
  player (message, give) do nothing. A mod block only receives random ticks when a `block_random_tick` rule names it
  (or names a tag).
* `block_step_on` is raised at most every 10 ticks per creature. `mob_spawn` is raised for a mob that has just appeared
  (spawned, hatched, summoned, born), not for mobs loaded from a saved chunk. `block_break`, `block_place` and `block_use`
  are raised for players only. `block_use` is also raised when the block took the item the player held.
  `item_use_on_block` is raised when the item itself handled the click.
* There are no inline `events` on items and blocks yet; `events.json` is the only way to attach rules.
* The game hooks are 18 one- or two-line additions in `VBV/patch/patch_mods_events_hooks.py`, each placed after the game's
  own action. With no loaded mod listening, each costs one array lookup.
