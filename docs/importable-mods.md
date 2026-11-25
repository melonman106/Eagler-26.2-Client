# Importable mods (.zip)

VBV can import mods that are plain `.zip` files. A mod describes what it adds in JSON and ships its pictures in an
`assets/` folder; there is **no Java code** in an importable mod, so nothing a player imports can run code in the page.

The system also *pretends to be* the Fabric pieces a mod normally depends on, so a mod written the Fabric way
(`fabric.mod.json` asking for Fabric Loader, Fabric API, Minecraft and Java) imports and loads, while the parts of Fabric
API that a data mod can really use (item settings, creative tabs, resource loading, registering items) are backed by
real code in this client.

> **Status:** importable mods can add **items, blocks, crops, effects, potions, HUD panels and crafting recipes**. Entities
> and tags are still accepted but ignored. Nothing here has been run in the game yet; it is compile-checked and covered by
> plain-JVM tests of the parts that use no game classes (`VBV/test/run-mods-core-tests.sh`).

## Quick start

1. Build the two example mods: `python3 VBV/examples/build_example_mods.py` (writes `VBV/examples/dist/*.zip`). For the
   Slime test mod, run `python3 artifacts/build_slime_mod.py` (writes `artifacts/slime_mod.zip`).
2. In the game open **Mods → Imported Mods... → Import .zip** and choose `ruby_tools.zip` or `slime_mod.zip`.
3. Press **Reload page** when asked. The mod's items are in the creative inventory (Ingredients, Combat, Tools & Utilities).
   The Slime mod also adds a block, a crop, an effect, a potion, a HUD panel and recipes.

Mods are stored in the browser (the same storage the worlds use), under `vbv/mods/`.

## A mod's files

```text
my_mod.zip
├── info.json            (or fabric.mod.json)   required
├── pack.png             icon, PNG, up to 512 KB  (any size; 64x64 is typical)
├── items.json           items, food and potion items
├── content.json         optional: effects, potions, blocks, crops, HUD and recipes
├── assets/<mod id>/
│   ├── textures/item/<item>.png     one per item (textures/block/ also works)
│   ├── textures/block/<block>.png   block and crop textures
│   ├── textures/mob_effect/<id>.png effect icon
│   ├── models/item/<item>.json      optional: your own model instead of the generated one
│   ├── blockstates/, models/block/  block and crop models
│   └── lang/en_us.json             optional: extra translations
└── entities.json, tags.json         accepted, ignored for now
```

A folder wrapping everything inside the zip is unwrapped. `.class` and `.jar` files are ignored with a warning.

### info.json

```json
{
  "schema": 1,
  "id": "ruby_tools",
  "name": "Ruby Tools",
  "version": "1.0.0",
  "description": "A ruby, a ruby sword and a ruby apple.",
  "authors": ["Alexander"],
  "provides": ["item"],
  "survival": true,
  "multiplayer": false,
  "requires": { "vbvclient": ">=2.0.0", "fabricloader": ">=0.15.0", "fabric-api": "*" }
}
```

* `id`: lower case letters, digits, `_` and `-`, starting with a letter, 2–64 characters. It is also the namespace of the
  items (`ruby_tools:ruby_sword`). Ids used by the Fabric pieces (`fabric-*`, `fabric_api`, `fabric_loader`, `fabric`,
  `minecraft`, `java`, `c`) and by the client (`vbv`, `vbvclient`, `eaglercraft`, `realms`) are refused. Do not reuse the id
  of a built-in VBV mod either; an imported mod with such an id is not listed in the main Mods list.
* `requires`, `recommends`, `conflicts`, `breaks`: an object of `id: version range`, a list of such objects, or a list of
  strings like `"othermod >=1.2"`. Ranges follow Fabric: `>= <= > < =`, `~1.2`, `^1.2`, `1.x`, spaces for AND, `||` for OR.
* `survival` / `multiplayer`: the author's own statement, shown on the details page. They do not change behaviour.

### fabric.mod.json

A normal `fabric.mod.json` (schema 1) works too: `id`, `version`, `name`, `description`, `authors`, `icon` (a path, or a
size map where the biggest wins) and `depends` / `recommends` / `conflicts` / `breaks`. Its `entrypoints`, `mixins` and
`jars` are **reported and not run** (the details page says so). The mod still loads if it only needs items.

### items.json

```json
{ "items": [
  { "id": "ruby_sword", "name": "Ruby Sword",
    "attack_damage": 8, "attack_speed": 1.6, "durability": 1800, "enchantability": 15,
    "rarity": "rare", "repair_with": "minecraft:diamond",
    "creative_tab": ["combat", "tools_and_utilities"] },
  { "id": "ruby_apple", "name": "Ruby Apple", "rarity": "epic", "glint": true,
    "food": { "nutrition": 6, "saturation": 9.6, "always_edible": true } }
] }
```

| Field | Meaning |
| --- | --- |
| `id` | required; same character rules as the mod id |
| `name` | English name (also written to `lang/en_us.json` as `item.<mod>.<item>`) |
| `texture` | `assets/<mod>/textures/<path>.png`; defaults to `item/<id>` |
| `model` | `generated` or `handheld`; weapons default to `handheld` |
| `stack_size` | 1–99 (default 64; 1 when it has durability) |
| `durability` | up to 1,000,000; cannot stack and cannot be food |
| `attack_damage`, `attack_speed` | what the tooltip shows (damage 8 = "8 Attack Damage", speed 1.6 = 1.6 swings per second) |
| `rarity` | `common`, `uncommon`, `rare`, `epic` |
| `repair_with` | an item id that repairs it in an anvil (ignored with a message if that id does not exist) |
| `enchantability` | enchantment value |
| `fire_resistant`, `glint` | booleans |
| `food` | `nutrition`, `saturation`, `always_edible` |
| `potion` | a potion id from `content.json`; the item is a drinkable potion and must have `"stack_size": 1` |
| `creative_tab` | one tab id or a list: `building_blocks`, `colored_blocks`, `natural_blocks`, `functional_blocks`, `redstone_blocks`, `tools_and_utilities`, `combat`, `food_and_drinks`, `ingredients`, `spawn_eggs`, `op_blocks`, or `none` to hide it. Defaults by kind (food, weapon, tool, ingredient). |
| `tags` | read and reported, **not applied** in this version |

At most 256 items per mod.

### content.json

`content.json` is optional. It holds the content that is not a plain item:

```json
{
  "effects": [
    { "id": "slime_body", "name": "Slime Body", "color": "#66D9FF", "category": "beneficial",
      "heal_interval": 40, "heal_amount": 1 }
  ],
  "potions": [
    { "id": "slime_body", "name": "Slime Body",
      "effects": [{ "effect": "vbvslime:slime_body", "duration": 900, "amplifier": 0 }] }
  ],
  "blocks": [
    { "id": "magicule_crystal_block", "name": "Magicule Crystal Block",
      "texture": "block/magicule_crystal_block", "hardness": 0.6, "resistance": 1.5,
      "light": 9, "sound": "amethyst", "creative_tab": "natural_blocks" }
  ],
  "crops": [
    { "id": "slime_herb", "name": "Slime Herb", "block": "slime_herb_crop",
      "seed": "slime_herb_seeds", "drop": "slime_herb", "texture": "block/slime_herb",
      "seed_texture": "item/slime_herb_seeds", "stages": 4, "creative_tab": "natural_blocks" }
  ],
  "hud": {
    "id": "slime_body", "title": "SLIME BODY", "effect": "vbvslime:slime_body",
    "show_timer": true, "nearby_entity": "minecraft:slime", "always_show": false
  },
  "recipes": [
    { "type": "minecraft:crafting_shapeless",
      "ingredients": ["minecraft:slime_ball", "minecraft:wheat_seeds"],
      "result": { "id": "vbvslime:slime_herb_seeds", "count": 2 } }
  ]
}
```

* **effects:** `id`, `name`, `color` (`#RRGGBB`), `category` (`beneficial`, `harmful` or `neutral`), `heal_interval` in ticks
  and `heal_amount` per tick of that interval. The icon is `assets/<mod>/textures/mob_effect/<id>.png`.
* **potions:** `id`, `name`, and up to eight `effects` with `effect`, `duration` and `amplifier`. Use the potion from an
  item with `"potion": "<mod>:<potion>"`.
* **blocks:** `id`, `name`, optional `texture` (default `block/<id>`), `hardness`, `resistance`, `light` (0–15), `sound`,
  and `creative_tab`. The block item uses the block id. Supply `assets/<mod>/blockstates/<id>.json`, a block model and an
  item definition/model, or use the generated layout from `artifacts/slime_mod` as a template.
* **crops:** `id`, `name`, `block` (default `<id>_crop`), `seed` (default `<id>_seeds`), `drop` (default `<id>`), and
  `stages` (1–7; the generated assets use `_stage0`, `_stage1`, …). Breaking a mature crop drops the crop item and seeds;
  breaking an immature one drops a seed.
* **hud:** one panel per mod, shown while `effect` is active (or always with `"always_show": true`). `nearby_entity`
  optionally counts matching entities within 16 blocks. The panel is drawn above the hotbar.
* **recipes:** ordinary Minecraft recipe JSON. Up to 256 per mod. They are merged after the game's own recipes, so they can
  use vanilla and imported ingredients.

### Limits

A zip may be up to 24 MB, 4000 entries, 8 MB per file and 48 MB unpacked; textures up to 4096 px. Paths containing `..`,
absolute paths and odd characters are rejected, and only known kinds of file are kept.

## The Fabric pieces

| Piece | What a mod gets |
| --- | --- |
| Fabric Loader | **faked**: reported as present (0.16.14) so dependency checks pass; nothing is discovered, no entrypoints or mixins run |
| Fabric API | **partly real**: the modules below |
| `minecraft`, `java` | real versions; a mismatch only warns |
| `fabric-api-base`, `fabric-resource-loader-v0` | **real**: `assets/` is served as a resource pack |
| `fabric-object-builder-api-v1`, `fabric-item-api-v1` | **partly real**: item settings from `items.json`, plus blocks and crops from `content.json` |
| `fabric-item-group-api-v1` | **partly real**: `creative_tab` puts items into the vanilla tabs (no custom tabs yet) |
| `fabric-registry-sync-v0` | **partly real**: items, blocks, crops, effects and potions are registered before the registry freezes; no id sync with servers |
| `fabric-rendering-v1` | **partly real**: content-defined HUD panels work; custom drawing code only for Java ports |
| lifecycle events, key bindings, screens | present for mods ported to Java *into this client*; a zip mod cannot use them yet |
| ~33 other Fabric API modules | **faked**: accepted so the mod loads, nothing behind them |

The Fabric pieces never conflict with or break anything, and a version range they do not satisfy only produces a warning
("asked for fabric-api >=9.0; the faked one says 0.121.0+26.2 and is accepted anyway"). A *real* dependency on another
imported mod that is missing or too old keeps the mod from loading, and the details page says which.

Mods ported to Java into the client see the same picture through `FabricLoader.getInstance().isModLoaded("fabric-api")`,
`getModContainer(...)` and `getAllMods()`.

## How loading works

Items, blocks, effects, potions and crops must exist in the game's registry before it freezes, so mods are decided **once,
at start-up**:

* The page computes the list of mods that will load (enabled, dependencies satisfied) and registers their content; the
  integrated-server worker must register exactly the same content. The page therefore publishes what it registered
  (`vbv/mods/applied.json` and content-named copies under `vbv/mods/applied/`) and the worker only reads that. If the
  worker cannot reproduce it, it stops with a message instead of starting with different ids.
* Recipes are merged into the recipe map after the game loads its own recipes, so they need no data pack of their own.
* Importing, removing or turning a mod off only changes the stored files. The screen shows **Reload page** until the page
  has been reloaded; each mod shows whether it is *Loaded*, *Loads after restart*, *Turns off after restart*, etc.
* Every mod becomes a required resource pack named `vbvmod/<id>` that sits above the base game and below the player's own
  packs; later ids override earlier ones.

### If a mod breaks the game

* **Safe mode** (button on the screen) loads no imported mod at every start until you turn it off.
* Open the page with `?nomods=1` (or `?safe`, the client's general safe mode) to start **one session** without any imported
  mod. Nothing is stored; open the page normally afterwards and the mods load again.
* If the previous start stopped while mods were being registered, safe mode switches on by itself and says why.

## Limits worth knowing

* Items, blocks, crops, effects, potions, HUD panels and recipes. Entities, tags and loot tables are not loaded yet.
* No Java: entrypoints, mixins and jars are never run.
* **Multiplayer:** there is no id sync. A server that does not know the items cannot use them; do not expect imported
  items to work on servers.
* Removing a mod whose items are in your worlds or inventories may make those items disappear or fail to load. Keep a
  backup of worlds you care about.
* Browser storage can be cleared by the browser or the school's policy; keep the `.zip` files.

## Where things live

```text
VBV/src/main/java/com/melonman106/vbvclient/mods/   core (ModVersion, FabricFaker, ModManifest, ModItems, ModContent,
                                                     ModPackage, ModAssets, ModStore, ModView use no game classes and
                                                     are tested on a plain JVM) + game side (ModPlatform,
                                                     ModBootstrap, ModContentBootstrap, ModBlock, ModCropBlock,
                                                     ModMobEffect, ModPotionItem, ModHud, ModRecipes, ModItemGroups,
                                                     ModPackSource, ModPackResources)
VBV/src/main/java/com/melonman106/vbvclient/VBVImportedModsScreen.java
VBV/patch/patch_mods_hooks.py        four exact-once hooks: BuiltInRegistries.bootStrap(), the Minecraft pack
                                     repository, CreativeModeTab.buildContents(), RecipeManager.apply()
VBV/test/run-mods-core-tests.sh      plain-JVM checks (needs a Gson jar; also builds and imports the examples)
VBV/examples/                        two example mods and the script that zips them
```
