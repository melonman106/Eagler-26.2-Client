package com.melonman106.vbvclient.mods;

import static com.melonman106.vbvclient.mods.TestKit.any;
import static com.melonman106.vbvclient.mods.TestKit.check;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Imports the example mods that ship with the repository (built by VBV/examples/build_example_mods.py) through the real
 * store, so the documentation's examples cannot drift away from what the importer accepts. Skipped when the property
 * {@code vbv.examples} is not set.
 */
final class ExampleTests {
    private ExampleTests() {}

    static void run() {
        String folder = System.getProperty("vbv.examples");
        if (folder == null || folder.isEmpty()) {
            System.out.println("(example mods not checked: set -Dvbv.examples=<folder with the built zips>)");
            return;
        }
        try {
            TestKit.MemoryFiles files = new TestKit.MemoryFiles();
            ModStore store = new ModStore(files, id -> id.equals("vbvclient") ? "2.0.0" : null);
            for (String name : List.of("ruby_tools", "fabric_style_demo", "ember_demo")) {
                Path zip = Paths.get(folder, name + ".zip");
                check(Files.isRegularFile(zip), "example zip exists: " + zip);
                ModStore.InstallResult result = store.install(Files.readAllBytes(zip));
                check(result.ok(), name + " imports: " + result.problems());
            }
            ModStore.Plan plan = store.plan();
            check(plan.isActive("ruby_tools") && plan.isActive("fabric_style_demo") && plan.isActive("ember_demo"), "all three examples would load: " + plan.blocked());

            ModPackage ruby = plan.active().stream().filter(p -> p.manifest.id.equals("ruby_tools")).findFirst().orElseThrow();
            check(ruby.items.size() == 3, "ruby_tools has three items");
            check(ruby.icon != null && ModPackage.pngSize(ruby.icon)[0] == 64, "ruby_tools has its 64x64 pack.png");
            check(ruby.warnings.isEmpty(), "ruby_tools is clean: " + ruby.warnings);
            Map<String, byte[]> assets = ModAssets.build(ruby);
            check(assets.containsKey("ruby_tools/items/ruby_sword.json") && assets.containsKey("ruby_tools/models/item/ruby_sword.json"), "item definition and model are made");
            String lang = new String(assets.get("ruby_tools/lang/en_us.json"), java.nio.charset.StandardCharsets.UTF_8);
            check(lang.contains("item.ruby_tools.ruby_sword") && lang.contains("Ruby Sword"), "language file names the sword");
            check(new String(assets.get("ruby_tools/models/item/ruby_sword.json"), java.nio.charset.StandardCharsets.UTF_8).contains("handheld"), "a weapon uses the handheld model");
            ModItems.ItemDef apple = ruby.items.stream().filter(i -> i.id().equals("ruby_apple")).findFirst().orElseThrow();
            check(apple.food() != null && apple.food().nutrition() == 6 && apple.glint(), "ruby_apple is food with a glint");
            ModItems.ItemDef sword = ruby.items.stream().filter(i -> i.id().equals("ruby_sword")).findFirst().orElseThrow();
            check(sword.creativeTabs().equals(List.of("combat", "tools_and_utilities")) && sword.durability() == 1800, "ruby_sword tabs and durability");

            ModPackage demo = plan.active().stream().filter(p -> p.manifest.id.equals("fabric_style_demo")).findFirst().orElseThrow();
            check(demo.manifest.format == ModManifest.Format.FABRIC, "the demo is read as a fabric.mod.json mod");
            check(demo.icon != null, "the demo's icon path in fabric.mod.json is found");
            check(any(demo.warnings, "entrypoint") || any(demo.warnings, "Java"), "its Java entrypoint is reported as not run: " + demo.warnings);
            FabricFaker.Resolution resolution = plan.resolutions().get("fabric_style_demo");
            check(resolution != null && resolution.faked().contains("fabricloader") && resolution.faked().contains("fabric-api"), "Fabric Loader and API are faked for it: " + resolution);
            check(resolution.faked().contains("fabric-item-group-api-v1") || FabricFaker.find("fabric-item-group-api-v1").backing() == FabricFaker.Backing.PARTIAL, "item group module is partly real");

            List<ModView.Line> lines = ModView.details(store.installed().stream().filter(m -> m.id().equals("fabric_style_demo")).findFirst().orElseThrow(), plan, Map.of());
            check(lines.stream().anyMatch(l -> l.text().contains("Fabric Loader") && l.text().contains("faked")), "details say the loader is faked");

            ModPackage ember = plan.active().stream().filter(p -> p.manifest.id.equals("ember_demo")).findFirst().orElseThrow();
            check(ember.ok() && ember.warnings.isEmpty(), "ember_demo is clean: " + ember.problems + ember.warnings);
            check(ember.kinds.contains("item") && ember.kinds.contains("event"), "ember_demo provides items and events: " + ember.kinds);
            check(ember.events.size() == 6, "ember_demo has six event rules: " + ember.events.size());
            check(ModEvents.commandCount(ember.events) == 1 && ember.manifest.effectiveCommandLevel() == 2, "one command, at level 2");
            check(ember.events.stream().anyMatch(b -> b.event() == ModEvents.Event.ITEM_USE && "ember_demo:ember_wand".equals(b.target())), "the wand rule targets the mod's own item");
            check(!ModEvents.wantsRandomTicks(ember.events, "ember_demo:anything"), "no block_random_tick rule, so no random ticks");

            // The switch: commands are off until the player allows them, and the applied snapshot carries the choice.
            check(!store.commandsAllowed(ember), "commands start switched off");
            check(!store.apply(store.plan()).commandsAllowed().contains("ember_demo"), "an unapproved mod's commands are not in the snapshot");
            check(store.allowCommands(ember) && store.commandsAllowed(ember), "allowing records the approval");
            check(store.apply(store.plan()).commandsAllowed().contains("ember_demo"), "the snapshot now carries the approval");
            check(store.readApplied().commandsAllowed().contains("ember_demo"), "the integrated-server worker reads the approval back");
            check(ModView.commandState(ember, true, Boolean.TRUE) == ModView.CommandState.ALLOWED, "the screen shows the commands as allowed");
            check(ModView.allowPrompt(ember).stream().anyMatch(l -> l.text().contains("particle minecraft:lava")), "the prompt shows the command it would allow");
            List<ModView.Line> emberLines = ModView.details(store.installed().stream().filter(m -> m.id().equals("ember_demo")).findFirst().orElseThrow(), plan, Map.of(), true, Boolean.TRUE);
            check(emberLines.stream().anyMatch(l -> l.text().contains("6 rules react to")), "details list the rules");
            check(emberLines.stream().anyMatch(l -> l.text().equals("Commands are allowed.")), "details say the commands are allowed");
        } catch (IOException ex) {
            check(false, "could not read the example zips: " + ex);
        }
    }
}
