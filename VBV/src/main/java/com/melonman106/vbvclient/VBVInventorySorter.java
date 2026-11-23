package com.melonman106.vbvclient;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Inventory Sorter: orders the open storage block or the 27 backpack slots by item name and
 * merges partial stacks of the same item.
 *
 * <p>There is no server-side sort packet, so the order is built the same way a player would build
 * it: a short series of ordinary pick-up clicks that walk each item to its slot. A click sequence
 * is spread over a few ticks instead of being sent in one burst, which keeps it inside what a
 * server accepts from a normal player. The hotbar, armor and offhand slots are never touched.</p>
 *
 * <p>Inside a chest, barrel, ender chest or shulker box the block's own slots are sorted instead of
 * the backpack. Left-clicking a slot while holding the same item tops the slot up to its stack
 * limit and leaves the remainder on the cursor, so three loose stacks of three dirt become one
 * stack of nine (and full stacks plus one remainder when the total passes the limit).</p>
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
    private static String target = "Inventory";
    private static long lastSortAt;

    private VBVInventorySorter() {}

    /** Called by the container screen before its own key handling. */
    public static boolean handleKey(AbstractContainerScreen<?> screen, KeyEvent event) {
        if (event == null || event.key() != VBVKeybinds.code(VBVKeybinds.SORT)) return false;
        if (!enabled || screen == null) return false;
        // The creative menu's slots are not backed by the survival inventory layout.
        if (screen instanceof CreativeModeInventoryScreen) return false;
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

        List<Integer> slots = new ArrayList<>();
        boolean storage = storageSlots(menu, minecraft, slots);
        if (!storage) {
            for (int i = 0; i < menu.slots.size(); i++) {
                Slot slot = menu.getSlot(i);
                if (slot.container == minecraft.player.getInventory()
                    && slot.getContainerSlot() >= 9 && slot.getContainerSlot() <= 35) {
                    slots.add(i);
                }
            }
            if (slots.size() != 27) return;
        }
        if (slots.isEmpty()) return;
        target = storage ? "Storage" : "Inventory";

        int size = slots.size();
        ItemStack[] items = new ItemStack[size];
        int[] counts = new int[size];
        for (int i = 0; i < size; i++) {
            ItemStack stack = menu.getSlot(slots.get(i)).getItem();
            items[i] = stack;
            counts[i] = stack.isEmpty() ? 0 : stack.getCount();
        }

        QUEUE.clear();
        merge(items, counts, slots);
        sort(items, counts, slots);

        if (QUEUE.isEmpty()) {
            minecraft.showDebugChat(Component.literal(target + " already sorted"));
        } else {
            owner = screen;
        }
    }

    /**
     * Collects the block's own slots when a chest, barrel, ender chest or shulker box is open.
     * Their menus always place the block grid first, so the first rows × 9 slots are the storage.
     */
    private static boolean storageSlots(AbstractContainerMenu menu, Minecraft minecraft, List<Integer> slots) {
        int size;
        if (menu instanceof ChestMenu) {
            size = ((ChestMenu) menu).getRowCount() * 9;
        } else if (menu instanceof ShulkerBoxMenu) {
            size = 27;
        } else {
            return false;
        }
        if (size <= 0 || size > menu.slots.size()) return false;
        for (int i = 0; i < size; i++) {
            // The block's storage slots are never backed by the player's own inventory.
            if (menu.getSlot(i).container == minecraft.player.getInventory()) return false;
            slots.add(i);
        }
        return true;
    }

    /** Consolidates every group of equal stacks into full stacks plus at most one remainder. */
    private static void merge(ItemStack[] items, int[] counts, List<Integer> slots) {
        for (int i = 0; i < items.length; i++) {
            if (counts[i] <= 0 || items[i].isEmpty()) continue;
            int max = items[i].getMaxStackSize();
            if (max <= 1 || counts[i] >= max) continue;
            for (int k = i + 1; k < items.length && counts[i] < max; k++) {
                if (counts[k] <= 0 || items[k].isEmpty()) continue;
                if (!ItemStack.isSameItemSameComponents(items[i], items[k])) continue;
                int moved = Math.min(max - counts[i], counts[k]);
                if (moved <= 0) continue;
                // Pick the loose stack up, top the first slot up from the cursor, and put back
                // whatever did not fit in the same slot it came from.
                QUEUE.add(new Click(slots.get(k), 0));
                QUEUE.add(new Click(slots.get(i), 0));
                counts[i] += moved;
                counts[k] -= moved;
                if (counts[k] > 0) {
                    QUEUE.add(new Click(slots.get(k), 0));
                } else {
                    items[k] = ItemStack.EMPTY;
                }
            }
        }
    }

    /** Reorders the (already merged) stacks into name order with a series of ordinary swaps. */
    private static void sort(ItemStack[] items, int[] counts, List<Integer> slots) {
        int size = items.length;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (counts[i] > 0 && !items[i].isEmpty()) order.add(i);
        }
        order.sort((a, b) -> compare(items, counts, a, b));

        ItemStack[] wantItems = new ItemStack[size];
        int[] wantCounts = new int[size];
        for (int i = 0; i < size; i++) {
            if (i < order.size()) {
                int source = order.get(i);
                wantItems[i] = items[source];
                wantCounts[i] = counts[source];
            } else {
                wantItems[i] = ItemStack.EMPTY;
                wantCounts[i] = 0;
            }
        }

        for (int i = 0; i < size; i++) {
            if (same(items[i], counts[i], wantItems[i], wantCounts[i])) continue;
            int source = -1;
            for (int k = i + 1; k < size; k++) {
                if (same(items[k], counts[k], wantItems[i], wantCounts[i])) {
                    source = k;
                    break;
                }
            }
            if (source < 0) continue;
            if (counts[i] > 0 && wantCounts[i] > 0
                && ItemStack.isSameItemSameComponents(items[i], wantItems[i])
                && counts[i] > wantCounts[i]) {
                // Same item, and the target needs the smaller stack. Lifting the larger stack
                // first makes the target slot top itself up and leaves the smaller stack on the
                // cursor, which then goes back into the source slot.
                QUEUE.add(new Click(slots.get(i), 0));
                QUEUE.add(new Click(slots.get(source), 0));
                QUEUE.add(new Click(slots.get(i), 0));
            } else {
                // Taking the source stack first swaps two different items. For the same item the
                // target tops up from the held stack and the remainder returns to the source.
                QUEUE.add(new Click(slots.get(source), 0));
                QUEUE.add(new Click(slots.get(i), 0));
                QUEUE.add(new Click(slots.get(source), 0));
            }
            ItemStack stack = items[i];
            items[i] = items[source];
            items[source] = stack;
            int count = counts[i];
            counts[i] = counts[source];
            counts[source] = count;
        }
    }

    private static int compare(ItemStack[] items, int[] counts, int a, int b) {
        int byName = items[a].getHoverName().getString().compareToIgnoreCase(items[b].getHoverName().getString());
        if (byName != 0) return byName;
        int byCount = Integer.compare(counts[b], counts[a]);
        if (byCount != 0) return byCount;
        return Integer.compare(items[a].getDamageValue(), items[b].getDamageValue());
    }

    private static boolean same(ItemStack a, int countA, ItemStack b, int countB) {
        if (countA <= 0 || countB <= 0) return countA <= 0 && countB <= 0;
        return countA == countB && ItemStack.isSameItemSameComponents(a, b);
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
            minecraft.showDebugChat(Component.literal(target + " sorted"));
        }
    }

    public static boolean busy() {
        return !QUEUE.isEmpty();
    }
}
