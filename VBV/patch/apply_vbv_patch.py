#!/usr/bin/env python3
"""Stable VBV patch runner.

The full patcher is pinned to the last known-good implementation so a bad
large-file edit cannot silently remove the game/source injection stages.
Small local compatibility fixes are applied in memory before execution.
"""
from pathlib import Path
from urllib.request import Request, urlopen

PINNED = (
    "https://raw.githubusercontent.com/melonman106/Eagler-26.2-Client/"
    "284ff62a345779f4773dca86a6881bc7a6df7335/VBV/patch/apply_vbv_patch.py"
)

req = Request(PINNED, headers={"User-Agent": "Eagler-26.2-Client-VBV-Patcher"})
with urlopen(req, timeout=30) as response:
    source = response.read().decode("utf-8")

# Use the new dedicated Shulker Box Tooltip icon and the MCDW PackIcons asset.
source = source.replace(
    '"shulkerboxtooltip": "ModMenu.png",',
    '"shulkerboxtooltip": "ShulkerBoxTooltips.jpeg",\n    "mcdw": "MCDW.png",',
    1,
)
source = source.replace(
    '        if not icon.is_file():\n            fallback = CLIENT_SIDE / "pack.png"',
    '        if not icon.is_file():\n'
    '            explicit_icon = PACK_ICON_DIR / f"{mod_id}.png"\n'
    '            stem_icon = PACK_ICON_DIR / f"{zippath.stem}.png"\n'
    '            fallback = explicit_icon if explicit_icon.is_file() else stem_icon if stem_icon.is_file() else CLIENT_SIDE / "pack.png"',
    1,
)

# The original patcher already normalizes every icon to 64x64. Keep its full
# native VBV/source patching logic intact; this wrapper only prevents accidental
# loss of that large implementation and applies the current icon additions.
exec(compile(source, str(Path(__file__).resolve()), "exec"), globals(), globals())
