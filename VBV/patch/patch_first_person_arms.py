#!/usr/bin/env python3
"""Inject skin-textured first-person arms without replacing vanilla item poses."""
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
if "public static void renderHeldArm(" not in helper_text:
    raise SystemExit("::error::VBVFirstPersonAnimations.renderHeldArm is missing")

text = renderer.read_text(encoding="utf-8")
needle = "poseStack.pushPose();\n         if (itemStack.isEmpty()) {"
hook = """poseStack.pushPose();
         // VBV_FIRST_PERSON_HELD_ARM: render the skin arm in vanilla's arm pose,
         // while leaving the following item transforms and animations untouched.
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

if count == 0:
    if "VBV_FIRST_PERSON_HELD_ARM" in text:
        print("[VBV first-person arms] Existing arm hook verified; no changes needed.")
        raise SystemExit(0)
    raise SystemExit("::error::Could not locate the vanilla held-arm branch")
if count != 1:
    raise SystemExit(
        f"::error::Expected exactly one held-arm branch, found {count}; refusing an unsafe patch"
    )

text = text.replace(needle, hook, 1)
if text.count("VBV_FIRST_PERSON_HELD_ARM") != 1:
    raise SystemExit("::error::First-person arm hook validation failed")

renderer.write_text(text, encoding="utf-8")
print("[VBV first-person arms] Added vanilla skin arm before held-item rendering.")

hook = """if (com.melonman106.vbvclient.VBVFirstPersonAnimations.isEnabled()) {
               com.melonman106.vbvclient.VBVFirstPersonAnimations.renderHeldArm(
                  poseStack, submitNodeCollector, lightCoords, player, arm, this.entityRenderDispatcher
               );
            }

            this.renderItem(
               player,
               itemStack,"""
count = text.count(needle)

if count == 0:
    if text.count("com.melonman106.vbvclient.VBVFirstPersonAnimations.renderHeldArm(") == 2:
        print("[VBV first-person arms] Existing two arm hooks verified; no changes needed.")
        raise SystemExit(0)
    raise SystemExit("::error::Could not locate the two vanilla held-item render sites")
if count != 2:
    raise SystemExit(
        f"::error::Expected exactly two held-item render sites, found {count}; refusing an unsafe patch"
    )

text = text.replace(needle, hook, 2)
if text.count("VBVFirstPersonAnimations.renderHeldArm(") != 2:
    raise SystemExit("::error::First-person arm hook count validation failed")

renderer.write_text(text, encoding="utf-8")
print("[VBV first-person arms] Added skin-textured arms to both vanilla held-item render paths.")
