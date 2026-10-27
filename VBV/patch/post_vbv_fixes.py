#!/usr/bin/env python3
"""Post-process the pinned VBV patch with fixes that must not be overwritten by it."""
from pathlib import Path
import re
import shutil
import sys

ROOT = Path(sys.argv[1]).resolve()
SRC = Path(__file__).resolve().parent.parent / "src/main/java/com/melonman106/vbvclient"
GAME = ROOT / "game/src/main/java"
RES = ROOT / "game/src/main/resources"
SHINE_TOKEN = "com.melonman106.vbvclient.ShineConfig"


def java_files_named(name):
    return list(GAME.rglob(name))


def matching_brace(text, brace):
    """Return the matching closing brace, ignoring Java strings, chars, and comments."""
    depth = 0
    i = brace
    state = "code"
    escaped = False
    while i < len(text):
        c = text[i]
        n = text[i + 1] if i + 1 < len(text) else ""
        if state == "code":
            if c == '"':
                state = "string"
            elif c == "'":
                state = "char"
            elif c == "/" and n == "/":
                state = "line_comment"
                i += 1
            elif c == "/" and n == "*":
                state = "block_comment"
                i += 1
            elif c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
                if depth == 0:
                    return i
        elif state in ("string", "char"):
            quote = '"' if state == "string" else "'"
            if escaped:
                escaped = False
            elif c == "\\":
                escaped = True
            elif c == quote:
                state = "code"
        elif state == "line_comment":
            if c == "\n":
                state = "code"
        elif state == "block_comment":
            if c == "*" and n == "/":
                state = "code"
                i += 1
        i += 1
    return -1


def find_if_block_containing(text, token_pos):
    """Only remove an if-block when ShineConfig appears in that if's condition."""
    candidates = list(re.finditer(r"\bif\s*\(", text[:token_pos]))
    for match in reversed(candidates):
        open_paren = text.find("(", match.start(), match.end())
        depth = 0
        close_paren = -1
        state = "code"
        escaped = False
        i = open_paren
        while i < len(text):
            c = text[i]
            if state in ("string", "char"):
                quote = '"' if state == "string" else "'"
                if escaped:
                    escaped = False
                elif c == "\\":
                    escaped = True
                elif c == quote:
                    state = "code"
            else:
                if c == '"':
                    state = "string"
                elif c == "'":
                    state = "char"
                elif c == "(":
                    depth += 1
                elif c == ")":
                    depth -= 1
                    if depth == 0:
                        close_paren = i
                        break
            i += 1
        if close_paren < 0 or not (match.start() <= token_pos <= close_paren):
            continue
        brace = close_paren + 1
        while brace < len(text) and text[brace].isspace():
            brace += 1
        if brace >= len(text) or text[brace] != "{":
            continue
        end = matching_brace(text, brace)
        if end >= token_pos:
            return match.start(), end + 1
    return None


def remove_shine_hooks(path):
    text = path.read_text(encoding="utf-8")
    original = text
    text = re.sub(
        r"(?m)^\s*import\s+com\.melonman106\.vbvclient\.ShineConfig\s*;\s*\n?",
        "",
        text,
    )
    while SHINE_TOKEN in text:
        token_pos = text.find(SHINE_TOKEN)
        block = find_if_block_containing(text, token_pos)
        if block is not None:
            start, end = block
            line_start = text.rfind("\n", 0, start) + 1
            line_end = text.find("\n", end)
            if line_end < 0:
                line_end = len(text)
            else:
                line_end += 1
            text = text[:line_start] + text[line_end:]
            continue
        line_start = text.rfind("\n", 0, token_pos) + 1
        line_end = text.find("\n", token_pos)
        if line_end < 0:
            line_end = len(text)
        else:
            line_end += 1
        text = text[:line_start] + text[line_end:]
    text = re.sub(r"(?m)^\s*private\s+boolean\s+vbvShineBloomFailed\s*;\s*\n", "", text)
    if text != original:
        path.write_text(text, encoding="utf-8")
        print("Removed generated Shine hooks from", path)


for path in GAME.rglob("*.java"):
    if SHINE_TOKEN in path.read_text(encoding="utf-8"):
        remove_shine_hooks(path)

remaining = []
for path in GAME.rglob("*.java"):
    if SHINE_TOKEN in path.read_text(encoding="utf-8"):
        remaining.append(str(path))
if remaining:
    raise RuntimeError("ShineConfig references remain after cleanup:\n" + "\n".join(remaining))

for path in RES.rglob("*"):
    if path.is_file() and ("shine_bloom" in path.name or "vbvclient/post_effect" in str(path) or "vbvclient/shaders/post" in str(path)):
        path.unlink()

# Keep the generated source synchronized with the native sources.
dest = GAME / "com/melonman106/vbvclient"
for source in SRC.rglob("*.java"):
    target = dest / source.relative_to(SRC)
    target.parent.mkdir(parents=True, exist_ok=True)
    # The maintained source tree is authoritative: copy every native mod
    # and compatibility class, not a stale hand-picked subset.
    shutil.copy2(source, target)

# Keep Fabric-style START and END tick callbacks on opposite sides of the
# real Minecraft.tick() body, rather than firing both consecutively at its end.
minecraft_candidates = java_files_named("Minecraft.java")
if not minecraft_candidates:
    raise RuntimeError("Minecraft.java not found; cannot split Fabric client tick callbacks")
minecraft = next((p for p in minecraft_candidates if p.as_posix().endswith("/net/minecraft/client/Minecraft.java")), minecraft_candidates[0])
# Resolve Eaglercraft's renamed Minecraft screen-navigation method from the
# generated source instead of assuming vanilla's setScreen name.
nav = dest / "VBVNav.java"
if nav.is_file():
    mc_text = minecraft.read_text(encoding="utf-8")
    screen_methods = []
    # Eagler's generated Minecraft.java can place modifiers/annotations or
    # qualified/generic Screen types differently from vanilla source. Match
    # the method declaration without requiring it to start at column zero.
    screen_pattern = re.compile(
        r"\b(?:public|protected|private)\s+(?:(?:static|final|synchronized|native|abstract)\s+)*"
        r"[\w.$<>?,\[\]\s]+\s+(\w+)\s*\(\s*"
        r"(?:final\s+)?(?:[\w.$]+\.)?Screen(?:\s*<[^>]+>)?\s+\w+",
        re.MULTILINE,
    )
    screen_methods.extend(
        match.group(1) for match in screen_pattern.finditer(mc_text)
        if match.group(1) not in {"if", "while", "switch", "catch"}
    )
    if "setScreen" in screen_methods:
        screen_method = "setScreen"
    elif "setScreenAndShow" in screen_methods:
        screen_method = "setScreenAndShow"
    elif screen_methods:
        screen_method = screen_methods[0]
    elif re.search(r"\bsetScreenAndShow\s*\(\s*(?:final\s+)?(?:[\w.$]+\.)?Screen\b", mc_text):
        # Known Eaglercraft naming; accept only when its Screen parameter is
        # actually present in the generated source.
        screen_method = "setScreenAndShow"
    else:
        raise RuntimeError(
            "Could not find Minecraft's one-argument Screen navigation method "
            "in generated Minecraft.java; refusing to guess a renamed method."
        )
    nav_text = nav.read_text(encoding="utf-8")
    fixed_nav = re.sub(
        r"Minecraft\.getInstance\(\)\.\w+\(screen\)",
        "Minecraft.getInstance()." + screen_method + "(screen)",
        nav_text,
        count=1,
    )
    if fixed_nav == nav_text and "Minecraft.getInstance()." + screen_method + "(screen)" not in nav_text:
        raise RuntimeError("Could not patch VBVNav.open() to the generated screen method")
    if fixed_nav != nav_text:
        nav.write_text(fixed_nav, encoding="utf-8")
        print("Patched VBVNav to generated Minecraft screen method:", screen_method)

mc_text = minecraft.read_text(encoding="utf-8")
start_call = "com.melonman106.vbvclient.fabriccompat.FabricCompatClient.startTick(this);"
end_call = "com.melonman106.vbvclient.fabriccompat.FabricCompatClient.endTick(this);"
legacy_call = "com.melonman106.vbvclient.fabriccompat.FabricCompatClient.tick(this);"
if legacy_call in mc_text:
    mc_text = mc_text.replace(legacy_call, end_call, 1)
elif end_call not in mc_text:
    raise RuntimeError("Fabric client tick hook was not found in Minecraft.java")
if start_call not in mc_text:
    tick_match = re.search(r"(?:public|protected|private)\s+void\s+tick\s*\(\s*\)\s*\{", mc_text)
    if not tick_match:
        raise RuntimeError("Minecraft.tick() was not found for Fabric START callback")
    brace = mc_text.find("{", tick_match.start())
    mc_text = mc_text[:brace + 1] + "\n        " + start_call + mc_text[brace + 1:]
minecraft.write_text(mc_text, encoding="utf-8")
print("Split Fabric START/END callbacks around Minecraft.tick().")

# Armor HUD: use the real vanilla hotbar sprite as the panel texture. The
# previous custom armor_hud texture path was pack-dependent and produced the
# wrong panel in the standalone client.
armor = dest / "VBVArmorHud.java"
if armor.is_file():
    text = armor.read_text(encoding="utf-8")
    replacements = {
        "private static final int STEP = 20;": "private static final int STEP = 26;",
        "private static final int SIZE = 22;": "private static final int SIZE = 24;",
        "private static final int HOTBAR_OFFSET = 98;": "private static final int HOTBAR_OFFSET = 100;",
        "if (!(minecraft.getCameraEntity() instanceof Player player)) return;": "Player player = minecraft.player;\n        if (player == null) return;",
        "VBVArmorHudConfig.enabled = false;": "if (!renderErrorLogged) { System.err.println(\"VBV Armor HUD render error: \" + t); renderErrorLogged = true; }\n            VBVArmorHudConfig.enabled = false;",
    }
    if "private static boolean renderErrorLogged;" not in text:
        text = text.replace(
            "private static final int WARNING_SIZE = 8;",
            "private static final int WARNING_SIZE = 8;\n    private static boolean renderErrorLogged;",
            1,
        )
    for old, new in replacements.items():
        text = text.replace(old, new, 1)
    text = text.replace(
        'private static final Identifier WARNING_TEXTURE = Identifier.parse("vbvclient:textures/mods/warn.png");',
        'private static final Identifier WARNING_TEXTURE = Identifier.parse("armor_hud:textures/gui/exclamation_marks.png");',
        1,
    )
    # blitSprite takes (u, v, x, y, width, height). Anchor panel segments to
    # the calculated widget rectangle instead of the screen origin.
    armor_blits = {
        "0, 0, 0, 0, textureWidth - 3, SIZE, 0xFFFFFFFF":
            "0, 0, rect[0], rect[1], textureWidth - 3, SIZE, 0xFFFFFFFF",
        "179, 0, textureWidth - 3, 0, 3, SIZE, 0xFFFFFFFF":
            "179, 0, rect[0] + textureWidth - 3, rect[1], 3, SIZE, 0xFFFFFFFF",
        "0, 1, 0, 0, 3, SIZE, 0xFFFFFFFF":
            "0, 1, rect[0], rect[1], 3, SIZE, 0xFFFFFFFF",
        "3, 0, 3, 0, textureWidth - 6, SIZE, 0xFFFFFFFF":
            "3, 0, rect[0] + 3, rect[1], textureWidth - 6, SIZE, 0xFFFFFFFF",
        "SIZE - 3, 1, textureWidth - 3, 0, 3, SIZE, 0xFFFFFFFF":
            "SIZE - 3, 1, rect[0] + textureWidth - 3, rect[1], 3, SIZE, 0xFFFFFFFF",
        "0, 1, 0, 0, SIZE, SIZE, 0xFFFFFFFF":
            "0, 1, rect[0], rect[1], SIZE, SIZE, 0xFFFFFFFF",
        "0, 1, 0, 0, SIZE - border, SIZE, 0xFFFFFFFF":
            "0, 1, rect[0], rect[1], SIZE - border, SIZE, 0xFFFFFFFF",
        "border, 1, border + j * STEP, 0, STEP, SIZE, 0xFFFFFFFF":
            "border, 1, rect[0] + border + j * STEP, rect[1], STEP, SIZE, 0xFFFFFFFF",
        "1, 1, textureWidth - STEP - border, 0, SIZE - border, SIZE, 0xFFFFFFFF":
            "1, 1, rect[0] + textureWidth - STEP - border, rect[1], SIZE - border, SIZE, 0xFFFFFFFF",
    }
    for old, new in armor_blits.items():
        text = text.replace(old, new)
    armor.write_text(text, encoding="utf-8")

config = dest / "VBVArmorHudConfig.java"
if config.is_file():
    text = config.read_text(encoding="utf-8")
    text = text.replace("widgetShown = SHOWN_NOT_EMPTY;", "widgetShown = SHOWN_ALWAYS;", 1)
    text = text.replace("offsetY = 0;", "offsetY = 8;", 1)
    text = text.replace("style = STYLE_NONE;", "style = STYLE_HOTBAR;", 1)
    config.write_text(text, encoding="utf-8")

# The panel deliberately uses the Unique Dark Lite Armor HUD texture.
# The pinned pack importer must have extracted this exact asset into game resources.
armor_texture = RES / "assets/armor_hud/textures/gui/hotbar_texture.png"
armor_source = dest / "VBVArmorHud.java"
if not armor_texture.is_file():
    raise RuntimeError("Unique Dark Lite Armor HUD panel texture is missing: " + str(armor_texture))
if armor_source.is_file():
    armor_text = armor_source.read_text(encoding="utf-8")
    if "armor_hud:textures/gui/hotbar_texture.png" not in armor_text:
        raise RuntimeError("Armor HUD is not configured to use Unique Dark Lite's hotbar_texture.png")

# AppleSkin is intentionally rendered after the vanilla food layer.
hud_candidates = list(GAME.rglob("Hud.java"))
if hud_candidates:
    hud = hud_candidates[0]
    hud_text = hud.read_text(encoding="utf-8")
    apple_call = "com.melonman106.vbvclient.VBVAppleSkin.render("
    if apple_call in hud_text:
        hud_text = re.sub(
            r"(?m)^\s*com\.melonman106\.vbvclient\.VBVAppleSkin\.render\([^;]+;\s*\n?",
            "",
            hud_text,
        )
        food = re.search(r"(?:public|protected|private)\s+void\s+extractFood\s*\([^)]*\)\s*\{", hud_text)
        if not food:
            raise RuntimeError("Hud.extractFood was not found; cannot layer AppleSkin above hunger icons")
        brace = hud_text.find("{", food.start())
        end = matching_brace(hud_text, brace)
        param = re.search(r"GuiGraphicsExtractor\s+(\w+)", food.group(0))
        if end < 0 or not param:
            raise RuntimeError("Could not identify Hud.extractFood body/graphics parameter")
        hud_text = hud_text[:end] + "\n        com.melonman106.vbvclient.VBVAppleSkin.render(" + param.group(1) + ");\n    " + hud_text[end:]
        hud.write_text(hud_text, encoding="utf-8")
        print("Moved AppleSkin overlay to the end of Hud.extractFood.")

client = dest / "VBVClient.java"
if client.is_file():
    text = client.read_text(encoding="utf-8")
    mod_id = '"vbv-first-person-animations"'
    if mod_id not in text:
        anchor = "        VBVGeneratedPackMods.register();"
        registration = """        registerMod(new VBVModInfo(
            "vbv-first-person-animations", "VBV First-Person Animations", "Native Eagler port 1.0.0",
            "melonman106", "Natural attack swing animation with visible arm movement. Configure with the Mod Menu Config button.",
            "vbvclient:textures/mods/viabackportvisuals-client.png", false));

"""
        if anchor not in text:
            raise RuntimeError("VBVClient registration anchor not found")
        text = text.replace(anchor, registration + anchor, 1)
        client.write_text(text, encoding="utf-8")
        print("Registered VBV First-Person Animations in Mod Menu.")

renderer_candidates = java_files_named("ItemInHandRenderer.java")
if renderer_candidates:
    renderer = renderer_candidates[0]
    text = renderer.read_text(encoding="utf-8")
    method = re.search(r"private\s+void\s+submitArmWithItem\s*\(", text)
    call = "com.melonman106.vbvclient.VBVFirstPersonAnimations.apply(poseStack, player, frameInterp, attack, hand, itemStack);"
    if method and call not in text:
        brace = text.find("{", method.start())
        end = matching_brace(text, brace)
        body = text[brace:end]
        anchor = "         poseStack.pushPose();"
        if anchor not in body:
            raise RuntimeError("submitArmWithItem pose-stack anchor not found")
        body = body.replace(anchor, anchor + "\n         " + call, 1)
        text = text[:brace] + body + text[end:]
        renderer.write_text(text, encoding="utf-8")
        print("Installed native first-person animation transform in ItemInHandRenderer.")
    elif not method:
        raise RuntimeError("ItemInHandRenderer.submitArmWithItem was not found")

panorama_src = Path(__file__).resolve().parent.parent / "panorama" / "panorama_overlay.png"
panorama_dst = RES / "assets/minecraft/textures/gui/title/background/panorama_overlay.png"
if panorama_src.is_file():
    panorama_dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(panorama_src, panorama_dst)
    print("Installed optional panorama_overlay.png")

font_root = RES / "assets/minecraft/textures/font"
if font_root.exists():
    print("Minecraft font resource directory present:", font_root)

print("Post-VBV fixes complete.")
