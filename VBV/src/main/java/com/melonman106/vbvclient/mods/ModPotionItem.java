package com.melonman106.vbvclient.mods;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

/** A potion item whose default stack carries the imported potion instead of vanilla Water. */
public final class ModPotionItem extends PotionItem {
    private final Holder<Potion> potion;

    public ModPotionItem(Item.Properties properties, Holder<Potion> potion) {
        super(properties);
        this.potion = potion;
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
        return stack;
    }
}
