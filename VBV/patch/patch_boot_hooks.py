#!/usr/bin/env python3
"""Wire VBV's start-up timer (VBVBoot) into the generated Eagler 26.2 sources.

Each hook has its own exact marker. This is important because other patches also
use names beginning with VBV_BOOT (for example VBV_BOOT_SCREEN); a broad
substring check would mistake those unrelated hooks for installed timer hooks.
"""
from pathlib import Path
import sys

if len(sys.argv) < 2:
    raise SystemExit("::error::Generated U2 workspace path was not supplied")

JAVA = Path(sys.argv[1]).resolve() / "game" / "src" / "main" / "java"

EDITS = [
    (
        "net/minecraft/client/gui/screens/LoadingOverlay.java",
        "reload-started",
        "// VBV_BOOT_HOOK: reload-started",
        "      this.onFinish = onFinish;\n",
        "      this.onFinish = onFinish;\n"
        "      // VBV_BOOT_HOOK: reload-started\n"
        "      com.melonman106.vbvclient.VBVBoot.reloadStarted();\n",
    ),
    (
        "net/minecraft/client/gui/screens/LoadingOverlay.java",
        "reload-finished",
        "// VBV_BOOT_HOOK: reload-finished",
        "            this.reload.checkExceptions();\n",
        "            // VBV_BOOT_HOOK: reload-finished\n"
        "            com.melonman106.vbvclient.VBVBoot.reloadFinished();\n"
        "            this.reload.checkExceptions();\n",
    ),
    (
        "net/minecraft/server/packs/resources/SimpleReloadInstance.java",
        "listener-track",
        "// VBV_BOOT_HOOK: listener-track",
        "         allSteps.add(state);\n",
        "         allSteps.add(state);\n"
        "         // VBV_BOOT_HOOK: listener-track\n"
        "         com.melonman106.vbvclient.VBVBoot.track(listener, state);\n",
    ),
]

# Validate and stage every edit before writing any file. An unrelated
# VBV_BOOT_SCREEN marker must never count as one of these exact hook markers.
staged = {}
for rel, label, marker, anchor, replacement in EDITS:
    path = JAVA / rel
    if not path.is_file():
        raise SystemExit(f"::error::{rel} is missing")

    text = staged.get(path)
    if text is None:
        text = path.read_text(encoding="utf-8")

    if marker in text:
        staged[path] = text
        continue

    count = text.count(anchor)
    if count != 1:
        raise SystemExit(
            f"::error::VBV boot hook '{label}' anchor matched {count} time(s) "
            f"in {rel}; expected exactly 1"
        )

    staged[path] = text.replace(anchor, replacement, 1)

changed = []
for path, text in staged.items():
    original = path.read_text(encoding="utf-8")
    if text != original:
        changed.append(path)

for path in changed:
    path.write_text(staged[path], encoding="utf-8")

installed = sum(1 for _, _, marker, _, _ in EDITS if marker in staged[JAVA / _])
print(
    f"[VBV boot] verified {len(EDITS)} distinct hooks "
    f"({len(changed)} file(s) updated)"
)
