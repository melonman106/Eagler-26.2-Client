#!/usr/bin/env python3
"""Stable VBV patch runner with local compatibility fixes."""
from pathlib import Path
from urllib.request import Request, urlopen
import hashlib
import os
import subprocess
import sys
import zipfile

CLIENT_SIDE = Path(__file__).resolve().parent.parent
REPO_ROOT = CLIENT_SIDE.parent


def _materialize_eagler_overlay() -> None:
    """Apply the verified native Eagler resource overlay before patching."""
    generated = Path(sys.argv[1]).resolve() if len(sys.argv) >= 2 else None
    if generated is None or not generated.is_dir():
        return

    overlay = REPO_ROOT / "legacy-u1-kit" / "inputs" / "resource-overlay-normal.zip"
    resources = generated / "game" / "src" / "main" / "resources"
    expected = "2ba7e3376891c64f8bf57f3687e05b8dbe1971a75475b6825449e5e5f96d71f3"

    if not overlay.is_file():
        raise SystemExit(
            "::error::Required resource overlay is missing: "
            f"{overlay}. Run the workflow's resource-overlay restore step first."
        )

    digest = hashlib.sha256(overlay.read_bytes()).hexdigest()
    if digest != expected:
        raise SystemExit(
            "::error::VBV resource overlay SHA-256 mismatch: "
            f"expected {expected}, got {digest}"
        )

    print(f"[VBV resource bootstrap] applying verified overlay: {overlay}")
    resources.mkdir(parents=True, exist_ok=True)

    with zipfile.ZipFile(overlay) as zf:
        for info in zf.infolist():
            name = info.filename.replace("\\", "/")
            if not name or name.endswith("/"):
                continue
            target = (resources / name).resolve()
            if os.path.commonpath((str(resources.resolve()), str(target))) != str(resources.resolve()):
                raise SystemExit(f"::error::Unsafe resource-overlay path: {name}")
            target.parent.mkdir(parents=True, exist_ok=True)
            with zf.open(info, "r") as src, target.open("wb") as dst:
                dst.write(src.read())

    critical = [
        "assets/minecraft/shaders/core/text.vsh",
        "assets/minecraft/shaders/core/text.fsh",
        "assets/minecraft/shaders/core/gui.vsh",
        "assets/minecraft/shaders/core/gui.fsh",
        "assets/minecraft/shaders/core/position_tex_color.vsh",
        "assets/minecraft/shaders/core/position_tex_color.fsh",
        "assets/minecraft/textures/gui/title/edition.png",
        "assets/minecraft/textures/gui/title/minecraft.png",
        "assets/minecraft/textures/block/lava_flow.png",
        "assets/minecraft/textures/block/lava_still.png",
        "assets/minecraft/textures/block/water_flow.png",
        "assets/minecraft/textures/block/water_still.png",
        "assets/minecraft/textures/block/water_overlay.png",
        "assets/minecraft/gpu_warnlist.json",
        "assets/minecraft/regional_compliancies.json",
    ]
    missing = [p for p in critical if not (resources / p).is_file()]
    if missing:
        raise SystemExit(
            "::error::Resource overlay extraction is incomplete; missing: "
            + ", ".join(missing)
        )

    count = sum(1 for p in resources.rglob("*") if p.is_file())
    if count < 19000:
        raise SystemExit(
            f"::error::Resource overlay extraction produced only {count} files; "
            "expected the full ~19,515-file tree."
        )

    print(
        f"[VBV resource bootstrap] verified {count} resource files and all "
        "critical shader/runtime assets."
    )


_materialize_eagler_overlay()

sanitize = CLIENT_SIDE / "patch/sanitize_modpacks.py"
if sanitize.is_file():
    subprocess.run([sys.executable, str(sanitize), str(REPO_ROOT)], check=True)

# Keep the Armor HUD warning icons in the pack namespace. The panel texture
# itself is intentionally Unique Dark Lite's hotbar_texture.png and is verified
# after the upstream importer extracts ModsPack/*.zip into game resources.
armor = CLIENT_SIDE / "src/main/java/com/melonman106/vbvclient/VBVArmorHud.java"
if armor.is_file():
    text = armor.read_text(encoding="utf-8")
    fixed = text.replace(
        'private static final Identifier WARNING_TEXTURE = Identifier.parse("vbvclient:textures/mods/warn.png");',
        'private static final Identifier WARNING_TEXTURE = Identifier.parse("armor_hud:textures/gui/exclamation_marks.png");',
        1,
    )
    if fixed != text:
        armor.write_text(fixed, encoding="utf-8")

PATCH_URL = (
    "https://raw.githubusercontent.com/melonman106/Eagler-26.2-Client/"
    "284ff62a345779f4773dca86a6881bc7a6df7335/VBV/patch/apply_vbv_patch.py"
)
req = Request(PATCH_URL, headers={"User-Agent": "VBV-native-builder"})
with urlopen(req, timeout=60) as response:
    upstream = response.read()

upstream_path = CLIENT_SIDE / "patch/.upstream_apply_vbv_patch.py"
upstream_path.write_bytes(upstream)
try:
    subprocess.run([sys.executable, str(upstream_path), *sys.argv[1:]], check=True)
finally:
    upstream_path.unlink(missing_ok=True)

post = CLIENT_SIDE / "patch/post_vbv_fixes.py"
if post.is_file():
    subprocess.run([sys.executable, str(post), *sys.argv[1:]], check=True)
