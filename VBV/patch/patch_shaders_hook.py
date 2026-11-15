#!/usr/bin/env python3
"""Wire VBV's Vibrant / Shadows post effects into the generated Eagler 26.2 sources.

One small hook in GameRenderer.renderLevel, placed just before the existing Shine bloom block:
after the world is drawn and before the hand and the HUD, so the effect only touches the world
image. The anchor must match exactly once or the script aborts before writing anything.

With the Shaders preset Off (the default), or on any error, VBVShaders.apply returns at once and
the frame is untouched. Run this after the upstream patch, which adds the Shine bloom block.
"""
from pathlib import Path
import sys

MARKER = "VBV_SHADERS"

if len(sys.argv) < 2:
    raise SystemExit("::error::Generated U2 workspace path was not supplied")

JAVA = Path(sys.argv[1]).resolve() / "game" / "src" / "main" / "java"
REL = "net/minecraft/client/renderer/GameRenderer.java"

ANCHOR = "      if (com.melonman106.vbvclient.ShineConfig.bloom() && !this.vbvShineBloomFailed) {\n"
REPLACEMENT = (
    "      // VBV_SHADERS: Vibrant / Shadows post effects on the finished world image\n"
    "      com.melonman106.vbvclient.VBVShaders.apply(this.minecraft, this.mainRenderTarget, this.resourcePool,\n"
    "         this.gameRenderState.levelRenderState, this.isEaglerOptimizedShaderSupported());\n"
    + ANCHOR
)

path = JAVA / REL
if not path.is_file():
    raise SystemExit(f"::error::{REL} is missing")
text = path.read_text(encoding="utf-8")
if MARKER in text:
    print("[VBV Shaders] hook already present")
    raise SystemExit(0)
count = text.count(ANCHOR)
if count != 1:
    raise SystemExit(
        f"::error::VBV Shaders hook anchor (the Shine bloom block in renderLevel) matched {count} time(s) in {REL}; expected exactly 1"
    )
path.write_text(text.replace(ANCHOR, REPLACEMENT, 1), encoding="utf-8")
print("[VBV Shaders] installed 1 hook")
