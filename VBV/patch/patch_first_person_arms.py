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
needle = "this.renderItem(\n               player,\n               itemStack,"
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
