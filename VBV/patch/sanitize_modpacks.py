#!/usr/bin/env python3
"""Remove resource-pack assets that are incompatible with the native Eagler UI and WebGL2 pipeline."""
from pathlib import Path
import os
import shutil
import sys
import tempfile
import zipfile

ROOT = Path(sys.argv[1]).resolve()
MODS = ROOT / "ModsPack"
if not MODS.is_dir():
    print("ModsPack/ not present; nothing to sanitize.")
    raise SystemExit(0)

# Keep normal resource-pack assets, including font definitions, font atlases,
# GUI icons and HUD textures. Only Java/Iris shader-pack files are unsupported.
for source in sorted(MODS.glob("*.zip")):
    fd, temp_name = tempfile.mkstemp(prefix=source.stem + "-", suffix=".zip", dir=source.parent)
    os.close(fd)
    temp = Path(temp_name)
    changed = False
    try:
        with zipfile.ZipFile(source, "r") as zin, zipfile.ZipFile(temp, "w") as zout:
            for info in zin.infolist():
                name = info.filename.replace("\\", "/").lstrip("/")
                lower = name.lower()
                blocked = (
                    # OptiFine/Iris shader assets cannot run in the Eagler WebGL2 pipeline.
                    lower.startswith("shaders/")
                    or "/shaders/" in lower
                    or lower.startswith("post_effect/")
                    or "/post_effect/" in lower
                )
                if blocked:
                    changed = True
                    continue
                zout.writestr(info, zin.read(info.filename))
        if changed:
            shutil.move(temp, source)
            print("Removed unsupported Java/Iris shader files:", source.name)
        else:
            temp.unlink(missing_ok=True)
    finally:
        temp.unlink(missing_ok=True)

print("Resource-pack sanitization complete.")
