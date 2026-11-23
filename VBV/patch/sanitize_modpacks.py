#!/usr/bin/env python3
"""Remove resource-pack assets that are incompatible with the native Eagler UI and WebGL2 pipeline.

Every MODS pack is merged into one resource tree, so the last pack copied owns
any path two packs supply. That was only visible in the build log. The same pass
now writes a short build index (per-pack file counts and the contested paths)
next to the patch scripts, and the standalone build embeds it so Diagnostics can
show which pack supplied what.
"""
from pathlib import Path
import json
import os
import shutil
import sys
import tempfile
import zipfile

ROOT = Path(sys.argv[1]).resolve()
MODS = ROOT / "ModsPack"
PATCH_DIR = ROOT / "VBV" / "patch"
if not MODS.is_dir():
    print("ModsPack/ not present; nothing to sanitize.")
    raise SystemExit(0)

# Paths worth reporting: these decide what the game actually draws.
INTERESTING = (
    "assets/minecraft/items/",
    "assets/minecraft/models/",
    "assets/minecraft/font/",
    "assets/minecraft/atlases/",
    "assets/minecraft/textures/",
    "assets/minecraft/sounds/",
    "optifine/cem/",
    "emf/cem/",
)

packs = []
owners = {}

for source in sorted(MODS.glob("*.zip")):
    fd, temp_name = tempfile.mkstemp(prefix=source.stem + "-", suffix=".zip", dir=source.parent)
    os.close(fd)
    temp = Path(temp_name)
    changed = False
    stats = {
        "name": source.name,
        "files": 0,
        "shaderFiles": 0,
        "models": 0,
        "itemDefinitions": 0,
        "cem": 0,
        "fonts": 0,
        "atlases": 0,
    }
    try:
        with zipfile.ZipFile(source, "r") as zin, zipfile.ZipFile(temp, "w") as zout:
            for info in zin.infolist():
                name = info.filename.replace("\\", "/").lstrip("/")
                lower = name.lower()
                blocked = (
                    # Drop OptiFine/Iris shader-pack roots, but preserve native Minecraft
                    # assets/minecraft/shaders and assets/minecraft/post_effect pipelines.
                    lower.startswith("shaders/")
                    or lower.startswith("optifine/shaders/")
                    or lower.startswith("iris/shaders/")
                    or lower.startswith("post_effect/")
                )
                if blocked:
                    changed = True
                    stats["shaderFiles"] += 1
                    continue
                if not info.is_dir():
                    stats["files"] += 1
                    if name.startswith("assets/minecraft/models/"):
                        stats["models"] += 1
                    elif name.startswith("assets/minecraft/items/"):
                        stats["itemDefinitions"] += 1
                    elif lower.startswith("optifine/cem/") or lower.startswith("emf/cem/"):
                        stats["cem"] += 1
                    elif name.startswith("assets/minecraft/font/"):
                        stats["fonts"] += 1
                    elif name.startswith("assets/minecraft/atlases/"):
                        stats["atlases"] += 1
                    for prefix in INTERESTING:
                        if name.startswith(prefix):
                            owners.setdefault(name, []).append(source.name)
                            break
                zout.writestr(info, zin.read(info.filename))
        if changed:
            shutil.move(temp, source)
            print("Removed unsupported Java/Iris shader files:", source.name)
        else:
            temp.unlink(missing_ok=True)
    finally:
        temp.unlink(missing_ok=True)
    packs.append(stats)

# A merged tree has thousands of shared textures; the report is for reading, so
# keep the contested models, item definitions and fonts first and cap the list.
priority = ("assets/minecraft/items/", "assets/minecraft/models/", "optifine/cem/", "emf/cem/", "assets/minecraft/font/")
conflicts = sorted(
    ((path, names) for path, names in owners.items() if len(names) > 1),
    key=lambda item: (not item[0].startswith(priority), -len(item[1]), item[0]),
)[:40]

lines = [f"Build resource index: {len(packs)} packs merged (last pack copied wins)"]
for stats in packs:
    bits = [f"{stats['files']} files"]
    if stats["models"]:
        bits.append(f"{stats['models']} models")
    if stats["itemDefinitions"]:
        bits.append(f"{stats['itemDefinitions']} item defs")
    if stats["cem"]:
        bits.append(f"{stats['cem']} CEM")
    if stats["fonts"]:
        bits.append(f"{stats['fonts']} font")
    if stats["atlases"]:
        bits.append(f"{stats['atlases']} atlas")
    if stats["shaderFiles"]:
        bits.append(f"{stats['shaderFiles']} shader files dropped")
    lines.append(f"{stats['name']}: " + ", ".join(bits))
if conflicts:
    lines.append("")
    lines.append("Contested paths (the last pack in this list wins):")
    for path, names in conflicts:
        lines.append(f"{path}: " + ", ".join(names))
else:
    lines.append("")
    lines.append("No contested item, model, CEM or font paths.")

PATCH_DIR.mkdir(parents=True, exist_ok=True)
(PATCH_DIR / ".vbv_pack_index.json").write_text(
    json.dumps({"packs": packs, "conflicts": [{"path": path, "packs": names} for path, names in conflicts]}, indent=2) + "\n",
    encoding="utf-8",
)
(PATCH_DIR / ".vbv_pack_index.txt").write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")
print(f"Wrote the resource-pack build index for {len(packs)} packs ({len(conflicts)} contested paths).")

print("Resource-pack sanitization complete.")
