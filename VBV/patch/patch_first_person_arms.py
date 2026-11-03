#!/usr/bin/env python3
"""Inject the skin-textured arm layer while retaining vanilla item rendering."""
from pathlib import Path
import sys

if len(sys.argv) < 2:
    raise SystemExit("::error::Generated U2 workspace path was not supplied")

generated_root = Path(sys.argv[1]).resolve()
java_root = generated_root / "game" / "src" / "main" / "java"
renderer = java_root / "net/minecraft/client/renderer/ItemInHandRenderer.java"
helper = java_root / "com/melonman106/vbvclient/VBVFirstPersonAnimations.java"

if not renderer.is_file():
    raise SystemExit(f"::error::First-person renderer source is missing: {renderer}")
if not helper.is_file():
    raise SystemExit(f"::error::VBV first-person arm helper was not injected: {helper}")

helper_text = helper.read_text(encoding="utf-8")
if "public static boolean isEnabled()" not in helper_text:
    raise SystemExit("::error::VBVFirstPersonAnimations.isEnabled is missing")

text = renderer.read_text(encoding="utf-8")
marker = "VBV_FIRST_PERSON_HELD_ARM"
if marker in text:
    if text.count(marker) != 1:
        raise SystemExit("::error::Duplicate first-person arm markers found")
    print("[VBV first-person arms] Existing vanilla arm hook verified; no changes needed.")
    raise SystemExit(0)

needle = "poseStack.pushPose();\n         if (itemStack.isEmpty()) {"
hook = """poseStack.pushPose();
         // VBV_FIRST_PERSON_HELD_ARM: draw the skin arm with vanilla's arm
         // transform, without changing the held item's own animation path.
         if (com.melonman106.vbvclient.VBVFirstPersonAnimations.isEnabled()
               && !itemStack.isEmpty()
               && !itemStack.has(DataComponents.MAP_ID)
               && !player.isInvisible()) {
            poseStack.pushPose();
            this.renderPlayerArm(poseStack, submitNodeCollector, lightCoords, inverseArmHeight, attack, arm);
            poseStack.popPose();
         }

         if (itemStack.isEmpty()) {"""

count = text.count(needle)
if count != 1:
    raise SystemExit(
        f"::error::Expected exactly one vanilla held-arm branch, found {count}; refusing an unsafe patch"
    )

text = text.replace(needle, hook, 1)
if text.count(marker) != 1 or "this.renderPlayerArm(poseStack, submitNodeCollector, lightCoords, inverseArmHeight, attack, arm);" not in text:
    raise SystemExit("::error::First-person arm hook validation failed")

renderer.write_text(text, encoding="utf-8")
print("[VBV first-person arms] Added the skin arm before vanilla held-item rendering.")
