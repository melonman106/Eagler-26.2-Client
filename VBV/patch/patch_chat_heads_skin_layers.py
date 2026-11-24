#!/usr/bin/env python3
"""Chat Heads and 3D Skin Layers hooks.

Chat Heads - three small edits:

  1. ChatListener hands the sender GameProfile to VBVChatHeads just before a player
     message is shown, because ChatComponent only receives the decorated text.
  2. ChatComponent tags the GuiMessage it creates, so the renderer can look the skin up.
  3. ChatComponent reserves an 11 px gutter in front of the chat text and draws the
     sender's face in it. Messages without a sender leave the gutter empty.

3D Skin Layers - one replacement:

  4. PlayerModel.createMesh builds the five outer layers (jacket, both sleeves, both
     pants legs) with a vanilla 0.25 px shell. The script hands the growth value to
     VBV3DSkinLayers instead, so the layers get a real, configurable thickness.

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
        "   private void vbvRenderChatHeads(final GuiGraphicsExtractor graphics, final int ticks, final ChatComponent.DisplayMode displayMode) {\n"
        "      if (!com.melonman106.vbvclient.VBVChatHeads.enabled || this.trimmedMessages.isEmpty()) return;\n"
        "      float scale = (float)this.getScale();\n"
        "      int chatBottom = Mth.floor((graphics.guiHeight() - 40) / scale);\n"
        "      float textOpacity = this.minecraft.options.chatOpacity().get().floatValue() * 0.9F + 0.1F;\n"
        "      float backgroundOpacity = this.minecraft.options.textBackgroundOpacity().get().floatValue();\n"
        "      double chatLineSpacing = this.minecraft.options.chatLineSpacing().get();\n"
        "      int entryHeight = (int)(9.0 * (chatLineSpacing + 1.0));\n"
        "      int entryBottomToMessageY = (int)Math.round(8.0 * (chatLineSpacing + 1.0) - 4.0 * chatLineSpacing);\n"
        "      ChatComponent.AlphaCalculator alphaCalculator = displayMode.foreground\n"
        "         ? ChatComponent.AlphaCalculator.FULLY_VISIBLE\n"
        "         : ChatComponent.AlphaCalculator.timeBased(ticks);\n"
        "      graphics.pose().pushMatrix();\n"
        "      graphics.pose().scale(scale, scale);\n"
        "      graphics.pose().translate(4.0F + com.melonman106.vbvclient.VBVChatHeads.GUTTER, 0.0F);\n"
        "      this.forEachLine(alphaCalculator, (line, lineIndex, alpha) -> {\n"
        "         int entryBottom = chatBottom - lineIndex * entryHeight;\n"
        "         int textTop = entryBottom - entryBottomToMessageY;\n"
        "         GuiMessage parent = line.parent();\n"
        "         int absolute = lineIndex + this.chatScrollbarPos;\n"
        "         boolean topLine = absolute + 1 >= this.trimmedMessages.size()\n"
        "            || this.trimmedMessages.get(absolute + 1).parent() != parent;\n"
        "         if (!topLine || !com.melonman106.vbvclient.VBVChatHeads.hasSender(parent)) return;\n"
        "         graphics.fill(-com.melonman106.vbvclient.VBVChatHeads.GUTTER + 1, textTop - 2, -1, textTop + 9,\n"
        "            ((int)(alpha * backgroundOpacity * 255.0F) << 24));\n"
        "         int color = ((int)(alpha * textOpacity * 255.0F) << 24) | 0xFFFFFF;\n"
        "         com.melonman106.vbvclient.VBVChatHeads.render(\n"
        "            graphics, parent, -com.melonman106.vbvclient.VBVChatHeads.GUTTER + 1,\n"
        "            textTop - 1, com.melonman106.vbvclient.VBVChatHeads.HEAD, color);\n"
        "      });\n"
        "      graphics.pose().popMatrix();\n"
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
    model_path.write_text(model, encoding="utf-8")
    print("[VBV 3D Skin Layers] jacket, sleeves and pants now use the configurable thickness")

print("[VBV Chat Heads / 3D Skin Layers] hooks complete")
