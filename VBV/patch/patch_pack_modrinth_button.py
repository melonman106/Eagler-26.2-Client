#!/usr/bin/env python3
"""Put a "Modrinth..." button in Options > Resource Packs (it used to live in the VBV launcher).

The button opens VBVModrinthScreen, VBV's resource-pack browser. When a pack finishes downloading
the browser hands it to VBVPackInstaller and returns to this screen, where the existing
VBV_MODRINTH_PACK_INSTALL hook in tick() imports it. This patch only adds the button to the footer,
between "Import Pack..." and "Done".
"""
from pathlib import Path
import sys

MARKER = "VBV_MODRINTH_BUTTON"

if len(sys.argv) < 2:
    raise SystemExit("::error::Generated U2 workspace path was not supplied")

generated_root = Path(sys.argv[1]).resolve()
java_root = generated_root / "game" / "src" / "main" / "java"
screen = java_root / "net/minecraft/client/gui/screens/packs/PackSelectionScreen.java"
browser = java_root / "com/melonman106/vbvclient/VBVModrinthScreen.java"

for required in (screen, browser):
    if not required.is_file():
        raise SystemExit(f"::error::Required Modrinth-button source is missing: {required}")

text = screen.read_text(encoding="utf-8")
if MARKER in text:
    print("[VBV modrinth button] already present")
    raise SystemExit(0)

anchor = "      this.doneButton = footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).build());\n"
if text.count(anchor) != 1:
    raise SystemExit(
        f"::error::PackSelectionScreen footer anchor matched {text.count(anchor)} time(s); expected exactly 1"
    )

button = (
    f"      // {MARKER}: browse and install resource packs from Modrinth.\n"
    "      footer.addChild(Button.builder(Component.literal(\"Modrinth...\"),\n"
    "         button -> this.minecraft.setScreen(new com.melonman106.vbvclient.VBVModrinthScreen(this)))\n"
    "         .width(100)\n"
    "         .tooltip(Tooltip.create(Component.literal(\"Search Modrinth and install resource packs\"))).build());\n"
)
screen.write_text(text.replace(anchor, button + anchor, 1), encoding="utf-8")
print("[VBV modrinth button] Resource Packs footer now has a Modrinth button")
