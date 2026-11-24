package com.melonman106.vbvclient.mods;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;

/**
 * Offers every mod that was registered at startup as a required resource pack. They sit above the game's own assets
 * and below the player's own packs, in mod-id order, so a later mod overrides an earlier one and a player's pack still
 * overrides them all. Which mods exist is decided at startup (see {@link ModBootstrap}), so this never reads the
 * library of imported files: a mod that was just imported or switched off changes nothing until the next restart.
 */
public final class ModPackSource implements RepositorySource {
    public static final String ID_PREFIX = "vbvmod/";

    private static final PackSource MOD_SOURCE = PackSource.create(
        name -> Component.translatable("pack.nameAndSource", name, Component.literal("Mod")).withStyle(ChatFormatting.GRAY), true);

    @Override
    public void loadPacks(Consumer<Pack> result) {
        for (ModPackage pkg : ModBootstrap.loaded()) {
            try {
                Pack pack = create(pkg);
                if (pack != null) result.accept(pack);
            } catch (Throwable t) {
                System.err.println("[VBV mods] " + pkg.manifest.id + " could not be offered as a resource pack: " + t);
            }
        }
    }

    private static Pack create(ModPackage pkg) {
        Map<String, byte[]> assets = ModAssets.build(pkg);
        byte[] meta = ModAssets.packMeta(pkg, SharedConstants.RESOURCE_PACK_FORMAT_MAJOR);
        PackLocationInfo location = new PackLocationInfo(
            ID_PREFIX + pkg.manifest.id, Component.literal(pkg.manifest.name), MOD_SOURCE, Optional.empty());
        Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo info) {
                return new ModPackResources(info, assets, meta, pkg.icon);
            }

            @Override
            public PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) {
                return new ModPackResources(info, assets, meta, pkg.icon);
            }
        };
        // Required and fixed at the bottom: the player cannot reorder these (the Mods screen is where they are managed),
        // and they stay beneath any pack the player adds.
        return Pack.readMetaAndCreate(location, supplier, PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Pack.Position.BOTTOM, true));
    }
}
