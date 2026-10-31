# Eaglercraft 26.2 VBV Client

Native Eaglercraft 26.2 u2 client-side ViaBackportVisuals project, built in GitHub Actions.

## Build pipeline

1. **02 - Build Eaglercraft 26.2 u2 (VBV native-only)** checks out the pinned u2 workspace, injects the native VBV client mods and Eagler Fabric compatibility shim, compiles Java, creates the browser build, and produces the standalone HTML.
2. **03 - Retrieve Eaglercraft 26.2 u2 (Single HTML)** retrieves a successful standalone HTML artifact from workflow 02.

The build workflows are manually triggered from the GitHub Actions tab.

## Eaglercraft filesystem storage

The standalone HTML build now embeds a browser storage manager using the Chromium File System Access API when available, with the existing browser storage path retained as a fallback.

- **Import Existing Storage** connects an existing user-selected folder without copying its contents.
- **Choose New Storage Folder** creates the managed directory layout; **Reconnect Existing Folder** reuses a saved directory handle when the browser still grants permission.
- Managed folders include `Worlds/`, `WorldMap/`, `Resource Packs/Global/`, `Resource Packs/Servers/`, `Datapacks/Global/`, `Datapacks/Servers/`, `Server Profiles/`, `Backups/`, `Cache/`, and `Settings/`.
- The explored-chunk world map reads and writes its per-world map files through the filesystem bridge when a folder is connected. Writes are coalesced, and the previous map version is kept under `Backups/WorldMap/`.
- Server profiles store the server address, selected global and server-specific resource packs/datapacks, and per-server graphics/performance preferences.
- The storage manager reports the size of its managed files and the browser-managed storage estimate, and it warns when a filesystem write fails.

**Browser/API limits:** the browser does not expose the exact free-space value of an arbitrary selected folder. The world-map storage is connected, but Eaglercraft's actual singleplayer world-save backend has not yet been redirected to `Worlds/`. Resource packs, datapacks, and profile settings can be stored and selected per server, but automatic runtime application of those packs/settings still needs integration with the Eagler resource-pack and options systems. IndexedDB remains necessary for the saved folder handle and fallback metadata; browser permissions may need to be granted again after a restart or browser policy change.

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


## Build performance

The native Java compilation uses the Gradle Wrapper from the pinned Eaglercraft 26.2 u2 workspace.

- **Gradle Wrapper:** Builds run through `./gradlew`, so the workspace selects and downloads its pinned Gradle version. There is no need to install Gradle separately or switch this Gradle project to Maven.
- **Gradle daemon:** The compile step uses the Gradle daemon instead of forcing `--no-daemon`. This keeps the Gradle JVM available for the duration of the build process and avoids deliberately disabling daemon reuse. GitHub-hosted runners are temporary, so the daemon is not expected to remain warm between separate workflow runs.
- **Build cache and incremental compilation:** The workspace has `org.gradle.caching=true`. Gradle can reuse eligible task outputs and skip work when inputs have not changed; cache hits depend on the task being cacheable and the relevant cache entries being available.
- **Parallel execution:** Parallel Gradle execution is deliberately not enabled. The pinned workspace sets `org.gradle.parallel=false` and `org.gradle.workers.max=1`, while the TeaVM/WASM stages have substantial memory requirements. Forcing multiple workers could increase peak memory use and make builds less reliable. The current Java compile targets the `:game` project, so there is no demonstrated multi-module compile workload to gain from `--parallel`.
- **Skip unrelated tasks:** The workflow explicitly invokes only `:game:processResources` and `:game:compileJava`. It does not invoke the test or Javadoc tasks, so adding `-x test`, `-DskipTests`, or a Javadoc exclusion would not make this step faster.

The standalone TeaVM/WASM and HTML packaging stages are still required for the browser client and are not skipped by these Java-compilation optimisations.
