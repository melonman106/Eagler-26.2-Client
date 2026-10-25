# Eaglercraft 26.2 VBV Client

Native Eaglercraft 26.2 u2 client-side ViaBackportVisuals project, built in GitHub Actions.

## Build pipeline

1. **02 - Build Eaglercraft 26.2 u2 (VBV + Herz, no Litematica)** checks out the pinned u2 workspace, injects native client mods and the Eagler Fabric compatibility shim, compiles Java, creates the browser build, and embeds the WebMC JSMod compatibility layer.
2. **03 - Retrieve Eaglercraft 26.2 u2 (Single HTML)** retrieves a successful standalone HTML artifact from workflow 02.

The build workflows are manually triggered from the GitHub Actions tab.

## WebMC JSMod compatibility layer

The standalone HTML now includes the useful JS-mod architecture demonstrated by `AmphibianDev/webmc-jsmod`: a browser-side mod runtime, isolated converted-mod loading, optional lazy loading, and a Mods-panel integration. The reference project has demonstrated real Fabric-jar conversions including AppleSkin, Gamma Utils, Jade, JEI, Litematica, Meteor Client, Shulker Box Tooltip, and Zoomify.

The VBV client uses the converted packages as **enhancements/fallbacks**, not as replacements for the native ports. The native Eagler implementations continue to run even if a converted package is unavailable or incompatible. AppleSkin, Gamma Utils, Jade, Zoomify, and Shulker Box Tooltip are embedded and loaded after the game starts. JEI, Litematica, and Meteor Client are exposed as lazy-loadable packages so the normal HTML does not pay their large startup cost.

This does **not** pretend that a 1.21.11/WebMC mixin plan is automatically identical to the Eagler 26.2 TeaVM client. The runtime reports missing capabilities rather than silently claiming that a patch was applied. The native VBV feature remains authoritative until an actual Eagler-compatible internal hook is available.

## Eagler Fabric compatibility layer

`VBV/src/main/java/com/melonman106/vbvclient/fabriccompat/` contains a small, browser-safe API for future manual mod ports:

- `net.fabricmc.loader.api.FabricLoader`, `ModContainer`, `ModMetadata`, `Version`, and `net.fabricmc.api.ModInitializer` for basic lookup and entrypoints.
- `ModInitializer` and `FabricCompatClient.registerInitializer(...)` for manually registered mod entrypoints.
- `ClientTickEvents` and `ClientLifecycleEvents` for client callbacks.
- `KeyBinding` and `KeyBindingHelper` for logical key registration and keyboard dispatch primitives.

This is not upstream Fabric Loader/API and does not execute arbitrary Fabric JARs. Converted JS mods use the browser-side JSMod layer instead.

## Current mod coverage

Native Eagler ports already cover the major lightweight features such as AppleSkin-style food information, Gamma Utils-style gamma controls, Jade-style target information, BetterF3/debug information, Zoomify-style camera controls, Armor HUD, minimap/world-map, resource-pack management, and Shine.

The JSMod enhancement layer additionally exposes the real converted packages for:

- AppleSkin
- Gamma Utils
- Jade
- Zoomify
- Shulker Box Tooltip
- JEI (lazy)
- Litematica (lazy)
- Meteor Client (lazy)

Large packages are deliberately not embedded into the initial page load because the target device is a low-memory Chromebook.

## Repository layout

- `VBV/` — native client mod sources, Fabric compatibility shim, patch scripts, and browser-side mod helpers.
- `VBV/webmods/jsmod-loader.js` — converted Fabric JS-mod loader and native-fallback policy.
- `ModsPack/` — optional resource-pack client mods imported during the VBV patch.
- `.github/workflows/02-compile-eagler-vbv.yml` — compile, compatibility-source checks, and JSMod integration.
- `.github/workflows/03-single-html-eagler-vbv.yml` — standalone HTML retrieval.
- `.github/workflows/04-import-build-litematica-malilib.yml` — separate experimental source import/build workflow.
