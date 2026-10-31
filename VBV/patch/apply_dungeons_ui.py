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


def make_screen_parameter_assignable(text, method_name, param_name):
    """Remove final from the generated navigation parameter because older hooks replace it."""
    pattern = re.compile(
        rf"(\b{re.escape(method_name)}\s*\(\s*)final\s+((?:[\w.$]+\.)?Screen(?:\s*<[^>]+>)?\s+{re.escape(param_name)}\s*\))"
    )
    updated, count = pattern.subn(r"\1\2", text, count=1)
    if count:
        print(f"[VBV Dungeons UI] Removed final from {method_name}({param_name}).")
    return updated


def inject_once(text, marker, code, method_name, param_name):
    if marker in text:
        return text
    pattern = re.compile(
        rf"(\b{re.escape(method_name)}\s*\(\s*(?:final\s+)?(?:[\w.$]+\.)?Screen(?:\s*<[^>]+>)?\s+{re.escape(param_name)}\s*\)\s*\{{)"
    )
    match = pattern.search(text)
    if not match:
        raise RuntimeError(f"Could not find body of {method_name}({param_name})")
    return text[:match.end()] + "\n        " + code + "\n" + text[match.end():]


def _new_screen_expressions(text):
    """Return (class_name, argument_text) for balanced new X(...) expressions."""
    out = []
    for match in re.finditer(r"\bnew\s+([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)\s*\(", text):
        start = match.end()
        depth = 1
        i = start
        while i < len(text) and depth:
            if text[i] == '(':
                depth += 1
            elif text[i] == ')':
                depth -= 1
            i += 1
        if depth == 0:
            out.append((match.group(1), text[start:i - 1].strip()))
    return out


def _pick_screen_expression(title_text, preferred_names):
    """Pick the actual menu destination, not an unrelated Eagler helper screen."""
    expressions = _new_screen_expressions(title_text)
    preferred = {name.lower() for name in preferred_names}
    for class_name, args in expressions:
        simple = class_name.rsplit(".", 1)[-1]
        if simple.lower() not in preferred:
            continue
        adapted = re.sub(r"\bthis\.minecraft\.options\b", "Minecraft.getInstance().options", args)
        adapted = re.sub(r"\bthis\.options\b", "Minecraft.getInstance().options", adapted)
        adapted = re.sub(r"\bminecraft\.options\b", "Minecraft.getInstance().options", adapted)
        adapted = re.sub(r"\bthis\b", "parent", adapted)
        if not re.search(r"\b(?:this|minecraft|options)\b", adapted):
            return class_name, adapted
    return None


def _screen_imports(title_text):
    return {simple: full for full, simple in re.findall(
        r"^\s*import\s+([\w.$]+\.([A-Za-z_$][\w$]*));", title_text, re.M
    )}


def configure_navigation_helpers(method):
    nav_files = list(GAME.rglob("VBVNav.java"))
    if not nav_files:
        return
    title_files = list(GAME.rglob("TitleScreen.java"))
    if not title_files:
        print("[VBV Dungeons UI] TitleScreen.java not found; keeping navigation fallbacks.")
        return
    title_text = title_files[0].read_text(encoding="utf-8")
    imports = _screen_imports(title_text)
    targets = {
        "openMultiplayer": _pick_screen_expression(title_text, ("JoinMultiplayerScreen",)),
        "openSingleplayer": _pick_screen_expression(title_text, ("SelectWorldScreen",)),
        "openOptions": _pick_screen_expression(title_text, ("OptionsScreen",)),
    }
    package_match = re.search(r"^\s*package\s+([\w.]+);", title_text, re.M)
    title_package = package_match.group(1) if package_match else "net.minecraft.client.gui.screens"

    def fqcn(class_name):
        if "." in class_name:
            return class_name
        return imports.get(class_name, f"{title_package}.{class_name}")

    def body(name):
        target = targets[name]
        if not target:
            return "open(parent);"
        class_name, args = target
        return f"Minecraft.getInstance().{method}(new {fqcn(class_name)}({args}));"

    generated = f'''package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Eagler 26.2-compatible navigation generated from the actual TitleScreen. */
public final class VBVNav {{
    private VBVNav() {{}}

    public static void open(Screen screen) {{
        Minecraft.getInstance().{method}(screen);
    }}

    public static void openMultiplayer(Screen parent) {{
        {body("openMultiplayer")}
    }}

    public static void openSingleplayer(Screen parent) {{
        {body("openSingleplayer")}
    }}

    public static void openOptions(Screen parent) {{
        {body("openOptions")}
    }}
}}
'''
    nav_files[0].write_text(generated, encoding="utf-8")
    print("[VBV Dungeons UI] Navigation targets:",
          {key: value[0] if value else "fallback" for key, value in targets.items()})


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
    pattern = re.compile(r"private static final List<Identifier> BACKDROPS = List\.of\(.*?\n    \);", re.S)
    replacement = "private static final List<Identifier> BACKDROPS = List.of(\n        " + names + "\n    );"
    updated, count = pattern.subn(replacement, text, count=1)
    if count != 1:
        raise RuntimeError("Could not configure VBVServerLoadingScreen BACKDROPS list")
    LOADING_CLASS.write_text(updated, encoding="utf-8")


minecraft = find_minecraft()
text = minecraft.read_text(encoding="utf-8")
method, param = find_screen_method(text)
text = make_screen_parameter_assignable(text, method, param)
configure_navigation_helpers(method)

title_marker = "VBV_DUNGEONS_TITLE_HOOK"
title_code = (
    f"// {title_marker}\n"
    f"if ({param} instanceof net.minecraft.client.gui.screens.TitleScreen && !({param} instanceof com.melonman106.vbvclient.VBVDungeonsTitleScreen)) {{\n"
    f"    this.{method}(new com.melonman106.vbvclient.VBVDungeonsTitleScreen());\n"
    f"    return;\n"
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
        f"    this.{method}(new com.melonman106.vbvclient.VBVServerLoadingScreen());\n"
        f"    return;\n"
        f"}}"
    )
    text = inject_once(text, loading_marker, loading_code, method, param)

minecraft.write_text(text, encoding="utf-8")
print("[VBV Dungeons UI] Patched Minecraft screen navigation:", method, param)
