#!/usr/bin/env python3
"""Put a Modrinth icon button in the top-right corner of Options > Resource Packs.

The button opens VBVModrinthScreen, VBV's resource-pack browser, and is drawn from
VBV/PackIcons/modrinth.png, which is installed as
assets/vbvclient/textures/mods/modrinth.png. When a pack finishes downloading the browser
hands it to VBVPackInstaller and returns to this screen, where the existing
VBV_MODRINTH_PACK_INSTALL hook in tick() imports it.

The button used to be a text button in the footer; it now sits in the empty top-right
corner so the footer keeps only "Import Pack..." and "Done".
"""
from pathlib import Path
import shutil
import sys

MARKER = "VBV_MODRINTH_BUTTON"

if len(sys.argv) < 2:
    raise SystemExit("::error::Generated U2 workspace path was not supplied")

generated_root = Path(sys.argv[1]).resolve()
java_root = generated_root / "game" / "src" / "main" / "java"
screen = java_root / "net/minecraft/client/gui/screens/packs/PackSelectionScreen.java"
browser = java_root / "com/melonman106/vbvclient/VBVModrinthScreen.java"
ui = java_root / "com/melonman106/vbvclient/VBVUi.java"
icon = Path(__file__).resolve().parent.parent / "PackIcons" / "modrinth.png"
icon_target = (generated_root / "game" / "src" / "main" / "resources"
               / "assets" / "vbvclient" / "textures" / "mods" / "modrinth.png")

for required in (screen, browser, ui, icon):
    if not required.is_file():
        raise SystemExit(f"::error::Required Modrinth-button source is missing: {required}")

if "class IconButton" not in ui.read_text(encoding="utf-8"):
    raise SystemExit("::error::VBVUi.IconButton is missing; the Modrinth corner button cannot be built")

icon_target.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(icon, icon_target)
print(f"[VBV modrinth button] installed {icon.name} as {icon_target.relative_to(generated_root)}")

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
    f"      // {MARKER}: Modrinth pack browser in the top-right corner.\n"
    "      this.addRenderableWidget(new com.melonman106.vbvclient.VBVUi.IconButton(\n"
    "         this.width - 24, 4, 20,\n"
    "         net.minecraft.resources.Identifier.parse(\"vbvclient:textures/mods/modrinth.png\"),\n"
    "         \"Browse and install resource packs from Modrinth\",\n"
    "         button -> com.melonman106.vbvclient.VBVNav.open(new com.melonman106.vbvclient.VBVModrinthScreen(this))));\n"
)
screen.write_text(text.replace(anchor, button + anchor, 1), encoding="utf-8")
print("[VBV modrinth button] Resource Packs now has a Modrinth icon in the top-right corner")
