#!/usr/bin/env python3
"""Normalize files named *.png to real PNG files before Eagler packaging.

Some Minecraft/Eagler resource sources carry AVIF or WebP bytes under a .png
name. Minecraft's PNG loader rejects those files with "Bad PNG Signature".
Use ffmpeg because large AVIF images can be extremely slow to decode through
Python image libraries on the GitHub runner.
"""
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else None
if ROOT is None or not ROOT.is_dir():
    raise SystemExit("usage: normalize_png_assets.py <resource-root>")

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
FFMPEG = shutil.which("ffmpeg")
if not FFMPEG:
    raise SystemExit("::error::ffmpeg is required to normalize invalid PNG-named assets")

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

    fd, temp_name = tempfile.mkstemp(prefix=path.stem + "-", suffix=".png", dir=path.parent)
    Path(temp_name).unlink(missing_ok=True)
    temp = Path(temp_name)
    try:
        subprocess.run(
            [FFMPEG, "-y", "-loglevel", "error", "-i", str(path), "-frames:v", "1", str(temp)],
            check=True,
        )
        with temp.open("rb") as stream:
            if stream.read(8) != PNG_SIGNATURE:
                raise RuntimeError("ffmpeg did not produce a valid PNG signature")
        temp.replace(path)
    except Exception as exc:
        temp.unlink(missing_ok=True)
        raise SystemExit(f"::error::Could not convert invalid PNG asset {path}: {exc}")
    converted.append(path.relative_to(ROOT).as_posix())

print(f"[VBV PNG] checked {checked} PNG-named assets; converted {len(converted)} invalid PNG signature(s)")
for name in converted:
    print(f"[VBV PNG] converted: {name}")
