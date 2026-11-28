package com.melonman106.vbvclient.mods;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * Registers the data-defined content of imported mods. Effects are registered first, then potions, then blocks and
 * crops, so a potion can refer to an effect and a crop's seed/drop can be resolved lazily after the normal items have
 * been registered. The client and the integrated-server worker run this same code from the same applied mod list.
 */
public final class ModContentBootstrap {
    private static final Map<String, Holder<MobEffect>> EFFECTS = new LinkedHashMap<>();
    private static final Map<String, Holder<Potion>> POTIONS = new LinkedHashMap<>();
    private static final Map<String, Block> BLOCKS = new LinkedHashMap<>();
    private static final Map<String, ModCropBlock> CROPS = new LinkedHashMap<>();
    private static final List<ModContent.HudDef> HUDS = new ArrayList<>();

    private ModContentBootstrap() {}

    public static synchronized void registerAll(List<ModPackage> mods) {
        for (ModPackage pkg : mods) {
            if (pkg.content == null) continue;
            for (ModContent.EffectDef def : pkg.content.effects) registerEffect(pkg.manifest.id, def);
        }
        for (ModPackage pkg : mods) {
            if (pkg.content == null) continue;
            for (ModContent.PotionDef def : pkg.content.potions) registerPotion(pkg.manifest.id, def);
        }
        for (ModPackage pkg : mods) {
            if (pkg.content == null) continue;
            for (ModContent.BlockDef def : pkg.content.blocks) registerBlock(pkg.manifest.id, def, pkg.events);
            for (ModContent.CropDef def : pkg.content.crops) registerCrop(pkg.manifest.id, def);
            if (pkg.content.hud != null) HUDS.add(pkg.content.hud);
        }
    }

    public static synchronized Holder<MobEffect> effect(String id) {
        return EFFECTS.get(id);
    }

    public static synchronized Holder<Potion> potion(String id) {
        return POTIONS.get(id);
    }

    public static synchronized List<ModContent.HudDef> huds() {
        return Collections.unmodifiableList(new ArrayList<>(HUDS));
    }

    /** Registers the block item and seed item for each block/crop after the mod's normal items. */
    static void registerBlockItems(ModPackage pkg, ModBootstrap.Report report) {
        if (pkg.content == null) return;
        for (ModContent.BlockDef def : pkg.content.blocks) {
            Block block = BLOCKS.get(full(pkg.manifest.id, def.id()));
            if (block == null) continue;
            try {
                registerBlockItem(pkg.manifest.id, def.id(), block, def.creativeTabs());
                report.registered++;
            } catch (Throwable t) {
                report.failures.add(def.id() + ": " + t);
                System.err.println("[VBV mods] " + pkg.manifest.id + ":" + def.id() + " block item could not be registered: " + t);
            }
        }
        for (ModContent.CropDef def : pkg.content.crops) {
            ModCropBlock crop = CROPS.get(full(pkg.manifest.id, def.block()));
            if (crop == null) continue;
            try {
                registerBlockItem(pkg.manifest.id, def.seed(), crop, def.creativeTabs());
                report.registered++;
            } catch (Throwable t) {
                report.failures.add(def.id() + ": " + t);
                System.err.println("[VBV mods] " + pkg.manifest.id + ":" + def.seed() + " seed item could not be registered: " + t);
            }
        }
    }

    private static void registerEffect(String modId, ModContent.EffectDef def) {
        try {
            ResourceKey<MobEffect> key = ResourceKey.create(Registries.MOB_EFFECT, id(modId, def.id()));
            MobEffectCategory category = category(def.category());
            MobEffect effect = new ModMobEffect(category, def.color(), def.healInterval(), def.healAmount());
            Registry.register(BuiltInRegistries.MOB_EFFECT, key, effect);
            EFFECTS.put(full(modId, def.id()), BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect));
        } catch (Throwable t) {
            System.err.println("[VBV mods] " + modId + ":" + def.id() + " effect could not be registered: " + t);
        }
    }

    private static void registerPotion(String modId, ModContent.PotionDef def) {
        try {
            List<MobEffectInstance> instances = new ArrayList<>();
            for (ModContent.PotionEffectDef entry : def.effects()) {
                Holder<MobEffect> effect = EFFECTS.get(entry.effect());
                if (effect == null) {
                    System.err.println("[VBV mods] " + modId + ":" + def.id() + " refers to unknown effect " + entry.effect() + "; it is skipped.");
                    continue;
                }
                instances.add(new MobEffectInstance(effect, entry.duration(), entry.amplifier()));
            }
            if (instances.isEmpty()) return;
            ResourceKey<Potion> key = ResourceKey.create(Registries.POTION, id(modId, def.id()));
            Potion potion = new Potion(def.id(), instances.toArray(new MobEffectInstance[0]));
            Registry.register(BuiltInRegistries.POTION, key, potion);
            POTIONS.put(full(modId, def.id()), BuiltInRegistries.POTION.wrapAsHolder(potion));
        } catch (Throwable t) {
            System.err.println("[VBV mods] " + modId + ":" + def.id() + " potion could not be registered: " + t);
        }
    }

    private static void registerBlock(String modId, ModContent.BlockDef def, List<ModEvents.Binding> events) {
        try {
            ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(modId, def.id()));
            BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(def.hardness(), def.resistance())
                .sound(sound(def.sound()))
                .lightLevel(state -> def.light())
                .setId(key);
            // Only blocks that a block_random_tick rule asks about are put on the random-tick list.
            if (ModEvents.wantsRandomTicks(events, full(modId, def.id()))) properties.randomTicks();
            ModBlock block = new ModBlock(properties);
            block.withDrop(() -> item(modId, def.id()));
            Registry.register(BuiltInRegistries.BLOCK, key, block);
            registerStates(block);
            BLOCKS.put(full(modId, def.id()), block);
        } catch (Throwable t) {
            System.err.println("[VBV mods] " + modId + ":" + def.id() + " block could not be registered: " + t);
        }
    }

    private static void registerCrop(String modId, ModContent.CropDef def) {
        try {
            ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(modId, def.block()));
            BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .mapColor(MapColor.PLANT)
                .randomTicks()
                .noCollision()
                .instabreak()
                .sound(SoundType.CROP)
                .offsetType(BlockBehaviour.OffsetType.XZ)
                .setId(key);
            ModCropBlock crop = new ModCropBlock(properties);
            crop.withDrops(() -> item(modId, def.seed()), () -> item(modId, def.drop()));
            Registry.register(BuiltInRegistries.BLOCK, key, crop);
            registerStates(crop);
            CROPS.put(full(modId, def.block()), crop);
        } catch (Throwable t) {
            System.err.println("[VBV mods] " + modId + ":" + def.block() + " crop could not be registered: " + t);
        }
    }

    /**
     * Gives a block's states their global ids and builds their caches. Vanilla does this once, for its own blocks,
     * in the static initializer of {@code Blocks}, which has already run by the time mod content is registered, so a
     * block registered here would otherwise have no state id (chunks, packets and the world could not store it) and
     * an empty cache (shape, fluid and ticking queries would fail). A mod loader has to do the same for any block it
     * registers late; the ids are handed out in registration order, which the page and the integrated server share.
     */
    private static void registerStates(Block block) {
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            if (Block.BLOCK_STATE_REGISTRY.getId(state) == -1) Block.BLOCK_STATE_REGISTRY.add(state);
            state.initCache();
        }
    }

    private static void registerBlockItem(String modId, String path, Block block, List<String> tabs) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(modId, path));
        blockItem(key, block, tabs);
    }

    private static void blockItem(ResourceKey<Item> key, Block block, List<String> tabs) {
        Item.Properties properties = new Item.Properties().setId(key);
        BlockItem item = new BlockItem(block, properties);
        Registry.register(BuiltInRegistries.ITEM, key, item);
        item.registerBlocks(Item.BY_BLOCK, item);
        ModItemGroups.add(tabs, item);
    }

    private static ItemLike item(String modId, String path) {
        Item item = BuiltInRegistries.ITEM.getValue(id(modId, path));
        return item == null ? Items.AIR : item;
    }

    private static MobEffectCategory category(String name) {
        if ("harmful".equals(name)) return MobEffectCategory.HARMFUL;
        if ("neutral".equals(name)) return MobEffectCategory.NEUTRAL;
        return MobEffectCategory.BENEFICIAL;
    }

    private static SoundType sound(String name) {
        switch (name) {
            case "amethyst": return SoundType.AMETHYST;
            case "glass": return SoundType.GLASS;
            case "crop": return SoundType.CROP;
            case "grass": return SoundType.GRASS;
            case "wood": return SoundType.WOOD;
            case "metal": return SoundType.METAL;
            case "wool": return SoundType.WOOL;
            case "gravel": return SoundType.GRAVEL;
            case "sand": return SoundType.SAND;
            case "snow": return SoundType.SNOW;
            case "ladder": return SoundType.LADDER;
            case "slime": return SoundType.SLIME_BLOCK;
            case "honey": return SoundType.HONEY_BLOCK;
            case "netherite": return SoundType.NETHERITE_BLOCK;
            case "bone": return SoundType.BONE_BLOCK;
            default: return SoundType.STONE;
        }
    }

    private static Identifier id(String modId, String path) {
        return Identifier.fromNamespaceAndPath(modId, path);
    }

    private static String full(String modId, String path) {
        return modId + ":" + path;
    }
}
