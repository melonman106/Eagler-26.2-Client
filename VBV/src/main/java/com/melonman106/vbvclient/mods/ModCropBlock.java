package com.melonman106.vbvclient.mods;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;

/** A normal eight-stage crop with data-supplied seed and harvest drops, so it needs no loot table. */
public final class ModCropBlock extends CropBlock {
    public static final MapCodec<ModCropBlock> CODEC = BlockBehaviour.simpleCodec(ModCropBlock::new);
    private Supplier<ItemLike> seed = () -> Items.WHEAT_SEEDS;
    private Supplier<ItemLike> drop = () -> Items.AIR;

    public ModCropBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    public ModCropBlock withDrops(Supplier<ItemLike> seed, Supplier<ItemLike> drop) {
        this.seed = seed;
        this.drop = drop;
        return this;
    }

    @Override
    public MapCodec<? extends CropBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return seed.get();
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        ItemLike seedItem = seed.get();
        List<ItemStack> out = new ArrayList<>();
        if (this.getAge(state) >= this.getMaxAge()) {
            ItemLike harvest = drop.get();
            if (harvest != null && harvest.asItem() != Items.AIR) {
                out.add(new ItemStack(harvest, 1 + params.getLevel().getRandom().nextInt(2)));
            }
            if (seedItem != null && seedItem.asItem() != Items.AIR) {
                out.add(new ItemStack(seedItem, 1 + params.getLevel().getRandom().nextInt(3)));
            }
            return out;
        }
        if (seedItem != null && seedItem.asItem() != Items.AIR) out.add(new ItemStack(seedItem));
        return out;
    }
}
