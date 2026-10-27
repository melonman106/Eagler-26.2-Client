#!/usr/bin/env python3
"""Post-process the pinned VBV patch with fixes that must not be overwritten by it."""
from pathlib import Path
import re
import shutil
import sys

ROOT = Path(sys.argv[1]).resolve()
SRC = Path(__file__).resolve().parent.parent / "src/main/java/com/melonman106/vbvclient"
GAME = ROOT / "game/src/main/java"
RES = ROOT / "game/src/main/resources"


def java_files_named(name):
    return list(GAME.rglob(name))


def remove_brace_block(text, needle):
    start = text.find(needle)
    if start < 0:
        return text, False
    brace = text.find("{", start)
    if brace < 0:
        return text, False
    depth = 0
    in_string = False
    escaped = False
    i = brace
    while i < len(text):
        c = text[i]
        if in_string:
            if escaped:
                escaped = False
            elif c == "\\":
                escaped = True
            elif c == '"':
                in_string = False
        else:
            if c == '"':
                in_string = True
            elif c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
                if depth == 0:
                    end = i + 1
                    if end < len(text) and text[end] == "\n":
                        end += 1
                    return text[:start] + text[end:], True
        i += 1
    return text, False

# The pinned patch still installs Shine lighting/bloom. Remove every generated
# Shine hook and its resources so the unstable post-processing path is absent.
for path in java_files_named("LightmapRenderStateExtractor.java"):
    text = path.read_text(encoding="utf-8")
    changed = False
    for needle in (
        "if (com.melonman106.vbvclient.ShineConfig.enabled())",
        "if (com.melonman106.vbvclient.ShineConfig.disabledShading())",
    ):
        while needle in text:
            text, did = remove_brace_block(text, needle)
            changed |= did
    if changed:
        path.write_text(text, encoding="utf-8")
        print("Removed Shine lightmap hooks from", path)

for path in java_files_named("GameRenderer.java"):
    text = path.read_text(encoding="utf-8")
    changed = False
    while "if (com.melonman106.vbvclient.ShineConfig.bloom()" in text:
        text, did = remove_brace_block(text, "if (com.melonman106.vbvclient.ShineConfig.bloom()")
        changed |= did
    text2 = re.sub(r"\s*private boolean vbvShineBloomFailed;\n", "\n", text)
    changed |= text2 != text
    if changed:
        path.write_text(text2, encoding="utf-8")
        print("Removed Shine post-processing hook from", path)

for path in RES.rglob("*"):
    if path.is_file() and ("shine_bloom" in path.name or "vbvclient/post_effect" in str(path) or "vbvclient/shaders/post" in str(path)):
        path.unlink()

# Keep the generated source synchronized with the current native sources after
# the pinned patch has copied them.
dest = GAME / "com/melonman106/vbvclient"
for source in SRC.rglob("*.java"):
    target = dest / source.relative_to(SRC)
    target.parent.mkdir(parents=True, exist_ok=True)
    if source.name in {"VBVClient.java", "VBVModsScreen.java", "VBVAppleSkin.java", "VBVArmorHudConfig.java"}:
        shutil.copy2(source, target)

# Armor HUD: smaller icons, wider spacing, reliable local-player lookup, and a
# safe default without the vanilla sprite-slicing frame that was failing in Eagler.
armor = dest / "VBVArmorHud.java"
if armor.is_file():
    text = armor.read_text(encoding="utf-8")
    replacements = {
        "private static final int STEP = 20;": "private static final int STEP = 24;",
        "private static final int SIZE = 22;": "private static final int SIZE = 18;",
        "private static final int HOTBAR_OFFSET = 98;": "private static final int HOTBAR_OFFSET = 100;",
        "if (!(minecraft.getCameraEntity() instanceof Player player)) return;": "Player player = minecraft.player;\n        if (player == null) return;",
        "VBVArmorHudConfig.enabled = false;": "System.err.println(\"VBV Armor HUD render error: \" + t);",
    }
    for old, new in replacements.items():
        text = text.replace(old, new, 1)
    armor.write_text(text, encoding="utf-8")

config = dest / "VBVArmorHudConfig.java"
if config.is_file():
    text = config.read_text(encoding="utf-8").replace(
        "style = STYLE_HOTBAR;", "style = STYLE_NONE;", 1
    )
    config.write_text(text, encoding="utf-8")

# Optional panorama overlay: preserve the six-face panorama handling and also
# copy panorama_overlay.png when supplied by the user.
panorama_src = Path(__file__).resolve().parent.parent / "panorama" / "panorama_overlay.png"
panorama_dst = RES / "assets/minecraft/textures/gui/title/background/panorama_overlay.png"
if panorama_src.is_file():
    panorama_dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(panorama_src, panorama_dst)
    print("Installed optional panorama_overlay.png")

# Never let an imported pack replace Eagler's font atlas. The pre-build workflow
# sanitizes the ZIPs before the pinned importer runs; this check documents the
# invariant for the generated resource tree.
font_root = RES / "assets/minecraft/textures/font"
if font_root.exists():
    print("Minecraft font resource directory present:", font_root)

print("Post-VBV fixes complete.")
