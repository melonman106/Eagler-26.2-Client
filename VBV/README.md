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
- `net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper`: Fabric-shaped registration for native `KeyMapping` objects.
- `FabricCompatClient.registerClientInitializer(id, initializer)`: manual registration of Fabric-style client entrypoints.

Existing native VBV mod metadata is exposed through the compatibility registry. The layer is intentionally browser-safe and has no dependency on Fabric Loader JARs or Mixin.

## Compatibility limits

This is a **porting shim, not a drop-in Fabric runtime**. It does not load mod JARs, parse `fabric.mod.json`, resolve dependencies, apply Mixins, transform bytecode, or implement the full Fabric API. The build patch connects registered keybindings to browser keydown/keyup events. Mod-specific inventory behavior still needs explicit integration, and APIs that rely on unsupported Minecraft internals must be adapted manually.

The intended workflow is to port one small mod feature at a time, add only the API surface it needs, and verify it through the Eaglercraft 26.2 u2 GitHub Actions compile. Screen and HUD callbacks are connected to the native render lifecycle; resource reload callbacks fire only after a successful resource-pack reload.

## Existing client features

- Native mod viewer and mod details screens, available from the main menu and the in-game Escape menu.
- `V` opens the Mods screen; `M` opens the explored-chunk world map.
- Uku's Armor HUD, Simple HUD Enhanced, AppleSkin-style hunger information, and other native client features.
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
