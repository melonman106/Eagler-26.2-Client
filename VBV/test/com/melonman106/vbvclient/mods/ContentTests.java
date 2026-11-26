package com.melonman106.vbvclient.mods;

import static com.melonman106.vbvclient.mods.TestKit.any;
import static com.melonman106.vbvclient.mods.TestKit.check;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * content.json (effects, potions, blocks, crops, HUD panel, recipes): the parser's rules, and, when the repository has an
 * artifacts/ folder with the Slime Mod, that example too so it cannot drift away from what the importer accepts. These
 * files come from untrusted zips, so most checks here are about what is refused.
 */
final class ContentTests {
    private ContentTests() {}

    static void run() {
        rules();
        limits();
        slimeMod();
    }

    private static ModContent.Result parse(String json) {
        return ModContent.parse("testmod", json);
    }

    private static void rules() {
        check(!parse("[]").problems().isEmpty(), "content.json must be an object");
        check(!parse("{ nope").problems().isEmpty(), "broken JSON is a problem, not an exception");
        check(parse("{}").problems().isEmpty() && !parse("{}").content().hasContent(), "an empty content.json is fine and has no content");
        check(any(parse("{\"surprise\":1}").warnings(), "unknown field"), "unknown top-level fields only warn");

        // effects
        ModContent.Result effect = parse("{\"effects\":[{\"id\":\"glow\",\"name\":\"Glow\",\"color\":\"#FF8800\",\"category\":\"harmful\","
            + "\"heal_interval\":20,\"heal_amount\":2}]}");
        check(effect.problems().isEmpty() && effect.content().effects.size() == 1, "a valid effect is accepted: " + effect.problems());
        if (!effect.content().effects.isEmpty()) {
            ModContent.EffectDef def = effect.content().effects.get(0);
            check(def.color() == 0xFF8800, "the colour is read from #RRGGBB");
            check("harmful".equals(def.category()) && def.healInterval() == 20 && def.healAmount() == 2.0F, "effect fields are read");
        }
        check(!parse("{\"effects\":[{\"id\":\"Bad Id\"}]}").problems().isEmpty(), "an effect id with capitals and spaces is refused");
        check(parse("{\"effects\":[{\"id\":\"Bad Id\"}]}").content().effects.isEmpty(), "and it is not kept");
        check(any(parse("{\"effects\":[{\"id\":\"a\"},{\"id\":\"a\"}]}").problems(), "listed twice"), "a duplicate effect id is refused");
        check(any(parse("{\"effects\":[{\"id\":\"a\",\"category\":\"spooky\"}]}").problems(), "category must be one of"), "an unknown category is refused");
        check(!parse("{\"effects\":[{\"id\":\"a\",\"heal_interval\":0}]}").problems().isEmpty(), "a heal interval of 0 is refused");
        check(any(parse("{\"effects\":{}}").problems(), "must be a list"), "effects must be a list");
        check(!parse("{\"effects\":[\"x\"]}").problems().isEmpty(), "an effect that is not an object is refused");

        // potions
        ModContent.Result potion = parse("{\"potions\":[{\"id\":\"brew\",\"effects\":[{\"effect\":\"glow\",\"duration\":100,\"amplifier\":1}]}]}");
        check(potion.problems().isEmpty() && potion.content().potions.size() == 1, "a valid potion is accepted: " + potion.problems());
        if (!potion.content().potions.isEmpty()) {
            ModContent.PotionEffectDef entry = potion.content().potions.get(0).effects().get(0);
            check("testmod:glow".equals(entry.effect()), "a bare effect id means one of this mod's effects: " + entry.effect());
            check(entry.duration() == 100 && entry.amplifier() == 1, "duration and amplifier are read");
        }
        check(!parse("{\"potions\":[{\"id\":\"brew\"}]}").problems().isEmpty(), "a potion without effects is refused");
        check(!parse("{\"potions\":[{\"id\":\"brew\",\"effects\":[]}]}").problems().isEmpty(), "a potion with an empty effect list is refused");
        check(!parse("{\"potions\":[{\"id\":\"brew\",\"effects\":[{\"effect\":\"glow\",\"amplifier\":99}]}]}").problems().isEmpty(),
            "an amplifier far past the limit is refused");
        check(!parse("{\"potions\":[{\"id\":\"brew\",\"effects\":[{\"effect\":\"glow\",\"duration\":0}]}]}").problems().isEmpty(),
            "a duration of 0 is refused");

        // blocks (a texture is required, so every refusal below is for the field being tested)
        ModContent.Result block = parse(blocks("\"hardness\":1.5,\"light\":15,\"sound\":\"glass\",\"creative_tab\":\"building_blocks\""));
        check(block.problems().isEmpty() && block.content().blocks.size() == 1, "a valid block is accepted: " + block.problems());
        if (!block.content().blocks.isEmpty()) {
            ModContent.BlockDef def = block.content().blocks.get(0);
            check(def.hardness() == 1.5F && def.resistance() == 1.5F, "resistance defaults to the hardness");
            check(def.light() == 15 && "glass".equals(def.sound()), "light and sound are read");
            check(def.creativeTabs().equals(List.of("building_blocks")), "a single creative tab is read as a list: " + def.creativeTabs());
        }
        check(parse(blocks("\"light\":15")).problems().isEmpty(), "control: light 15 is fine");
        check(!parse(blocks("\"light\":16")).problems().isEmpty(), "light above 15 is refused");
        check(parse(blocks("\"hardness\":0")).problems().isEmpty(), "control: hardness 0 is fine");
        check(!parse(blocks("\"hardness\":-1")).problems().isEmpty(), "a negative hardness is refused");
        check(!parse(blocks("\"sound\":\"explosion\"")).problems().isEmpty(), "an unknown sound is refused");
        check(!parse("{\"blocks\":[{\"id\":\"lamp\",\"texture\":\"../../etc/passwd\"}]}").problems().isEmpty(), "a texture path that climbs out is refused");
        ModContent.Result bare = parse("{\"blocks\":[{\"id\":\"lamp\"}]}");
        check(bare.problems().isEmpty() && bare.content().blocks.size() == 1, "a block without a texture is fine (the docs say it is optional): " + bare.problems());
        if (!bare.content().blocks.isEmpty()) {
            check("testmod:block/lamp".equals(bare.content().blocks.get(0).texture()), "and its texture defaults to block/<id>: " + bare.content().blocks.get(0).texture());
        }
        check(!parse("{\"blocks\":[{\"id\":\"lamp\",\"texture\":5}]}").problems().isEmpty(), "a texture that is not text is refused, not defaulted silently");
        check(!parse("{\"blocks\":[{\"id\":\"lamp\",\"texture\":\"entity/lamp\"}]}").problems().isEmpty(), "a texture outside block/ and item/ is refused");
        ModContent.Result bareCrop = parse("{\"crops\":[{\"id\":\"herb\"}]}");
        check(bareCrop.problems().isEmpty() && bareCrop.content().crops.size() == 1, "a crop with only an id is fine: " + bareCrop.problems());
        if (!bareCrop.content().crops.isEmpty()) {
            ModContent.CropDef def = bareCrop.content().crops.get(0);
            check("testmod:block/herb".equals(def.texture()) && "testmod:item/herb_seeds".equals(def.seedTexture()),
                "crop textures default from the id and the seed name: " + def.texture() + " " + def.seedTexture());
        }
        check(any(parse("{\"blocks\":[{\"id\":\"a\",\"texture\":\"block/a\"},{\"id\":\"a\",\"texture\":\"block/a\"}]}").problems(), "listed twice"),
            "a duplicate block id is refused");

        // crops
        ModContent.Result crop = parse(crops("\"stages\":4"));
        check(crop.problems().isEmpty() && crop.content().crops.size() == 1, "a valid crop is accepted: " + crop.problems());
        if (!crop.content().crops.isEmpty()) {
            ModContent.CropDef def = crop.content().crops.get(0);
            check("herb_crop".equals(def.block()) && "herb_seeds".equals(def.seed()) && "herb".equals(def.drop()),
                "block, seed and drop default from the id: " + def.block() + " " + def.seed() + " " + def.drop());
            check(def.stages() == 4, "stages are read");
        }
        String twoCrops = "{\"crops\":[{\"id\":\"herb\",\"texture\":\"block/herb\",\"seed_texture\":\"item/herb_seeds\"},"
            + "{\"id\":\"herb\",\"texture\":\"block/herb\",\"seed_texture\":\"item/herb_seeds\"}]}";
        check(any(parse(twoCrops).problems(), "listed twice"), "a duplicate crop id is refused");
        check(any(parse("{\"potions\":[{\"id\":\"p\",\"effects\":[{\"effect\":\"e\"}]},{\"id\":\"p\",\"effects\":[{\"effect\":\"e\"}]}]}").problems(), "listed twice"),
            "a duplicate potion id is refused");
        check(parse(crops("\"stages\":7")).problems().isEmpty(), "control: 7 stages is fine");
        check(!parse(crops("\"stages\":8")).problems().isEmpty(), "more than 7 stages is refused");
        check(!parse(crops("\"stages\":0")).problems().isEmpty(), "0 stages is refused");

        // HUD
        ModContent.Result hud = parse("{\"hud\":{\"id\":\"timer\",\"effect\":\"glow\",\"nearby_entity\":\"slime\",\"always_show\":true}}");
        check(hud.problems().isEmpty() && hud.content().hud != null, "a valid HUD panel is accepted: " + hud.problems());
        if (hud.content().hud != null) {
            ModContent.HudDef def = hud.content().hud;
            check("testmod:glow".equals(def.effect()), "the HUD's bare effect is this mod's: " + def.effect());
            check("minecraft:slime".equals(def.nearbyEntity()), "a bare entity id means a vanilla entity: " + def.nearbyEntity());
            check(def.alwaysShow() && def.showTimer(), "always_show is read and show_timer defaults to true");
        }
        check(!parse("{\"hud\":{\"id\":\"timer\"}}").problems().isEmpty(), "a HUD panel without an effect is refused");
        check(parse("{\"hud\":{\"id\":\"timer\"}}").content().hud == null, "and it is not kept");
        check(!parse("{\"hud\":[]}").problems().isEmpty(), "the HUD must be an object");

        // recipes
        ModContent.Result recipes = parse("{\"recipes\":[{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[\"minecraft:stick\"],\"result\":{\"id\":\"minecraft:torch\"}}]}");
        check(recipes.problems().isEmpty() && recipes.content().recipes.size() == 1, "a recipe with a type is accepted: " + recipes.problems());
        check(any(parse("{\"recipes\":[{\"result\":{}}]}").problems(), "needs a \"type\""), "a recipe without a type is refused");
        check(any(parse("{\"recipes\":{}}").problems(), "must be a list"), "recipes must be a list");
        check(!parse("{\"recipes\":[1]}").problems().isEmpty(), "a recipe that is not an object is refused");
        check(parse("{\"recipes\":[{\"type\":\"a:b\"}]}").content().kinds().equals(List.of("recipe")), "kinds() names what the file provides");
    }

    private static String blocks(String fields) {
        return "{\"blocks\":[{\"id\":\"lamp\",\"texture\":\"block/lamp\"," + fields + "}]}";
    }

    private static String crops(String fields) {
        return "{\"crops\":[{\"id\":\"herb\",\"texture\":\"block/herb\",\"seed_texture\":\"item/herb_seeds\"," + fields + "}]}";
    }

    private static void limits() {
        check(!parse(many("effects", ModContent.MAX_EFFECTS + 1)).problems().isEmpty(), "more effects than the limit are refused");
        check(parse(many("effects", ModContent.MAX_EFFECTS)).problems().isEmpty(), "exactly the limit of effects is fine");
        check(any(parse(many("blocks", ModContent.MAX_BLOCKS + 1)).problems(), "the limit is"), "more blocks than the limit are refused");
        check(any(parse(many("crops", ModContent.MAX_CROPS + 1)).problems(), "the limit is"), "more crops than the limit are refused");
        check(any(parse(many("effects", ModContent.MAX_EFFECTS + 1)).problems(), "the limit is"), "and the message names the limit");
        StringBuilder recipes = new StringBuilder("{\"recipes\":[");
        for (int i = 0; i <= ModContent.MAX_RECIPES; i++) recipes.append(i == 0 ? "" : ",").append("{\"type\":\"a:b\"}");
        check(!parse(recipes.append("]}").toString()).problems().isEmpty(), "more recipes than the limit are refused");
    }

    /** {"kind":[{"id":"x0"},{"id":"x1"},...]} */
    private static String many(String kind, int count) {
        StringBuilder out = new StringBuilder("{\"" + kind + "\":[");
        for (int i = 0; i < count; i++) out.append(i == 0 ? "" : ",").append("{\"id\":\"x").append(i).append("\"}");
        return out.append("]}").toString();
    }

    private static void slimeMod() {
        String folder = System.getProperty("vbv.slime");
        if (folder == null || folder.isEmpty()) {
            System.out.println("(Slime Mod not checked: set -Dvbv.slime=<repository artifacts folder>)");
            return;
        }
        try {
            Path root = Paths.get(folder);
            String json = new String(Files.readAllBytes(root.resolve("slime_mod/content.json")), StandardCharsets.UTF_8);
            ModContent.Result result = ModContent.parse("vbvslime", json);
            check(result.problems().isEmpty(), "the Slime Mod's content.json has no problems: " + result.problems());
            ModContent content = result.content();
            check(content.effects.size() == 1 && content.potions.size() == 1 && content.blocks.size() == 1 && content.crops.size() == 1,
                "the Slime Mod declares one effect, potion, block and crop");
            check(content.hud != null && "vbvslime:slime_body".equals(content.hud.effect()), "its HUD watches its own effect");
            check(content.recipes.size() == 6, "and it has six recipes: " + content.recipes.size());
            check(content.kinds().equals(List.of("effect", "potion", "block", "crop", "hud", "recipe")), "kinds: " + content.kinds());
            if (!content.potions.isEmpty()) {
                check("vbvslime:slime_body".equals(content.potions.get(0).effects().get(0).effect()), "its potion uses its effect");
            }

            // The shipped zip goes through the whole importer, including the cross-checks between files.
            Path zip = root.resolve("slime_mod.zip");
            check(Files.isRegularFile(zip), "artifacts/slime_mod.zip exists");
            if (Files.isRegularFile(zip)) {
                ModPackage pkg = ModPackage.read(Files.readAllBytes(zip));
                check(pkg.problems.isEmpty() && pkg.manifest != null, "the shipped slime_mod.zip imports without problems: " + pkg.problems);
                if (pkg.manifest != null) {
                    check("vbvslime".equals(pkg.manifest.id), "its id is vbvslime");
                    check(pkg.content != null && pkg.content.hasContent(), "the importer keeps its content");
                    check(pkg.kinds.containsAll(List.of("item", "block", "crop", "effect", "potion", "hud", "recipe")), "the importer lists every kind: " + pkg.kinds);
                }
            }
        } catch (IOException ex) {
            check(false, "could not read the Slime Mod: " + ex);
        }
    }
}
