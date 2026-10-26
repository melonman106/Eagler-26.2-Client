#!/usr/bin/env python3
"""Stable VBV patch runner.

The full patcher is pinned to the last known-good implementation so a bad
large-file edit cannot silently remove the game/source injection stages.
Small local compatibility fixes are applied in memory before execution.
"""
from pathlib import Path
from urllib.request import Request, urlopen

CLIENT_SIDE = Path(__file__).resolve().parent.parent

# Fix the native Mod Menu icon source rectangle before the pinned patcher copies
# the Java sources into the generated Eagler workspace. The renderer displays a
# 32x32 icon but the source image is a complete 64x64 texture; using 32x32 as
# the source rectangle only showed the upper-left quarter.
screen = CLIENT_SIDE / "src/main/java/com/melonman106/vbvclient/VBVModsScreen.java"
if screen.is_file():
    text = screen.read_text(encoding="utf-8")
    fixed = text.replace(
        "graphics.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0.0F, 0.0F,\n                size, size, 64, 64);",
        "graphics.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0.0F, 0.0F,\n                64, 64, 64, 64);",
        1,
    )
    if fixed != text:
        screen.write_text(fixed, encoding="utf-8")
        print("Fixed VBV Mod Menu icon source rectangle (full 64x64 texture).")

# Use the Unique Dark HUD textures when the Armor HUD is rendered. The pack is
# imported by the normal resource-pack extraction stage, so these identifiers
# resolve to the files supplied in ModsPack/Unique Dark - Lite - 1.20.2-26.x.zip.
armor = CLIENT_SIDE / "src/main/java/com/melonman106/vbvclient/VBVArmorHud.java"
if armor.is_file():
    text = armor.read_text(encoding="utf-8")
    fixed = text.replace(
        'private static final Identifier WARNING_TEXTURE = Identifier.parse("vbvclient:textures/mods/warn.png");',
        'private static final Identifier WARNING_TEXTURE = Identifier.parse("vbvclient:textures/armor_hud/exclamation_marks.png");',
        1,
    )
    fixed = fixed.replace(
        '    private static final Identifier HOTBAR_SPRITE = Identifier.withDefaultNamespace("hud/hotbar");',
        '    private static final Identifier HOTBAR_SPRITE = Identifier.withDefaultNamespace("hud/hotbar");\n    private static final Identifier UNIQUE_DARK_HOTBAR_TEXTURE = Identifier.parse("vbvclient:textures/armor_hud/hotbar_texture.png");',
        1,
    )
    old_hotbar = '''            case VBVArmorHudConfig.STYLE_HOTBAR -> {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE, 182, 22, 0, 0, 0, 0, textureWidth - 3, SIZE, 0xFFFFFFFF);
                g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE, 182, 22, 179, 0, textureWidth - 3, 0, 3, SIZE, 0xFFFFFFFF);
            }'''
    new_hotbar = '''            case VBVArmorHudConfig.STYLE_HOTBAR -> {
                int drawWidth = Math.min(textureWidth, 182);
                g.blit(RenderPipelines.GUI_TEXTURED, UNIQUE_DARK_HOTBAR_TEXTURE,
                    rect[0], rect[1], 0, 0, drawWidth, SIZE, 182, 22);
            }'''
    fixed = fixed.replace(old_hotbar, new_hotbar, 1)
    if fixed != text:
        armor.write_text(fixed, encoding="utf-8")
        print("Applied Unique Dark Armor HUD hotbar/warning textures.")

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

exec(compile(source, str(Path(__file__).resolve()), "exec"), globals(), globals())
