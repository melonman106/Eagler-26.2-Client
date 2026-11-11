#!/usr/bin/env python3
"""Wire VBV's Entity Culling into the generated Eagler 26.2 sources.

Three small hooks in LevelExtractor. Every anchor must match exactly once or the whole script
aborts before writing anything, so the tree is never left half patched:

  1. extractVisibleEntities       - marks the start of a frame for the culler.
  2. entity visibility check      - skips entities hidden behind solid blocks (after the frustum test).
  3. extractVisibleBlockEntities  - skips chests, signs, banners... hidden behind solid blocks.

With Entity Culling switched off each hook is a single boolean check.
"""
from pathlib import Path
import sys

MARKER = "VBV_ENTITY_CULLING"

if len(sys.argv) < 2:
    raise SystemExit("::error::Generated U2 workspace path was not supplied")

rel = "net/minecraft/client/renderer/extract/LevelExtractor.java"
path = Path(sys.argv[1]).resolve() / "game" / "src" / "main" / "java" / rel
if not path.is_file():
    raise SystemExit(f"::error::{rel} is missing")
text = path.read_text(encoding="utf-8")

if MARKER in text:
    print("[VBV Entity Culling] hooks already present")
    raise SystemExit(0)

HOOKS = [
    (
        "begin frame",
        "      this.level.forEachEntityForRendering(entityBounds, entity -> {\n",
        "      // VBV_ENTITY_CULLING: start of frame\n"
        "      com.melonman106.vbvclient.VBVEntityCulling.beginFrame(this.level);\n"
        "      this.level.forEachEntityForRendering(entityBounds, entity -> {\n",
    ),
    (
        "entity check",
        "         if (this.isEntityVisible(entity, frustum, camX, camY, camZ)\n"
        "            && (entity != camera.entity()",
        "         if (this.isEntityVisible(entity, frustum, camX, camY, camZ)\n"
        "            // VBV_ENTITY_CULLING: skip entities hidden behind solid blocks\n"
        "            && com.melonman106.vbvclient.VBVEntityCulling.isVisible(entity, camX, camY, camZ)\n"
        "            && (entity != camera.entity()",
    ),
    (
        "block entity check",
        "               if (!frustum.isVisible(renderBounds)) {\n"
        "                  continue;\n"
        "               }\n",
        "               if (!frustum.isVisible(renderBounds)) {\n"
        "                  continue;\n"
        "               }\n"
        "               // VBV_ENTITY_CULLING: skip block entities hidden behind solid blocks\n"
        "               if (!com.melonman106.vbvclient.VBVEntityCulling.isBlockEntityVisible(blockEntity, camX, camY, camZ)) {\n"
        "                  continue;\n"
        "               }\n",
    ),
]

for label, anchor, replacement in HOOKS:
    count = text.count(anchor)
    if count != 1:
        raise SystemExit(f"::error::Entity Culling hook '{label}' anchor matched {count} time(s) in {rel}; expected exactly 1")
    text = text.replace(anchor, replacement, 1)

path.write_text(text, encoding="utf-8")
print(f"[VBV Entity Culling] installed {len(HOOKS)} hooks in {rel}")
