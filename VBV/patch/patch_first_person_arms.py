#!/usr/bin/env python3
"""Animate the skin-textured first-person arm and improve held-item alignment."""
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
         // VBV_FIRST_PERSON_ITEM_OFFSET: move the hand/item pair slightly forward.
         if (com.melonman106.vbvclient.VBVFirstPersonAnimations.isEnabled() && !itemStack.isEmpty() && !itemStack.has(DataComponents.MAP_ID)) {
            float vbvHandSide = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
            poseStack.translate(vbvHandSide * 0.015F, 0.01F, 0.08F);
         }
         // VBV_FIRST_PERSON_HELD_ARM: keep the skin arm attached to the item.
         // Vanilla renderPlayerArm already animates attacks; eating and drinking
         // need their use transform applied before the arm is drawn.
         if (com.melonman106.vbvclient.VBVFirstPersonAnimations.isEnabled()
               && !itemStack.isEmpty()
               && !itemStack.has(DataComponents.MAP_ID)
               && !player.isInvisible()) {
            poseStack.pushPose();
            if (player.isUsingItem() && player.getUseItemRemainingTicks() > 0
                  && player.getUsedItemHand() == hand
                  && (itemStack.getUseAnimation() == ItemUseAnimation.EAT
                      || itemStack.getUseAnimation() == ItemUseAnimation.DRINK)) {
               this.applyEatTransform(poseStack, frameInterp, arm, itemStack, player);
               this.applyItemArmTransform(poseStack, arm, inverseArmHeight);
            }
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
