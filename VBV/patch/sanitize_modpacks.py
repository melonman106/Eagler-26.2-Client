#!/usr/bin/env python3
"""Remove resource-pack assets that are incompatible with the native Eagler UI."""
from pathlib import Path
import shutil
import sys
import tempfile
import zipfile

ROOT = Path(sys.argv[1]).resolve()
MODS = ROOT / "ModsPack"
if not MODS.is_dir():
    print("ModsPack/ not present; nothing to sanitize.")
    raise SystemExit(0)

# Eagler's built-in font atlas is not compatible with arbitrary Java resource
# pack font overrides. A bad font atlas manifests as every character becoming
# the unknown-glyph rectangle. Keep pack HUD textures (including AppleSkin's
# assets/appleskin/textures/icons.png) while protecting the core font.
for source in sorted(MODS.glob("*.zip")):
    fd, temp_name = tempfile.mkstemp(prefix=source.stem + "-", suffix=".zip", dir=source.parent)
    Path(temp_name).unlink(missing_ok=True)
    temp = Path(temp_name)
    changed = False
    try:
        with zipfile.ZipFile(source, "r") as zin, zipfile.ZipFile(temp, "w") as zout:
            for info in zin.infolist():
                name = info.filename.replace("\\", "/").lstrip("/")
                lower = name.lower()
                blocked = (
                    lower.startswith("assets/minecraft/font/")
                    or "/assets/minecraft/font/" in lower
                    or lower.startswith("assets/minecraft/textures/font/")
                    or "/assets/minecraft/textures/font/" in lower
                )
                if blocked:
                    changed = True
                    continue
                zout.writestr(info, zin.read(info.filename))
        if changed:
            shutil.move(temp, source)
            print("Sanitized incompatible font overrides:", source.name)
        else:
            temp.unlink(missing_ok=True)
    finally:
        temp.unlink(missing_ok=True)

print("Resource-pack sanitization complete.")
