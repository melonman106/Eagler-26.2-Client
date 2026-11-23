package com.melonman106.vbvclient;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Inventory Sorter: orders the 27 backpack slots of the open container by item name.
 *
 * <p>There is no server-side sort packet, so the order is built the same way a player would build it:
 * a short series of ordinary pick-up clicks that walk each item to its slot. A click sequence is
 * spilled over a few ticks instead of being sent in one burst, which keeps it inside what a server
 * accepts from a normal player. The hotbar, armor and offhand slots are never touched.</p>
 *
 * <p>The container screen calls {@link #handleKey} from its key handler and
 * {@link VBVClient} calls {@link #tick} from the shared client tick; the generated screen exposes the
 * two small helpers this class needs ({@code vbvMenu()} and {@code vbvInventoryClick}).</p>
 */
public final class VBVInventorySorter {
    public static boolean enabled = true;

    private static final int CLICKS_PER_TICK = 1;
    private static final long REPEAT_DELAY_MS = 700L;

    private static final class Click {
        final int slot;
        final int button;

        Click(int slot, int button) {
            this.slot = slot;
            this.button = button;
        }
    }

    private static final ArrayDeque<Click> QUEUE = new ArrayDeque<>();
    private static AbstractContainerScreen<?> owner;
    private static long lastSortAt;

    private VBVInventorySorter() {}

    /** Called by the container screen before its own key handling. */
    public static boolean handleKey(AbstractContainerScreen<?> screen, KeyEvent event) {
        if (event == null || event.key() != VBVKeybinds.code(VBVKeybinds.SORT)) return false;
        if (!enabled || screen == null) return false;
        // The creative menu's slots are not backed by the survival inventory layout.
        if (screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) return false;
        // An anvil or a sign has a focused text box: never steal a letter typed into it.
        if (screen.getFocused() != null) return false;
        if (busy()) return true;
        long now = System.currentTimeMillis();
        if (now - lastSortAt < REPEAT_DELAY_MS) return true;
        lastSortAt = now;
        start(screen);
        return true;
    }

    private static void start(AbstractContainerScreen<?> screen) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) return;
        AbstractContainerMenu menu = screen.vbvMenu();
        if (menu == null) return;
        if (!menu.getCarried().isEmpty()) {
            // The click sequence assumes an empty cursor; a held stack would be swapped into a slot.
            minecraft.showDebugChat(Component.literal("Put the held item down before sorting"));
            return;
        }

        List<Integer> slots = new ArrayList<>(27);
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            if (slot.container == minecraft.player.getInventory()
                && slot.getContainerSlot() >= 9 && slot.getContainerSlot() <= 35) {
                slots.add(i);
            }
        }
        if (slots.size() != 27) return;

        List<ItemStack> current = new ArrayList<>(27);
        List<ItemStack> desired = new ArrayList<>(27);
        for (int index : slots) {
            ItemStack stack = menu.getSlot(index).getItem();
            current.add(stack.copy());
            if (!stack.isEmpty()) desired.add(stack.copy());
        }
        desired.sort((a, b) -> {
            int byName = a.getHoverName().getString().compareToIgnoreCase(b.getHoverName().getString());
            if (byName != 0) return byName;
            int byCount = Integer.compare(b.getCount(), a.getCount());
            if (byCount != 0) return byCount;
            return Integer.compare(a.getDamageValue(), b.getDamageValue());
        });
        while (desired.size() < 27) desired.add(ItemStack.EMPTY);

        QUEUE.clear();
        for (int i = 0; i < 27; i++) {
            if (same(current.get(i), desired.get(i))) continue;
            int source = -1;
            for (int k = i + 1; k < 27; k++) {
                if (same(current.get(k), desired.get(i))) {
                    source = k;
                    break;
                }
            }
            if (source < 0) continue;
            // Carry the item from the slot that has it to the slot where it belongs. Clicking an
            // occupied slot while holding a stack swaps the two, so the displaced item ends up in
            // the cursor and is placed into the slot the first stack came from.
            QUEUE.add(new Click(slots.get(i), 0));
            QUEUE.add(new Click(slots.get(source), 0));
            QUEUE.add(new Click(slots.get(i), 0));
            ItemStack swap = current.get(i);
            current.set(i, current.get(source));
            current.set(source, swap);
        }

        if (QUEUE.isEmpty()) {
            minecraft.showDebugChat(Component.literal("Inventory already sorted"));
        } else {
            owner = screen;
        }
    }

    private static boolean same(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return a.isEmpty() && b.isEmpty();
        return a.getCount() == b.getCount() && ItemStack.isSameItemSameComponents(a, b);
    }

    /** Called from the shared client tick. Sends a few clicks per tick until the order is done. */
    public static void tick(Minecraft minecraft) {
        if (QUEUE.isEmpty()) {
            owner = null;
            return;
        }
        if (minecraft == null || owner == null || minecraft.gui.screen() != owner) {
            QUEUE.clear();
            owner = null;
            return;
        }
        int budget = CLICKS_PER_TICK;
        while (budget-- > 0 && !QUEUE.isEmpty()) {
            Click click = QUEUE.poll();
            try {
                owner.vbvInventoryClick(click.slot, click.button, false);
            } catch (RuntimeException ignored) {
                QUEUE.clear();
                break;
            }
        }
        if (QUEUE.isEmpty()) {
            owner = null;
            minecraft.showDebugChat(Component.literal("Inventory sorted"));
        }
    }

    public static boolean busy() {
        return !QUEUE.isEmpty();
    }
}
