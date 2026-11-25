package com.melonman106.vbvclient.mods;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;

/** Adds the recipes in imported mods' content.json to the recipe map the game just loaded. */
public final class ModRecipes {
    private ModRecipes() {}

    public static RecipeMap augment(RecipeMap loaded, HolderLookup.Provider registries) {
        List<RecipeHolder<?>> all = new ArrayList<>(loaded.values());
        int added = 0;
        for (ModPackage pkg : ModBootstrap.loaded()) {
            if (pkg.content == null || pkg.content.recipes.isEmpty()) continue;
            for (int index = 0; index < pkg.content.recipes.size(); index++) {
                JsonElement json = pkg.content.recipes.get(index);
                try {
                    Recipe<?> recipe = (Recipe<?>)Recipe.CODEC
                        .parse(registries.createSerializationContext(JsonOps.INSTANCE), json)
                        .getOrThrow(JsonParseException::new);
                    ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE,
                        Identifier.fromNamespaceAndPath(pkg.manifest.id, "imported_" + index));
                    all.add(new RecipeHolder<>(key, recipe));
                    added++;
                } catch (Throwable t) {
                    System.err.println("[VBV mods] " + pkg.manifest.id + " recipe " + (index + 1) + " was ignored: " + t);
                }
            }
        }
        return added == 0 ? loaded : RecipeMap.create(all);
    }
}
