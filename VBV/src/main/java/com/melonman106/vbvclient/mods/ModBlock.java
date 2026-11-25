package com.melonman106.vbvclient.mods;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;

/** A plain block whose only custom behaviour is dropping its own block item, so it needs no loot table. */
public final class ModBlock extends Block {
    public static final MapCodec<ModBlock> CODEC = BlockBehaviour.simpleCodec(ModBlock::new);
    private Supplier<ItemLike> drop = () -> Items.AIR;

    public ModBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    public ModBlock withDrop(Supplier<ItemLike> drop) {
        this.drop = drop;
        return this;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        ItemLike item = drop.get();
        if (item == null || item.asItem() == Items.AIR) return List.of();
        return List.of(new ItemStack(item));
    }
}
