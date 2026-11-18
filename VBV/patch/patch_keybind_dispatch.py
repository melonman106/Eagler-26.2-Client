#!/usr/bin/env python3
"""Route the generated V (Mods) / M (World Map) hotkeys through VBV's configurable keybinds.

The upstream patch injects two hardcoded key checks into KeyboardHandler:

    if (event.key() == 86) {   ... VBVModsScreen      }
    if (event.key() == 77) {   ... VBVWorldMapScreen  }

Those literals make the keys impossible to rebind from Options > Modded Settings > Keybinds...,
because the injection always fires on V and M. This stage swaps only the two conditions for the
configured lookups and leaves the surrounding menu-gating and screen dispatch untouched. Every
anchor must match exactly once, or nothing is written.
"""
from pathlib import Path
import re
import sys

MARKER = "VBV_KEYBIND_DISPATCH"

if len(sys.argv) < 2:
    raise SystemExit("::error::Generated U2 workspace path was not supplied")

JAVA = Path(sys.argv[1]).resolve() / "game" / "src" / "main" / "java"

handler = None
for path in JAVA.rglob("KeyboardHandler.java"):
    source = path.read_text(encoding="utf-8")
    if re.search(r"class\s+KeyboardHandler\b", source):
        handler = (path, source)
        break

if handler is None:
    raise SystemExit("::error::KeyboardHandler.java was not found; the V/M hotkeys cannot be rebound")

path, text = handler

EDITS = (
    (
        "Mods menu",
        "            if (event.key() == 86) {\n",
        "            if (com.melonman106.vbvclient.VBVKeybinds.mods(event)) { // VBV_KEYBIND_DISPATCH\n",
    ),
    (
        "World map",
        "            if (event.key() == 77) {\n",
        "            if (com.melonman106.vbvclient.VBVKeybinds.map(event)) { // VBV_KEYBIND_DISPATCH\n",
    ),
)

# Check everything before touching the file, so a partial patch is impossible.
pending = []
for label, anchor, replacement in EDITS:
    if MARKER in text:
        pending.append(None)
        continue
    count = text.count(anchor)
    if count != 1:
        raise SystemExit(
            f"::error::Keybind dispatch anchor for '{label}' matched {count} time(s) "
            f"in {path.name}; expected exactly 1"
        )
    pending.append((anchor, replacement))

if all(item is None for item in pending):
    print("[VBV keybinds] dispatch already routed through VBVKeybinds")
    raise SystemExit(0)
if any(item is None for item in pending):
    raise SystemExit("::error::Keybind dispatch hooks are only partly installed; refusing to patch")

for anchor, replacement in pending:
    text = text.replace(anchor, replacement, 1)

path.write_text(text, encoding="utf-8")
print(f"[VBV keybinds] routed V/M through VBVKeybinds in {path.name}: rebindable from Modded Settings")
