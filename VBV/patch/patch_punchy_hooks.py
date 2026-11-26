#!/usr/bin/env python3
"""Wire VBV's Punchy resource-pack support into the generated Eagler 26.2 sources.

Two hooks, each with its own exact marker so a re-run never applies one twice:

* EntityRenderDispatcher.onResourceManagerReload(): after the custom-entity-model reload, let VBVPunchy read the
  pack's punchy/animations, punchy/compat and (optionally) punchy/geo files. Runs on every resource reload.
* ItemInHandRenderer.submitArmWithItem(): once the arm is known, offer the hand to VBVPunchy. It returns true when
  it drew the arm and item from the pack's animation (Punchy switched on, a pack loaded, not a map, player visible);
  otherwise vanilla (and the VBV arm layer) carries on exactly as before.

Run after patch_cem_hooks.py, whose reload line is one of the anchors. Fail closed: every anchor must match exactly
once, and nothing is written unless all of them do.
"""
from pathlib import Path
import sys

if len(sys.argv) < 2:
    raise SystemExit("::error::Generated U2 workspace path was not supplied")

JAVA = Path(sys.argv[1]).resolve() / "game" / "src" / "main" / "java"

EDITS = [
    (
        "net/minecraft/client/renderer/entity/EntityRenderDispatcher.java",
        "reload",
        "// VBV_PUNCHY_HOOK: reload",
        "      com.melonman106.vbvclient.VBVCem.reload(resourceManager);\n",
        "      com.melonman106.vbvclient.VBVCem.reload(resourceManager);\n"
        "      // VBV_PUNCHY_HOOK: reload\n"
        "      com.melonman106.vbvclient.VBVPunchy.reload(resourceManager);\n",
    ),
    (
        "net/minecraft/client/renderer/ItemInHandRenderer.java",
        "submit-arm",
        "// VBV_PUNCHY_HOOK: submit-arm",
        "         HumanoidArm arm = isMainHand ? player.getMainArm() : player.getMainArm().getOpposite();\n",
        "         HumanoidArm arm = isMainHand ? player.getMainArm() : player.getMainArm().getOpposite();\n"
        "         // VBV_PUNCHY_HOOK: submit-arm\n"
        "         if (com.melonman106.vbvclient.VBVPunchy.submit(this, player, hand, arm, itemStack, poseStack, submitNodeCollector, lightCoords)) {\n"
        "            return;\n"
        "         }\n",
    ),
]

# Validate and stage every edit before writing any file.
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
            f"::error::VBV Punchy hook '{label}' anchor matched {count} time(s) "
            f"in {rel}; expected exactly 1"
        )

    staged[path] = text.replace(anchor, replacement, 1)

changed = []
for path, text in staged.items():
    if text != path.read_text(encoding="utf-8"):
        changed.append(path)

for path in changed:
    path.write_text(staged[path], encoding="utf-8")

print(
    f"[VBV Punchy] verified {len(EDITS)} distinct hooks "
    f"({len(changed)} file(s) updated)"
)
