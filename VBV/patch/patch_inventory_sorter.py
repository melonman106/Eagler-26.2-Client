#!/usr/bin/env python3
"""Give the container screen the two small helpers the Inventory Sorter needs.

The sorter lives in its own class, but a container screen's `menu` and `slotClicked` are protected.
This stage adds one read accessor and one click helper to `AbstractContainerScreen`, then calls
`VBVInventorySorter.handleKey` at the top of `keyPressed`. Nothing else in the class changes, and a
sort is a short series of ordinary pick-up clicks (one per tick), so it stays server-friendly.
"""
from pathlib import Path
import sys

MARKER = "VBV_SORT_HOOK"

if len(sys.argv) < 2:
    raise SystemExit("::error::Generated U2 workspace path was not supplied")

generated_root = Path(sys.argv[1]).resolve()
java_root = generated_root / "game" / "src" / "main" / "java"
screen = java_root / "net" / "minecraft" / "client" / "gui" / "screens" / "inventory" / "AbstractContainerScreen.java"
helper = java_root / "com" / "melonman106" / "vbvclient" / "VBVInventorySorter.java"

for required in (screen, helper):
    if not required.is_file():
        raise SystemExit(f"::error::Required Inventory Sorter source is missing: {required}")

helper_text = helper.read_text(encoding="utf-8")
if "public static boolean handleKey(" not in helper_text:
    raise SystemExit("::error::VBVInventorySorter.handleKey(AbstractContainerScreen, KeyEvent) is missing")

text = screen.read_text(encoding="utf-8")
if MARKER in text:
    print("[VBV inventory sorter] key hook already present")
    raise SystemExit(0)

anchor = "   @Override\n   public boolean keyPressed(final KeyEvent event) {\n"
if text.count(anchor) != 1:
    raise SystemExit(
        f"::error::AbstractContainerScreen.keyPressed(KeyEvent) anchor matched {text.count(anchor)} time(s); expected exactly 1"
    )
if "protected void slotClicked(" not in text:
    raise SystemExit("::error::AbstractContainerScreen.slotClicked(Slot, int, int, ContainerInput) was not found")
if "   protected final T menu;" not in text:
    raise SystemExit("::error::AbstractContainerScreen.menu field was not found")

replacement = (
    f"   // {MARKER}: read-only accessors used by the native Inventory Sorter.\n"
    "   public net.minecraft.world.inventory.AbstractContainerMenu vbvMenu() {\n"
    "      return this.menu;\n"
    "   }\n\n"
    "   public void vbvInventoryClick(final int slotIndex, final int button, final boolean quickMove) {\n"
    "      this.slotClicked(this.menu.getSlot(slotIndex), slotIndex, button,\n"
    "         quickMove ? net.minecraft.world.inventory.ContainerInput.QUICK_MOVE\n"
    "                   : net.minecraft.world.inventory.ContainerInput.PICKUP);\n"
    "   }\n\n"
    + anchor
    + "      if (com.melonman106.vbvclient.VBVInventorySorter.handleKey(this, event)) {\n"
    "         return true;\n"
    "      }\n"
)
screen.write_text(text.replace(anchor, replacement, 1), encoding="utf-8")
print("[VBV inventory sorter] container screens now forward the sort key to VBVInventorySorter")
