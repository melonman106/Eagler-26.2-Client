#!/usr/bin/env python3
"""Put the world-loading screens on the VBV theme and shorten the longest worldgen stalls.

Two screens appear between the main menu and the world:

* ``EaglerIntegratedServerBusyScreen`` - what the browser build shows while the integrated server
  starts ("Starting up integrated server", "Preparing spawn area", ...). It drew the classic tiled
  dirt menu background behind a single status line.
* ``LevelLoadingScreen`` - the single-thread path, with vanilla's "Downloading terrain" text and a
  thin green bar.

Both now draw the same backdrop, wordmark and segmented bar as the VBV boot screen, with a highlight
that keeps moving and an elapsed-time counter, so a slow world shows that it is still working.
``EaglerWorldgenExecutor``'s first generation bursts are also shortened so the page repaints more
often while the spawn area is generated.
"""
from pathlib import Path
import sys

MARKER = "VBV_LOADING_THEME"
WORLDGEN_MARKER = "VBV_WORLDGEN_BURST"

if len(sys.argv) < 2:
    raise SystemExit("::error::Generated U2 workspace path was not supplied")

ROOT = Path(sys.argv[1]).resolve()
JAVA = ROOT / "game" / "src" / "main" / "java"

BUSY_REL = "net/lax1dude/eaglercraft/v1_8/sp/gui/EaglerIntegratedServerBusyScreen.java"
LEVEL_REL = "net/minecraft/client/gui/screens/LevelLoadingScreen.java"
WORLDGEN_REL = "net/lax1dude/eaglercraft/v1_8/sp/server/EaglerWorldgenExecutor.java"


def read(rel):
    path = JAVA / rel
    if not path.is_file():
        raise SystemExit(f"::error::{rel} is missing")
    return path, path.read_text(encoding="utf-8")


def replace_once(text, anchor, replacement, label):
    count = text.count(anchor)
    if count != 1:
        raise SystemExit(
            f"::error::{label} anchor matched {count} time(s); expected exactly 1"
        )
    return text.replace(anchor, replacement, 1)


# ----- 1. the integrated-server busy screen: VBV backdrop, wordmark and bar -----
busy_path, busy = read(BUSY_REL)
if MARKER not in busy:
    anchor = (
        "\t@Override\n"
        "\tpublic void extractBackground(final net.minecraft.client.gui.GuiGraphicsExtractor graphics,\n"
        "\t\t\tfinal int mouseX, final int mouseY, final float a) {\n"
        "\t\t// upstream busy screen uses the classic tiled menu background\n"
        "\t\tthis.extractMenuBackground(graphics);\n"
        "\t}\n"
    )
    replacement = (
        "\t@Override\n"
        "\tpublic void extractBackground(final net.minecraft.client.gui.GuiGraphicsExtractor graphics,\n"
        "\t\t\tfinal int mouseX, final int mouseY, final float a) {\n"
        f"\t\t// {MARKER}: the VBV loading look instead of the tiled dirt menu background.\n"
        "\t\t// The bar keeps sweeping while a step is running, so a slow spawn never looks frozen.\n"
        "\t\tcom.melonman106.vbvclient.VBVLoadingTheme.backdrop(graphics, this.width, this.height);\n"
        "\t\tfloat vbvProgress = SingleplayerServerController26.worldStatusProgress();\n"
        "\t\tString vbvStage = humanize(SingleplayerServerController26.worldStatusString());\n"
        "\t\t// labels off: this screen already draws the status line, the timer and the cancel button.\n"
        "\t\tcom.melonman106.vbvclient.VBVLoadingTheme.shell(graphics, this.width, this.height,\n"
        "\t\t\t\tvbvProgress > 0.0F ? vbvProgress : -1.0F, vbvStage,\n"
        "\t\t\t\tSystem.currentTimeMillis() - this.openedAt, false);\n"
        "\t}\n"
    )
    busy = replace_once(busy, anchor, replacement, "busy screen background")
    busy_path.write_text(busy, encoding="utf-8")
    print("[VBV loading] integrated-server busy screen themed")
else:
    print("[VBV loading] integrated-server busy screen already themed")

# ----- 2. LevelLoadingScreen: VBV backdrop behind the chunk map, VBV bar under it -----
level_path, level = read(LEVEL_REL)
if MARKER not in level:
    # Start the elapsed timer the first time the screen is shown.
    level = replace_once(
        level,
        "   private float smoothedProgress;\n",
        "   private float smoothedProgress;\n"
        f"   private final long vbvOpenedAt = System.currentTimeMillis(); // {MARKER}\n",
        "level loading screen field",
    )
    # Dark backdrop first, so the chunk progress map is drawn on top of it.
    level = replace_once(
        level,
        "      super.extractRenderState(graphics, mouseX, mouseY, a);\n      long current = Util.getMillis();\n",
        "      super.extractRenderState(graphics, mouseX, mouseY, a);\n"
        f"      com.melonman106.vbvclient.VBVLoadingTheme.backdrop(graphics, this.width, this.height); // {MARKER}\n"
        "      long current = Util.getMillis();\n",
        "level loading screen backdrop",
    )
    # Replace vanilla's text + thin green bar with the VBV bar.
    level = replace_once(
        level,
        "      graphics.centeredText(this.font, DOWNLOADING_TERRAIN_TEXT, xCenter, textTop, -1);\n"
        "      if (this.loadTracker.hasProgress()) {\n"
        "         this.drawProgressBar(graphics, xCenter - 100, textTop + 9 + 3, 200, 2, this.smoothedProgress);\n"
        "      }\n",
        f"      // {MARKER}: the same wordmark and segmented bar as the boot screen.\n"
        "      com.melonman106.vbvclient.VBVLoadingTheme.shell(graphics, this.width, this.height,\n"
        "         this.loadTracker.hasProgress() ? this.smoothedProgress : -1.0F, \"BUILDING TERRAIN\",\n"
        "         System.currentTimeMillis() - this.vbvOpenedAt);\n",
        "level loading screen progress",
    )
    level_path.write_text(level, encoding="utf-8")
    print("[VBV loading] level loading screen themed")
else:
    print("[VBV loading] level loading screen already themed")

# ----- 3. shorter worldgen bursts while the spawn area is generated -----
worldgen_path, worldgen = read(WORLDGEN_REL)
if WORLDGEN_MARKER not in worldgen:
    worldgen = replace_once(
        worldgen,
        "\tprivate static final long INITIAL_SLICE_MILLIS = 128L;\n",
        f"\t// {WORLDGEN_MARKER}: 128 ms let the loading screen sit still for over a tenth of a\n"
        "\t// second per burst; 48 ms keeps the spawn generation responsive at a small throughput cost.\n"
        "\tprivate static final long INITIAL_SLICE_MILLIS = 48L;\n",
        "worldgen slice",
    )
    worldgen_path.write_text(worldgen, encoding="utf-8")
    print("[VBV loading] worldgen bursts shortened to 48 ms")
else:
    print("[VBV loading] worldgen bursts already shortened")
