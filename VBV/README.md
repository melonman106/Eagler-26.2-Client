# ViaBackportVisuals 26.2 Client Side

Native Eaglercraft 26.2 u2 client-side mod suite with a Herz-style adapter and a small Eagler-native Fabric compatibility API.

This project does **not** embed upstream Fabric Loader, Fabric API, or Mixin. The compatibility package provides selected familiar interfaces so features can be ported incrementally without requiring the full Fabric runtime.

## Eagler Fabric compatibility layer

Sources are under `src/main/java/com/melonman106/vbvclient/fabriccompat/` and are copied into the generated Eagler game source by the existing recursive Java-source injection step.

Initial APIs:

- `net.fabricmc.loader.api.FabricLoader.getInstance()`: environment type, registered mod lookup, `isModLoaded`, `getModContainer`, and `getAllMods`.
- `net.fabricmc.api.ModInitializer` and `net.fabricmc.api.EnvType`, plus a minimal metadata/version view under `net.fabricmc.loader.api`.
- `ModMetadata` and `ModContainer`: basic metadata access.
- `ModInitializer`: familiar initializer contract for manually integrated ports.
- `ClientTickEvents.START_CLIENT_TICK / END_CLIENT_TICK`: client tick callbacks, dispatched through the existing client lifecycle.
- `ClientLifecycleEvents.CLIENT_STARTED / CLIENT_STOPPING`: client startup/shutdown callbacks.
- `KeyBinding` and `KeyBindingHelper`: logical key registration and key press/release dispatch primitives.

Existing native VBV mod metadata is exposed through the compatibility registry. The layer is intentionally browser-safe and has no dependency on Fabric Loader JARs or Mixin.

## Compatibility limits

This is a **porting shim, not a drop-in Fabric runtime**. It does not load mod JARs, parse `fabric.mod.json`, resolve dependencies, apply Mixins, transform bytecode, or implement the full Fabric API. Keybindings must be connected to an actual browser keyboard hook before they can receive input. APIs that rely on unsupported Minecraft internals must still be adapted manually.

The intended workflow is to port one small mod feature at a time, add only the API surface it needs, and verify it through the Eaglercraft 26.2 u2 GitHub Actions compile.

## Existing client features

- Native mod viewer and mod details screens, available from the main menu and the in-game Escape menu.
- `V` opens the Mods screen; `M` opens the explored-chunk world map.
- Uku's Armor HUD, Simple HUD Enhanced, AppleSkin-style hunger information, and other native client features.
- Embedded resource-pack assets are copied into Minecraft's actual `assets/<namespace>/...` resource tree.
- Client-only explored-chunk world map and waypoints.
- GitHub Actions compile and single-HTML packaging pipeline.

## Intentionally not included

- Full upstream Fabric Loader or Fabric API.
- Mixin/bytecode transformation.
- Automatic discovery or loading of third-party Fabric JARs.
- Fabric server-side APIs.
- 26.3 texture and blockstate recreation.
- Better Clouds or Low Fire.

The client project preserves normal 26.2 textures rather than replacing them with 26.3 placeholders.
