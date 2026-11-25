#!/usr/bin/env python3
"""Builds artifacts/slime_mod.zip, the importable version of the Slime test mod.

    python3 artifacts/build_slime_mod.py

The zip is data only: info.json, items.json, content.json, pack.png and the assets folder. Import it from
Mods -> Imported Mods... -> Import .zip and reload the page.
"""
import json
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
SOURCE = HERE / "slime_mod"
TARGET = HERE / "slime_mod.zip"
MOD_ID = "vbvslime"


def main():
    info = json.loads((SOURCE / "info.json").read_text(encoding="utf-8"))
    items = json.loads((SOURCE / "items.json").read_text(encoding="utf-8"))
    content = json.loads((SOURCE / "content.json").read_text(encoding="utf-8"))
    assert info["id"] == MOD_ID
    assert len(items["items"]) == 4
    assert len(content["recipes"]) == 6

    # A missing texture is the most common reason a mod looks broken in game, so check every referenced file.
    paths = [
        "assets/vbvslime/textures/item/magicule_crystal.png",
        "assets/vbvslime/textures/item/slime_herb.png",
        "assets/vbvslime/textures/item/slime_jelly.png",
        "assets/vbvslime/textures/item/slime_elixir.png",
        "assets/vbvslime/textures/item/slime_herb_seeds.png",
        "assets/vbvslime/textures/block/magicule_crystal_block.png",
        "assets/vbvslime/textures/mob_effect/slime_body.png",
    ] + [f"assets/vbvslime/textures/block/slime_herb_stage{i}.png" for i in range(4)]
    missing = [path for path in paths if not (SOURCE / path).is_file()]
    if missing:
        raise SystemExit("missing assets:\n  " + "\n  ".join(missing))

    if TARGET.exists():
        TARGET.unlink()
    with zipfile.ZipFile(TARGET, "w", zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(SOURCE.rglob("*")):
            if path.is_file():
                archive.write(path, path.relative_to(SOURCE).as_posix())

    print(f"wrote {TARGET} ({TARGET.stat().st_size} bytes)")
    for name in sorted(zipfile.ZipFile(TARGET).namelist()):
        print(" ", name)


if __name__ == "__main__":
    main()
