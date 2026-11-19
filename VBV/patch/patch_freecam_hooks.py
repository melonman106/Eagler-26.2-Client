#!/usr/bin/env python3
"""Wire VBV's spectator-style free camera into the generated Eagler 26.2 sources.

Six small, independent hooks. Every anchor must match exactly once, or the whole script aborts
before writing anything, so the tree is never left half patched:

  1. Camera            - place and rotate the camera from the free camera instead of the player.
  2. MouseHandler      - the mouse turns the free camera instead of the player while it is on.
  3. KeyboardInput     - clears the player's movement input, so the player stands still.
  4. Minecraft         - three guards: no attack, no item/block use (so no chests) and no mining.
  5. ItemInHandRenderer- the first-person hands are drawn at the camera, so they are hidden.

Everything is a no-op while the free camera is off (the default), apart from one boolean check
per hook.
"""
from pathlib import Path
import sys

MARKER = "VBV_FREECAM"

if len(sys.argv) < 2:
    raise SystemExit("::error::Generated U2 workspace path was not supplied")

root = Path(sys.argv[1]).resolve() / "game" / "src" / "main" / "java"
client = "net/minecraft/client"

# (file, anchor, replacement, label)
HOOKS = [
    (
        f"{client}/Camera.java",
        "         this.move(0.0F, 0.15F, 0.0F);\n      }\n   }\n",
        "         this.move(0.0F, 0.15F, 0.0F);\n      }\n"
        "\n"
        "      // VBV_FREECAM_CAMERA: while the free camera is on it owns the placement and\n"
        "      // rotation; the renderer, fog and LOD then all follow the free camera.\n"
        "      if (com.melonman106.vbvclient.VBVFreecam.isActive()) {\n"
        "         this.detached = false;\n"
        "         this.setRotation(com.melonman106.vbvclient.VBVFreecam.yRot(),\n"
        "            com.melonman106.vbvclient.VBVFreecam.xRot());\n"
        "         this.setPosition(com.melonman106.vbvclient.VBVFreecam.posX(partialTicks),\n"
        "            com.melonman106.vbvclient.VBVFreecam.posY(partialTicks),\n"
        "            com.melonman106.vbvclient.VBVFreecam.posZ(partialTicks));\n"
        "      }\n"
        "   }\n",
        "camera placement",
    ),
    (
        f"{client}/MouseHandler.java",
        "      this.minecraft.getTutorial().onMouse(xo, yo);\n"
        "      if (this.minecraft.player != null) {\n",
        "      // VBV_FREECAM_LOOK: while the camera is detached the mouse turns it, not the player,\n"
        "      // so the player's own rotation is exactly what it was when the camera left.\n"
        "      if (com.melonman106.vbvclient.VBVFreecam.isActive()) {\n"
        "         com.melonman106.vbvclient.VBVFreecam.look(\n"
        "            this.minecraft.options.invertMouseX().get() ? -xo : xo,\n"
        "            this.minecraft.options.invertMouseY().get() ? -yo : yo);\n"
        "         return;\n"
        "      }\n"
        "      this.minecraft.getTutorial().onMouse(xo, yo);\n"
        "      if (this.minecraft.player != null) {\n",
        "mouse look",
    ),
    (
        f"{client}/player/KeyboardInput.java",
        "   @Override\n   public void tick() {\n",
        "   @Override\n   public void tick() {\n"
        "      // VBV_FREECAM_FREEZE: the player keeps standing exactly where the camera left them.\n"
        "      if (com.melonman106.vbvclient.VBVFreecam.isActive()) {\n"
        "         this.keyPresses = net.minecraft.world.entity.player.Input.EMPTY;\n"
        "         this.moveVector = net.minecraft.world.phys.Vec2.ZERO;\n"
        "         return;\n"
        "      }\n",
        "player freeze",
    ),
    (
        f"{client}/Minecraft.java",
        "   private boolean startAttack() {\n",
        "   private boolean startAttack() {\n"
        "      // VBV_FREECAM_NO_ATTACK: the free camera never hits anything.\n"
        "      if (com.melonman106.vbvclient.VBVFreecam.isActive()) {\n"
        "         return false;\n"
        "      }\n",
        "no attack",
    ),
    (
        f"{client}/Minecraft.java",
        "   private void startUseItem() {\n",
        "   private void startUseItem() {\n"
        "      // VBV_FREECAM_NO_USE: no block use, so no chests, doors or buckets while flying.\n"
        "      if (com.melonman106.vbvclient.VBVFreecam.isActive()) {\n"
        "         return;\n"
        "      }\n",
        "no item use",
    ),
    (
        f"{client}/Minecraft.java",
        "   private void continueAttack(final boolean down) {\n",
        "   private void continueAttack(final boolean down) {\n"
        "      // VBV_FREECAM_NO_MINING: no block breaking while the camera is detached.\n"
        "      if (com.melonman106.vbvclient.VBVFreecam.isActive()) {\n"
        "         return;\n"
        "      }\n",
        "no mining",
    ),
    (
        f"{client}/renderer/ItemInHandRenderer.java",
        "   public void submitHandsWithItems(\n"
        "      final float frameInterp, final PoseStack poseStack, final SubmitNodeCollector submitNodeCollector, final LocalPlayer player, final int lightCoords\n"
        "   ) {\n",
        "   public void submitHandsWithItems(\n"
        "      final float frameInterp, final PoseStack poseStack, final SubmitNodeCollector submitNodeCollector, final LocalPlayer player, final int lightCoords\n"
        "   ) {\n"
        "      // VBV_FREECAM_NO_HANDS: the held item is drawn at the camera, which is not the player\n"
        "      // any more, so it would float in the air; hide it while the camera is detached.\n"
        "      if (com.melonman106.vbvclient.VBVFreecam.isActive()) {\n"
        "         return;\n"
        "      }\n",
        "hands hidden",
    ),
]

# ---- Validate everything first, then write ------------------------------------------------
files = {}
pending = {}
for rel, anchor, replacement, label in HOOKS:
    path = root / rel
    if not path.is_file():
        raise SystemExit(f"::error::{rel} is missing ({label})")
    if rel not in files:
        files[rel] = path.read_text(encoding="utf-8")
        pending[rel] = files[rel]

already = [rel for rel, text in files.items() if MARKER in text]
if len(already) == len(files):
    print("[VBV freecam] hooks already present")
    raise SystemExit(0)
if already:
    raise SystemExit(f"::error::free camera hooks are partly applied ({', '.join(already)}); refusing to continue")

for rel, anchor, replacement, label in HOOKS:
    count = pending[rel].count(anchor)
    if count != 1:
        raise SystemExit(f"::error::free camera hook '{label}' anchor matched {count} time(s) in {rel}; expected exactly 1")
    pending[rel] = pending[rel].replace(anchor, replacement, 1)

for rel, text in pending.items():
    (root / rel).write_text(text, encoding="utf-8")
print(f"[VBV freecam] installed {len(HOOKS)} hooks in {len(pending)} files")
