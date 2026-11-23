#!/usr/bin/env python3
"""Send the game's own screenshot key through the VBV storage manager.

`Screenshot.grab(Minecraft, boolean)` is the one method the screenshot key and the screenshot
command go through. The hook asks `VBVScreenshots.capture` first: that reads the frame back once,
turns it into a PNG on a canvas and writes it into Screenshots/ in the managed storage folder (or
downloads it when no folder is connected). The original Eagler download path remains underneath as
the fallback when the frame cannot be read, so no capture is ever lost.
"""
from pathlib import Path
import sys

MARKER = "VBV_SCREENSHOT_STORAGE"

if len(sys.argv) < 2:
    raise SystemExit("::error::Generated U2 workspace path was not supplied")

generated_root = Path(sys.argv[1]).resolve()
java_root = generated_root / "game" / "src" / "main" / "java"
screenshot = java_root / "net" / "minecraft" / "client" / "Screenshot.java"
helper = java_root / "com" / "melonman106" / "vbvclient" / "VBVScreenshots.java"

for required in (screenshot, helper):
    if not required.is_file():
        raise SystemExit(f"::error::Required screenshot source is missing: {required}")

helper_text = helper.read_text(encoding="utf-8")
if "public static boolean capture(" not in helper_text:
    raise SystemExit("::error::VBVScreenshots.capture(Minecraft) is missing")

text = screenshot.read_text(encoding="utf-8")
if MARKER in text:
    print("[VBV screenshots] storage hook already present")
    raise SystemExit(0)

anchor = "   public static void grab(final Minecraft minecraft, final boolean debugPanoramaRequested) {\n"
if text.count(anchor) != 1:
    raise SystemExit(
        f"::error::Screenshot.grab(Minecraft, boolean) anchor matched {text.count(anchor)} time(s); expected exactly 1"
    )
if "SharedConstants.DEBUG_PANORAMA_SCREENSHOT" not in text:
    raise SystemExit("::error::The panorama screenshot guard was not found in Screenshot.java")

hook = (
    anchor
    + f"      // {MARKER}: save the capture through the VBV storage folder first. The vanilla\n"
    + "      // download below is still the fallback when the frame cannot be read back.\n"
    + "      if (!(debugPanoramaRequested && SharedConstants.DEBUG_PANORAMA_SCREENSHOT)\n"
    + "            && com.melonman106.vbvclient.VBVScreenshots.capture(minecraft)) {\n"
    + "         return;\n"
    + "      }\n"
)
screenshot.write_text(text.replace(anchor, hook, 1), encoding="utf-8")
print("[VBV screenshots] F2 captures now go to the managed storage Screenshots folder")
