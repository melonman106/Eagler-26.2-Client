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

What is still not supported: first-person hand animations from OptiFine-style packs (Punchy packs are handled separately, below), `.properties` variant rules (random entities), emissive `_e` textures, CIT, CTM, colormaps and the other non-model OptiFine features. `nbt()` only answers the paths packs are known to use.

Note: This is a custom Eaglercraft implementation, not the original OptiFine mod. Compatibility depends on the entity model formats and features supported by the implementation.

### Punchy resource packs
The client can play the first-person hand animations of **Punchy** resource packs such as HyperPunchy. Switch it on under **Options > Modded Settings > Hand Position...**; while it is on and a pack is loaded it replaces the client's own hand animations.
- Reads the pack's `punchy/animations`, `punchy/compat` and (optionally) `punchy/geo/player.geo.json`, and plays the right clip for attacking, mining, eating, drawing a bow, blocking and equipping items.
- It has not been run in the game yet: the maths and rules are covered by plain-JVM tests, but how the arm and held item sit still needs checking in the game (the Hand Position rows nudge the item). Mining is detected approximately, and the `interaction` / `inspect` rules and the pack's camera shake are not used yet.
- See [`docs/punchy.md`](docs/punchy.md) for the files it reads, what triggers what and the known limits.

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
- Potion HUD with effect icons, amplifiers and remaining time; while it is enabled it replaces the vanilla effect icons.
- Inventory Sorter for the 27 backpack slots, or the open chest, barrel, ender chest or shulker box; partial stacks of the same item are merged into full stacks (normal pick-up clicks, hotbar untouched).
- Screenshot Manager: F2 captures are written into the storage folder's `Screenshots/` directory.
- Chat Heads: each player message shows the sender's head in the panel's left margin. Only the messages that get a face move right, and only by their own lines; the chat panel, its background, the scrollbar and messages without a sender stay exactly where vanilla draws them. The same faces are listed in the Tab player list, which vanilla only fills while an online-mode server is connected.
- 3D Skin Layers: every drawn pixel of the jacket, sleeves, pants and hat is extruded into a real 1–3 px voxel cube, instead of only inflating the whole layer outward. Cut-outs, seams and sleeve ends get their own side walls, and transparent pixels have no cube at all. A skin that has not been read yet falls back to a plain shell.
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
| Potion HUD | Active effect icons, amplifier and remaining time (replaces the vanilla icons while enabled) |
| Inventory Sorter | Orders the 27 backpack slots or the open storage block and merges partial stacks (R by default) |
| Screenshot Manager | Saves screenshots into the storage folder and lists them |
| Chat Heads | Draws the sender's skin head beside each player chat line (only that message is indented) and in the Tab player list |
| 3D Skin Layers | Extrudes every drawn pixel of the jacket, sleeves, pants and hat into a voxel cube |

Additional resource-pack-based client modifications are imported from the `ModsPack/` directory.

These features are registered through the native `VBVClient` system and displayed through the VBV Mods interface.

**Important:** These are native implementations and ports, not arbitrary Fabric mod JARs running inside the browser.

Shine was removed because its post-processing implementation caused rendering instability in Eaglercraft.

---

## Custom Mod Management

The client includes a native VBV Mods interface for managing integrated client modifications.

Modifications are registered during the build process and made available through the client rather than being loaded through an external JavaScript mod loader.

The project also supports configurable features and additional client-side functionality through its native mod architecture.

### Importable mods (.zip)

Mods can also be imported at runtime from **Mods → Imported Mods... → Import .zip**. An importable mod is a `.zip` with an
`info.json` (or a Fabric-style `fabric.mod.json`), an `items.json`, a `pack.png` and an `assets/` folder; it contains no code.
Mods that depend on Fabric Loader or Fabric API load because the client reports those as present (faked), while the
parts of Fabric API a data mod can really use — item settings, vanilla creative tabs, resource loading and item
registration — are backed by real code. This first version adds **items only**. See
[`docs/importable-mods.md`](docs/importable-mods.md) for the format, the Fabric table, safe mode (`?nomods=1`, `?safe`) and the
limits, and `VBV/examples/` for two example mods. It has not been run in the game yet.

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
├── Screenshots/
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
When a stored panorama is selected, the menu draws that frame and skips the 3D camera panorama that
used to sit underneath it; and if the menu still renders below about eight FPS with Motion on, the
overlay holds its current frame for the rest of the session so it is not also paying a decode,
a texture upload and a second full-screen blend (the saved Motion choice is unchanged).

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
- The top-right corner has a **notification bell**. It is only part of the main menu: the first time
  the menu appears it asks the public GitHub Releases API for
  `melonman106/Eagler-26.2-Client`, and when a newer release exists the bell shows an unread badge.
  Clicking the bell opens the notification list (client updates, saved screenshots and Modrinth
  updates); an announcement that was already shown is remembered, so it is not repeated every
  launch. Each row has a small **X** on its right edge that dismisses that notification. Hovering the
  bell shows its tooltip. The panel is drawn above the menu and closes when you click outside it.
- The launch countdown and the early-load page that prints "Unpacking assets..." use the same dark
  VBV screen as the in-game loader: wordmark, accent rule, segmented bar with a travelling highlight,
  the percentage on the right and the running step on the left, so a cold start is one continuous
  screen instead of two white pages followed by the real one.
- The world-loading screens use the same look as the menu: while the integrated server starts
  ("Starting up integrated server", "Preparing spawn area") and while the single-thread path builds
  terrain, you get the dark backdrop, the VBV wordmark and the segmented bar instead of the tiled
  dirt screen and the thin green vanilla bar. The highlight keeps travelling along the bar and the
  elapsed timer keeps counting while a step runs, so a slow world never looks frozen. The first
  worldgen bursts are also shorter (48 ms instead of 128 ms), so the page repaints more often while
  the spawn area is generated.
- The game opens on the main menu. The profile/skin screen that upstream puts in front of the title
  screen is skipped at start-up (it is still one click away on the title screen and in Extras > Hero).
- **Loading screen**: while resources reload, the red Mojang screen is replaced by a dark VBV screen
  with the wordmark, a segmented progress bar with a sweeping light (so you can tell the game is
  still working when the percentage sits still), the name of the loading step that is running, the
  step count, elapsed time and a rotating tip. It is drawn with a few flat rectangles per frame so
  it stays cheap on low-power machines. The asset pack is one compressed stream that used to be
  unpacked in a single blocking call, which froze the tab for seconds on a slow machine; it is now
  unpacked in 24 ms slices (only after the first 1.5 s, so fast machines pay nothing) and the bar
  moves through it, with the asset count and unpack time written to the log. The page that appears before the game starts (the browser
  page and WebAssembly download) is not part of it.
- **Extras** has a sidebar: Panoramas, Hero (skin and name), Friends, Party, Storage and Appearance
  (accent colour, hand position).
- **Options > Resource Packs** now has a **Modrinth** icon in the top-right corner (the Modrinth mark from
  `VBV/PackIcons/modrinth.png`) that opens the Modrinth pack browser. The footer keeps only
  *Import Pack...* and *Done*. Resource packs are no longer listed in the Mods menu; they still work
  exactly as before. Installs also fetch the pack's **required dependency packs** (up to eight, three
  levels deep) and queue them behind it, so a pack that depends on another one works on the first
  try. The browser's **UPDATES** button compares every pack it installed with the newest Modrinth
  version and offers a one-click update; found updates are also added to the main-menu notification
  centre.

Resource-pack client modifications are imported from `ModsPack/` during the build.

---

## Friends (Cloudflare Worker + D1)

Extras > Friends opens a friends list: add players by name, accept or decline requests, and see who is online
and which server they are on. It is backed by a small Cloudflare Worker with a D1 (SQLite) database, in
[`cloudflare/vbv-friends`](cloudflare/vbv-friends).

- **Accounts are anonymous.** The first time, you pick a name (it defaults to your Eagler name). The browser
  creates a random account key; only a hash of it is stored. Names are first come, first served, because
  Eaglercraft has no account system to check them against. **Copy key** saves the key so the account can be
  restored in another browser with **Sign in with name + key**.
- **Presence** is a heartbeat every 30 seconds with the server address (or "Singleplayer"). **Appear: offline**
  hides you and your server from friends. Nothing else about you, and no IP address, is stored.
- Limits: 100 friends, 50 waiting requests, and rate limits on registering and sending requests.

### Deploying the Friends service (one time)

Nothing is created in your Cloudflare account until you run the workflow yourself.

1. In Cloudflare, create an API token with **Workers Scripts: Edit** and **D1: Edit** permissions, and copy your
   account id (Workers & Pages overview).
2. In this repository, Settings > Secrets and variables > Actions, add the secrets `CLOUDFLARE_API_TOKEN` and
   `CLOUDFLARE_ACCOUNT_ID`.
3. Actions > **06 - Deploy VBV Friends** > Run workflow. It runs the tests, creates the `vbv-friends` D1
   database if it does not exist, applies the migrations, deploys the Worker and checks `/health`.

The Worker is called `vbv-friends`, so it is served at `https://vbv-friends.<your-subdomain>.workers.dev`. The
client assumes the subdomain of the existing party relay (`u2471966200`); if yours differs, paste the address
shown in the workflow summary into the service address box on the Friends screen while signed out.
To run the tests locally: `cd cloudflare/vbv-friends && npm test` (Node 22; they use the real schema and also run the
browser bridge from `VBVFriends.java` against the Worker).

### Voice chat (Cloudflare Realtime)

The top of the Friends screen is a voice chat for friends: **START** opens a room, a friend who is in one gets a
**JOIN** button, and everyone in the room is listed with a dot that lights up while they talk. **MUTE** and
**LEAVE** are beside the room's title. In game, a small panel on the right edge shows whether you are muted, the
mute key (default **B**, rebindable in Options > Modded Settings > Keybinds) and who is speaking.

- **Who can join:** only friends of someone in the room, up to 8 people. Starting or joining needs a Friends
  account and microphone permission, and the page has to be served over `https://` (browsers refuse the
  microphone otherwise).
- **How it works:** the browser sends your microphone to the Cloudflare Realtime SFU and plays everyone else's
  from it; the audio does not pass through the Worker. The Worker only creates the sessions (it holds the Realtime
  app secret, so the secret is never in the game) and keeps the room list in D1. A call ends when you leave, close
  the page (the browser tells the service right away), or the service stops answering for about 15 seconds; if a
  browser simply vanishes, the room drops it after about 45 seconds.
- **Not included:** positional (in-world) audio, push-to-talk, per-person volume.
- **School and other restricted networks** often block direct WebRTC. Without a TURN key the call may fail with a
  message saying so; adding one (below) is the fix. Voice also needs the microphone to be allowed for the site,
  which a managed device can block.

Setup (once, after the Friends service is deployed), in the Cloudflare dashboard:

1. Realtime > SFU > **Create application**. Copy its App ID and App Secret.
2. Optional but recommended: Realtime > TURN > **Create key**. Copy its Key ID and API Token.
3. Add repository secrets `CF_REALTIME_APP_ID` and `CF_REALTIME_APP_SECRET` (and `TURN_KEY_ID` and
   `TURN_KEY_API_TOKEN`), then run **06 - Deploy VBV Friends** again. The workflow summary says whether voice and
   TURN are ready; until then the voice buttons report that the service is not set up.

The Worker's voice routes and the browser bridge in `VBVVoice.java` are tested together
(`cloudflare/vbv-friends/test/voice.test.mjs`, `voice-bridge.test.mjs`) with a simulated Cloudflare SFU and a
simulated browser (WebRTC, microphone and audio output are fakes). Real audio over a real network has not been
tried yet.

## HUD, diagnostics and performance controls

- **HUD Editor** (Mods screen) cycles through the overlays that can be dragged: the Jade target
tooltip, the Simple HUD information block, the FPS meter and the Potion HUD. Each one is shown at its real size and
position, the offset is saved on mouse release, and **Reset position** puts the current overlay back
where its anchor puts it. The minimap is not part of the editor; its own config page no longer links
there either.
- **Options > Modded Settings > Diagnostics...** prints the frame breakdown, what the resource packs
supplied (models found, baked rigs, layer followers, remembered poses), the LOD mesh and sample
counts, and the heap state. **Copy** puts the same text on the clipboard.
- **Auto Quality** lowers the render distance while the smoothed FPS stays under 42: one chunk at a
  time normally, two at a time below 25 FPS, down to a floor of 2. Below 22 FPS it first switches LOD
  off, because the far-terrain mesh can cost more than the chunks it hides. It walks the render
  distance back up to where it started once the frame rate recovers, at least eight seconds between
  changes, saved like any other setting. If the frame rate stays below 15 FPS for two windows
  (about four seconds), it applies **Low-End Mode** once instead of slowly stepping down.
- **Low-End Mode** (Modded Settings) is the one-click profile for a machine in single-digit FPS. It
  starts from the engine's web `FAST` preset and goes below it: render distance 2, simulation 3,
  clouds off, particles minimal, entity distance 0.5, biome blend 0, ambient occlusion, entity
  shadows, the vignette and the menu background blur off, LOD off, custom entity models and
  animations off, auto quality and entity culling on, world-map terrain capture off, a still menu
  backdrop and the FPS meter on. Every value is an
  ordinary setting that can be raised again afterwards; nothing here is permanent. The older
  **Performance Preset** name still applies the same profile.
- **Memory** figures come from the browser (`performance.memory`) when it exposes them, because
  `Runtime.maxMemory()` is constant-folded to a meaningless donor value inside TeaVM. Chrome gives
  this tab about 2 GB of heap on an 8 GB machine, the embedded asset pack lives in that same heap, and
  the page cannot raise the limit, so the client measures against the real number instead of a fake
  one. The watchdog sheds the far LOD cache once the heap passes 78 % or has under 256 MB free, and
  drops LOD altogether after three sheds, rather than let the tab be killed with the session in it.
  **Diagnostics** includes a heap audit (heap used, limit and free, LOD cache size, panorama frame
  count, entity counts) so it is clear which cache is worth shrinking.
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
- **BetterF3** (F3) is a native port of the mod's layout: gold section titles, dim `Label:` with a
bright value, a left/right split, and sections for the player, the world, the targeted block or
entity, the system and rendering (with the frame breakdown and LOD state).
Each section can be switched off in Mods > BetterF3; the compact layout tightens the spacing.
- **Shields**: a pack written for 1.20 that ships `models/item/shield.json` parented to
`builtin/entity` has that file skipped at build time. The parent was removed in this version, and its
old display transforms turn the shield the wrong way round, so the game's own special shield renderer
draws it (with the pack's textures) in the vanilla orientation. The build fails if such a file is ever
copied again. A pack that replaces the shield's item definition (`items/shield.json`, as Enchantment
Outlines does for its glowing overlay) also gets the game's own 180 degree shield rotation put back on
every special shield model, because without it the shield faces the player instead of the world.

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

`FabricLoader.getInstance()` also reports the faked Fabric pieces (`fabricloader`, `fabric-api` and its modules, `minecraft`, `java`) and every imported mod that loaded, so a dependency check on them succeeds. See [`docs/importable-mods.md`](docs/importable-mods.md) for what is real, partly real or faked.

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

The repository has two workflows:

| Workflow | Purpose |
|---|---|
| `02 - Compile HTML (Eaglercraft 26.2 VBV + Herz)` | Builds the native VBV + Herz browser client, then boots it headlessly |
| `03 - Compile Signed Web Bundle (.swbn)` | Packages the browser build from workflow 02 as a signed Chrome Isolated Web App |

Both can be started by hand. Workflow 02 builds the branch it is started from (select
`Dev-Beta` instead of `main` for development builds). Workflow 03 starts automatically
after a successful 02 run, or can be run manually against the latest successful 02 build
on the selected branch (or a specific build run ID).

### Step 1: Compile the Client

1. Open the repository's **Actions** tab.
2. Select `02 - Compile HTML (Eaglercraft 26.2 VBV + Herz)`.
3. Select **Run workflow**.
4. Type the **Client version** the build should report (for example `2.5.0`; default `2.0.0`).
   It is stamped into `VBVClient.VERSION`, shown on the boot and loading screens, in the Mods list
   and in F3, and it is the version the GitHub release check compares against, so a release tagged
   `2.5.0` is not offered as an update to a `2.5.0` build.
5. Wait for the build to finish successfully.

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

When workflow 02 finishes, open its run page and download the
`Eaglercraft-26.2-VBV-Herz-Single-HTML-<run id>` artifact. The standalone client
inside it is:

```text
eaglercraft-26.2-vbv-herz.html
```

### Step 3: Compile the Signed Web Bundle

Workflow `03 - Compile Signed Web Bundle (.swbn)` packages the browser build from a
successful workflow 02 run. It starts automatically when 02 completes; to package an
older build, run it manually and optionally pass that build's run ID. It produces:

```text
eaglercraft-26.2-vbv-herz.swbn
eaglercraft-26.2-vbv-herz-swbn.txt
```

Download `Eaglercraft-26.2-VBV-Herz-SWB-<run id>` and install the `.swbn` from
`chrome://web-app-internals` (with the Isolated Web App developer mode flag enabled)
using **Install IWA from Signed Web Bundle**. The bundle contains its own web app
manifest at `/.well-known/manifest.webmanifest` plus 192, 512 and maskable 512 icons
generated from `VBV/pack.png`.

The Web Bundle ID is derived from the signing key, so the app identity is stable only
if the key is. Add a repository secret named `IWA_SIGNING_KEY` holding an Ed25519
private key in PEM form (or the base64 of that PEM). To keep the key encrypted at
rest, also add `IWA_SIGNING_KEY_PASSPHRASE`:

```bash
openssl genpkey -algorithm Ed25519 -out private_key.pem
openssl pkcs8 -in private_key.pem -topk8 -out encrypted_key.pem
```

Without the secret each run signs with a fresh key and gets a new Web Bundle ID (so
it installs as a new app). The workflow summary prints the ID and `isolated-app://`
origin used for that run.

Chrome applies its Isolated Web App content security policy to the bundle, which is
stricter than a normal page (inline scripts are blocked and `blob:` workers are not
allowed). The `.swbn` is packaged for IWA testing; the website and standalone HTML
remain the supported clients until the shell is adapted to that policy.

### Development branch (Dev-Beta)

`Dev-Beta` is for client work that has not been through a full in-game pass yet;
`main` stays the release branch. The build workflow checks out whichever branch
it is started from, so pick `Dev-Beta` in the run dialog.

**Headless boot check.** After the compile job, a `smoke` job serves the built
HTML with the cross-origin isolation headers, opens it in headless Chromium
(SwiftShader, so no GPU is needed) and waits for `window.__loaded`. It uploads
`smoke-report.txt` and `smoke.png`; a client that never reaches the loading
screen transition fails with the last screen text in the log. It is slow and it
cannot judge how the game looks.

**Safe mode and the crash journal.** The runtime console writes the current boot
stage and the last errors to `localStorage` every five seconds. If a launch never
finishes loading, the next one says so and offers **Reload in safe mode**;
`?safe` can also be added to the address by hand. Safe mode turns custom entity
models and their animations, LOD, world-map capture and the moving panorama off,
keeps entity culling on, and refuses to save settings for that session,
so a rescue cannot overwrite a working configuration. **Save report** downloads
the console text.

**Hardware-aware first run.** On the first launch the client reads
`navigator.deviceMemory` and `hardwareConcurrency` once. At 4 GB or less of
reported memory, or 4 cores or fewer (an N100 reports 4), it starts with LOD Low,
cheaper custom-model animations, terrain capture off and the automatic quality
governor on. Every value is an ordinary setting, and the choice is never made
again after the first run.

**Pack provenance.** The build merges every `ModsPack/` archive and writes a short
index of what each pack contributed and which item, model, CEM and font paths
more than one pack supplies. It is embedded in the standalone HTML and printed by
**Options > Modded Settings > Diagnostics...**, so "which pack changed this" is
answerable without the CI log. **Save Settings** on that screen downloads the
saved key/value configuration as a text file.

### Worker modes and singleplayer

The standalone HTML decides at start-up which Web Workers it uses:

| URL | Integrated server worker | Mesh workers | Singleplayer |
|---|---|---|---|
| (default) | on | off | works |
| `?workers` | on | on | works |
| `?singlethread` | off | off | **not available** |

On the Wasm-GC build a world can only be started by the integrated-server worker (the single-thread
fallback has no server classes), so the server worker is on by default. Its WebAssembly module is compiled
a moment *after* the game has booted, so it does not slow down start-up or raise the peak memory while
loading. Mesh workers speed up chunk meshing but cost extra memory, so they stay opt-in.

With `?singlethread`, or in the lean build (`eaglercraft-26.2-vbv-herz-lean.html`, which has the server and
mesh payloads removed), creating a singleplayer world shows a "Singleplayer cannot start in this page"
screen instead of waiting forever on "Starting integrated server". Use the normal single HTML for
singleplayer; use the lean build only for joining servers on very low-memory devices.

### Build Artifacts

Workflows 02 and 03 produce:

| Artifact | Description |
|---|---|
| Single HTML | Standalone browser client (02) |
| COI Website | Cross-origin-isolated website bundle (02) |
| Web Build | Raw browser website output (02) |
| Signed Web Bundle | `.swbn` Chrome Isolated Web App package and its Web Bundle ID (03) |

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
│       └── 02-compile-eagler-vbv.yml
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
