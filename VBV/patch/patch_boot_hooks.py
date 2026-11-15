#!/usr/bin/env python3
"""Wire VBV's start-up timer (VBVBoot) into the generated Eagler 26.2 sources.

Three small hooks; every anchor must match exactly once or the script aborts before writing anything:

  1. LoadingOverlay constructor          - marks when the resource reload starts.
  2. LoadingOverlay.tick                  - marks when it ends.
  3. SimpleReloadInstance.prepareTasks    - times every reload listener (textures, models, fonts, sounds ...).

The hooks only record timestamps; they never change what the game loads.
"""
from pathlib import Path
import sys

MARKER = "VBV_BOOT"

if len(sys.argv) < 2:
    raise SystemExit("::error::Generated U2 workspace path was not supplied")

JAVA = Path(sys.argv[1]).resolve() / "game" / "src" / "main" / "java"

EDITS = [
    (
        "net/minecraft/client/gui/screens/LoadingOverlay.java",
        "constructor",
        "      this.onFinish = onFinish;\n",
        "      this.onFinish = onFinish;\n"
        "      // VBV_BOOT: start-up timer\n"
        "      com.melonman106.vbvclient.VBVBoot.reloadStarted();\n",
    ),
    (
        "net/minecraft/client/gui/screens/LoadingOverlay.java",
        "tick",
        "            this.reload.checkExceptions();\n",
        "            com.melonman106.vbvclient.VBVBoot.reloadFinished();\n"
        "            this.reload.checkExceptions();\n",
    ),
    (
        "net/minecraft/server/packs/resources/SimpleReloadInstance.java",
        "prepareTasks",
        "         allSteps.add(state);\n",
        "         allSteps.add(state);\n"
        "         // VBV_BOOT: how long did this listener take?\n"
        "         com.melonman106.vbvclient.VBVBoot.track(listener, state);\n",
    ),
]

# Read and check everything first; files with several edits are patched in one pass.
by_file = {}
for rel, label, anchor, replacement in EDITS:
    by_file.setdefault(rel, []).append((label, anchor, replacement))

pending = []
for rel, edits in by_file.items():
    path = JAVA / rel
    if not path.is_file():
        raise SystemExit(f"::error::{rel} is missing")
    text = path.read_text(encoding="utf-8")
    if MARKER in text:
        pending.append(None)
        continue
    for label, anchor, replacement in edits:
        count = text.count(anchor)
        if count != 1:
            raise SystemExit(f"::error::VBV boot hook '{label}' anchor matched {count} time(s) in {rel}; expected exactly 1")
        text = text.replace(anchor, replacement, 1)
    pending.append((path, text))

if all(item is None for item in pending):
    print("[VBV boot] hooks already present")
    raise SystemExit(0)
if any(item is None for item in pending):
    raise SystemExit("::error::VBV boot hooks are only partly installed; refusing to patch")

for path, text in pending:
    path.write_text(text, encoding="utf-8")
print(f"[VBV boot] installed {len(EDITS)} hooks")
