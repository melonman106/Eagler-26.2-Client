#!/usr/bin/env python3
import os
import sys

# Never let files next to this script (e.g. a stray zipfile.py) shadow the stdlib.
_here = os.path.dirname(os.path.abspath(__file__))
sys.path[:] = [p for p in sys.path if os.path.abspath(p or os.getcwd()) != _here]

import json
import pathlib
import re
import shutil
import struct
import zlib
import zipfile

ROOT = pathlib.Path(sys.argv[1]).resolve()

CLIENT_SIDE = pathlib.Path(__file__).resolve().parent.parent
SOURCE = CLIENT_SIDE / "src/main/java/com/melonman106/vbvclient"

if not SOURCE.is_dir():
    raise SystemExit(f"VBV source directory missing: {SOURCE}")
if not ROOT.is_dir():
    raise SystemExit(f"Generated project directory missing: {ROOT}")

def find_source_root():
    for p in sorted(ROOT.rglob("Minecraft.java")):
        parts = p.parts
        if "build" in parts:
            continue
        if p.parent.name == "client" and p.parent.parent.name == "minecraft":
            return p.parents[3]
    return None

TARGET = find_source_root()
if TARGET is None:
    print("Project tree (top levels) for debugging:")
    for p in sorted(ROOT.glob("*")) + sorted(ROOT.glob("*/*")):
        print("  ", p.relative_to(ROOT))
    raise SystemExit("Could not find net/minecraft/client/Minecraft.java in the generated project")

print("Using game source root:", TARGET)

# Preserve upstream attribution while adding the custom client modder credit
# beside the existing "Made by o_xer" main-menu line.
version_files = list(ROOT.rglob("EaglercraftVersion.java"))
version_file = next(
    (p for p in version_files if "platform" in p.parts and "src" in p.parts),
    None
)
if version_file is None:
    raise SystemExit("EaglercraftVersion.java was not found; cannot add main-menu modder credit")
version_src = version_file.read_text(encoding="utf-8")
old_credit = 'public static final String mainMenuStringF = "Made by " + projectForkVendor;'
new_credit = 'public static final String mainMenuStringF = "Made by " + projectForkVendor + " | Modded by Melonman106";'
if new_credit in version_src:
    print("Main-menu modder credit already present.")
elif old_credit in version_src:
    version_file.write_text(version_src.replace(old_credit, new_credit, 1), encoding="utf-8")
    print("Added 'Modded by Melonman106' beside the existing upstream main-menu credit.")
else:
    raise SystemExit("Expected mainMenuStringF attribution was not found; refusing to patch an unknown version file")
DEST = TARGET / "com/melonman106/vbvclient"
DEST.mkdir(parents=True, exist_ok=True)
source_files = sorted(SOURCE.rglob("*.java"))
for src in source_files:
    relative = src.relative_to(SOURCE)
    target = DEST / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, target)
print("Copied", len(source_files), "VBV Java files (including subpackages) to", DEST)

REPO_ROOT = CLIENT_SIDE.parent
MODS_PACK = REPO_ROOT / "ModsPack"
GENERATED_PACK_CLASS = DEST / "VBVGeneratedPackMods.java"
RESOURCE_ROOT = TARGET.parent / "resources"
GENERATED_PACK_RESOURCE_ROOT = RESOURCE_ROOT / "assets" / "vbvclient" / "mods"

 
def png_chunk(kind, payload):
    return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload) & 0xffffffff)

def write_custom_panorama_face(path, face):
    # Small dependency-free pixel-art skybox. All six faces share the same
    # horizon palette so the game's default title panorama is visibly custom
    # without requiring an external image or renderer library in Actions.
    width = height = 256
    rows = []
    for y in range(height):
        row = bytearray([0])
        for x in range(width):
            sky = y < 154
            if sky:
                t = y / 153.0
                base = (int(45 + 75 * t), int(112 + 95 * t), int(190 + 50 * t))
                # Broad pixelated clouds, shifted subtly across cube faces.
                cloud_x = (x + face * 19) % 256
                cloud = ((y < 66 and ((cloud_x // 24 + y // 11) % 5 in (0, 1)))
                         or (y < 95 and ((cloud_x // 31 + y // 13 + face) % 7 == 2)))
                if cloud:
                    base = (min(255, base[0] + 55), min(255, base[1] + 49), min(255, base[2] + 36))
                color = base
            else:
                # Blocky distant hills, green valley and darker foreground.
                xx = (x + face * 7) % 256
                ridge = 150 + ((xx // 16 * 7 + (xx // 37) * 11 + (xx // 9) % 13) % 29)
                if y < ridge:
                    color = (65, 103, 105)
                elif y < 190:
                    color = (51, 111, 66)
                elif y < 224:
                    color = (39, 91, 49)
                else:
                    color = (31, 69, 42)
                # Pixelated lighter grass patches on the valley floor.
                if 180 <= y < 220 and ((xx // 12 + y // 8 + face) % 9 == 0):
                    color = (72, 129, 63)
            row.extend(color)
        rows.append(bytes(row))
    raw = b"".join(rows)
    data = b"\x89PNG\r\n\x1a\n"
    data += png_chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
    data += png_chunk(b"IDAT", zlib.compress(raw, 9))
    data += png_chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)

panorama_dir = RESOURCE_ROOT / "assets" / "minecraft" / "textures" / "gui" / "title" / "background"
uploaded_panorama_dir = CLIENT_SIDE / "panorama"
uploaded_faces = [uploaded_panorama_dir / f"panorama_{i}.png" for i in range(6)]

if any(path.is_file() for path in uploaded_faces):
    missing_faces = [path.name for path in uploaded_faces if not path.is_file()]
    if missing_faces:
        raise SystemExit(
            "Custom panorama is incomplete in VBV/panorama/. Upload all six files "
            "panorama_0.png through panorama_5.png. Missing: " + ", ".join(missing_faces)
        )
    for source_face in uploaded_faces:
        with source_face.open("rb") as stream:
            signature = stream.read(8)
        if signature != b"\x89PNG\r\n\x1a\n":
            raise SystemExit(f"Custom panorama face is not a PNG file: {source_face}")
        panorama_dir.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source_face, panorama_dir / source_face.name)
    print("Using all six uploaded custom panorama faces from", uploaded_panorama_dir)
else:
    for panorama_face in range(6):
        write_custom_panorama_face(panorama_dir / f"panorama_{panorama_face}.png", panorama_face)
    print("No uploaded panorama found; generated six default pixel-art panorama faces in", panorama_dir)


def java_string(value):
    return json.dumps(str(value), ensure_ascii=False)

def text_from_component(value):
    if isinstance(value, str):
        return value
    if isinstance(value, list):
        return "".join(text_from_component(v) for v in value)
    if isinstance(value, dict):
        if "text" in value:
            return text_from_component(value["text"])
        if "translate" in value:
            return str(value["translate"])
        if "extra" in value:
            return text_from_component(value["extra"])
    return str(value)

def safe_mod_id(stem):
    value = re.sub(r"[^a-zA-Z0-9._-]+", "-", stem.strip().lower())
    value = re.sub(r"-+", "-", value).strip("-._")
    return value or "resource-pack-mod"

def display_name(stem):
    value = re.sub(r"[_-]+", " ", stem).strip()
    return value or "Resource Pack Mod"

def find_zip_root(names):
    normalized = [n.replace("\\", "/").lstrip("/") for n in names]
    for name in normalized:
        if name == "pack.mcmeta":
            return ""
    for prefix in sorted({n.split("/", 1)[0] for n in normalized if "/" in n}):
        if f"{prefix}/pack.mcmeta" in normalized:
            return prefix + "/"
    return ""

def read_pack_metadata(zf, root):
    description = None
    meta_name = root + "pack.mcmeta"
    try:
        raw = zf.read(meta_name).decode("utf-8")
        meta = json.loads(raw)
        pack = meta.get("pack", {}) if isinstance(meta, dict) else {}
        if isinstance(pack, dict) and "description" in pack:
            description = text_from_component(pack["description"]).strip()
    except (KeyError, UnicodeDecodeError, json.JSONDecodeError):
        pass
    return description

def extract_resource_pack(zippath, mod_id):
    # The old code nested pack assets below resources/vbvclient/mods/<id>,
    # which is not a resource namespace Minecraft scans. Merge actual assets
    # into resources/assets/<namespace>/... and keep each pack icon in VBV's
    # own namespace so the native mod list can display it.
    icon_destination = GENERATED_PACK_RESOURCE_ROOT / mod_id
    if icon_destination.exists():
        shutil.rmtree(icon_destination)
    icon_destination.mkdir(parents=True, exist_ok=True)

    with zipfile.ZipFile(zippath) as zf:
        names = [n.replace("\\", "/") for n in zf.namelist()]
        root = find_zip_root(names)
        description = read_pack_metadata(zf, root)
        extracted = 0

        for raw_name in names:
            name = raw_name.lstrip("/")
            if not name.startswith(root):
                continue
            relative = name[len(root):]
            if not relative or relative.endswith("/"):
                continue

            if relative == "pack.png":
                target = icon_destination / "pack.png"
            elif relative.startswith("assets/"):
                target = RESOURCE_ROOT / relative
            else:
                # Client resource loading does not consume datapack data files.
                continue

            target.parent.mkdir(parents=True, exist_ok=True)
            try:
                # Partial language files must not replace Minecraft's full locale.
                # Otherwise vanilla buttons show keys such as menu.singleplayer.
                if (relative.startswith("assets/") and "/lang/" in relative
                        and relative.lower().endswith(".json") and target.is_file()):
                    try:
                        existing = json.loads(target.read_text(encoding="utf-8"))
                        with zf.open(raw_name) as src:
                            incoming = json.loads(src.read().decode("utf-8"))
                        if isinstance(existing, dict) and isinstance(incoming, dict):
                            existing.update(incoming)
                            target.write_text(json.dumps(existing, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
                        else:
                            with zf.open(raw_name) as src, target.open("wb") as dst:
                                shutil.copyfileobj(src, dst)
                    except (UnicodeDecodeError, json.JSONDecodeError):
                        with zf.open(raw_name) as src, target.open("wb") as dst:
                            shutil.copyfileobj(src, dst)
                else:
                    with zf.open(raw_name) as src, target.open("wb") as dst:
                        shutil.copyfileobj(src, dst)
                extracted += 1
            except KeyError:
                continue

        if extracted == 0:
            raise SystemExit(f"{zippath.name}: ZIP contains no supported resource-pack assets")
        return description

pack_entries = []
if MODS_PACK.is_dir():
    zip_files = sorted(
        p for p in MODS_PACK.rglob("*.zip")
        if p.is_file() and "__MACOSX" not in p.parts
    )
    print("ModsPack ZIPs found:", len(zip_files))

    for zippath in zip_files:
        mod_id = safe_mod_id(zippath.stem)
        description = extract_resource_pack(zippath, mod_id)
        if not description:
            description = f"Resource-pack client mod imported from {zippath.name}."
        name = display_name(zippath.stem)

        icon = GENERATED_PACK_RESOURCE_ROOT / mod_id / "pack.png"
        if not icon.is_file():
            fallback = CLIENT_SIDE / "pack.png"
            if fallback.is_file():
                shutil.copy2(fallback, icon)

        mod_json = GENERATED_PACK_RESOURCE_ROOT / mod_id / "mod.json"
        mod_json.write_text(
            json.dumps({
                "id": mod_id,
                "name": name,
                "version": "1.0.0-26.2",
                "authors": "Resource pack",
                "description": description,
                "pack": True
            }, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8"
        )

        pack_entries.append((mod_id, name, description))
        print(f"Imported resource-pack mod: {name} ({mod_id}) from {zippath.name}")
else:
    print("ModsPack/ does not exist; no uploaded resource-pack mods to import.")

generated_lines = [
    "package com.melonman106.vbvclient;",
    "",
    "/** Generated by apply_vbv_patch.py from ModsPack/*.zip. */",
    "public final class VBVGeneratedPackMods {",
    "    private VBVGeneratedPackMods() {}",
    "",
    "    public static void register() {",
]
for mod_id, name, description in pack_entries:
    generated_lines.extend([
        "        VBVClient.registerMod(new VBVModInfo(",
        f"            {java_string(mod_id)},",
        f"            {java_string(name)},",
        '            "1.0.0-26.2",',
        '            "Resource pack",',
        f"            {java_string(description)},",
        f"            {java_string('vbvclient:mods/' + mod_id + '/pack.png')},",
        "            true",
        "        ));",
    ])
generated_lines.extend([
    "    }",
    "}",
    "",
])
GENERATED_PACK_CLASS.write_text("\n".join(generated_lines), encoding="utf-8")
print("Generated pack-mod registry:", GENERATED_PACK_CLASS)
print("Imported pack mods:", len(pack_entries))

def java_files():
    for p in TARGET.rglob("*.java"):
        try:
            yield p, p.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue

def find_method_end(text, method_start):
    """Return the index of the closing brace for the method containing method_start."""
    brace = text.find("{", method_start)
    if brace < 0:
        return None

    depth = 0
    in_string = False
    in_char = False
    in_line_comment = False
    in_block_comment = False
    escaped = False
    i = brace

    while i < len(text):
        c = text[i]
        n = text[i + 1] if i + 1 < len(text) else ""

        if in_line_comment:
            if c == "\n":
                in_line_comment = False
        elif in_block_comment:
            if c == "*" and n == "/":
                in_block_comment = False
                i += 1
        elif in_string:
            if escaped:
                escaped = False
            elif c == "\\":
                escaped = True
            elif c == '"':
                in_string = False
        elif in_char:
            if escaped:
                escaped = False
            elif c == "\\":
                escaped = True
            elif c == "'":
                in_char = False
        else:
            if c == "/" and n == "/":
                in_line_comment = True
                i += 1
            elif c == "/" and n == "*":
                in_block_comment = True
                i += 1
            elif c == '"':
                in_string = True
            elif c == "'":
                in_char = True
            elif c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
                if depth == 0:
                    return i

        i += 1

    return None

minecraft = None
for p, s in java_files():
    if re.search(r"class\s+Minecraft\b", s) and "static Minecraft" in s:
        minecraft = (p, s)
        break
if not minecraft:
    raise SystemExit("Minecraft class was not identified; VBV would never initialize")

mc_path, mc_src = minecraft
SCREEN_METHOD_RE = re.compile(
    r"(?:public|protected|private)?\s*(?:final\s+)?void\s+(\w+)\(\s*(?:@Nullable\s+)?(?:final\s+)?Screen\s+\w+\s*\)"
)
screen_methods = [m.group(1) for m in SCREEN_METHOD_RE.finditer(mc_src)]
open_name = None
for preferred in ("setScreen", "setScreenAndShow", "openScreen", "showScreen"):
    if preferred in screen_methods:
        open_name = preferred
        break
if open_name is None and screen_methods:
    open_name = screen_methods[0]
if open_name is None:
    raise SystemExit("Could not find a method on Minecraft that takes a single Screen")
print("Screen-opening method on Minecraft:", open_name, "(candidates:", screen_methods, ")")

hud_path = None
hud_src = None
for p, s in java_files():
    if p.name == "Hud.java" and re.search(r"class\s+Hud\b", s):
        hud_path = p
        hud_src = s
        break
if hud_path is None:
    raise SystemExit("Hud.java was not identified; VBV HUD hooks cannot be installed")

HUD_CALL = "com.melonman106.vbvclient.VBVSimpleHud.render(graphics);"
if HUD_CALL not in hud_src:
    hud_match = re.search(
        r"(?:public|private|protected)\s+void\s+extractItemHotbar\s*\([^)]*\)\s*\{",
        hud_src
    )
    if not hud_match:
        raise SystemExit("Hud.extractItemHotbar method was not identified")
    param = re.search(r"GuiGraphicsExtractor\s+(\w+)", hud_match.group(0))
    if not param:
        raise SystemExit("Hud.extractItemHotbar has no GuiGraphicsExtractor parameter")
    HUD_CALL = HUD_CALL.replace("graphics", param.group(1))
    end = find_method_end(hud_src, hud_match.start())
    if end is None:
        raise SystemExit("Could not find end of Hud.extractItemHotbar")
    hud_src = hud_src[:end] + "\n        " + HUD_CALL + "\n    " + hud_src[end:]
    hud_path.write_text(hud_src, encoding="utf-8")
    print("Inserted existing VBV Simple HUD hook into", hud_path)
else:
    print("Existing VBV Simple HUD hook already present.")

ARMOR_CALL = "com.melonman106.vbvclient.VBVArmorHud.render(graphics);"
hud_src = hud_path.read_text(encoding="utf-8")
if ARMOR_CALL in hud_src:
    print("VBV Armor HUD own hook already present.")
else:
    _m = re.search(r"(?:public|private|protected)\s+void\s+extractItemHotbar\s*\([^)]*\)\s*\{", hud_src)
    if not _m:
        raise SystemExit("Hud.extractItemHotbar method was not identified")
    _param = re.search(r"GuiGraphicsExtractor\s+(\w+)", _m.group(0))
    if not _param:
        raise SystemExit("Hud.extractItemHotbar has no GuiGraphicsExtractor parameter")
    ARMOR_CALL = ARMOR_CALL.replace("graphics", _param.group(1))
    _end = find_method_end(hud_src, _m.start())
    if _end is None:
        raise SystemExit("Could not find end of Hud.extractItemHotbar")
    hud_src = hud_src[:_end] + "\n        " + ARMOR_CALL + "\n    " + hud_src[_end:]
    hud_path.write_text(hud_src, encoding="utf-8")
    print("Inserted Armor HUD hook into", hud_path)


# BetterF3-inspired compact debug overlay (toggle with B).
DEBUG_CALL = "com.melonman106.vbvclient.VBVExtraFeatures.renderDebug(graphics);"
hud_src = hud_path.read_text(encoding="utf-8")
if DEBUG_CALL not in hud_src:
    _m = re.search(r"(?:public|private|protected)\s+void\s+extractItemHotbar\s*\([^)]*\)\s*\{", hud_src)
    if not _m:
        raise SystemExit("Hud.extractItemHotbar method was not identified for enhanced debug HUD")
    _param = re.search(r"GuiGraphicsExtractor\s+(\w+)", _m.group(0))
    if not _param:
        raise SystemExit("Hud.extractItemHotbar has no GuiGraphicsExtractor parameter for enhanced debug HUD")
    DEBUG_CALL = DEBUG_CALL.replace("graphics", _param.group(1))
    _end = find_method_end(hud_src, _m.start())
    if _end is None:
        raise SystemExit("Could not find end of Hud.extractItemHotbar for enhanced debug HUD")
    hud_src = hud_src[:_end] + "\n        " + DEBUG_CALL + "\n    " + hud_src[_end:]
    hud_path.write_text(hud_src, encoding="utf-8")
    print("Inserted BetterF3-inspired debug overlay hook into", hud_path)

# AppleSkin-style live food/saturation/exhaustion information overlay.
APPLE_CALL = "com.melonman106.vbvclient.VBVAppleSkin.render(graphics);"
hud_src = hud_path.read_text(encoding="utf-8")
if APPLE_CALL not in hud_src:
    _m = re.search(r"(?:public|private|protected)\s+void\s+extractItemHotbar\s*\([^)]*\)\s*\{", hud_src)
    if not _m:
        raise SystemExit("Hud.extractItemHotbar method was not identified for AppleSkin hook")
    _param = re.search(r"GuiGraphicsExtractor\s+(\w+)", _m.group(0))
    if not _param:
        raise SystemExit("Hud.extractItemHotbar has no GuiGraphicsExtractor parameter for AppleSkin hook")
    APPLE_CALL = APPLE_CALL.replace("graphics", _param.group(1))
    _end = find_method_end(hud_src, _m.start())
    if _end is None:
        raise SystemExit("Could not find end of Hud.extractItemHotbar for AppleSkin hook")
    hud_src = hud_src[:_end] + "\n        " + APPLE_CALL + "\n    " + hud_src[_end:]
    hud_path.write_text(hud_src, encoding="utf-8")
    print("Inserted AppleSkin-style food information hook into", hud_path)


nav = DEST / "VBVNav.java"
nav_src = nav.read_text(encoding="utf-8")
nav.write_text(nav_src.replace(".setScreen(screen)", f".{open_name}(screen)"), encoding="utf-8")

INIT_CALL = "com.melonman106.vbvclient.VBVClient.init();"

def end_of_ctor_call(text, start):
    i = text.index("(", start)
    depth = 0
    while i < len(text):
        c = text[i]
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                break
        i += 1
    j = text.index(";", i)
    return j + 1

if INIT_CALL in mc_src:
    print("VBV initialization hook already present.")
else:
    ctor_re = re.compile(r"(?:public|private|protected)\s+Minecraft\s*\([^)]*\)\s*(?:throws\s+[\w.,\s]+)?\{")
    inserted = False
    for m in ctor_re.finditer(mc_src):
        rest = mc_src[m.end():]
        call = re.match(r"\s*(super|this)\s*\(", rest)
        if call and call.group(1) == "this":
            continue
        if call:
            pos = m.end() + end_of_ctor_call(rest, call.start(1))
        else:
            pos = m.end()
        mc_src = mc_src[:pos] + "\n        " + INIT_CALL + "\n" + mc_src[pos:]
        mc_path.write_text(mc_src, encoding="utf-8")
        print("Inserted VBV initialization into", mc_path)
        inserted = True
        break
    if not inserted:
        raise SystemExit("Minecraft found, but its constructor hook was not identified")

# Hook the local player's current chunk into the explored-chunk tracker.
MAP_TICK_CALL = "com.melonman106.vbvclient.VBVWorldMap.tick();"
if MAP_TICK_CALL not in mc_src:
    tick_candidates = list(re.finditer(
        r"(?:public|protected|private)\s+void\s+tick\s*\(\s*\)\s*\{",
        mc_src
    ))
    if not tick_candidates:
        raise SystemExit("Minecraft.tick() was not identified; explored chunks cannot be tracked")
    tick_match = tick_candidates[0]
    tick_end = find_method_end(mc_src, tick_match.start())
    if tick_end is None:
        raise SystemExit("Could not find end of Minecraft.tick()")
    mc_src = mc_src[:tick_end] + "\n        " + MAP_TICK_CALL + "\n    " + mc_src[tick_end:]
    mc_path.write_text(mc_src, encoding="utf-8")
    print("Inserted explored-chunk tracking hook into", mc_path)
else:
    print("Explored-chunk tracking hook already present.")

# Gamma Utils fullbright override: vanilla's UnitDouble gamma setting rejects values > 1.
GAMMA_OVERRIDE = "com.melonman106.vbvclient.VBVExtraFeatures.gammaOverride(this.minecraft.options.gamma().get().floatValue())"
lightmap = None
for p, source_text in java_files():
    if p.name == "LightmapRenderStateExtractor.java":
        lightmap = (p, source_text)
        break
if lightmap:
    p, source_text = lightmap
    if GAMMA_OVERRIDE not in source_text:
        pattern = r"float\s+brightnessOption\s*=\s*this\.minecraft\.options\.gamma\(\)\.get\(\)\.floatValue\(\)\s*;"
        source_text, count = re.subn(pattern, "float brightnessOption = " + GAMMA_OVERRIDE + ";", source_text, count=1)
        if count != 1:
            raise SystemExit("Could not locate gamma brightness input in LightmapRenderStateExtractor.java")
        p.write_text(source_text, encoding="utf-8")
        print("Installed Gamma Utils lightmap override in", p)
    else:
        print("Gamma Utils lightmap override already present.")
else:
    raise SystemExit("LightmapRenderStateExtractor.java was not found; Gamma Utils override cannot work.")

title = None
for p, source_text in java_files():
    if re.search(r"class\s+TitleScreen\b", source_text) and "extends Screen" in source_text:
        title = (p, source_text)
        break

if title:
    p, source_text = title
    if "VBVModsScreen" not in source_text:
        # Put Mods immediately below Multiplayer and move the lower menu rows down.
        normal_menu_call = re.search(
            r"topPos\s*=\s*this\.createNormalMenuOptions\(topPos,\s*24,\s*liteMode\);",
            source_text
        )
        if normal_menu_call:
            insert_at = normal_menu_call.end()
            button = """
      this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
         net.minecraft.network.chat.Component.literal("Mods"),
         button -> com.melonman106.vbvclient.VBVNav.open(
            new com.melonman106.vbvclient.VBVModsScreen(this)
         )
      ).bounds(this.width / 2 - 100, topPos + 24, 200, 20).build());
      topPos += 24;
"""
            source_text = source_text[:insert_at] + button + source_text[insert_at:]
            p.write_text(source_text, encoding="utf-8")
            print("Inserted Mods below Multiplayer and shifted lower title buttons:", p)
        else:
            print("TitleScreen found, but createNormalMenuOptions hook was not identified.")
    else:
        print("Native Mods button already present; keeping existing title-screen hook.")
else:
    print("TitleScreen was not identified.")

# Add a real Mods row to the in-game Escape menu before Disconnect.
pause = None
for p, source_text in java_files():
    if re.search(r"class\s+PauseScreen\b", source_text) and "extends Screen" in source_text:
        pause = (p, source_text)
        break

if pause:
    p, source_text = pause
    marker = "this.disconnectButton = helper.addChild("
    if "VBVModsScreen" not in source_text and marker in source_text:
        button = """helper.addChild(
         Button.builder(net.minecraft.network.chat.Component.literal("Mods"),
            button -> this.minecraft.gui.setScreen(new com.melonman106.vbvclient.VBVModsScreen(this)))
            .width(204).build(),
         2
      );
      """
        source_text = source_text.replace(marker, button + marker, 1)
        p.write_text(source_text, encoding="utf-8")
        print("Inserted Mods row into in-game PauseScreen:", p)
    else:
        print("PauseScreen Mods row already present or insertion marker missing.")

# Hook the real keyboard dispatch. V opens Mods; M opens the explored-chunk map.
# G toggles maximum vanilla brightness, C zooms while held, and B toggles the
# compact BetterF3-inspired debug overlay.
keyboard = None
for p, source_text in java_files():
    if p.name == "KeyboardHandler.java" and re.search(r"class\s+KeyboardHandler\b", source_text):
        keyboard = (p, source_text)
        break

if keyboard:
    p, source_text = keyboard
    hook = """if (action == 1 && !event.hasControlDownWithQuirk()
            && (screen == null || !(screen.getFocused() instanceof EditBox))) {
            if (event.key() == 86) {
               com.melonman106.vbvclient.VBVNav.open(new com.melonman106.vbvclient.VBVModsScreen(screen));
               return;
            }
            if (event.key() == 77) {
               com.melonman106.vbvclient.VBVNav.open(new com.melonman106.vbvclient.VBVWorldMapScreen(screen));
               return;
            }
         }

         """
    anchor = "Screen screen = this.minecraft.gui.screen();"
    if anchor not in source_text:
        raise SystemExit("KeyboardHandler screen dispatch anchor was not identified")

    if "VBVExtraFeatures.handleKey(event.key(), action)" not in source_text:
        feature_hook = """if (!event.hasControlDownWithQuirk()
            && (screen == null || !(screen.getFocused() instanceof EditBox))) {
            com.melonman106.vbvclient.VBVExtraFeatures.handleKey(event.key(), action);
         }

         """
        source_text = source_text.replace(anchor, anchor + "\n         " + feature_hook, 1)

    if "VBVWorldMapScreen(screen)" not in source_text:
        source_text = source_text.replace(anchor, anchor + "\n         " + hook, 1)

    p.write_text(source_text, encoding="utf-8")
    print("Registered V/M, gamma, zoom, and enhanced-debug keybind hooks in KeyboardHandler:", p)
else:
    print("KeyboardHandler was not identified; global VBV keybinds were not installed.")
