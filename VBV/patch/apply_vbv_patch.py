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
GENERATED_PACK_ICON_ROOT = RESOURCE_ROOT / "assets" / "vbvclient" / "textures" / "mods"

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
                elif y < 205:
                    color = (48, 91, 54)
                else:
                    color = (35, 62, 42)
            row.extend(color)
        rows.append(bytes(row))
    raw = b"".join(rows)
    compressed = zlib.compress(raw, 9)
    png = bytearray(b"\x89PNG\r\n\x1a\n")
    png.extend(png_chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)))
    png.extend(png_chunk(b"IDAT", compressed))
    png.extend(png_chunk(b"IEND", b""))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)

BACKGROUND = TARGET.parent / "resources/assets/minecraft/textures/gui/title/background"
existing_panorama = sorted(BACKGROUND.glob("panorama_*.png")) if BACKGROUND.is_dir() else []
if not existing_panorama:
    for face in range(6):
        write_custom_panorama_face(BACKGROUND / f"panorama_{face}.png", face)
    print("No uploaded panorama found; generated six default pixel-art panorama faces in", BACKGROUND)
else:
    print("Existing panorama found; leaving uploaded panorama unchanged.")

def text_from_component(value):
    if isinstance(value, str):
        return value
    if isinstance(value, list):
        return "".join(text_from_component(v) for v in value)
    if isinstance(value, dict):
        if isinstance(value.get("text"), str):
            return value["text"] + text_from_component(value.get("extra", []))
        return text_from_component(value.get("extra", []))
    return str(value)

def safe_mod_id(name):
    value = re.sub(r"[^a-z0-9._-]+", "-", name.lower()).strip("-._")
    return value or "resource-pack"

def display_name(name):
    return re.sub(r"[_-]+", " ", name).strip().title()

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
    icon_destination = GENERATED_PACK_ICON_ROOT / mod_id
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

        icon = GENERATED_PACK_ICON_ROOT / mod_id / "pack.png"
        if not icon.is_file():
            fallback = CLIENT_SIDE / "pack.png"
            if fallback.is_file():
                icon.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(fallback, icon)
        if icon.is_file():
            try:
                from PIL import Image
                with Image.open(icon) as image:
                    image.convert("RGBA").resize((64, 64), Image.Resampling.LANCZOS).save(icon, format="PNG")
            except Exception as exc:
                raise SystemExit(f"Could not normalize resource-pack icon {icon}: {exc}")

        mod_json = GENERATED_PACK_RESOURCE_ROOT / mod_id / "mod.json"
        mod_json.parent.mkdir(parents=True, exist_ok=True)
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
        f"            {java_string('vbvclient:mods/' + mod_id + '/pack')},",
        "            true",
        "        ));",
    ])
generated_lines.extend([
    "    }",
    "}",
])
GENERATED_PACK_CLASS.write_text("\n".join(generated_lines) + "\n", encoding="utf-8")
print("Generated resource-pack mod registry:", GENERATED_PACK_CLASS)
