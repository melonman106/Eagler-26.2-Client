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

FABRIC_API_SOURCE = CLIENT_SIDE / "src/main/java/net/fabricmc"
if FABRIC_API_SOURCE.is_dir():
    fabric_api_dest = TARGET / "net/fabricmc"
    shutil.copytree(FABRIC_API_SOURCE, fabric_api_dest, dirs_exist_ok=True)
    print("Copied Eagler Fabric namespace shims to", fabric_api_dest)
else:
    raise SystemExit("Fabric namespace shim sources are missing: " + str(FABRIC_API_SOURCE))

REPO_ROOT = CLIENT_SIDE.parent
MODS_PACK = REPO_ROOT / "ModsPack"
GENERATED_PACK_CLASS = DEST / "VBVGeneratedPackMods.java"
RESOURCE_ROOT = TARGET.parent / "resources"
GENERATED_PACK_RESOURCE_ROOT = RESOURCE_ROOT / "assets" / "vbvclient" / "mods"
GENERATED_PACK_ICON_ROOT = RESOURCE_ROOT / "assets" / "vbvclient" / "textures" / "mods"

PACK_ICON_DIR = CLIENT_SIDE / "PackIcons"
NATIVE_PACK_ICONS = {
    "viabackportvisuals-client": "ModMenu.png",
    "ukus-armor-hud": "AmourHud.png",
    "simple-hud-enhanced": "ModMenu.png",
    "shine": "Shine.png",
    "mouse-tweaks": "MouseTweaks.jpg",
    "appleskin": "AppleSkin.png",
    "gamma-utils": "gamma.png",
    "zoomify": "Zoomify.png",
    "betterf3": "BetterF3.png",
    "vbv-minimap": "Minimap.png",
    "jade": "Jade.png",
    "shulkerboxtooltip": "ShulkerBoxTooltips.jpeg",
    "mcdw": "MCDW.png",
}

def install_native_pack_icons():
    from PIL import Image
    copied = 0
    for mod_id, filename in NATIVE_PACK_ICONS.items():
        source = PACK_ICON_DIR / filename
        if not source.is_file():
            if mod_id == "mcdw":
                continue
            raise SystemExit(f"Required VBV mod icon is missing: {source}")
        destination = RESOURCE_ROOT / "assets" / "vbvclient" / "textures" / "mods" / f"{mod_id}.png"
        destination.parent.mkdir(parents=True, exist_ok=True)
        try:
            with Image.open(source) as image:
                image.convert("RGBA").resize((64, 64), Image.Resampling.LANCZOS).save(destination, format="PNG")
        except Exception as exc:
            raise SystemExit(f"Could not process VBV mod icon {source}: {exc}")
        copied += 1
        print(f"Installed native mod icon: {mod_id} <- {filename}")
    print(f"Installed {copied} native VBV mod icons.")

install_native_pack_icons()

SHINE_RESOURCE_SOURCE = CLIENT_SIDE / "patch" / "resources"
if not (SHINE_RESOURCE_SOURCE / "assets" / "vbvclient" / "post_effect" / "shine_bloom.json").is_file():
    raise SystemExit("Shine bloom post-chain JSON is missing from VBV/patch/resources")
if not (SHINE_RESOURCE_SOURCE / "assets" / "vbvclient" / "shaders" / "post" / "shine_bloom.fsh").is_file():
    raise SystemExit("Shine bloom fragment shader is missing from VBV/patch/resources")
shutil.copytree(SHINE_RESOURCE_SOURCE, RESOURCE_ROOT, dirs_exist_ok=True)
print("Installed Shine screen-space bloom post-chain and shader assets.")


def png_chunk(kind, payload):
    return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload) & 0xffffffff)

def write_custom_panorama_face(path, face):
    width = height = 256
    rows = []
    for y in range(height):
        row = bytearray([0])
        for x in range(width):
            sky = y < 154
            if sky:
                t = y / 153.0
                base = (int(45 + 75 * t), int(112 + 95 * t), int(190 + 50 * t))
                cloud_x = (x + face * 19) % 256
                cloud = ((y < 66 and ((cloud_x // 24 + y // 11) % 5 in (0, 1)))
                         or (y < 95 and ((cloud_x // 31 + y // 13 + face) % 7 == 2)))
                if cloud:
                    base = (min(255, base[0] + 55), min(255, base[1] + 49), min(255, base[2] + 36))
                color = base
            else:
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
                continue

            target.parent.mkdir(parents=True, exist_ok=True)
            try:
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
            explicit_icon = PACK_ICON_DIR / f"{mod_id}.png"
            stem_icon = PACK_ICON_DIR / f"{zippath.stem}.png"
            fallback = explicit_icon if explicit_icon.is_file() else stem_icon if stem_icon.is_file() else CLIENT_SIDE / "pack.png"
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

# ... existing generated VBVGeneratedPackMods.java and remaining patch logic ...
