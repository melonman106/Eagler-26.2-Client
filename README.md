# Eaglercraft 26.2 VBV Client

A heavily modified, browser-based Eaglercraft 26.2 client featuring native Java client modifications, an expanded UI, custom storage management, resource-pack integration, and ViaBackportVisuals (VBV).

Built using the Eaglercraft 26.2 u2 workspace and compiled into a standalone HTML client using GitHub Actions.

**Repository:** [melonman106/Eagler-26.2-Client](https://github.com/melonman106/Eagler-26.2-Client)

---

## Overview

This project aims to bring a more complete modded Minecraft experience to Eaglercraft 26.2.

Instead of relying on browser-side JavaScript mod loaders or converted Fabric mods, the client implements its modifications directly in Java before compiling the game into WebAssembly using TeaVM.

The result is a standalone browser client with native modifications, custom interfaces, additional HUD elements, improved storage management, and integrated resource-pack features.

### Main Features

- Native Java-based VBV client modifications.
- Herz-style client API and mod registration.
- Custom mod management interface.
- Integrated resource-pack client mods.
- Customizable brightness and gamma.
- Zoom controls.
- AppleSkin-style food information.
- Jade-style block and entity information.
- Shulker box previews.
- Armor HUD and additional HUD elements.
- BetterF3-style debug information.
- Mouse Tweaks.
- Minimap and explored-chunk world map.
- Custom filesystem storage manager.
- Per-server profiles.
- Resource-pack and datapack organization.
- Custom first-person functionality.
- Browser-compatible standalone HTML output.

---

## Native VBV Architecture

One of the main goals of this project is to implement modifications natively rather than relying on external JavaScript mod loaders.

The client uses Java source injection to integrate the modifications directly into the Eaglercraft source before compilation.

### Native Client Modifications

| Modification | Functionality |
|---|---|
| AppleSkin | Food and saturation information |
| Gamma Utils | Configurable brightness and gamma |
| Jade | Target information and position controls |
| Zoomify | Hold and toggle zoom |
| ShulkerBoxTooltip | Shulker box previews |
| BetterF3 | Expanded debug information |
| Armor HUD | Displays equipped armor information |
| Simple HUD Enhanced | Additional HUD functionality |
| Mouse Tweaks | Improved inventory interaction |
| Worldmap | Worldmap and explored-chunk mapping |

Additional resource-pack-based client modifications are imported from the `ModsPack/` directory.

These features are registered through the native `VBVClient` system and displayed through the VBV Mods interface.

**Important:** These are native implementations and ports, not arbitrary Fabric mod JARs running inside the browser.

Shine was removed because its post-processing implementation caused rendering instability in Eaglercraft.

---

## Custom Mod Management

The client includes a native VBV Mods interface for managing integrated client modifications.

Modifications are registered during the build process and made available through the client rather than being loaded through an external JavaScript mod loader.

The project also supports configurable features and additional client-side functionality through its native mod architecture.

---

## Eaglercraft Filesystem Storage

The client includes an expanded browser storage manager.

It uses the Chromium File System Access API when available, while retaining the existing browser storage system as a fallback.

### Storage Features

- **Import Existing Storage:** Connect an existing folder without copying its contents.
- **Choose New Storage Folder:** Create a new managed storage directory.
- **Reconnect Existing Folder:** Reconnect a previously selected folder when browser permissions allow.
- **Storage Information:** View managed file sizes and browser storage estimates.
- **Write Protection:** Detect filesystem write failures and report errors.
- **World Map Backups:** Preserve previous world-map versions.

### Managed Directory Structure

When a new storage folder is created, the client organizes its files into the following structure:

```text
Eaglercraft/
├── Worlds/
├── WorldMap/
├── Resource Packs/
│   ├── Global/
│   └── Servers/
├── Datapacks/
│   ├── Global/
│   └── Servers/
├── Server Profiles/
├── Backups/
│   └── WorldMap/
├── Cache/
└── Settings/
```

### World Map Storage

The explored-chunk world map uses the filesystem bridge to read and write per-world map data when a folder is connected.

Map writes are coalesced to reduce unnecessary filesystem operations.

Previous map versions are preserved under:

```text
Backups/WorldMap/
```

### Server Profiles

Server profiles allow individual servers to have their own saved configuration.

Profiles store:

- Server addresses.
- Selected global resource packs.
- Server-specific resource packs.
- Selected global datapacks.
- Server-specific datapacks.
- Graphics preferences.
- Performance preferences.

This provides the foundation for keeping different server configurations separate.

### Storage Limitations

Some browser restrictions still apply:

- Browsers do not expose the exact remaining free space of an arbitrary selected folder.
- Folder permissions may need to be granted again after restarting the browser.
- IndexedDB is still required for saved folder handles and fallback metadata.
- Actual singleplayer world saves have not yet been redirected to the managed `Worlds/` directory.
- Automatic application of stored resource packs, datapacks, and server settings still requires integration with the corresponding Eaglercraft runtime systems.

---

## Resource-Pack Compatibility

The build system integrates resource-pack assets into the client while protecting important Eaglercraft resources.

Special handling is used for font resources because incompatible font overrides can cause characters to render as unknown-glyph rectangles.

The build preserves the core Minecraft font atlas while supporting additional font assets and custom glyph providers.

### Included Resource Assets

- AppleSkin texture atlas.
- Embedded resource-pack client modifications.
- Custom font resources.
- Optional custom panorama overlay.
- Six panorama faces when available.

The optional panorama overlay is located at:

```text
VBV/panorama/panorama_overlay.png
```

Resource-pack client modifications are imported from `ModsPack/` during the build.

---

## Herz Client API

The project includes a lightweight Herz-style client API integrated with VBV.

It provides an organized registration system for client-side modifications.

Core components include:

- `HerzMod`
- `HerzModRegistry`
- `HerzClient`
- `HerzVBVBridge`

The API handles mod registration and connects the client initialization process with the native VBV system.

This implementation is designed for the Eaglercraft 26.2 environment rather than relying on an incompatible upstream implementation.

---

## Eagler Fabric Compatibility Layer

The project includes a lightweight Fabric compatibility layer for manually porting client-side modifications.

Location:

```text
VBV/src/main/java/com/melonman106/vbvclient/fabriccompat/
```

It provides browser-safe implementations and compatibility interfaces for selected Fabric APIs.

### Supported Components

- `FabricLoader`
- `ModContainer`
- `ModMetadata`
- `Version`
- `ModInitializer`
- `ClientTickEvents`
- `ClientLifecycleEvents`
- `KeyBinding`
- `KeyBindingHelper`

Additional compatibility hooks include:

- Screen events.
- HUD rendering callbacks.
- Resource reload events.
- Client lifecycle integration.
- Keyboard input registration.

**This is not the official Fabric Loader or Fabric API.**

It does not load arbitrary Fabric mod JARs. It is a compatibility layer intended to help manually port selected functionality into the native Eaglercraft environment.

---

## Experimental Litematica Support

The repository also contains experimental work toward bringing Litematica functionality to Eaglercraft.

The separate preparation workflow imports upstream Litematica and MaLiLib source snapshots and generates a compatibility audit.

The current browser-compatible first-stage implementation focuses on:

- Importing `.litematic` files.
- Reading schematic data.
- Displaying schematic regions.
- Showing dimensions.
- Displaying palette information.
- Displaying block counts.

Full Litematica functionality is not yet implemented.

Remaining work includes blockstate-property decoding, ghost-schematic rendering, placement and selection tools, and additional configuration features.

The upstream Fabric JARs are reference builds only and are not injected into the browser client.

---

## Building the Client

The project uses GitHub Actions to compile and package the client.

You do not need to install Java, Gradle, or Node.js locally to run the repository's GitHub Actions workflows.

### Build Workflow

The main build process uses two workflows:

| Workflow | Purpose |
|---|---|
| `02 - Build Eaglercraft 26.2 u2 (VBV + Herz)` | Builds the native VBV + Herz client |
| `03 - Retrieve Eaglercraft 26.2 u2 (Single HTML)` | Retrieves and verifies the standalone HTML |

Both workflows are manually triggered.

### Step 1: Compile the Client

1. Open the repository's **Actions** tab.
2. Select `02 - Build Eaglercraft 26.2 u2 (VBV + Herz)`.
3. Select **Run workflow**.
4. Wait for the build to finish successfully.

The workflow performs the following operations:

1. Checks out the VBV project.
2. Retrieves the pinned Eaglercraft 26.2 u2 workspace.
3. Sets up Java 25 and Node.js 24.
4. Restores and verifies the resource overlay.
5. Installs image-processing dependencies.
6. Injects VBV and Herz client modifications.
7. Imports and verifies resource-pack client modifications.
8. Compiles the modified Java sources.
9. Builds the WebAssembly client.
10. Packages the standalone HTML.
11. Verifies the generated client.
12. Uploads the build artifacts.

### Step 2: Retrieve the Standalone HTML

After workflow 02 completes successfully:

1. Open the Actions tab.
2. Select `03 - Retrieve Eaglercraft 26.2 u2 (Single HTML)`.
3. Select **Run workflow**.
4. Optionally provide the successful workflow 02 run ID.
5. Leave the run ID empty to use the newest successful build.
6. Download the generated artifact.

The resulting standalone client is:

```text
eaglercraft-26.2-vbv-herz.html
```

### Build Artifacts

The main compilation workflow produces:

| Artifact | Description |
|---|---|
| Single HTML | Standalone browser client |
| COI Website | Cross-origin-isolated website bundle |
| Web Build | Raw browser website output |

Artifacts are retained for 14 days.

---

## Build Technology

| Technology | Purpose |
|---|---|
| Java 25 | Client compilation |
| Gradle Wrapper | Build management |
| Node.js 24 | WebAssembly and HTML packaging |
| TeaVM | Java-to-WebAssembly compilation |
| WebAssembly | Browser execution |
| Python 3 | Build scripts and asset processing |
| Pillow | Image processing |
| FFmpeg | Image asset normalization |
| GitHub Actions | Automated builds |

The project uses a pinned Eaglercraft 26.2 u2 workspace commit:

```text
f0dafd1e3c1fb39006735ffc0bc6fd879fa98de3
```

The Gradle Wrapper is used instead of installing or managing a separate Gradle version.

Build caching is enabled where supported, and Java compilation is kept separate from the memory-intensive WebAssembly packaging stages.

---

## Repository Structure

```text
Eagler-26.2-Client/
│
├── .github/
│   └── workflows/
│       ├── 02-compile-eagler-vbv.yml
│       ├── 03-single-html-eagler-vbv.yml
│       └── 04-import-build-litematica-malilib.yml
│
├── VBV/
│   ├── src/
│   │   └── main/
│   │       └── java/
│   │
│   ├── patch/
│   │   ├── apply_vbv_patch.py
│   │   ├── storage_manager.js
│   │   └── ...
│   │
│   └── panorama/
│
├── ModsPack/
│
└── README.md
```

### Important Directories

- `VBV/` — Native client modifications, compatibility code, and patching scripts.
- `VBV/src/` — Java source files for the native client modifications.
- `VBV/patch/` — Build injection scripts and browser-side storage integration.
- `VBV/panorama/` — Optional panorama assets.
- `ModsPack/` — Resource-pack client modifications imported during compilation.
- `.github/workflows/` — Automated compilation and preparation workflows.

---

## Project Goals

The long-term goal is to expand Eaglercraft 26.2 into a more complete modded browser Minecraft experience.

Planned development areas include:

- Expanding native client modification support.
- Improving the Mods interface and configuration options.
- Expanding filesystem storage integration.
- Improving server profile management.
- Further developing minimap and world map functionality.
- Improving first-person animations and visual effects.
- Expanding compatibility with selected Minecraft and Fabric modifications.
- Continuing development of the Litematica port.
- Improving browser performance and memory management.

---

## Important Notes

This is an experimental community-developed Eaglercraft modification project.

Not every feature is guaranteed to work identically to its original Java Edition modification.

Browser security restrictions, WebAssembly limitations, memory constraints, and differences in Eaglercraft's internal APIs can affect compatibility.

The project does not support installing arbitrary Minecraft Fabric `.jar` modifications directly into the browser client.

---

## Credits

- **Eaglercraft** — Original browser-based Minecraft implementation.
- **Minecraft** — Original game and assets.
- **ViaBackportVisuals (VBV)** — Visual and client modification project.
- **Herz** — Inspiration for the client-side mod API architecture.
- **Fabric** — API compatibility references.
- **TeaVM** — Java-to-WebAssembly compilation.
- **Litematica** — Schematic functionality reference.
- **MaLiLib** — Litematica dependency and reference implementation.

Additional credit belongs to the developers of the integrated client modifications and resource packs.

---

**Developed and maintained by [melonman106](https://github.com/melonman106).**
