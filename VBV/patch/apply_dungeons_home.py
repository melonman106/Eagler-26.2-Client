#!/usr/bin/env python3
"""Apply the Dungeons-style home layout and optional animated panorama overlay."""
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(sys.argv[1]).resolve()
CLIENT_SIDE = Path(__file__).resolve().parent.parent
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
            raise RuntimeError(f"{gif.name} is present but neither Pillow nor ImageMagick is available")
        subprocess.run([convert, str(gif), str(out_dir / "frame_%d.png")], check=True)
        return len(list(out_dir.glob("frame_*.png")))


def find_panorama_gif() -> Path | None:
    candidates = (
        CLIENT_SIDE / "panorama/panorama_overlay.gif",
        CLIENT_SIDE / "LoadingScreen/panorama_overlay.gif",
        CLIENT_SIDE / "panorama_overlay.gif",
    )
    return next((p for p in candidates if p.is_file()), None)


def prepare_panorama_animation() -> int:
    gif = find_panorama_gif()
    if not gif:
        print("[VBV Dungeons Home] No panorama_overlay.gif found; keeping the normal static panorama overlay.")
        return 0
    frames = RES / "assets/vbvclient/textures/panorama/animated"
    count = extract_gif(gif, frames)
    if count < 1:
        raise RuntimeError("panorama_overlay.gif produced no frames")
    print(f"[VBV Dungeons Home] Extracted {count} panorama overlay frames from {gif.name}.")
    return count


def patch_title_screen(frame_count: int) -> None:
    files = list(GAME.rglob("VBVDungeonsTitleScreen.java"))
    if not files:
        raise RuntimeError("VBVDungeonsTitleScreen.java not found in generated source")
    path = files[0]
    text = path.read_text(encoding="utf-8")

    marker = "VBV_DUNGEONS_HOME_LAYOUT"
    if marker in text:
        print("[VBV Dungeons Home] Home layout already patched.")
        return

    overlay_block = ""
    if frame_count:
        overlay_block = f'''\n    private static final Identifier[] ANIMATED_OVERLAY = buildAnimatedOverlay();\n\n    private static Identifier[] buildAnimatedOverlay() {{\n        Identifier[] frames = new Identifier[{frame_count}];\n        for (int i = 0; i < frames.length; i++) {{\n            frames[i] = Identifier.parse("vbvclient:textures/panorama/animated/frame_" + i + ".png");\n        }}\n        return frames;\n    }}\n\n'''

    overlay_draw = ""
    if frame_count:
        overlay_draw = '''\n        if (ANIMATED_OVERLAY.length > 0) {\n            int frame = (int)((System.currentTimeMillis() / 100L) % ANIMATED_OVERLAY.length);\n            graphics.blit(RenderPipelines.GUI_TEXTURED, ANIMATED_OVERLAY[frame],\n                0, 0, 0, 0, width, height, width, height, width, height);\n        }\n'''

    new_text = f'''package com.melonman106.vbvclient;\n\nimport net.minecraft.client.gui.GuiGraphicsExtractor;\nimport net.minecraft.client.gui.components.Button;\nimport net.minecraft.client.gui.screens.TitleScreen;\nimport net.minecraft.client.renderer.RenderPipelines;\nimport net.minecraft.network.chat.Component;\nimport net.minecraft.resources.Identifier;\n\n/** Minecraft Dungeons II-inspired VBV title screen. */\npublic final class VBVDungeonsTitleScreen extends TitleScreen {{\n{overlay_block}    public VBVDungeonsTitleScreen() {{\n        super();\n    }}\n\n    @Override\n    protected void init() {{\n        super.init();\n        clearWidgets();\n\n        int buttonWidth = Math.min(250, Math.max(190, width / 5));\n        int buttonHeight = 28;\n        int gap = 7;\n        int x = Math.max(28, width / 24);\n        int y = Math.max(90, height / 2 + 15);\n\n        addRenderableWidget(Button.builder(Component.literal("Play"), b ->\n            VBVNav.openMultiplayer(this))\n            .bounds(x, y, buttonWidth, buttonHeight).build());\n        addRenderableWidget(Button.builder(Component.literal("Join a Party"), b ->\n            VBVNav.openMultiplayer(this))\n            .bounds(x, y + (buttonHeight + gap), buttonWidth, buttonHeight).build());\n        addRenderableWidget(Button.builder(Component.literal("Heroes"), b ->\n            VBVNav.open(new VBVDungeonsExtrasScreen(this)))\n            .bounds(x, y + (buttonHeight + gap) * 2, buttonWidth, buttonHeight).build());\n        addRenderableWidget(Button.builder(Component.literal("DLC"), b ->\n            VBVNav.open(new VBVDungeonsExtrasScreen(this)))\n            .bounds(x, y + (buttonHeight + gap) * 3, buttonWidth, buttonHeight).build());\n\n        addRenderableWidget(Button.builder(Component.literal("Settings"), b ->\n            VBVNav.openOptions(this))\n            .bounds(x, height - 46, 110, 22).build());\n        addRenderableWidget(Button.builder(Component.literal("Party"), b ->\n            VBVNav.openMultiplayer(this))\n            .bounds(x + 120, height - 46, 100, 22).build());\n    }}\n\n    @Override\n    protected void extractPanorama(GuiGraphicsExtractor graphics, float partialTick) {{\n        super.extractPanorama(graphics, partialTick);\n{overlay_draw}    }}\n\n    @Override\n    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {{\n        super.extractRenderState(graphics, mouseX, mouseY, partialTick);\n        // {marker}\n        String title = "MINECRAFT DUNGEONS II";\n        graphics.text(font, title, width / 2 - font.width(title) / 2, 28, 0xFFFFFFFF, true);\n    }}\n}}\n'''
    path.write_text(new_text, encoding="utf-8")
    print(f"[VBV Dungeons Home] Installed Dungeons-style home layout with {frame_count} animated overlay frames.")


count = prepare_panorama_animation()
patch_title_screen(count)
