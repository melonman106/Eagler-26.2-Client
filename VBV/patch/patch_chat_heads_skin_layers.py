"""Chat Heads and TAB faces hooks.

Chat Heads - three small edits:

  1. ChatListener hands the sender GameProfile to VBVChatHeads just before a player
     message is shown, because ChatComponent only receives the decorated text.
  2. ChatComponent tags the GuiMessage it creates, so the renderer can look the skin up.
  3. ChatComponent indents only the messages that have a sender, and draws that
     sender's face in the space it makes. The chat panel, its background, the
     scrollbar and the queue line all stay exactly where vanilla draws them;
     system output and command feedback are never moved.

Tab list - one replacement:

  4. PlayerTabOverlay only draws a player's face when the connection is online. Eagler
     is always offline, so the tab list never showed one; the gate now also opens while
     the Chat Heads mod is enabled.

Every anchor must match exactly or the script aborts before writing anything, and a
second run is a no-op (each file is checked for its marker first).
"""
from pathlib import Path
import re
import sys

MARKER = "VBV_CHAT_HEADS"
TAB_MARKER = "VBV_TAB_HEADS"
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

    # Only a message that gets a face is wrapped narrower; every other message keeps the
    # vanilla width, because nothing else is indented.
    chat = replace_once(
        chat,
        "   private void addMessageToDisplayQueue(final GuiMessage message) {\n"
        "      int maxWidth = Mth.floor(this.getWidth() / this.getScale());\n",
        "   private void addMessageToDisplayQueue(final GuiMessage message) {\n"
        "      int maxWidth = Mth.floor(this.getWidth() / this.getScale());\n"
        "      if (com.melonman106.vbvclient.VBVChatHeads.enabled\n"
        "         && com.melonman106.vbvclient.VBVChatHeads.hasSender(message)) {\n"
        "         maxWidth -= com.melonman106.vbvclient.VBVChatHeads.GUTTER;\n"
        "      }\n",
        "ChatComponent.java",
        "chat message wrap width",
    )

    # The chat itself keeps vanilla's position. A message that has a sender is shifted right
    # just for its own lines, which leaves room for the face in the panel's left margin.
    chat = replace_once(
        chat,
        "               int textTop = entryBottom - entryBottomToMessageY;\n",
        "               int textTop = entryBottom - entryBottomToMessageY;\n"
        "               // VBV_CHAT_HEADS: only this message moves, and only because it has a face.\n"
        "               boolean vbvHead = com.melonman106.vbvclient.VBVChatHeads.enabled\n"
        "                  && com.melonman106.vbvclient.VBVChatHeads.hasSender(line.parent());\n"
        "               if (vbvHead) {\n"
        "                  graphics.updatePose(pose -> pose.translate(com.melonman106.vbvclient.VBVChatHeads.GUTTER, 0.0F));\n"
        "               }\n",
        "ChatComponent.java",
        "chat line text position",
    )

    chat = replace_once(
        chat,
        "                     graphics.handleTagIcon(iconLeft, textBottom, forceIconRendering, tag, tag.icon());\n"
        "                  }\n"
        "               }\n"
        "            }\n",
        "                     graphics.handleTagIcon(iconLeft, textBottom, forceIconRendering, tag, tag.icon());\n"
        "                  }\n"
        "               }\n"
        "               if (vbvHead) {\n"
        "                  graphics.updatePose(pose -> pose.translate(-com.melonman106.vbvclient.VBVChatHeads.GUTTER, 0.0F));\n"
        "               }\n"
        "            }\n",
        "ChatComponent.java",
        "chat line end",
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
        "   // VBV_CHAT_HEADS: draws each sender's face in the margin the message indented for it.\n"
        "   // The caller already applied the chat scale and the panel translation, so this method\n"
        "   // must not apply either one again.\n"
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
        "            graphics, parent, -1, textTop - 1, com.melonman106.vbvclient.VBVChatHeads.HEAD, color);\n"
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
    print("[VBV Chat Heads] per-message face gutter installed")

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

# ---------------------------------------------------------------- TAB list --
tab_path, tab = read_source("net/minecraft/client/gui/components/PlayerTabOverlay.java")
if TAB_MARKER in tab:
    print("[VBV Chat Heads] tab list already patched")
else:
    head_gate = re.search(
        r"(?m)^([ \t]*)(?:final )?boolean showHead = this\.minecraft\.getConnection\(\)\.onlineMode\(\);[ \t]*$",
        tab,
    )
    if head_gate is None:
        print(
            "::warning::[VBV Chat Heads] PlayerTabOverlay has no onlineMode face gate; "
            "the tab list keeps vanilla behaviour"
        )
    else:
        indent = head_gate.group(1)
        folded = (
            indent + "// VBV_TAB_HEADS: Eagler connections are never online, so vanilla's onlineMode\n"
            + indent + "// gate hid every tab-list face. Draw the faces while Chat Heads is enabled.\n"
            + indent + "boolean showHead = this.minecraft.getConnection().onlineMode()\n"
            + indent + "   || com.melonman106.vbvclient.VBVChatHeads.enabled;"
        )
        tab = tab[:head_gate.start()] + folded + tab[head_gate.end():]
        tab_path.write_text(tab, encoding="utf-8")
        print("[VBV Chat Heads] tab-list faces enabled for offline (Eagler) connections")

print("[VBV Chat Heads] hooks complete")
