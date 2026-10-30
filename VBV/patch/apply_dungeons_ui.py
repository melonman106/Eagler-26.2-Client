#!/usr/bin/env python3
"""Inject VBV's Dungeons II-style title and multiplayer loading screens."""
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(sys.argv[1]).resolve()
CLIENT_SIDE = Path(__file__).resolve().parent.parent
GAME = ROOT / "game/src/main/java"
RES = ROOT / "game/src/main/resources"
ASSETS = CLIENT_SIDE / "LoadingScreen"
LOADING_CLASS = CLIENT_SIDE / "src/main/java/com/melonman106/vbvclient/VBVServerLoadingScreen.java"


def find_minecraft():
    candidates = list(GAME.rglob("Minecraft.java"))
    if not candidates:
        raise RuntimeError("Minecraft.java not found for Dungeons UI hooks")
    return next((p for p in candidates if p.as_posix().endswith("/net/minecraft/client/Minecraft.java")), candidates[0])


def find_screen_method(text):
    for candidate in ("setScreen", "setScreenAndShow"):
        match = re.search(
            r"\b" + re.escape(candidate) + r"\s*\(\s*(?:final\s+)?(?:[\w.$]+\.)?Screen(?:\s*<[^>]+>)?\s+(\w+)",
            text,
        )
        if match:
            return candidate, match.group(1)
    match = re.search(
        r"\b(?:public|protected|private)\s+(?:(?:static|final|synchronized|native|abstract)\s+)*"
        r"[\w.$<>?,\[\]\s]+\s+(\w+)\s*\(\s*(?:final\s+)?(?:[\w.$]+\.)?Screen(?:\s*<[^>]+>)?\s+(\w+)",
        text,
    )
    if not match:
        raise RuntimeError("Could not locate Minecraft Screen navigation method")
    return match.group(1), match.group(2)


def inject_once(text, marker, code, method_name, param_name):
    if marker in text:
        return text
    pattern = re.compile(
        r"(\b(?:public|protected|private)\s+(?:(?:static|final|synchronized|native|abstract)\s+)*"
        r"[\w.$<>?,\[\]\s]+\s+" + re.escape(method_name) +
        r"\s*\(\s*(?:final\s+)?(?:[\w.$]+\.)?Screen(?:\s*<[^>]+>)?\s+" +
        re.escape(param_name) + r"\s*\)\s*\{)",
    )
    match = pattern.search(text)
    if not match:
        raise RuntimeError(f"Could not find body of {method_name}({param_name})")
    return text[:match.end()] + "\n        " + code + "\n" + text[match.end():]


def prepare_assets():
    target = RES / "assets/vbvclient/textures/loadingscreen"
    panorama_target = RES / "assets/vbvclient/textures/panorama/panorama_overlay.png"

    backgrounds = []
    for i in range(1, 4):
        source = ASSETS / f"LoadingScreen{i}.png"
        if source.is_file():
            backgrounds.append(source)

    if not backgrounds:
        print("[VBV Dungeons UI] No LoadingScreen*.png assets found; using vanilla connection screen.")
        return []

    target.mkdir(parents=True, exist_ok=True)
    for source in backgrounds:
        shutil.copy2(source, target / source.name.lower())

    for i in range(1, 4):
        expected = target / f"loadingscreen{i}.png"
        if not (ASSETS / f"LoadingScreen{i}.png").is_file():
            expected.unlink(missing_ok=True)

    gif = ASSETS / "Walking.gif"
    frames_dir = target / "walking"
    if gif.is_file():
        frames_dir.mkdir(parents=True, exist_ok=True)
        for old in frames_dir.glob("frame_*.png"):
            old.unlink()
        try:
            from PIL import Image
            with Image.open(gif) as image:
                count = getattr(image, "n_frames", 1)
                for i in range(count):
                    image.seek(i)
                    image.convert("RGBA").save(frames_dir / f"frame_{i}.png")
        except Exception:
            convert = shutil.which("magick") or shutil.which("convert")
            if not convert:
                raise RuntimeError("Walking.gif is present but neither Pillow nor ImageMagick is available")
            subprocess.run([convert, str(gif), str(frames_dir / "frame_%d.png")], check=True)
    else:
        print("[VBV Dungeons UI] Walking.gif missing; loading text will remain but no creeper animation will be available.")

    overlay = CLIENT_SIDE / "panorama/panorama_overlay.png"
    if overlay.is_file():
        panorama_target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(overlay, panorama_target)
    return backgrounds


def configure_loading_class(backgrounds):
    if not backgrounds or not LOADING_CLASS.is_file():
        return
    names = ",\n        ".join(
        f'Identifier.parse("vbvclient:textures/loadingscreen/{p.name.lower()}")'
        for p in backgrounds
    )
    text = LOADING_CLASS.read_text(encoding="utf-8")
    pattern = re.compile(
        r"private static final List<Identifier> BACKDROPS = List\.of\(.*?\n    \);",
        re.S,
    )
    replacement = "private static final List<Identifier> BACKDROPS = List.of(\n        " + names + "\n    );"
    updated, count = pattern.subn(replacement, text, count=1)
    if count != 1:
        raise RuntimeError("Could not configure VBVServerLoadingScreen BACKDROPS list")
    LOADING_CLASS.write_text(updated, encoding="utf-8")


minecraft = find_minecraft()
text = minecraft.read_text(encoding="utf-8")
method, param = find_screen_method(text)

title_marker = "VBV_DUNGEONS_TITLE_HOOK"
title_code = (
    f"// {title_marker}\n"
    f"if ({param} instanceof net.minecraft.client.gui.screens.TitleScreen && !({param} instanceof com.melonman106.vbvclient.VBVDungeonsTitleScreen)) {{\n"
    f"    {param} = new com.melonman106.vbvclient.VBVDungeonsTitleScreen();\n"
    f"}}"
)
text = inject_once(text, title_marker, title_code, method, param)

backgrounds = prepare_assets()
configure_loading_class(backgrounds)
loading_marker = "VBV_DUNGEONS_LOADING_HOOK"
if backgrounds:
    loading_code = (
        f"// {loading_marker}\n"
        f"if ({param} instanceof net.minecraft.client.gui.screens.ConnectScreen && !({param} instanceof com.melonman106.vbvclient.VBVServerLoadingScreen)) {{\n"
        f"    {param} = new com.melonman106.vbvclient.VBVServerLoadingScreen();\n"
        f"}}"
    )
    text = inject_once(text, loading_marker, loading_code, method, param)

minecraft.write_text(text, encoding="utf-8")
print("[VBV Dungeons UI] Patched Minecraft screen navigation:", method, param)
