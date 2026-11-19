# Eaglercraft 26.2 VBV Client

A heavily modified, browser-based Eaglercraft 26.2 client featuring native Java client modifications, an expanded UI, custom storage management, resource-pack integration, and ViaBackportVisuals (VBV).

Built using the Eaglercraft 26.2 u2 workspace and compiled into a standalone HTML client using GitHub Actions.

**Repository:** [melonman106/Eagler-26.2-Client](https://github.com/melonman106/Eagler-26.2-Client)

---

## Main Menu
<img width="1365" height="767" alt="MainMenu" src="https://github.com/user-attachments/assets/2ac051aa-24b9-4352-9fa7-1d98ca26037d" />

## Overview

This project aims to bring a more complete modded Minecraft experience to Eaglercraft 26.2.

Instead of relying on browser-side JavaScript mod loaders or converted Fabric mods, the client implements its modifications directly in Java before compiling the game into WebAssembly using TeaVM.

The result is a standalone browser client with native modifications, custom interfaces, additional HUD elements, improved storage management, and integrated resource-pack features.

### Custom OptiFine Support
The client includes custom compatibility support for OptiFine-style Custom Entity Models (CEM), bringing custom entity rendering to Eaglercraft 26.2.
- Supports custom entity model rendering through the client's custom rendering implementation.
- Designed to bring OptiFine CEM-style model support to the browser client without requiring OptiFine itself.
- Allows compatible resource packs to customise the appearance and models of supported entities.
- Integrated into the client rather than relying on a separate OptiFine installation.
- Reads models from both `optifine/cem/` and `emf/cem/`, the folder Fresh Animations' Player Add-on uses (including its `21-2` / `21-5` version overlay folders).
- Player models, cape and the four armor layers (`player_inner_armor`, `player_outer_armor`) are replaced and animated, not just the main body.
- Animation expressions support `nbt()` (held item, equipment slots, off hand, sleeping, flying), `wraprad()` / `wrapdeg()`, `log10`, `sinh`/`cosh`/`tanh` and the frame-counter pattern packs use for smoothing.
- Variables shared between a player's body, armor, cape and elytra models are kept per entity, so the add-on's smoothing and state carry over between parts.
- Layered models follow their main model: a sheep's `wool` / `wool_undercoat` (and baby sheep) layers, saddles and similar overlays are mapped by name and copy the animated body's pose, so the fleece moves with the body instead of staying frozen over it.
- `sin` / `cos` in animation expressions use Minecraft's own lookup-table sine, because packs such as Fresh Animations detect a vanilla attack swing by comparing against it exactly; with a different sine the player's arms never moved when attacking.
- `is_swinging_right_arm` / `is_swinging_left_arm` follow EMF's meaning (swinging **or** using an item with that arm), which Fresh Animations needs to pose a blocking shield arm and item-use arms.

What is still not supported: first-person hand animations, `.properties` variant rules (random entities), emissive `_e` textures, CIT, CTM, colormaps and the other non-model OptiFine features. `nbt()` only answers the paths packs are known to use.

Note: This is a custom Eaglercraft implementation, not the original OptiFine mod. Compatibility depends on the entity model formats and features supported by the implementation.

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
- Animated menu panoramas (one entry per file in `VBV/panorama/`).
- Six panorama faces when available.

### Menu panoramas

Drop any `.mp4`, `.webm`, `.mov`, `.gif` or `.png` into:

```text
VBV/panorama/
```

On the next build every file is converted to numbered frames (5 fps, up to 1280 px wide) and shows up
in **Extras > Panoramas** under a name made from the file name (`LoadingScreenNight.mp4` becomes
"Loading Screen Night"). The choice is saved with your settings. `panorama_overlay` is the default;
the **Minecraft** card in Extras switches back to the vanilla panorama. Only a short ring of frames
is kept in memory at a time, so long clips cost build size (PNG frames) rather than game memory.

Frame quality is a size trade-off, and it is also load time: the game downloads and unpacks every frame of
every clip when it starts. At 10 fps and 1280 px the four bundled clips came to about 152 MB of a 178 MB
assets pack. The frames are now stored at 5 fps and the game fades the next frame in over the current one
(on the bundled clips the blended frame is within 34-42 dB PSNR of the frame that was dropped, so slow camera
moves still look smooth); `panorama_overlay` keeps 1280 px, the other clips use 1024 px. The settings live in
the `env:` block at the top of `.github/workflows/02-compile-eagler-vbv.yml`:

| Setting | Default | Effect |
| --- | --- | --- |
| `VBV_PANORAMA_WIDTH` | `1280` | Width of the default panorama (`1920` is sharper on a 1080p screen, ~1.7x the size) |
| `VBV_PANORAMA_EXTRA_WIDTH` | `1024` | Width of every other clip |
| `VBV_PANORAMA_FPS` | `5` | Stored frames per second (the game blends between them) |
| `VBV_PANORAMA_DITHER` | `sierra2` | `sierra2`, `sierra2_4a`, `floyd_steinberg`, `bayer` or `none` |

The fastest start is to keep only the clips you actually use in `VBV/panorama/`.

## Main menu (2.0.0)

The title screen is a compact, left-aligned list of wide-spaced capitals (PLAY, SINGLEPLAYER, MODS,
OPTIONS) over the panorama, with **EXTRAS** in the bottom-right corner. The selected row gets a light
highlight bar with a slanted edge; hover with the mouse or use the up/down keys and Enter. No version
or credit text is drawn at the bottom of the screen. The wordmark, rows and text are sized from the GUI
height (row pitch about 5.6% of the screen height, label capitals about 1.9%) so the menu looks the same
proportion at any window size or GUI scale (a lower GUI scale just makes the text sharper).

- After the game finishes loading you land on a "press any button to begin" screen over the panorama
  (once per launch). The bottom-left corner shows how long the resource reload took.
- **Loading screen**: while resources reload, the red Mojang screen is replaced by a dark VBV screen
  with the wordmark, a segmented progress bar with a sweeping light (so you can tell the game is
  still working when the percentage sits still), the name of the loading step that is running, the
  step count, elapsed time and a rotating tip. It is drawn with a few flat rectangles per frame so
  it stays cheap on low-power machines. The page that appears before the game starts (the browser
  page and WebAssembly download) is not part of it.
- **Extras** has a sidebar: Panoramas, Hero (skin and name), Party, Storage and Appearance
  (accent colour, hand position).
- **Options > Resource Packs** now has a **Modrinth...** button that opens the Modrinth pack browser.
  Resource packs are no longer listed in the Mods menu; they still work exactly as before.

Resource-pack client modifications are imported from `ModsPack/` during the build.

---

## HUD, diagnostics and performance controls

- **HUD Editor** (Mods screen) cycles through the overlays that can be dragged: the Jade target
tooltip, the Simple HUD information block and the FPS meter. Each one is shown at its real size and
position, the offset is saved on mouse release, and **Reset position** puts the current overlay back
where its anchor puts it. The minimap is not part of the editor; its own config page no longer links
there either.
- **Options > Modded Settings > Diagnostics...** prints the frame breakdown, what the resource packs
supplied (models found, baked rigs, layer followers, remembered poses), the LOD mesh and sample
counts, and the heap state. **Copy** puts the same text on the clipboard.
- **Auto Quality** lowers the render distance one step while the smoothed FPS stays under 42, falls
back to LOD when the render distance is at its floor, and walks back up to where it started once the
frame rate recovers. Changes are at least eight seconds apart and saved like any other setting.
- **LOD** remembers its sampled terrain in browser storage, so far terrain is drawn immediately on
the next visit instead of filling in as you explore. The saved set is capped at 8192 chunks around
the player (under a megabyte). The three levels reach 32, 64 and 128 chunks, and the first ring
always starts two chunks inside the real render distance, so the far terrain stacks with any render
distance you pick - including 30. The mesh has a quad budget that follows the frame rate: below
about 45 FPS it drops to 70k quads (and below 30 FPS to 40k) and stops rebuilding until the camera
has moved 96 blocks, so the far terrain costs least when the machine can least afford it.
- **Entity Culling** also drops anything that is entirely behind the camera, before the block ray
walk, which catches the near range the block test skips.
- **Custom models** refresh far rigs every second or third frame and put the last pose back on the
frames in between, so distant animated mobs cost less without snapping to the vanilla pose.
- **FPS meter** adds the chunk build / upload / terrain draw phases, the LOD mesh size and rebuild
time, the auto-quality line and a low-memory warning.
- **Shaders** draw a cartoon look: a toon brightness band, strong vibrance, a small contrast curve,
warmth and a vignette, with an optional four-tap outline at High quality. The old nine-tap bloom is
gone, which was most of the cost of the pass. An FPS guard switches the preset off after four
seconds below 40 FPS instead of letting it cost frames; picking a preset again re-arms the guard.
- **Free camera** (default **B**, rebindable in Modded Settings > Keybinds) detaches the camera and
flies it with the normal movement keys (sprint for 4x speed, jump/sneak for up/down). The player
stands still where the camera left: movement input is cleared, the mouse turns the camera rather
than the player, and attacking, using items or blocks and mining are all suppressed, so nothing is
placed, hit or opened. Turning it off (or leaving the world) puts the camera straight back on the
player, who never moved.
- **BetterF3** (F3) is a native port of the mod's layout: gold section titles, dim `Label:` with a
bright value, a left/right split, and sections for the player, the world, the targeted block or
entity, the system, rendering (with the frame breakdown, LOD and shader state) and the free camera.
Each section can be switched off in Mods > BetterF3; the compact layout tightens the spacing.
- **Shields**: a pack written for 1.20 that ships `models/item/shield.json` parented to
`builtin/entity` has that file skipped at build time. The parent was removed in this version, and its
old display transforms turn the shield the wrong way round, so the game's own special shield renderer
draws it (with the pack's textures) in the vanilla orientation. The build fails if such a file is ever
copied again.

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
