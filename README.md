# Eaglercraft 26.2 VBV Client

Native Eaglercraft 26.2 u2 client-side ViaBackportVisuals project, built in GitHub Actions.

## Build pipeline

1. **02 - Build Eaglercraft 26.2 u2 (VBV + Herz, no Litematica)** checks out the pinned u2 workspace, injects native client mods and the Eagler Fabric compatibility shim, compiles Java, and creates the browser build output.
2. **03 - Package Eaglercraft 26.2 u2 (Single HTML)** packages a successful build from workflow 02 into the standalone HTML output.

The build workflows are manually triggered from the GitHub Actions tab.

## Eagler Fabric compatibility layer

`VBV/src/main/java/com/melonman106/vbvclient/fabriccompat/` contains a small, browser-safe API for future manual mod ports:

- `net.fabricmc.loader.api.FabricLoader`, `ModContainer`, `ModMetadata`, `Version`, and `net.fabricmc.api.ModInitializer` for basic lookup and entrypoints.
- `ModInitializer` and `FabricCompatClient.registerInitializer(...)` for manually registered mod entrypoints.
- `ClientTickEvents` and `ClientLifecycleEvents` for client callbacks.
- `KeyBinding` and `KeyBindingHelper` for logical key registration and keyboard dispatch primitives.

This is not upstream Fabric Loader/API and does not load arbitrary Fabric JARs, parse `fabric.mod.json`, resolve dependencies, apply Mixins, or transform bytecode. Each future port still needs its unsupported Minecraft/Fabric calls adapted to Eaglercraft.

## Repository layout

- `VBV/` — native client mod sources, Fabric compatibility shim, patch scripts, and web-side mod helpers.
- `ModsPack/` — optional resource-pack client mods imported during the VBV patch.
- `.github/workflows/02-compile-eagler-vbv.yml` — compile and compatibility-source checks.
- `.github/workflows/03-single-html-eagler-vbv.yml` — standalone HTML packaging.
- `.github/workflows/04-import-build-litematica-malilib.yml` — separate experimental source import/build workflow; it does not add Litematica to the browser client.
