# ViaBackportVisuals 26.2 Client Side

Native Eaglercraft 26.2 u2 client-side mod suite with a Herz-style adapter and a small Eagler-native Fabric compatibility API.

This project does **not** embed upstream Fabric Loader, Fabric API, or Mixin. The compatibility package provides selected familiar interfaces so features can be ported incrementally without requiring the full Fabric runtime.

## Eagler Fabric compatibility layer

Sources are under `src/main/java/com/melonman106/vbvclient/fabriccompat/` and are copied into the generated Eagler game source by the existing recursive Java-source injection step.

Initial APIs:

- `net.fabricmc.loader.api.FabricLoader.getInstance()`: environment type, registered mod lookup, `isModLoaded`, `getModContainer`, and `getAllMods`.
- `net.fabricmc.api.ModInitializer`, `ClientModInitializer`, and `EnvType`, plus a minimal metadata/version view under `net.fabricmc.loader.api`.
- `ModMetadata` and `ModContainer`: basic metadata access.
- `ModInitializer`: familiar initializer contract for manually integrated ports.
- `net.fabricmc.fabric.api.event.Event` and `EventFactory.createArrayBacked(...)`: listener registration, invoker generation, and listener counts for the common Fabric event pattern.
- `ClientTickEvents.START_CLIENT_TICK / END_CLIENT_TICK`: client tick callbacks backed by the shared event factory. The explored world map now records player-chunk visits through `END_CLIENT_TICK`.
- `ClientLifecycleEvents.CLIENT_STARTED / CLIENT_STOPPING`: client startup/shutdown callbacks backed by the shared event factory.
- `ClientResourceEvents.RELOAD`: callback after Minecraft successfully reloads client resource packs.
- `HudRenderCallback.EVENT`: shared post-vanilla HUD rendering callback with the live Eagler render context and tick tracker. AppleSkin, Uku's Armor HUD, Simple HUD Enhanced, Gamma status, VBV Minimap, Jade, and Custom Crosshair now register through this event instead of separate per-feature HUD injections.
- `ScreenEvents`: before/after screen init and render, after screen tick, and screen removal callbacks, wired into the native `Screen` lifecycle.
- `net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper`: Fabric-shaped registration for native `KeyMapping` objects. Gamma Utils and Zoomify now use registered key mappings, with click/hold behaviour polled through `ClientTickEvents.END_CLIENT_TICK`; Zoomify also uses `ScreenEvents.AFTER_INIT` to restore normal FOV immediately when a screen opens.
- `FabricCompatClient.registerClientInitializer(id, initializer)`: manual registration of Fabric-style client entrypoints.

Existing native VBV mod metadata is exposed through the compatibility registry. The layer is intentionally browser-safe and has no dependency on Fabric Loader JARs or Mixin.

## Compatibility limits

This is a **porting shim, not a drop-in Fabric runtime**. It does not load mod JARs, parse `fabric.mod.json`, resolve dependencies, apply Mixins, transform bytecode, or implement the full Fabric API. The build patch connects registered keybindings to browser keydown/keyup events. Mod-specific inventory behavior still needs explicit integration, and APIs that rely on unsupported Minecraft internals must be adapted manually.

The intended workflow is to port one small mod feature at a time, add only the API surface it needs, and verify it through the Eaglercraft 26.2 u2 GitHub Actions compile. Screen and HUD callbacks are connected to the native render lifecycle; resource reload callbacks fire only after a successful resource-pack reload.

## Existing client features

- Native mod viewer and mod details screens, available from the main menu and the in-game Escape menu.
- `V` opens the Mods screen; `M` opens the explored-chunk world map.
- Uku's Armor HUD, Simple HUD Enhanced, AppleSkin-style hunger information, Gamma Utils, Zoomify, Shine lighting/bloom controls, and other native client features.
- Embedded resource-pack assets are copied into Minecraft's actual `assets/<namespace>/...` resource tree.
- Client-only explored-chunk world map and waypoints, with an Export Backup download and a visible warning if browser storage writes fail.
- GitHub Actions compile and single-HTML packaging pipeline.

## Intentionally not included

- Full upstream Fabric Loader or Fabric API.
- Mixin/bytecode transformation.
- Automatic discovery or loading of third-party Fabric JARs.
- Fabric server-side APIs.
- 26.3 texture and blockstate recreation.
- Better Clouds or Low Fire.

The client project preserves normal 26.2 textures rather than replacing them with 26.3 placeholders.


## 26.2 U2 client fixes and configuration

- The animated panorama is attached only to the custom title screen; it is not installed on the base `Screen` class, so inventory, chat, pause, HUD editor, crafting and furnace screens remain unaffected.
- The title screen no longer draws the temporary black menu panels, profile footer label, VBV/Eaglercraft footer or upstream `o_xer` credit.
- The native crosshair editor has a dedicated Mod Menu entry and now supports pattern editing, 1x–4x scale, color presets and persistent settings.
- First-person arms use the vanilla attack swing transform, receive the eating/drinking use transform, and share a small forward alignment offset with the held item.
- The minimap scans one nearby chunk per tick rather than sampling the whole area in a single frame. Its map size, scan radius, frame, player marker and north indicator are configurable. Explored-chunk saves are batched every 100 ticks and flushed when leaving a world to avoid a full-map storage write on every chunk entry.
- Jade, AppleSkin, BetterF3, Gamma, Mouse Tweaks, ShulkerBoxTooltip, Simple HUD, first-person animation and Armor HUD settings are persisted through the Eagler runtime storage API. Uku's Armor HUD screen layout is unchanged.
- Server profiles can be listed, applied and deleted. Their saved render-distance setting is applied when the matching server is joined; the profile editor supports up to 128 chunks.
- The U2 render-distance option ceiling is raised to 128. Actual server view distance and browser/GPU memory limits can still impose a lower effective distance.
- Shine now attempts its bundled screen-space bloom post-chain on the supported renderer. It disables itself after a renderer error; other lighting controls remain experimental.

## Actually 3D Stuff Eagler 26.2 U2 port

The pinned U2 source already contains the native 26.x item-definition pipeline, including `select`, `condition`, `display_context`, and cuboid item-model baking. The Actually 3D Stuff pack can therefore keep its custom item definitions and cuboid geometry; it does not need a separate replacement renderer.

The build now injects `Actually3DStuffCompat` and hooks it into the first-person held-item pose. It ports the simple whole-item transforms from the pack's Hold My Items add-ons for candles, seed items, fishing rods, carrot/warped-fungus-on-a-stick, brush, stick, string, paper/maps, name tags, writable books, armor stands, hanging items and banner patterns.

This is a partial native animation port, not a Lua interpreter. The original scripts' per-cuboid animation, fish/boat/totem/harness motion, particles, shearing controls, and stateful movement still need a model-part animation system. The pack's Java Edition GLSL shader overrides and RespackOpts `.rpo` rules are not installed: the former require a tested WebGL2 shader translation, and the latter depend on the external RespackOpts runtime.

Use the matching `Actually-3D-Stuff-Eagler-26.2-U2.zip` resource pack with a build containing `Actually3DStuffCompat`. The ZIP alone does not add client-side animation code.

## Resource-pack compatibility

Every ZIP under `ModsPack/` is imported automatically during the build, including the newer Creepers Revamped and Emissive End Portal packs. The sanitizer preserves normal textures, models, sounds and language files while removing incompatible font overrides and third-party shader/post-effect files that cannot run in the Eagler WebGL2 pipeline. Generated pack entries use the client release version `1.5.3`.

## Online party service

The client party manager uses the existing Eagler relay Worker HTTP endpoint at `/party`. The D1-backed Worker implementation and SQL schema are in the companion `melonman106/Wisp-Relay` repository branch `vbv-party-d1-2026-11-04`. Before parties can connect, deploy that Worker code, create the D1 database, run `cloudflare/schema/0001_vbv_parties.sql`, and bind the database as `VBV_PARTIES`. The client polls every five seconds; inactive members are removed after 30 seconds and a stale host causes the party to be deleted.

**Storage limitation:** server profiles and feature settings use the VBV storage bridge, but Minecraft singleplayer world saves still use the native Eagler filesystem/IndexedDB backend. The selected external folder is currently for VBV files, backups and imports; it does not yet replace the native world-save backend.
