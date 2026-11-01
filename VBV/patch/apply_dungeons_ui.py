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

    # Minecraft cannot load animated GIFs directly, so extract the user's
    # slime.gif into PNG frames and animate those frames in VBVServerLoadingScreen.
    gif = ASSETS / "slime.gif"
    frames_dir = target / "slime"
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
                raise RuntimeError("slime.gif is present but neither Pillow nor ImageMagick is available")
            subprocess.run([convert, str(gif), str(frames_dir / "frame_%d.png")], check=True)
    else:
        print("[VBV Dungeons UI] slime.gif missing; loading text will remain but no slime animation will be available.")

    overlay = CLIENT_SIDE / "panorama/panorama_overlay.png"
    if overlay.is_file():
        panorama_target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(overlay, panorama_target)
    return backgrounds


def configure_slime_frames():
    frames_dir = RES / "assets/vbvclient/textures/loadingscreen/slime"
    frames = sorted(frames_dir.glob("frame_*.png"), key=lambda p: int(p.stem.split("_")[-1]))
    if not LOADING_CLASS.is_file() or not frames:
        return
    text = LOADING_CLASS.read_text(encoding="utf-8")
    start = text.find("    private static Identifier[] buildSlimeFrames() {")
    if start < 0:
        raise RuntimeError("Could not find buildSlimeFrames() in VBVServerLoadingScreen")
    end = text.find("\n    }", start) + len("\n    }")
    if end < len("\n    }"):
        raise RuntimeError("Could not find end of buildSlimeFrames()")
    lines = ",\n            ".join(
        'Identifier.parse("vbvclient:textures/loadingscreen/slime/' + frame.name + '")'
        for frame in frames
    )
    replacement = "    private static Identifier[] buildSlimeFrames() {\n        return new Identifier[] {\n            " + lines + "\n        };\n    }"
    text = text[:start] + replacement + text[end:]
    LOADING_CLASS.write_text(text, encoding="utf-8")
    print("[VBV Dungeons UI] Configured " + str(len(frames)) + " slime GIF frames.")


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
configure_slime_frames()
# Keep the real ConnectScreen alive: it owns connection polling, status updates,
# error handling and the cancel button. Only replace its background artwork.
if backgrounds:
    connect_files = list(GAME.rglob("ConnectScreen.java"))
    if not connect_files:
        raise RuntimeError("ConnectScreen.java not found for custom loading background")
    connect_screen = next(
        (p for p in connect_files if p.as_posix().endswith("/net/minecraft/client/gui/screens/ConnectScreen.java")),
        connect_files[0],
    )
    connect_text = connect_screen.read_text(encoding="utf-8")
    connect_marker = "VBV_DUNGEONS_CONNECT_BACKGROUND"
    if connect_marker not in connect_text:
        render_pattern = re.compile(
            r"(\bextractRenderState\s*\(\s*(?:final\s+)?(?:[\w.$]+\.)?GuiGraphicsExtractor\s+(\w+)[^)]*\)\s*\{)"
        )
        render_match = render_pattern.search(connect_text)
        if not render_match:
            raise RuntimeError("Could not locate ConnectScreen.extractRenderState for custom background")
        graphics_name = render_match.group(2)
        draw_code = (
            f"\n        // {connect_marker}\n"
            f"        com.melonman106.vbvclient.VBVServerLoadingScreen.renderConnectionBackground({graphics_name}, this.width, this.height);"
        )
        connect_text = connect_text[:render_match.end()] + draw_code + connect_text[render_match.end():]

    status_pattern = re.compile(
        r"graphics\.centeredText\(this\.font,\s*this\.status,\s*this\.width\s*/\s*2,\s*this\.height\s*/\s*2\s*-\s*50,\s*-1\);"
    )
    status_replacement = (
        "com.melonman106.vbvclient.VBVServerLoadingScreen.renderConnectionStatus("
        "graphics, this.font, this.width, this.height);"
    )
    connect_text, status_count = status_pattern.subn(status_replacement, connect_text, count=1)
    if status_count != 1:
        raise RuntimeError("Could not replace ConnectScreen status text with VBV animated loading text")

    connect_screen.write_text(connect_text, encoding="utf-8")
    print("[VBV Dungeons UI] Added custom background and animated connection status to the real ConnectScreen.")

minecraft.write_text(text, encoding="utf-8")
print("[VBV Dungeons UI] Patched Minecraft screen navigation:", method, param)
