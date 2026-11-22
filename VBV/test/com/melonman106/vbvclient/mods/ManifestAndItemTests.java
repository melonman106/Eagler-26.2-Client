package com.melonman106.vbvclient.mods;

import static com.melonman106.vbvclient.mods.TestKit.any;
import static com.melonman106.vbvclient.mods.TestKit.check;

import java.util.List;

final class ManifestAndItemTests {
    private ManifestAndItemTests() {}

    static void run() {
        manifest();
        fabricManifest();
        items();
    }

    private static ModManifest.Result info(String json) { return ModManifest.parse(ModManifest.Format.VBV, json); }

    static void manifest() {
        ModManifest.Result ok = info("{\"id\":\"ruby_tools\",\"name\":\"Ruby Tools\",\"version\":\"1.2.3\"}");
        check(ok.ok(), "minimal info.json is accepted: " + ok.problems());
        check(ok.manifest().survival && !ok.manifest().multiplayer, "survival defaults to true, multiplayer to false");
        check("pack.png".equals(ok.manifest().icon), "icon defaults to pack.png");
        check(ok.manifest().environment.equals("*"), "environment defaults to *");

        check(!info("not json").ok() && any(info("not json").problems(), "not valid JSON"), "garbage is reported as invalid JSON");
        check(!info("[]").ok() && any(info("[]").problems(), "must be a JSON object"), "a list at the top is refused");
        check(!info("{}").ok() && any(info("{}").problems(), "\"id\" is required"), "id is required");
        check(any(info("{\"id\":\"ok_id\",\"version\":\"1\"}").problems(), "\"name\" is required"), "name is required in info.json");
        check(any(info("{\"id\":\"ok_id\",\"name\":\"x\"}").problems(), "\"version\" is required"), "version is required");
        check(any(info("{\"id\":\"Bad Id\",\"name\":\"x\",\"version\":\"1\"}").problems(), "must be 2-64 characters"), "uppercase/space id refused");
        check(any(info("{\"id\":\"../x\",\"name\":\"x\",\"version\":\"1\"}").problems(), "must be 2-64 characters"), "path-like id refused");
        check(any(info("{\"id\":\"x\",\"name\":\"x\",\"version\":\"1\"}").problems(), "must be 2-64 characters"), "one-letter id refused");
        for (String reserved : new String[] {"minecraft", "vbvclient", "fabric-api", "fabricloader", "fabric-whatever", "java"}) {
            check(any(info("{\"id\":\"" + reserved + "\",\"name\":\"x\",\"version\":\"1\"}").problems(), "reserved"), "reserved id refused: " + reserved);
        }
        check(any(info("{\"schema\":2,\"id\":\"ok_id\",\"name\":\"x\",\"version\":\"1\"}").problems(), "understands up to 1"), "newer schema refused");
        check(any(info("{\"id\":\"ok_id\",\"name\":\"x\",\"version\":\"1\",\"survival\":\"yes\"}").problems(), "true or false"), "non-boolean survival refused");
        check(any(info("{\"id\":\"ok_id\",\"name\":\"x\",\"version\":3}").problems(), "must be text"), "numeric version refused");
        check(any(info("{\"id\":\"ok_id\",\"name\":\"x\",\"version\":\"1\",\"provides\":[\"gizmo\"]}").problems(), "unknown content kind"), "unknown kind refused");
        check(any(info("{\"id\":\"ok_id\",\"name\":\"x\",\"version\":\"1\",\"provides\":\"item\"}").problems(), "must be a list"), "provides must be a list");
        check(any(info("{\"id\":\"ok_id\",\"name\":\"x\",\"version\":\"1\",\"environment\":\"both\"}").problems(), "environment"), "bad environment refused");

        ModManifest.Result kinds = info("{\"id\":\"ok_id\",\"name\":\"x\",\"version\":\"1\",\"provides\":[\"Item\",\"item\",\"block\"]}");
        check(kinds.ok() && kinds.manifest().declaredKinds.equals(List.of("item", "block")), "kinds are lowercased and de-duplicated: " + kinds.problems());

        ModManifest.Result longName = info("{\"id\":\"ok_id\",\"name\":\"" + "n".repeat(80) + "\",\"version\":\"1\"}");
        check(longName.ok() && longName.manifest().name.length() == 48 && any(longName.warnings(), "shortened"), "long names are shortened with a warning");

        // The three shapes of "requires".
        ModManifest.Result objectForm = info("{\"id\":\"ok_id\",\"name\":\"x\",\"version\":\"1\",\"requires\":{\"other\":\">=1.0\",\"fabric-api\":\"*\"}}");
        check(objectForm.ok() && objectForm.manifest().dependencies.size() == 2, "requires as an object: " + objectForm.problems());
        ModManifest.Result listForm = info("{\"id\":\"ok_id\",\"name\":\"x\",\"version\":\"1\",\"requires\":[{\"id\":\"other\",\"version\":\">=1.0.0\"},{\"id\":\"b\"}]}");
        check(listForm.ok() && listForm.manifest().dependencies.size() == 2, "requires as objects: " + listForm.problems());
        check(listForm.manifest().dependencies.get(0).range().toString().equals(">=1.0.0") && listForm.manifest().dependencies.get(1).range().toString().equals("*"), "versions and default range");
        ModManifest.Result stringForm = info("{\"id\":\"ok_id\",\"name\":\"x\",\"version\":\"1\",\"requires\":[\"other >=2.0\",\"fabric-loader\"]}");
        check(stringForm.ok() && stringForm.manifest().dependencies.get(0).range().toString().equals(">=2.0"), "requires as strings");
        check(stringForm.manifest().dependencies.get(1).id().equals("fabricloader"), "alias is normalised in requires");
        check(!info("{\"id\":\"ok_id\",\"name\":\"x\",\"version\":\"1\",\"requires\":{\"other\":5}}").ok(), "numeric range refused");
        check(!info("{\"id\":\"ok_id\",\"name\":\"x\",\"version\":\"1\",\"requires\":5}").ok(), "requires must be a list or object");
        ModManifest.Result kindsOfDeps = info("{\"id\":\"ok_id\",\"name\":\"x\",\"version\":\"1\",\"requires\":[\"a\"],\"recommends\":[\"b\"],\"conflicts\":[\"c\"],\"breaks\":[\"d\"]}");
        check(kindsOfDeps.ok() && kindsOfDeps.manifest().dependencies.get(0).kind() == FabricFaker.Kind.REQUIRES
            && kindsOfDeps.manifest().dependencies.get(1).kind() == FabricFaker.Kind.RECOMMENDS
            && kindsOfDeps.manifest().dependencies.get(2).kind() == FabricFaker.Kind.CONFLICTS
            && kindsOfDeps.manifest().dependencies.get(3).kind() == FabricFaker.Kind.BREAKS, "all four dependency kinds are read");
    }

    static void fabricManifest() {
        String json = "{\"schemaVersion\":1,\"id\":\"cool-mod\",\"version\":\"2.0.0+mc26.2\",\"name\":\"Cool Mod\",\"description\":\"Hi\","
            + "\"authors\":[\"A\",{\"name\":\"B\",\"contact\":{}}],\"icon\":{\"16\":\"small.png\",\"128\":\"big.png\"},\"environment\":\"client\","
            + "\"entrypoints\":{\"main\":[\"com.example.Main\"]},\"mixins\":[\"cool.mixins.json\"],"
            + "\"depends\":{\"fabricloader\":\">=0.15.0\",\"fabric-api\":\"*\",\"minecraft\":\"~26.2\",\"java\":\">=21\"},"
            + "\"recommends\":{\"modmenu\":\"*\"},\"breaks\":{\"badmod\":\"<1\"},\"suggests\":{\"x\":\"*\"}}";
        ModManifest.Result r = ModManifest.parse(ModManifest.Format.FABRIC, json);
        check(r.ok(), "real-looking fabric.mod.json is accepted: " + r.problems());
        ModManifest m = r.manifest();
        check(m.id.equals("cool-mod") && m.name.equals("Cool Mod") && m.version.equals("2.0.0+mc26.2"), "fabric basics");
        check(m.authors.equals(List.of("A", "B")), "authors as strings and objects: " + m.authors);
        check("big.png".equals(m.icon), "biggest icon wins");
        check(m.environment.equals("client"), "environment is read");
        check(m.hasJavaCode && any(r.warnings(), "Java cannot run"), "entrypoints/mixins produce the Java warning");
        check(m.dependencies.size() == 6, "depends + recommends + breaks are all read: " + m.dependencies.size());
        FabricFaker.Resolution resolution = FabricFaker.resolve(m.dependencies, id -> null);
        check(resolution.ok() && resolution.faked().contains("fabricloader") && resolution.faked().contains("fabric-api"), "its Fabric dependencies are satisfied by the faker: " + resolution.errors());
        check(resolution.warnings().size() == 1 && resolution.warnings().get(0).contains("modmenu"), "missing recommended mod only warns: " + resolution.warnings());

        ModManifest.Result noName = ModManifest.parse(ModManifest.Format.FABRIC, "{\"id\":\"plain\",\"version\":\"1.0\"}");
        check(noName.ok() && noName.manifest().name.equals("plain") && noName.manifest().icon == null, "fabric name is optional and there is no default icon");
        check(!ModManifest.parse(ModManifest.Format.FABRIC, "{\"version\":\"1.0\"}").ok(), "fabric id is required");
        ModManifest.Result array = ModManifest.parse(ModManifest.Format.FABRIC, "{\"id\":\"plain\",\"version\":\"1\",\"depends\":{\"fabric-api\":[\">=0.1\",\"1.x\"]}}");
        check(array.ok() && array.manifest().dependencies.get(0).range().matches(ModVersion.parse("1.4")), "an array of ranges is any-of");
        ModManifest.Result pure = ModManifest.parse(ModManifest.Format.FABRIC, "{\"id\":\"plain\",\"version\":\"1\"}");
        check(pure.ok() && !pure.manifest().hasJavaCode && pure.warnings().isEmpty(), "no java warning without entrypoints");
    }

    private static ModItems.Result items(String json) { return ModItems.parse("ruby_tools", json); }

    static void items() {
        ModItems.Result sword = items("{\"items\":[{\"id\":\"ruby_sword\",\"attack_damage\":8,\"attack_speed\":1.6,\"durability\":1800,\"rarity\":\"rare\",\"repair_with\":\"diamond\"}]}");
        check(sword.problems().isEmpty() && sword.items().size() == 1, "sword is valid: " + sword.problems());
        ModItems.ItemDef d = sword.items().get(0);
        check(d.name().equals("Ruby Sword"), "name defaults to a readable id: " + d.name());
        check(d.texture().equals("ruby_tools:item/ruby_sword"), "texture defaults to <mod>:item/<id>");
        check(d.model().equals("handheld"), "weapons default to the handheld model");
        check(d.stackSize() == 1 && d.durability() == 1800, "durable items stack to 1");
        check(d.attackDamage() == 8.0 && d.attackSpeed() == 1.6, "damage and speed kept as the final numbers");
        check(d.repairWith().equals("minecraft:diamond"), "repair item gets the minecraft namespace");
        check(d.creativeTabs().equals(List.of("combat")), "weapons default to the combat tab: " + d.creativeTabs());
        check(d.descriptionKey("ruby_tools").equals("item.ruby_tools.ruby_sword"), "description key");

        ModItems.ItemDef plain = items("[{\"id\":\"ruby\"}]").items().get(0);
        check(plain.stackSize() == 64 && plain.model().equals("generated") && plain.creativeTabs().equals(List.of("ingredients")), "plain items: stack 64, generated model, ingredients tab");
        check(items("[{\"id\":\"pick\",\"durability\":100}]").items().get(0).creativeTabs().equals(List.of("tools_and_utilities")), "durable tool default tab");
        ModItems.ItemDef food = items("[{\"id\":\"pie\",\"food\":{\"nutrition\":6,\"saturation\":4.5}}]").items().get(0);
        check(food.food() != null && food.food().nutrition() == 6 && food.food().saturation() == 4.5f && !food.food().alwaysEdible(), "food values");
        check(food.creativeTabs().equals(List.of("food_and_drinks")), "food default tab");

        check(any(items("not json").problems(), "not valid JSON"), "items: invalid JSON");
        check(any(items("{\"things\":[]}").problems(), "must look like"), "items: wrong top shape");
        check(any(items("[5]").problems(), "must be an object"), "items: non-object entry");
        check(any(items("[{}]").problems(), "\"id\" is required"), "items: id required");
        check(any(items("[{\"id\":\"Bad-Id\"}]").problems(), "lowercase letters"), "items: bad id");
        check(any(items("[{\"id\":\"a\"},{\"id\":\"a\"}]").problems(), "listed twice"), "items: duplicate id");
        check(any(items("[{\"id\":\"a\",\"rarity\":\"mythic\"}]").problems(), "rarity must be"), "items: bad rarity");
        check(any(items("[{\"id\":\"a\",\"durability\":10,\"stack_size\":16}]").problems(), "cannot stack"), "items: durability with stack > 1");
        check(any(items("[{\"id\":\"a\",\"stack_size\":0}]").problems(), "between 1 and 99"), "items: stack range");
        check(any(items("[{\"id\":\"a\",\"stack_size\":100}]").problems(), "between 1 and 99"), "items: stack range upper");
        check(any(items("[{\"id\":\"a\",\"stack_size\":2.5}]").problems(), "whole number"), "items: stack must be whole");
        check(any(items("[{\"id\":\"a\",\"attack_damage\":-1}]").problems(), "between 0 and 1024"), "items: negative damage");
        check(any(items("[{\"id\":\"a\",\"attack_damage\":\"8\"}]").problems(), "must be a number"), "items: damage must be a number");
        check(any(items("[{\"id\":\"a\",\"model\":\"cube\"}]").problems(), "model must be"), "items: bad model");
        check(any(items("[{\"id\":\"a\",\"texture\":\"BAD TEXTURE\"}]").problems(), "namespace:path"), "items: bad texture id");
        check(any(items("[{\"id\":\"a\",\"glint\":\"yes\"}]").problems(), "true or false"), "items: bad flag");
        check(any(items("[{\"id\":\"a\",\"creative_tab\":\"stuff\"}]").problems(), "unknown creative_tab"), "items: bad tab");
        check(any(items("[{\"id\":\"a\",\"creative_tab\":[\"combat\",\"none\"]}]").problems(), "cannot mix"), "items: none mixed with tabs");
        check(items("[{\"id\":\"a\",\"creative_tab\":\"minecraft:combat\"}]").items().get(0).creativeTabs().equals(List.of("combat")), "items: tab with namespace");
        check(items("[{\"id\":\"a\",\"creative_tab\":[\"combat\",\"ingredients\"]}]").items().get(0).creativeTabs().size() == 2, "items: several tabs");
        check(items("[{\"id\":\"a\",\"creative_tab\":\"none\"}]").items().get(0).creativeTabs().equals(List.of("none")), "items: hidden item");
        check(any(items("[{\"id\":\"a\",\"food\":{\"nutrition\":50}}]").problems(), "between 0 and 20"), "items: food range");
        check(any(items("[{\"id\":\"a\",\"durability\":9,\"food\":{\"nutrition\":2}}]").problems(), "food and have durability"), "items: food with durability");

        ModItems.Result warned = items("[{\"id\":\"a\",\"attack_speed\":2,\"banana\":1,\"repair_with\":\"diamond\",\"tags\":[\"minecraft:swords\"]},{\"id\":\"b\",\"tags\":[\"swords\"]}]");
        check(warned.problems().isEmpty() && warned.items().size() == 2, "warnings do not reject: " + warned.problems());
        check(any(warned.warnings(), "attack_speed has no effect"), "speed without damage warns");
        check(any(warned.warnings(), "unknown field \"banana\""), "unknown field warns");
        check(any(warned.warnings(), "repair_with has no effect"), "repair without durability warns");
        check(warned.warnings().stream().filter(w -> w.contains("Item tags are read")).count() == 1, "the tags notice appears once");
        check(warned.items().get(1).tags().equals(List.of("minecraft:swords")), "bare tag gets the minecraft namespace");
        check(warned.items().get(0).attackSpeed() == null, "ignored speed is dropped");

        // One bad item does not hide the good one, but the whole list is reported as having problems.
        ModItems.Result mixed = items("[{\"id\":\"good\"},{\"id\":\"BAD\"}]");
        check(mixed.items().size() == 1 && mixed.problems().size() == 1, "bad items are left out of the list and reported");

        StringBuilder many = new StringBuilder("[");
        for (int i = 0; i <= ModItems.MAX_ITEMS; i++) many.append(i == 0 ? "" : ",").append("{\"id\":\"i").append(i).append("\"}");
        many.append("]");
        check(any(items(many.toString()).problems(), "the limit is"), "too many items refused");
        check(ModItems.prettify("a_b_cd").equals("A B Cd"), "prettify");
    }
}
