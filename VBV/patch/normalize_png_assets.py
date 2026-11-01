#!/usr/bin/env python3
"""Normalize files named *.png to real PNG files before Eagler asset packaging.

Some Minecraft/Eagler resource sources carry AVIF or WebP bytes under a .png
name. Minecraft's PNG loader rejects those files with "Bad PNG Signature".
Convert those files in-place while leaving genuine PNG files untouched.
"""
from pathlib import Path
import sys

from PIL import Image

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else None
if ROOT is None or not ROOT.is_dir():
    raise SystemExit("usage: normalize_png_assets.py <resource-root>")

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
converted = []
checked = 0

for path in sorted(ROOT.rglob("*.png")):
    if not path.is_file():
        continue
    checked += 1
    with path.open("rb") as stream:
        signature = stream.read(8)
    if signature == PNG_SIGNATURE:
        continue

    try:
        with Image.open(path) as source:
            source.load()
            image = source.convert("RGBA")
            image.save(path, format="PNG", optimize=True)
    except Exception as exc:
        raise SystemExit(f"::error::Could not convert invalid PNG asset {path}: {exc}")

    with path.open("rb") as stream:
        if stream.read(8) != PNG_SIGNATURE:
            raise SystemExit(f"::error::PNG conversion did not produce a valid PNG: {path}")
    converted.append(path.relative_to(ROOT).as_posix())

print(f"[VBV PNG] checked {checked} PNG-named assets; converted {len(converted)} invalid PNG signature(s)")
for name in converted:
    print(f"[VBV PNG] converted: {name}")
