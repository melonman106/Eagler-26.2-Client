#!/usr/bin/env python3
"""Make every Screen re-layout itself when the GUI size changes under it.

Screens cache `width`/`height` and lay their widgets out from those values. Minecraft
normally calls Screen.resize() from Minecraft.resizeGui(), but when the browser enters
or leaves fullscreen that call can be missed, which leaves menus laid out for the old
size. This patch makes Screen.extractRenderStateWithTooltipAndSubtitles() (the single
entry point every screen is drawn through) compare the cached size with the window's
current GUI size and call resize() if they differ. When sizes already match it costs
two integer comparisons per frame.
"""
from pathlib import Path
import sys

MARKER = "VBV_SCREEN_RESIZE_GUARD"

if len(sys.argv) < 2:
    raise SystemExit("::error::Generated U2 workspace path was not supplied")

screen = (Path(sys.argv[1]).resolve() / "game" / "src" / "main" / "java"
          / "net/minecraft/client/gui/screens/Screen.java")
if not screen.is_file():
    raise SystemExit(f"::error::Screen.java is missing: {screen}")

text = screen.read_text(encoding="utf-8")
if MARKER in text:
    print("[VBV resize guard] already present")
    raise SystemExit(0)

anchor = (
    "   public final void extractRenderStateWithTooltipAndSubtitles("
    "final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {\n"
)
if text.count(anchor) != 1:
    raise SystemExit(
        f"::error::Screen render entry anchor matched {text.count(anchor)} time(s); expected exactly 1"
    )
if "public void resize(final int width, final int height)" not in text:
    raise SystemExit("::error::Screen.resize(int, int) was not found")

guard = (
    anchor
    + f"      // {MARKER}: re-layout if the window's GUI size changed without a resize event\n"
    + "      // (browser fullscreen enter/exit).\n"
    + "      if (this.initialized && (graphics.guiWidth() != this.width || graphics.guiHeight() != this.height)) {\n"
    + "         this.resize(graphics.guiWidth(), graphics.guiHeight());\n"
    + "      }\n"
)
screen.write_text(text.replace(anchor, guard, 1), encoding="utf-8")
print("[VBV resize guard] Screen render entry now re-lays out on GUI size changes")
