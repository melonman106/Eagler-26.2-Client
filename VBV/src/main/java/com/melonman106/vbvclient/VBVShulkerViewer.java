package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/**
 * Native Eaglercraft implementation of the ShulkerBoxTooltip preview.
 * Shows a 9x3 contents grid while Shift-hovering a shulker box in a container screen.
 */
public final class VBVShulkerViewer {
    public static boolean enabled = true;
    private static final int SLOT = 18;
    private static final int COLUMNS = 9;
    private static final int ROWS = 3;
    private VBVShulkerViewer() {}

    public static void render(GuiGraphicsExtractor graphics, Slot hovered, int mouseX, int mouseY, boolean shiftDown) {
        if (!enabled || !shiftDown || hovered == null || !hovered.hasItem()) return;
        ItemStack box = hovered.getItem();
        if (!(box.getItem() instanceof net.minecraft.world.item.BlockItem blockItem)
                || !(blockItem.getBlock() instanceof ShulkerBoxBlock)) return;

        ItemContainerContents contents = box.get(DataComponents.CONTAINER);
        if (contents == null) return;

        Minecraft mc = Minecraft.getInstance();
        int width = COLUMNS * SLOT + 8;
        int height = ROWS * SLOT + 27;
        int x = mouseX + 14;
        int y = mouseY - height / 2;
        if (x + width > graphics.guiWidth()) x = mouseX - width - 8;
        if (y + height > graphics.guiHeight()) y = graphics.guiHeight() - height - 4;
        if (y < 4) y = 4;
        if (x < 4) x = 4;

        graphics.fill(x, y, x + width, y + height, 0xF0101010);
        graphics.fill(x, y, x + width, y + 1, 0xFF777777);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF777777);
        graphics.fill(x, y, x + 1, y + height, 0xFF777777);
        graphics.fill(x + width - 1, y, x + width, y + height, 0xFF777777);
        graphics.text(mc.font, box.getHoverName(), x + 4, y + 4, 0xFFFFFFFF, true);

        NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        contents.copyInto(items);
        int gridX = x + 4;
        int gridY = y + 19;
        for (int i = 0; i < 27; i++) {
            int sx = gridX + (i % COLUMNS) * SLOT;
            int sy = gridY + (i / COLUMNS) * SLOT;
            graphics.fill(sx, sy, sx + 17, sy + 17, 0xFF373737);
            graphics.fill(sx + 1, sy + 1, sx + 16, sy + 16, 0xFF171717);
            ItemStack item = items.get(i);
            if (!item.isEmpty()) {
                graphics.item(item, sx + 1, sy + 1);
                graphics.itemDecorations(mc.font, item, sx + 1, sy + 1);
            }
        }
        graphics.text(mc.font, "Hold Shift to preview", x + 4, gridY + ROWS * SLOT + 2, 0xFFAAAAAA, false);
    }

    public static void toggle() { enabled = !enabled; }
}
