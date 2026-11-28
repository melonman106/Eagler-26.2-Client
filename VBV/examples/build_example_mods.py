#!/usr/bin/env python3
"""Builds the example mods in VBV/examples/mods into importable .zip files.

    python3 VBV/examples/build_example_mods.py [output-folder]

The JSON comes from the folders next to this script; the pictures (textures and icons) are drawn here, so no binary
files live in the repository. Import the zips from "Mods -> Imported Mods... -> Import .zip".
"""
import struct
import sys
import zipfile
import zlib
from pathlib import Path

HERE = Path(__file__).resolve().parent
PALETTE = {
    ".": None,
    "k": (40, 12, 16, 255),
    "r": (214, 40, 62, 255),
    "R": (150, 24, 44, 255),
    "w": (255, 190, 200, 255),
    "b": (120, 78, 40, 255),
    "g": (70, 160, 70, 255),
    "y": (235, 200, 60, 255),
    "Y": (170, 130, 30, 255),
    "s": (200, 205, 215, 255),
    "S": (120, 125, 140, 255),
    "p": (150, 90, 220, 255),
    "P": (90, 50, 150, 255),
}

RUBY = [
    "................",
    "................",
    ".....kkkkkk.....",
    "....krrwwrrk....",
    "...krrwrrrrrk...",
    "..krwrrrrrrrrk..",
    "..krrrrrrrrrRk..",
    "..kRrrrrrrrRRk..",
    "...kRrrrrrRRk...",
    "....kRrrrRRk....",
    ".....kRrRRk.....",
    "......kRRk......",
    ".......kk.......",
    "................",
    "................",
    "................",
]
SWORD = [
    "..............kk",
    ".............krk",
    "............krwk",
    "...........krrRk",
    "..........krrRk.",
    ".........krrRk..",
    "........krrRk...",
    ".k.....krrRk....",
    "kbk...krrRk.....",
    ".kbk.krrRk......",
    "..kbkkrRk.......",
    "...kbkRk........",
    "..kkbkk.........",
    ".kbbk.bk........",
    "kbkk...k........",
    "kk..............",
]
APPLE = [
    "................",
    "..........kk....",
    ".........kgk....",
    "........kgk.....",
    "....kkk.kk.kk...",
    "...krrrkkrrrrk..",
    "..krwrrrrrrrrRk.",
    "..krwrrrrrrrrRk.",
    "..krrrrrrrrrrRk.",
    "..krrrrrrrrrRRk.",
    "...krrrrrrrrRk..",
    "...krrrrrrrRRk..",
    "....kkrrrrRRk...",
    ".....kkRRRkk....",
    ".......kkk......",
    "................",
]
INGOT = [
    "................",
    "................",
    "................",
    "................",
    "....kkkkkkkk....",
    "...kyyyyyyyyk...",
    "..kywwyyyyyyYk..",
    "..kyyyyyyyyYYk..",
    "..kkYYYYYYYYkk..",
    "...kkkkkkkkkk...",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]
WAND = [
    "..............kk",
    ".............kpk",
    "............kpwk",
    "...........kpwPk",
    "..........kpPPk.",
    ".........kbPkk..",
    "........kbbk....",
    ".......kbbk.....",
    "......kbbk......",
    ".....kbbk.......",
    "....kbbk........",
    "...kbbk.........",
    "..kbbk..........",
    ".kbbk...........",
    "kbbk............",
    "kkk.............",
]


def png(pixels):
    height = len(pixels)
    width = len(pixels[0])
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for pixel in row:
            raw.extend(pixel if pixel else (0, 0, 0, 0))

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")


def art(rows, scale=1):
    pixels = []
    for row in rows:
        line = []
        for ch in row:
            line.extend([PALETTE[ch]] * scale)
        pixels.extend([list(line) for _ in range(scale)])
    return png(pixels)


EXTRA_FILES = {
    "ruby_tools": {
        "pack.png": art(RUBY, 4),
        "assets/ruby_tools/textures/item/ruby.png": art(RUBY),
        "assets/ruby_tools/textures/item/ruby_sword.png": art(SWORD),
        "assets/ruby_tools/textures/item/ruby_apple.png": art(APPLE),
    },
    "ember_demo": {
        "pack.png": art(WAND, 4),
        "assets/ember_demo/textures/item/ember_wand.png": art(WAND),
    },
    "fabric_style_demo": {
        "assets/fabric_style_demo/icon.png": art(INGOT, 4),
        "assets/fabric_style_demo/textures/item/demo_ingot.png": art(INGOT),
        "assets/fabric_style_demo/textures/item/demo_wand.png": art(WAND),
    },
}


def build(mod_id, out_dir):
    source = HERE / "mods" / mod_id
    target = out_dir / f"{mod_id}.zip"
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(source.rglob("*")):
            if path.is_file():
                archive.write(path, path.relative_to(source).as_posix())
        for name, data in sorted(EXTRA_FILES[mod_id].items()):
            archive.writestr(name, data)
    return target


def main():
    out_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else HERE / "dist"
    out_dir.mkdir(parents=True, exist_ok=True)
    for mod_id in sorted(EXTRA_FILES):
        print("built", build(mod_id, out_dir))


if __name__ == "__main__":
    main()
