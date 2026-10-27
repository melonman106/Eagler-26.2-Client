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
        # Find the closing parenthesis for this if condition while respecting
        # strings and nested parentheses. The Shine token must be in the
        # condition itself; never delete an unrelated enclosing if/method.
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
    """Remove Shine blocks regardless of generated formatting, then validate."""
    text = path.read_text(encoding="utf-8")
    original = text

    # Remove imports first.
    text = re.sub(
        r"(?m)^\s*import\s+com\.melonman106\.vbvclient\.ShineConfig\s*;\s*\n?",
        "",
        text,
    )

    # The pinned patch has used several formatting variants. Locate every
    # ShineConfig reference and remove its enclosing if block by brace matching.
    # This handles conditions split over multiple lines, which the old exact
    # string matcher missed.
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

        # If a future patch emits a standalone Shine expression instead of an
        # if block, remove the whole Java statement containing the reference.
        line_start = text.rfind("\n", 0, token_pos) + 1
        line_end = text.find("\n", token_pos)
        if line_end < 0:
            line_end = len(text)
        else:
            line_end += 1
        text = text[:line_start] + text[line_end:]

    # Remove the variable declaration left by Shine bloom hooks if present.
    text = re.sub(r"(?m)^\s*private\s+boolean\s+vbvShineBloomFailed\s*;\s*\n", "", text)

    if text != original:
        path.write_text(text, encoding="utf-8")
        print("Removed generated Shine hooks from", path)


# The pinned patch still installs Shine lighting/bloom. Remove every generated
# Shine hook and its resources so the unstable post-processing path is absent.
for path in GAME.rglob("*.java"):
    if SHINE_TOKEN in path.read_text(encoding="utf-8"):
        remove_shine_hooks(path)

# Hard validation: the build must never reach javac with a ShineConfig reference.
remaining = []
for path in GAME.rglob("*.java"):
    if SHINE_TOKEN in path.read_text(encoding="utf-8"):
        remaining.append(str(path))
if remaining:
    raise RuntimeError("ShineConfig references remain after cleanup:\n" + "\n".join(remaining))

for path in RES.rglob("*"):
    if path.is_file() and ("shine_bloom" in path.name or "vbvclient/post_effect" in str(path) or "vbvclient/shaders/post" in str(path)):
        path.unlink()

# Keep the generated source synchronized with the current native sources after
# the pinned patch has copied them.
dest = GAME / "com/melonman106/vbvclient"
for source in SRC.rglob("*.java"):
    target = dest / source.relative_to(SRC)
    target.parent.mkdir(parents=True, exist_ok=True)
    if source.name in {"VBVClient.java", "VBVModsScreen.java", "VBVAppleSkin.java", "VBVArmorHudConfig.java", "VBVExtraFeatures.java", "VBVFirstPersonAnimations.java", "VBVFirstPersonAnimationsConfigScreen.java"}:
        shutil.copy2(source, target)

# Armor HUD: use bundled textures, keep all four slots visible, and raise the larger widget slightly above the hotbar.
armor = dest / "VBVArmorHud.java"
if armor.is_file():
    text = armor.read_text(encoding="utf-8")
    replacements = {
        "private static final int STEP = 20;": "private static final int STEP = 26;",
        "private static final int SIZE = 22;": "private static final int SIZE = 24;",
        "private static final int HOTBAR_OFFSET = 98;": "private static final int HOTBAR_OFFSET = 100;",
        "if (!(minecraft.getCameraEntity() instanceof Player player)) return;": "Player player = minecraft.player;\n        if (player == null) return;",
        "VBVArmorHudConfig.enabled = false;": "System.err.println(\"VBV Armor HUD render error: \" + t);",
    }
    for old, new in replacements.items():
        text = text.replace(old, new, 1)
    text = text.replace(
        'private static final Identifier WARNING_TEXTURE = Identifier.parse("vbvclient:textures/mods/warn.png");',
        'private static final Identifier WARNING_TEXTURE = Identifier.parse("armor_hud:textures/gui/exclamation_marks.png");',
        1,
    )
    # Keep the vanilla HOTBAR_SPRITE declaration intact. The Armor HUD source
    # already declares UNIQUE_DARK_HOTBAR_TEXTURE; replacing HOTBAR_SPRITE here
    # caused both a duplicate declaration and a missing symbol in generated Java.
    old_hotbar = """            case VBVArmorHudConfig.STYLE_HOTBAR -> {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE, 182, 22, 0, 0, 0, 0, textureWidth - 3, SIZE, 0xFFFFFFFF);
                g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE, 182, 22, 179, 0, textureWidth - 3, 0, 3, SIZE, 0xFFFFFFFF);
            }"""
    new_hotbar = """            case VBVArmorHudConfig.STYLE_HOTBAR -> {
                int drawWidth = Math.min(textureWidth, 182);
                g.blit(RenderPipelines.GUI_TEXTURED, UNIQUE_DARK_HOTBAR_TEXTURE,
                    rect[0], rect[1], drawWidth, SIZE, 0, 0, drawWidth, SIZE, 182, 22);
            }"""
    if old_hotbar in text:
        text = text.replace(old_hotbar, new_hotbar, 1)
    else:
        print("Armor HUD hotbar rendering block was already changed or not found.")
    armor.write_text(text, encoding="utf-8")

config = dest / "VBVArmorHudConfig.java"
if config.is_file():
    text = config.read_text(encoding="utf-8")
    text = text.replace("widgetShown = SHOWN_NOT_EMPTY;", "widgetShown = SHOWN_ALWAYS;", 1)
    text = text.replace("offsetY = 0;", "offsetY = 8;", 1)
    text = text.replace("style = STYLE_NONE;", "style = STYLE_HOTBAR;", 1)
    config.write_text(text, encoding="utf-8")

# The upstream hook is appended to extractItemHotbar, before vanilla hunger icons.
# Move AppleSkin to the end of extractFood so the overlay is painted on top.
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
        food = re.search(
            r"(?:public|protected|private)\s+void\s+extractFood\s*\([^)]*\)\s*\{",
            hud_text,
        )
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
            "melonman106", "Subtle first-person idle and swing animations. Configure with the Mod Menu Config button.",
            "vbvclient:textures/mods/viabackportvisuals-client.png", false));

"""
        if anchor not in text:
            raise RuntimeError("VBVClient registration anchor not found")
        text = text.replace(anchor, registration + anchor, 1)
        client.write_text(text, encoding="utf-8")
        print("Registered VBV First-Person Animations in Mod Menu.")

# Patch the generated renderer after the pinned upstream patch has applied.
# The transform is inserted inside submitArmWithItem's pushed pose, before vanilla
# hand/item-specific rendering; the native helper leaves item-use poses untouched.
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

# Optional panorama overlay: preserve the six-face panorama handling and also
# copy panorama_overlay.png when supplied by the user.
panorama_src = Path(__file__).resolve().parent.parent / "panorama" / "panorama_overlay.png"
panorama_dst = RES / "assets/minecraft/textures/gui/title/background/panorama_overlay.png"
if panorama_src.is_file():
    panorama_dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(panorama_src, panorama_dst)
    print("Installed optional panorama_overlay.png")

# Never let an imported pack replace Eagler's font atlas. The pre-build workflow
# sanitizes the ZIPs before the pinned importer runs; this check documents the
# invariant for the generated resource tree.
font_root = RES / "assets/minecraft/textures/font"
if font_root.exists():
    print("Minecraft font resource directory present:", font_root)

print("Post-VBV fixes complete.")
