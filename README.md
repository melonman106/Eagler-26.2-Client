# Eaglercraft 26.2 VBV Client

Native Eaglercraft 26.2 u2 client-side ViaBackportVisuals project, built in GitHub Actions.

## Build pipeline

1. **02 - Build Eaglercraft 26.2 u2 (VBV native-only)** checks out the pinned u2 workspace, injects the native VBV client mods and Eagler Fabric compatibility shim, compiles Java, creates the browser build, and produces the standalone HTML.
2. **03 - Retrieve Eaglercraft 26.2 u2 (Single HTML)** retrieves a successful standalone HTML artifact from workflow 02.

The build workflows are manually triggered from the GitHub Actions tab.

## Native VBV architecture

The client no longer uses WebMC, JSMod, converted JavaScript Fabric mods, or a browser-side JS mod loader. Client features are implemented in Java and injected into the Eaglercraft 26.2 u2 source before compilation.

The former JS-mod features have native VBV implementations:

- AppleSkin-style food/saturation information using the AppleSkin `icons.png` atlas when supplied by the embedded resource pack.
- Gamma Utils-style gamma control and configurable brightness strength.
- Jade-style target information and position controls.
- Zoomify-style hold/toggle zoom with configurable field of view.
- ShulkerBoxTooltip-style Shift-hover shulker previews.
- BetterF3/debug information.
- Armor HUD.
- Simple HUD Enhanced.
- Mouse Tweaks.
- Minimap/world map.
- Native resource-pack client mods, including MCDW.

Shine has been removed because its post-processing path caused unstable rendering in Eagler. These features are registered directly through `VBVClient` and displayed by the native VBV Mods screen. There is no browser fallback that silently loads a converted JavaScript package.

## Resource-pack compatibility

The build keeps the embedded resource-pack assets while protecting Eagler's core font atlas from Java resource-pack font overrides that can turn every character into the unknown-glyph rectangle. AppleSkin's `assets/appleskin/textures/icons.png` is preserved. An optional `VBV/panorama/panorama_overlay.png` is also embedded alongside the six panorama faces when present.

## Eagler Fabric compatibility layer

`VBV/src/main/java/com/melonman106/vbvclient/fabriccompat/` contains a small, browser-safe API for future manual native mod ports:

- `net.fabricmc.loader.api.FabricLoader`, `ModContainer`, `ModMetadata`, `Version`, and `net.fabricmc.api.ModInitializer` for basic lookup and entrypoints.
- `ModInitializer` and `FabricCompatClient.registerInitializer(...)` for manually registered mod entrypoints.
- `ClientTickEvents` and `ClientLifecycleEvents` for client callbacks.
- `KeyBinding` and `KeyBindingHelper` for logical key registration and keyboard dispatch primitives.

This is not upstream Fabric Loader/API and does not execute arbitrary Fabric JARs. Native VBV ports use the compatibility API only where it is useful for reproducing a specific client feature.

## Repository layout

- `VBV/` — native client mod sources, Fabric compatibility shim, patch scripts, and resource-pack client mods.
- `ModsPack/` — optional resource-pack client mods imported during the VBV patch.
- `.github/workflows/02-compile-eagler-vbv.yml` — manual native-only compile workflow.
- `.github/workflows/03-single-html-eagler-vbv.yml` — standalone HTML retrieval.
- `.github/workflows/04-import-build-litematica-malilib.yml` — separate experimental source import/build workflow.
