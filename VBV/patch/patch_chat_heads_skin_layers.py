#!/usr/bin/env python3
"""Chat Heads and 3D Skin Layers hooks.

Chat Heads - three small edits:

  1. ChatListener hands the sender GameProfile to VBVChatHeads just before a player
     message is shown, because ChatComponent only receives the decorated text.
  2. ChatComponent tags the GuiMessage it creates, so the renderer can look the skin up.
  3. ChatComponent reserves an 11 px gutter in front of the chat text and draws the
     sender's face in it. Messages without a sender leave the gutter empty.

3D Skin Layers - two replacements:

  4. PlayerModel.createMesh builds the jacket, both sleeves and both pants legs with a
     vanilla 0.25 px shell, while HumanoidModel builds the hat with a fixed 0.5 px
     shell. The script hands the growth value to VBV3DSkinLayers and replaces the
     player's hat cube, so every outer layer gets a real, configurable thickness.

Every anchor must match exactly or the script aborts before writing anything, and a
second run is a no-op (each file is checked for its marker first).
"""
from pathlib import Path
import sys

MARKER = "VBV_CHAT_HEADS"
SKIN_MARKER = "VBV3DSkinLayers"
if len(sys.argv) < 2:
    raise SystemExit("::error::Generated U2 workspace path was not supplied")

JAVA = Path(sys.argv[1]).resolve() / "game" / "src" / "main" / "java"


def read_source(relative):
    path = JAVA / relative
    if not path.is_file():
        raise SystemExit(f"::error::{relative} is missing")
    return path, path.read_text(encoding="utf-8")


def replace_once(text, old, new, relative, what):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"::error::{relative}: expected exactly one {what} anchor, found {count}")
    return text.replace(old, new, 1)


# ---------------------------------------------------------------- Chat Heads --
chat_path, chat = read_source("net/minecraft/client/gui/components/ChatComponent.java")
listener_path, listener = read_source("net/minecraft/client/multiplayer/chat/ChatListener.java")

if MARKER in chat:
    print("[VBV Chat Heads] hooks already present")
else:
    chat = replace_once(
        chat,
        "      GuiMessage message = new GuiMessage(this.minecraft.gui.hud.getGuiTicks(), contents, signature, source, tag);\n",
        "      GuiMessage message = new GuiMessage(this.minecraft.gui.hud.getGuiTicks(), contents, signature, source, tag);\n"
        "      com.melonman106.vbvclient.VBVChatHeads.attach(message, source == GuiMessageSource.PLAYER);\n",
        "ChatComponent.java",
        "GuiMessage creation",
    )

    chat = replace_once(
        chat,
        "         graphics.updatePose(pose -> {\n"
        "            pose.scale(scale, scale);\n"
        "            pose.translate(4.0F, 0.0F);\n"
        "         });\n",
        "         graphics.updatePose(pose -> {\n"
        "            pose.scale(scale, scale);\n"
        "            pose.translate(4.0F + (com.melonman106.vbvclient.VBVChatHeads.enabled\n"
        "               ? com.melonman106.vbvclient.VBVChatHeads.GUTTER : 0), 0.0F);\n"
        "         });\n",
        "ChatComponent.java",
        "chat text transform",
    )

    # Keep the chat panel anchored where vanilla draws it. The pose below is shifted by the
    # gutter so the text starts after the head; move every panel/scrollbar coordinate back by
    # the same amount, and wrap messages at the narrower text width.
    chat = replace_once(
        chat,
        "         int count = this.forEachLine(alphaCalculator, (var5x, lineIndex, alphax) -> {\n",
        "         final int chatGutter = com.melonman106.vbvclient.VBVChatHeads.enabled\n"
        "            ? com.melonman106.vbvclient.VBVChatHeads.GUTTER : 0;\n"
        "         int count = this.forEachLine(alphaCalculator, (var5x, lineIndex, alphax) -> {\n",
        "ChatComponent.java",
        "chat background loop",
    )

    chat = replace_once(
        chat,
        "            graphics.fill(-4, entryTop, maxWidth + 4 + 4, entryBottom, ARGB.black(alphax * backgroundOpacity));\n",
        "            graphics.fill(-4 - chatGutter, entryTop, maxWidth + 4 + 4 - chatGutter, entryBottom, ARGB.black(alphax * backgroundOpacity));\n",
        "ChatComponent.java",
        "chat background fill",
    )

    chat = replace_once(
        chat,
        "            graphics.fill(-2, chatBottom, maxWidth + 4, chatBottom + messageHeight, ARGB.black(backgroundOpacity));\n",
        "            graphics.fill(-2 - chatGutter, chatBottom, maxWidth + 4 - chatGutter, chatBottom + messageHeight, ARGB.black(backgroundOpacity));\n",
        "ChatComponent.java",
        "chat queue background",
    )

    chat = replace_once(
        chat,
        "            graphics.fill(-2, lineAboveMessagesY, maxWidth + 4 + 4, lineAboveMessagesY + entryHeight, ARGB.black(backgroundOpacity));\n",
        "            graphics.fill(-2 - chatGutter, lineAboveMessagesY, maxWidth + 4 + 4 - chatGutter, lineAboveMessagesY + entryHeight, ARGB.black(backgroundOpacity));\n",
        "ChatComponent.java",
        "restricted chat background",
    )

    chat = replace_once(
        chat,
        "               int scrollBarStartX = maxWidth + 4;\n",
        "               int scrollBarStartX = maxWidth + 4 - chatGutter;\n",
        "ChatComponent.java",
        "chat scrollbar",
    )

    chat = replace_once(
        chat,
        "               ? ComponentRenderUtils.clipText(RESTRICTED_CHAT_MESSAGE_WITH_HOVER, this.minecraft.font, maxWidth)\n",
        "               ? ComponentRenderUtils.clipText(RESTRICTED_CHAT_MESSAGE_WITH_HOVER, this.minecraft.font, maxWidth - chatGutter)\n",
        "ChatComponent.java",
        "restricted chat clip",
    )

    chat = replace_once(
        chat,
        "   private void addMessageToDisplayQueue(final GuiMessage message) {\n"
        "      int maxWidth = Mth.floor(this.getWidth() / this.getScale());\n"
        "      List<FormattedCharSequence> lines = message.splitLines(this.minecraft.font, maxWidth);\n",
        "   private void addMessageToDisplayQueue(final GuiMessage message) {\n"
        "      int maxWidth = Mth.floor(this.getWidth() / this.getScale());\n"
        "      if (com.melonman106.vbvclient.VBVChatHeads.enabled) {\n"
        "         maxWidth -= com.melonman106.vbvclient.VBVChatHeads.GUTTER;\n"
        "      }\n"
        "      List<FormattedCharSequence> lines = message.splitLines(this.minecraft.font, maxWidth);\n",
        "ChatComponent.java",
        "chat message wrap width",
    )

    chat = replace_once(
        chat,
        "         displayMode\n"
        "      );\n"
        "      graphics.pose().popMatrix();\n",
        "         displayMode\n"
        "      );\n"
        "      this.vbvRenderChatHeads(graphics, ticks, displayMode);\n"
        "      graphics.pose().popMatrix();\n",
        "ChatComponent.java",
        "outer chat render call",
    )

    heads_method = (
        "   // VBV_CHAT_HEADS: draws each sender's face in the gutter reserved beside the chat text.\n"
        "   // The caller already applied the chat scale and gutter translation, so this method must\n"
        "   // not apply either one again; doing that put the head over the first text column.\n"
        "   private void vbvRenderChatHeads(final GuiGraphicsExtractor graphics, final int ticks, final ChatComponent.DisplayMode displayMode) {\n"
        "      if (!com.melonman106.vbvclient.VBVChatHeads.enabled || this.trimmedMessages.isEmpty()) return;\n"
        "      float scale = (float)this.getScale();\n"
        "      int chatBottom = Mth.floor((graphics.guiHeight() - 40) / scale);\n"
        "      float textOpacity = this.minecraft.options.chatOpacity().get().floatValue() * 0.9F + 0.1F;\n"
        "      double chatLineSpacing = this.minecraft.options.chatLineSpacing().get();\n"
        "      int entryHeight = (int)(9.0 * (chatLineSpacing + 1.0));\n"
        "      int entryBottomToMessageY = (int)Math.round(8.0 * (chatLineSpacing + 1.0) - 4.0 * chatLineSpacing);\n"
        "      ChatComponent.AlphaCalculator alphaCalculator = displayMode.foreground\n"
        "         ? ChatComponent.AlphaCalculator.FULLY_VISIBLE\n"
        "         : ChatComponent.AlphaCalculator.timeBased(ticks);\n"
        "      this.forEachLine(alphaCalculator, (line, lineIndex, alpha) -> {\n"
        "         int entryBottom = chatBottom - lineIndex * entryHeight;\n"
        "         int textTop = entryBottom - entryBottomToMessageY;\n"
        "         GuiMessage parent = line.parent();\n"
        "         int absolute = lineIndex + this.chatScrollbarPos;\n"
        "         boolean topLine = absolute + 1 >= this.trimmedMessages.size()\n"
        "            || this.trimmedMessages.get(absolute + 1).parent() != parent;\n"
        "         if (!topLine || !com.melonman106.vbvclient.VBVChatHeads.hasSender(parent)) return;\n"
        "         int color = ((int)(alpha * textOpacity * 255.0F) << 24) | 0xFFFFFF;\n"
        "         com.melonman106.vbvclient.VBVChatHeads.render(\n"
        "            graphics, parent, -com.melonman106.vbvclient.VBVChatHeads.GUTTER + 1,\n"
        "            textTop - 1, com.melonman106.vbvclient.VBVChatHeads.HEAD, color);\n"
        "      });\n"
        "   }\n"
        "\n"
    )
    chat = replace_once(
        chat,
        "   public void clearMessages(final boolean history) {\n",
        heads_method + "   public void clearMessages(final boolean history) {\n",
        "ChatComponent.java",
        "clearMessages method",
    )
    chat_path.write_text(chat, encoding="utf-8")
    print("[VBV Chat Heads] ChatComponent gutter and face renderer installed")

if "VBVChatHeads.setPendingSender" in listener:
    print("[VBV Chat Heads] ChatListener sender hook already present")
else:
    listener = replace_once(
        listener,
        "            GuiMessageTag tag = trustLevel.createTag(message);\n"
        "            MessageSignature signature = message.signature();\n"
        "            FilterMask filterMask = message.filterMask();\n",
        "            GuiMessageTag tag = trustLevel.createTag(message);\n"
        "            MessageSignature signature = message.signature();\n"
        "            FilterMask filterMask = message.filterMask();\n"
        "            com.melonman106.vbvclient.VBVChatHeads.setPendingSender(sender);\n",
        "ChatListener.java",
        "player message sender",
    )
    listener_path.write_text(listener, encoding="utf-8")
    print("[VBV Chat Heads] ChatListener sender hook installed")

# ------------------------------------------------------------ 3D Skin Layers --
model_path, model = read_source("net/minecraft/client/model/player/PlayerModel.java")
if SKIN_MARKER in model:
    print("[VBV 3D Skin Layers] player model already patched")
else:
    shell = "scale.extend(0.25F)"
    count = model.count(shell)
    if count != 7:
        raise SystemExit(
            "::error::PlayerModel.java: expected seven outer-layer 0.25F shells (wide and slim sleeves, pants, jacket), found " + str(count)
        )
    model = model.replace(
        shell,
        "scale.extend(com.melonman106.vbvclient.VBV3DSkinLayers.overlayThickness())",
    )
    # HumanoidModel builds the hat separately with a fixed 0.5 px shell, so the seven
    # replacements above never touched it. Replace the player's hat cube with the same
    # configurable thickness; other humanoids keep their vanilla hat.
    model = replace_once(
        model,
        "      float overlayScale = 0.25F;\n",
        "      // VBV3DSkinLayers: replace the fixed 0.5 px hat shell from HumanoidModel.\n"
        "      PartDefinition head = root.getChild(\"head\");\n"
        "      head.addOrReplaceChild(\"hat\", CubeListBuilder.create().texOffs(32, 0)\n"
        "         .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F,\n"
        "            scale.extend(com.melonman106.vbvclient.VBV3DSkinLayers.hatThickness())), PartPose.ZERO);\n",
        "PlayerModel.java",
        "unused overlay scale",
    )
    model_path.write_text(model, encoding="utf-8")
    print("[VBV 3D Skin Layers] hat, jacket, sleeves and pants now use the configurable thickness")

print("[VBV Chat Heads / 3D Skin Layers] hooks complete")
