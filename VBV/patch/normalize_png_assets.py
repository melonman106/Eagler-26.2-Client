#!/usr/bin/env python3
"""Normalize Minecraft texture assets to real PNG bytes before Eagler packaging.

The filename is not trusted. A resource can be named *.png while its bytes are
AVIF, WebP, JPEG, GIF, or another image format. Minecraft's NativeImage/PNG
loader only accepts a real PNG stream for these texture paths, so decode the
actual image bytes with ffmpeg and write a genuine PNG back to the same path.
This makes the build independent of the source image encoding while preserving
the resource path expected by Minecraft models, textures, and JSON files.
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
    raise SystemExit("::error::ffmpeg is required to normalize image assets")

converted = []
checked = 0

# Only paths ending in .png are rewritten. The path itself is part of the
# Minecraft resource contract; the bytes underneath it may be any image type.
for path in sorted(ROOT.rglob("*")):
    if not path.is_file() or path.suffix.lower() != ".png":
        continue

    checked += 1
    with path.open("rb") as stream:
        signature = stream.read(8)
    if signature == PNG_SIGNATURE:
        continue

    fd, temp_name = tempfile.mkstemp(
        prefix=path.stem + "-",
        suffix=".png",
        dir=path.parent,
    )
    Path(temp_name).unlink(missing_ok=True)
    temp = Path(temp_name)

    try:
        # ffmpeg determines the real source format from the file contents,
        # rather than trusting the .png extension.
        subprocess.run(
            [
                FFMPEG,
                "-y",
                "-loglevel",
                "error",
                "-i",
                str(path),
                "-frames:v",
                "1",
                "-pix_fmt",
                "rgba",
                str(temp),
            ],
            check=True,
        )

        with temp.open("rb") as stream:
            if stream.read(8) != PNG_SIGNATURE:
                raise RuntimeError("ffmpeg did not produce a valid PNG signature")

        temp.replace(path)
    except Exception as exc:
        temp.unlink(missing_ok=True)
        raise SystemExit(
            f"::error::Could not convert image bytes stored as PNG {path}: {exc}"
        )

    converted.append(path.relative_to(ROOT).as_posix())

print(
    f"[VBV PNG] checked {checked} PNG resource paths; "
    f"converted {len(converted)} non-PNG image encoding(s)"
)
for name in converted:
    print(f"[VBV PNG] converted: {name}")
