#!/usr/bin/env python3
"""Build the Dungeons II-inspired title screen and animate every slime GIF frame."""
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(sys.argv[1]).resolve()
CLIENT = Path(__file__).resolve().parent.parent
GAME = ROOT / "game/src/main/java"
RES = ROOT / "game/src/main/resources"


def extract_gif(gif: Path, out_dir: Path) -> int:
    out_dir.mkdir(parents=True, exist_ok=True)
    for old in out_dir.glob("frame_*.png"):
        old.unlink()
    try:
        from PIL import Image
        with Image.open(gif) as image:
            count = getattr(image, "n_frames", 1)
            for i in range(count):
                image.seek(i)
                image.convert("RGBA").save(out_dir / f"frame_{i}.png")
            return count
    except Exception:
        convert = shutil.which("magick") or shutil.which("convert")
        if not convert:
            raise RuntimeError("slime.gif is present but neither Pillow nor ImageMagick is available")
        subprocess.run([convert, str(gif), str(out_dir / "frame_%d.png")], check=True)
        return len(list(out_dir.glob("frame_*.png")))


def find_slime_gif():
    for candidate in (CLIENT / "LoadingScreen/slime.gif", CLIENT / "slime.gif"):
        if candidate.is_file():
            return candidate
    return None


def patch_loading_animation(count: int):
    files = list(GAME.rglob("VBVServerLoadingScreen.java"))
    if not files:
        raise RuntimeError("VBVServerLoadingScreen.java not found")
    path = files[0]
    source = path.read_text(encoding="utf-8")
    if count:
        entries = ",\n            ".join(
            f'Identifier.parse("vbvclient:textures/loadingscreen/slime/frame_{i}.png")'
            for i in range(count)
        )
        replacement = "private static Identifier[] buildSlimeFrames() {\n        return new Identifier[] {\n            " + entries + "\n        };\n    }"
        pattern = re.compile(r"private static Identifier\[\] buildSlimeFrames\(\)\s*\{.*?\n    \}", re.S)
        source, n = pattern.subn(replacement, source, count=1)
        if n != 1:
            raise RuntimeError("Could not update generated slime frame list")
        path.write_text(source, encoding="utf-8")
    print(f"[VBV Dungeons Home] configured {count} animated slime frames")



def suppress_vanilla_title_branding():
    candidates = list(GAME.rglob("TitleScreen.java"))
    if not candidates:
        raise RuntimeError("TitleScreen.java not found; cannot suppress vanilla title branding")
    path = next((p for p in candidates if p.as_posix().endswith("/net/minecraft/client/gui/screens/TitleScreen.java")), candidates[0])
    source = path.read_text(encoding="utf-8")
    marker = "VBV_DUNGEONS_SUPPRESS_VANILLA_BRANDING"
    if marker in source:
        return
    logo = "this.logoRenderer.extractRenderState(graphics, this.width, this.logoRenderer.keepLogoThroughFade() ? 1.0F : widgetFade);"
    if logo in source:
        source = source.replace(logo, "if (!(this instanceof com.melonman106.vbvclient.VBVDungeonsTitleScreen)) { " + logo + " }", 1)
    splash = "this.splash.extractRenderState(graphics, this.width, this.font, widgetFade);"
    if splash in source:
        source = source.replace(splash, "if (!(this instanceof com.melonman106.vbvclient.VBVDungeonsTitleScreen)) { " + splash + " }", 1)
    version = "graphics.text(this.font, versionString, 2, this.height - 10, ARGB.white(widgetFade));"
    if version in source:
        source = source.replace(version, "if (!(this instanceof com.melonman106.vbvclient.VBVDungeonsTitleScreen)) { " + version + " }", 1)
    source = source.replace("public class TitleScreen", "public class TitleScreen /* " + marker + " */", 1)
    path.write_text(source, encoding="utf-8")
    print("[VBV Dungeons Home] suppressed vanilla logo, splash and version on the custom home screen")


def patch_title_screen():
    generated = list(GAME.rglob("VBVDungeonsTitleScreen.java"))
    template = CLIENT / "src/main/java/com/melonman106/vbvclient/VBVDungeonsTitleScreen.java"
    if not generated:
        raise RuntimeError("VBVDungeonsTitleScreen.java not found in generated source")
    if not template.is_file():
        raise RuntimeError(f"VBV home-screen template is missing: {template}")
    generated[0].write_text(template.read_text(encoding="utf-8"), encoding="utf-8")
    print("[VBV Dungeons Home] copied the maintained native-button home-screen template")



def configure_panorama_overlay():
    """Convert an optional GIF/MP4 panorama overlay to animated PNG frames."""
    overlay_root = CLIENT / "panorama"
    candidates = [overlay_root / "panorama_overlay.mp4", overlay_root / "panorama_overlay.gif"]
    animated = next((path for path in candidates if path.is_file()), None)
    frame_dir = RES / "assets/vbvclient/textures/panorama_overlay"
    frame_dir.mkdir(parents=True, exist_ok=True)
    for old in frame_dir.glob("frame_*.png"):
        old.unlink()

    if animated is None:
        print("[VBV Panorama] No GIF/MP4 overlay found; the static PNG path remains available.")
        return

    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        raise RuntimeError("panorama_overlay.gif/mp4 is present but FFmpeg is unavailable")
    output = frame_dir / "frame_%04d.png"
    subprocess.run([
        ffmpeg, "-y", "-hide_banner", "-loglevel", "error", "-i", str(animated),
        "-vf", "fps=12,scale=w='min(1280,iw)':h='min(720,ih)':force_original_aspect_ratio=decrease",
        "-vsync", "0", "-pix_fmt", "rgba", str(output),
    ], check=True)
    frames = sorted(frame_dir.glob("frame_*.png"))
    if not frames:
        raise RuntimeError(f"FFmpeg produced no frames from {animated.name}")

    java_files = list(GAME.rglob("VBVPanoramaOverlay.java"))
    if not java_files:
        raise RuntimeError("VBVPanoramaOverlay.java was not copied into generated sources")
    java = java_files[0]
    source = java.read_text(encoding="utf-8")
    entries = ",\n            ".join(
        f'Identifier.parse("vbvclient:textures/panorama_overlay/{frame.name}")'
        for frame in frames
    )
    replacement = "private static final Identifier[] FRAMES = new Identifier[] {\n            " + entries + "\n    };"
    source, count = re.subn(
        r"private static final Identifier\[\] FRAMES = new Identifier\[\] \{.*?\};",
        replacement, source, count=1, flags=re.S,
    )
    if count != 1:
        raise RuntimeError("Could not configure VBV panorama animation frame list")
    java.write_text(source, encoding="utf-8")

    title_files = list(GAME.rglob("TitleScreen.java"))
    if not title_files:
        raise RuntimeError("TitleScreen.java not found for animated panorama overlay hook")
    title = next((p for p in title_files if p.as_posix().endswith("/net/minecraft/client/gui/screens/TitleScreen.java")), title_files[0])
    title_source = title.read_text(encoding="utf-8")
    marker = "VBV_PANORAMA_OVERLAY_RENDER"
    if marker not in title_source:
        anchor = "this.extractPanorama(graphics, a);"
        if title_source.count(anchor) != 1:
            raise RuntimeError("Could not locate the unique title panorama render call")
        hook = (anchor + "\n      // " + marker + "\n" +
            "      if (this instanceof com.melonman106.vbvclient.VBVDungeonsTitleScreen) {\n" +
            "         com.melonman106.vbvclient.VBVPanoramaOverlay.render(graphics, this.width, this.height);\n" +
            "      }")
        title_source = title_source.replace(anchor, hook, 1)
        title.write_text(title_source, encoding="utf-8")
    print(f"[VBV Panorama] enabled looping {animated.suffix[1:].upper()} overlay: {len(frames)} frames at 12 FPS")

gif = find_slime_gif()
frame_count = 0
if gif:
    frame_count = extract_gif(gif, RES / "assets/vbvclient/textures/loadingscreen/slime")
patch_loading_animation(frame_count)
suppress_vanilla_title_branding()
patch_title_screen()
configure_panorama_overlay()
