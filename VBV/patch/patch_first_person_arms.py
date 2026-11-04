#!/usr/bin/env python3
"""Add the native Eagler first-person arm and Actually 3D Stuff pose adapter."""
from pathlib import Path
import sys

if len(sys.argv) < 2:
    raise SystemExit("::error::Generated U2 workspace path was not supplied")

generated_root = Path(sys.argv[1]).resolve()
java_root = generated_root / "game" / "src" / "main" / "java"
renderer = java_root / "net/minecraft/client/renderer/ItemInHandRenderer.java"
helper = java_root / "com/melonman106/vbvclient/VBVFirstPersonAnimations.java"
pose_adapter = java_root / "com/melonman106/vbvclient/Actually3DStuffCompat.java"

for required in (renderer, helper, pose_adapter):
    if not required.is_file():
        raise SystemExit(f"::error::Required first-person source is missing: {required}")

helper_text = helper.read_text(encoding="utf-8")
adapter_text = pose_adapter.read_text(encoding="utf-8")
if "public static boolean isEnabled()" not in helper_text:
    raise SystemExit("::error::VBVFirstPersonAnimations.isEnabled is missing")
if "public static void apply(" not in adapter_text:
    raise SystemExit("::error::Actually3DStuffCompat.apply is missing")

text = renderer.read_text(encoding="utf-8")
marker = "VBV_FIRST_PERSON_HELD_ARM"
hook = """poseStack.pushPose();
         // VBV_FIRST_PERSON_ITEM_OFFSET: move the hand/item pair slightly forward.
         if (com.melonman106.vbvclient.VBVFirstPersonAnimations.isEnabled() && !itemStack.isEmpty() && !itemStack.has(DataComponents.MAP_ID)) {
            float vbvHandSide = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
            poseStack.translate(vbvHandSide * 0.015F, 0.01F, 0.08F);
            // Actually 3D Stuff's Hold My Items Lua pose layer, ported to native Java.
            com.melonman106.vbvclient.Actually3DStuffCompat.apply(
               poseStack, itemStack, player, hand == InteractionHand.MAIN_HAND, frameInterp
            );
         }
         // VBV_FIRST_PERSON_HELD_ARM: keep the skin arm attached to the item.
         // Vanilla renderPlayerArm already animates attacks; eating and drinking
         // retain the native use transform applied below.
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

if marker in text:
    if text.count(marker) != 1:
        raise SystemExit("::error::Duplicate first-person arm markers found")
    if "Actually3DStuffCompat.apply(" not in text:
        raise SystemExit("::error::Existing arm hook lacks the Actually 3D Stuff adapter")
    print("[VBV first-person] Existing arm and Actually 3D Stuff hooks verified.")
    raise SystemExit(0)

needle = "poseStack.pushPose();\n         if (itemStack.isEmpty()) {"
count = text.count(needle)
if count != 1:
    raise SystemExit(f"::error::Expected exactly one vanilla held-arm branch, found {count}; refusing an unsafe patch")

text = text.replace(needle, hook, 1)
if text.count(marker) != 1 or text.count("Actually3DStuffCompat.apply(") != 1:
    raise SystemExit("::error::First-person arm / item-pose hook validation failed")

renderer.write_text(text, encoding="utf-8")
print("[VBV first-person] Added native skin-arm rendering and Actually 3D Stuff item poses.")
