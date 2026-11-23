package com.melonman106.vbvclient.mods;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Puts imported items into the game's creative tabs, as Fabric's item group events do for a Java mod. Mod items
 * are added after the vanilla ones of the tab they name; "none" leaves an item out of the creative inventory.
 */
public final class ModItemGroups {
    private static final Map<String, List<Item>> BY_TAB = new HashMap<>();

    private ModItemGroups() {}

    static synchronized void add(List<String> tabs, Item item) {
        for (String tab : tabs) {
            if (tab.equals(ModItems.NO_TAB)) continue;
            BY_TAB.computeIfAbsent(tab, key -> new ArrayList<>()).add(item);
        }
    }

    /** Called from {@code CreativeModeTab.buildContents} after the tab's own items have been added. */
    public static synchronized void append(CreativeModeTab tab, Consumer<ItemStack> output) {
        if (BY_TAB.isEmpty()) return;
        Identifier key = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
        if (key == null || !"minecraft".equals(key.getNamespace())) return;
        List<Item> items = BY_TAB.get(key.getPath());
        if (items == null) return;
        for (Item item : items) {
            try {
                output.accept(new ItemStack(item));
            } catch (Throwable t) {
                System.err.println("[VBV mods] could not add " + item + " to the creative tab " + key + ": " + t);
            }
        }
    }
}
