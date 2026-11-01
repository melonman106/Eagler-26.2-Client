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


def patch_title_screen():
    files = list(GAME.rglob("VBVDungeonsTitleScreen.java"))
    if not files:
        raise RuntimeError("VBVDungeonsTitleScreen.java not found in generated source")
    path = files[0]
    source = r'''package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/** Dungeons II-inspired home screen, arranged to match the supplied reference. */
public final class VBVDungeonsTitleScreen extends TitleScreen {
    private final Screen parent;
    private static final int MENU_X = 84;
    private static final int MENU_Y_OFFSET = 322;
    private static final int MENU_STEP = 51;

    public VBVDungeonsTitleScreen() {
        this(null);
    }

    public VBVDungeonsTitleScreen(Screen parent) {
        super();
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();

        int left = Math.max(44, width / 38);
        int menuTop = Math.max(150, height - MENU_Y_OFFSET);
        int menuWidth = Math.min(270, Math.max(190, width / 5));
        int menuHeight = 38;

        addMenuItem("PLAY", left + 40, menuTop, menuWidth, menuHeight,
            b -> VBVNav.openMultiplayer(this));
        addMenuItem("JOIN A PARTY", left + 40, menuTop + MENU_STEP, menuWidth, menuHeight,
            b -> {});
        addMenuItem("HEROES", left + 40, menuTop + MENU_STEP * 2, menuWidth, menuHeight,
            b -> {});
        addMenuItem("DLC", left + 40, menuTop + MENU_STEP * 3, menuWidth, menuHeight,
            b -> {});

        // Storage is a real native Minecraft widget, only present on the home screen.
        addRenderableWidget(Button.builder(Component.literal("Storage"), b ->
            VBVNav.open(new VBVStorageScreen(this)))
            .bounds(left + 40, height - 52, 112, 24).build());

        addRenderableWidget(Button.builder(Component.literal("Settings"), b ->
            VBVNav.open(new VBVSettingsScreen(this)))
            .bounds(left + 164, height - 52, 112, 24).build());
    }

    private void addMenuItem(String label, int x, int y, int w, int h, Button.OnPress action) {
        addRenderableWidget(new DungeonsTextButton(x, y, w, h, Component.literal(label), action, font));
    }

    private static final class DungeonsTextButton extends PlainTextButton {
        DungeonsTextButton(int x, int y, int width, int height, Component text,
                Button.OnPress action, Font font) {
            super(x, y, width, height, text, action, font);
        }

        @Override
        public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float alpha) {
            boolean hover = isHoveredOrFocused();
            int color = hover ? 0xFFFFFFFF : 0xFF777777;
            graphics.text(Minecraft.getInstance().font, getMessage(), getX(), getY() + 8, color, true);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        // Dungeons-style lower-left corner rail.
        int railX = Math.max(44, width / 38);
        int railTop = Math.max(150, height - MENU_Y_OFFSET - 12);
        graphics.fill(railX, railTop, railX + 2, height - 34, 0x887C8587);
        graphics.fill(railX, height - 34, railX + 15, height - 32, 0x887C8587);
        graphics.fill(railX, railTop, railX + 15, railTop + 2, 0x887C8587);

        // Player identity area occupies the same top-right region as the reference party strip.
        String username = Minecraft.getInstance().getUser().getName();
        String label = "PLAYER: " + username;
        int textX = width - font.width(label) - 82;
        graphics.text(font, label, textX, 27, 0xFF8EDFFF, true);
        int avatarX = width - 52;
        graphics.fill(avatarX, 17, avatarX + 30, 47, 0xFF273B58);
        graphics.fill(avatarX + 3, 20, avatarX + 27, 44, 0xFF8B9FB4);
        graphics.fill(avatarX + 8, 24, avatarX + 22, 39, 0xFF31435C);
        graphics.fill(avatarX + 11, 28, avatarX + 13, 30, 0xFFFFFFFF);
        graphics.fill(avatarX + 18, 28, avatarX + 20, 30, 0xFFFFFFFF);
        graphics.fill(avatarX + 12, 35, avatarX + 19, 37, 0xFF26303C);

        String settingsHint = "Settings";
        graphics.text(font, settingsHint, Math.max(12, railX + 40), height - 43, 0xFFFFFFFF, true);
        String partyHint = "Party";
        graphics.text(font, partyHint, Math.max(180, railX + 205), height - 43, 0xFFFFFFFF, true);
    }
}
'''
    path.write_text(source, encoding="utf-8")
    print("[VBV Dungeons Home] installed reference-based menu; removed heading and browser overlay controls")


gif = find_slime_gif()
frame_count = 0
if gif:
    frame_count = extract_gif(gif, RES / "assets/vbvclient/textures/loadingscreen/slime")
patch_loading_animation(frame_count)
patch_title_screen()
