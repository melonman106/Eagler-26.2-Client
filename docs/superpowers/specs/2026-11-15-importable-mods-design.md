# Importable `.zip` mods: design

Status: **draft for approval. No code has been written for this.**
Date: 2026-11-15. Requested by Alexander for the VBV Eaglercraft 26.2 client.

## Intent

Let people add new content to VBV by importing a `.zip` from the Mods screen, instead of editing the client's source and rebuilding. A mod brings its own `pack.png`, an `info.json` (name, version, description, who it works with), an `assets/` folder, and JSON files that describe new items, blocks, entities, effects and HUD pieces. Every mod says which kinds of content it holds.

Why: the client is built on a school Chromebook through GitHub Actions. Each new item today needs a Java change and a full build. A zip a friend can send and you can import is far faster and shareable.

## Goals

- Import one or more `.zip` mods from the Mods screen; list, enable/disable, export and remove them there.
- A fixed, documented, versioned zip layout (below) that is validated on import with clear error messages.
- New **items** that work in singleplayer survival: id, name, texture, attack damage, attack speed, durability, stack size and similar.
- Content-type tags (`item`, `block`, `entity`, `effect`, `hud`) shown for every mod, read from the files.
- `survival` / `multiplayer` flags and a `requires` list in `info.json`, enforced in the UI (a mod with a missing requirement is disabled and says why).

## Non-goals

- Running arbitrary code from a zip. Mods are data only (JSON + images/sounds/models). No Java, no JavaScript.
- Loading Fabric/Forge jars (the repo already states it does not).
- Making mod content work on servers that do not have the same mod. A vanilla server cannot know a custom item.
- Anything that would need the game's own source to change for each mod.

## Anti-goals (would be a failure even if it "works")

- A bad zip can crash the game at startup. A broken mod must be skipped with a message, never block launch.
- Importing a mod changes vanilla ids so existing worlds or servers break.
- A mod silently runs code or reads other files.

## Constraints found in the code

- The game's registries (items, blocks, entity types, effects) are **frozen at startup** (`MappedRegistry.freeze()`, called from `BuiltInRegistries.bootStrap()`), and writing afterwards throws "Registry is already frozen". New content therefore has to be registered **before the freeze**, in every place the game boots.
- Eagler boots the registries in more than one place: the client thread, the **integrated-server worker** (which runs `Bootstrap.bootStrap()` itself and rebuilds the world from the virtual filesystem), and the mesh worker. All of them must register the same mod content in the same order, or ids would disagree between the client and the integrated server.
- A browser page has no real folder. Eagler already has an IndexedDB-backed virtual filesystem (VFS) that the client and the server worker both read (`saves/`, `Paths.get(".")`).
- Eagler already imports resource-pack `.zip` files through a file picker (`FileChooserResult`) and VBV already queues them (`VBVPackInstaller`). Mods can reuse that import path.
- VBV already models "mods" (`VBVModInfo`, `HerzModRegistry`, the Mods screen with search, sort and type filter), so mods imported from zips can appear in the same list.
- Items are the most data-driven part of the game (`Item.Properties`, tool/attack attributes, client item definitions that are resource-pack driven). Blocks, entities and effects each need much more (models, collision, drops, AI, rendering).
- This sandbox cannot run the game, so every step must degrade safely and be easy for Alexander to test and report.

## The zip format (v1 schema)

```
my_mod.zip
  pack.png                       icon shown in the Mods list (square PNG)
  info.json                      required
  items.json                     optional
  blocks.json                    optional
  entities.json                  optional
  effects.json                   optional
  hud.json                       optional
  assets/<namespace>/
      textures/item/*.png        item textures
      textures/block/*.png       block textures
      lang/en_us.json            optional display names
      models/..., sounds/...     optional
```

`info.json`:

```json
{
  "schema": 1,
  "id": "ruby_tools",
  "name": "Ruby Tools",
  "version": "1.0.0",
  "description": "A ruby sword and pickaxe.",
  "authors": ["Alexander"],
  "provides": ["item"],
  "survival": true,
  "multiplayer": false,
  "requires": [ { "id": "other_mod", "version": ">=1.0.0" } ]
}
```

- `id` is lowercase letters, digits and `_`; it is also the namespace of everything the mod adds (`ruby_tools:ruby_sword`).
- `provides` must match the JSON files that are present; the Mods list shows these as tags.
- `survival`: the content is meant to be obtainable in survival worlds. If `false`, items are creative/command only.
- `multiplayer`: the author says the mod is meant to work on servers. See "Multiplayer" below for what that can honestly mean.
- `requires`: other mod ids (and optional version ranges) that must be present and enabled.

`items.json`:

```json
{
  "items": [
    {
      "id": "ruby_sword",
      "name": "Ruby Sword",
      "texture": "ruby_tools:item/ruby_sword",
      "stack_size": 1,
      "durability": 1800,
      "attack_damage": 8.0,
      "attack_speed": 1.6,
      "rarity": "rare",
      "repair_with": "minecraft:diamond",
      "tags": ["minecraft:swords"]
    }
  ]
}
```

`attack_speed` is the **final** swings-per-second number a player sees (the game stores it as a modifier on a base of 4.0, so the loader converts: 1.6 becomes -2.4). `blocks.json`, `entities.json`, `effects.json` and `hud.json` follow the same shape (a top-level list, each entry with an `id`); their fields are specified in the phase that implements them.

## Approaches considered

**A. Data-driven registration before the freeze, with mods stored in the virtual filesystem (recommended).** Imported zips are copied to a `mods/` folder in the VFS. A small loader, hooked into `Bootstrap` before the registries freeze, reads every enabled mod in a fixed order (sorted by id) and registers its content, in the client realm and in the integrated-server worker. The mod's `assets/` are served as an extra resource pack. New content is only ever **appended** after the vanilla entries, so every vanilla id stays the same. Trade-offs: a page reload is needed after importing ("Restart to apply"); the loader is one more patch into `Bootstrap`.

**B. Resource-pack-only "mods".** No registration: a "new item" is an existing item re-skinned through item models and components. No restart, safe everywhere. But no new ids, so it cannot do what was asked (real ids, new damage and speed, blocks, entities).

**C. Unfreeze the registries at import time.** Import and use immediately. Fragile: holder references, tag caches and the already running integrated server would not see the change, and ids could disagree between realms. Not worth the risk.

Recommendation: **A**. B stays available for texture-only packs through the existing resource-pack import.

## Decisions (my calls unless marked "yours")

1. **Import UI.** The Mods screen gets an **Import mod (.zip)** button (file picker) instead of a literal "open mods folder", because a browser page cannot show a real folder. A **Import folder...** option can read every `.zip` in a folder Alexander picks (Chrome's folder picker) and copy them in. Imported mods are copied into the VFS so they persist and both realms can read them.
2. **Apply on restart.** After import, enable/disable or removal, the screen shows "Restart to apply" with a reload button. (My call; Approach C is the alternative.)
3. **Validation.** On import the zip is checked (valid JSON, required fields, id format, files that `provides` promises, texture paths exist, no path escapes, size limits). A mod that fails is rejected with a readable list of problems. At startup a mod that fails to load is skipped and flagged, and the rest still load.
4. **Ids.** Everything is namespaced `<mod id>:<content id>`. Two mods with the same id: the newer version replaces the older one on import after a confirmation.
5. **Order.** Enabled mods load sorted by id; content is appended after all vanilla entries.
6. **Dependencies.** A mod whose `requires` are not met is disabled and shows which mod is missing. No automatic downloading.
7. **Display.** Imported mods appear in the existing Mods list with `pack.png`, name, version, description and the content-type tags; a Details view lists what the mod adds.
8. **Multiplayer (yours, see questions).** Because new content is appended, a vanilla server and this client still agree on every vanilla id, so joining a normal server keeps working. Mod items simply cannot exist on a server that lacks the mod. `multiplayer: false` mods show a warning when joining a server; `multiplayer: true` means the author expects the server to run the same mod.
9. **Survival.** `survival: false` content is only given by creative inventory/commands (hidden from survival recipes and loot).

## Phases

1. **Foundation + items (this is "v1").** Import/list/enable/remove UI, VFS storage, validation, the Bootstrap loader, `info.json` rules (survival, multiplayer warning, requires), `pack.png`, tags, and full **items** (damage, speed, durability, stack size, rarity, repair item, tags, optional shaped/shapeless recipe).
2. **Blocks.** Full-cube blocks with one or six textures, hardness, resistance, light, tool requirement, drop; block item generated automatically.
3. **Effects and HUD.** Effects (attribute modifiers, duration behaviour, icon). HUD elements as declarative pieces (text/icon bound to a few game values) using the existing HUD event.
4. **Entities.** Variants of an existing entity type (health, speed, damage, texture, optional `.jem` model through the new custom-model system); fully new AI is out of scope.

Each phase is its own pull request so a problem in one cannot break the others.

## Risks

- The loader runs at boot in more than one realm; a mistake could stop the game starting. Mitigation: every mod is wrapped, a failing mod is skipped, and a **safe mode switch** (one stored flag, plus a "disable all mods" button) lets Alexander recover without rebuilding.
- Custom items need item-model assets; if the client item-definition format differs from expectations in 26.2 u2 the texture may not show. Phase 1 starts with one hand-made example mod so this is seen first.
- None of this can be run in the build sandbox. It needs testing in the browser after each phase.

## What is left to the builder

Exact class layout, the `Bootstrap` hook anchor, the JSON parsing library use (Gson is already on the classpath), the recipe schema, the size limits, and the precise warning text.

## Questions for Alexander

1. Scope of the first pull request: items only (recommended), or items and blocks together?
2. When joining a server with a mod that is not marked `multiplayer: true`: warn only (recommended), or refuse to join?
3. Is "Restart to apply" (page reload) after importing OK?
4. Should Shaders or Mods go first now? (Shaders are already in pull request #15, waiting for your testing.)
