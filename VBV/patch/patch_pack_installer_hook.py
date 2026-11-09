#!/usr/bin/env python3
"""Let the VBV launcher feed downloaded Modrinth packs into Eagler's own pack importer.

PackSelectionScreen.importBrowserPack(FileChooserResult) already does everything the
"Import Pack..." button does: ZIP validation, unpacking into the browser filesystem,
the OptiFine/CIT compatibility indexes, progress text, error toasts and the re-scan
of the pack list. This patch only adds a few lines at the top of tick() that hand it
a pack queued by VBVPackInstaller, so the launcher never needs its own copy of that code.
"""
from pathlib import Path
import sys

MARKER = "VBV_MODRINTH_PACK_INSTALL"

if len(sys.argv) < 2:
    raise SystemExit("::error::Generated U2 workspace path was not supplied")

generated_root = Path(sys.argv[1]).resolve()
screen = (generated_root / "game" / "src" / "main" / "java" / "net/minecraft/client/gui/screens/packs/PackSelectionScreen.java")
installer = generated_root / "game" / "src" / "main" / "java" / "com/melonman106/vbvclient/VBVPackInstaller.java"

for required in (screen, installer):
    if not required.is_file():
        raise SystemExit(f"::error::Required pack-installer source is missing: {required}")

text = screen.read_text(encoding="utf-8")
if MARKER in text:
    print("[VBV pack installer] hook already present")
    raise SystemExit(0)

anchor = "   @Override\n   public void tick() {\n"
if text.count(anchor) != 1:
    raise SystemExit(
        f"::error::PackSelectionScreen.tick() anchor matched {text.count(anchor)} time(s); expected exactly 1"
    )
if "private void importBrowserPack(final FileChooserResult result)" not in text:
    raise SystemExit("::error::PackSelectionScreen.importBrowserPack(FileChooserResult) was not found")

hook = (
    anchor
    + f"      // {MARKER}: import a pack the VBV launcher downloaded from Modrinth.\n"
    + "      if (!this.browserImportInProgress && com.melonman106.vbvclient.VBVPackInstaller.hasPending()) {\n"
    + "         FileChooserResult vbvQueuedPack = com.melonman106.vbvclient.VBVPackInstaller.takePending();\n"
    + "         if (vbvQueuedPack != null) {\n"
    + "            this.importBrowserPack(vbvQueuedPack);\n"
    + "         }\n"
    + "      }\n"
)
screen.write_text(text.replace(anchor, hook, 1), encoding="utf-8")
print("[VBV pack installer] PackSelectionScreen.tick() now imports queued Modrinth packs")
