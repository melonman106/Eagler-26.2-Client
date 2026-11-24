package com.melonman106.vbvclient.mods;

import static com.melonman106.vbvclient.mods.TestKit.any;
import static com.melonman106.vbvclient.mods.TestKit.bytes;
import static com.melonman106.vbvclient.mods.TestKit.check;
import static com.melonman106.vbvclient.mods.TestKit.goodMod;
import static com.melonman106.vbvclient.mods.TestKit.zip;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Status and wording of the Mods screen's details pane. */
final class ViewTests {
    private ViewTests() {}

    static void run() {
        statuses();
        details();
    }

    private static ModStore store(TestKit.MemoryFiles files) {
        return new ModStore(files, id -> id.equals("vbvclient") ? "2.0.0" : null);
    }

    private static ModStore.Installed only(ModStore store, String id) {
        for (ModStore.Installed mod : store.installed()) if (mod.id().equals(id)) return mod;
        throw new IllegalStateException("not stored: " + id);
    }

    private static ModStore.Installed one(TestKit.MemoryFiles files, String id) {
        return only(store(files), id);
    }

    static void statuses() {
        TestKit.MemoryFiles files = new TestKit.MemoryFiles();
        ModStore store = store(files);
        check(store.install(zip(goodMod("ruby_tools"))).ok(), "installs");
        Map<String, Long> none = new HashMap<>();

        ModStore.Installed mod = only(store, "ruby_tools");
        check(ModView.status(mod, store.plan(), none) == ModView.Status.WILL_LOAD, "stored but not registered yet: loads after restart");

        Map<String, Long> loaded = new HashMap<>();
        loaded.put("ruby_tools", mod.pkg().crc);
        check(ModView.status(mod, store.plan(), loaded) == ModView.Status.LOADED, "registered and planned: loaded");

        loaded.put("ruby_tools", mod.pkg().crc + 1);
        check(ModView.status(mod, store.plan(), loaded) == ModView.Status.WILL_UPDATE, "a different copy was registered: updated copy loads after restart");

        loaded.put("ruby_tools", mod.pkg().crc);
        store.setEnabled("ruby_tools", false);
        mod = only(store, "ruby_tools");
        check(ModView.status(mod, store.plan(), loaded) == ModView.Status.WILL_UNLOAD, "turned off while loaded: turns off after restart");
        check(ModView.status(mod, store.plan(), none) == ModView.Status.DISABLED, "turned off and not loaded: turned off");
        check(!ModView.canTurnOff(mod), "a turned-off mod offers Turn on");
        store.setEnabled("ruby_tools", true);
        mod = only(store, "ruby_tools");
        check(ModView.canTurnOff(mod), "an enabled mod offers Turn off");

        store.setSafeMode(true);
        check(ModView.status(mod, store.plan(), none) == ModView.Status.SAFE_MODE, "safe mode, not loaded");
        check(ModView.status(mod, store.plan(), loaded) == ModView.Status.WILL_UNLOAD, "safe mode turned on while loaded");
        store.setSafeMode(false);

        Map<String, byte[]> needy = new LinkedHashMap<>();
        needy.put("info.json", bytes("{\"id\":\"needy\",\"name\":\"Needy\",\"version\":\"1.0.0\",\"requires\":[\"absent >=1.0\"]}"));
        check(store.install(zip(needy)).ok(), "installs the mod that needs another");
        check(ModView.status(only(store, "needy"), store.plan(), none) == ModView.Status.BLOCKED, "missing dependency: not loaded");

        files.map.put("mods/broken.zip", bytes("this is not a zip"));
        ModStore.Installed broken = only(store, "broken");
        check(ModView.status(broken, store.plan(), none) == ModView.Status.INVALID, "unreadable file is invalid");
        check(!ModView.canTurnOff(broken), "an invalid mod cannot be turned off (only removed)");

        for (ModView.Status s : ModView.Status.values()) check(s.label != null && !s.label.isEmpty(), "label for " + s);
    }

    static void details() {
        TestKit.MemoryFiles files = new TestKit.MemoryFiles();
        ModStore store = store(files);
        Map<String, byte[]> fabric = goodMod("ruby_tools");
        fabric.put("info.json", bytes("{\"id\":\"ruby_tools\",\"name\":\"Ruby Tools\",\"version\":\"1.0.0\",\"description\":\"A sword.\",\"authors\":[\"Alexander\"],"
            + "\"requires\":{\"fabricloader\":\">=0.15.0\",\"fabric-api\":\"*\",\"minecraft\":\"~26.2\"},\"survival\":true,\"multiplayer\":false}"));
        fabric.put("blocks.json", bytes("{\"blocks\":[]}"));
        check(store.install(zip(fabric)).ok(), "installs a mod with Fabric needs");
        ModStore.Installed mod = only(store, "ruby_tools");
        ModStore.Plan plan = store.plan();
        List<ModView.Line> lines = ModView.details(mod, plan, new HashMap<>());
        List<String> text = lines.stream().map(ModView.Line::text).toList();

        check(any(text, "Will load the next time"), "status sentence: " + text);
        check(any(text, "A sword."), "description shown");
        check(any(text, "By Alexander"), "author shown");
        check(any(text, "1 item: Ruby Sword"), "item count and names: " + text);
        check(any(text, "Survival: yes") && any(text, "Multiplayer: no"), "environment flags");
        check(any(text, "faked (reported as present, does nothing)") && any(text, "reports " + FabricFaker.LOADER_VERSION), "Fabric Loader is shown as faked with the version it reports: " + text);
        check(any(text, "partly real") && any(text, "reports " + FabricFaker.API_VERSION), "Fabric API is shown as partly real: " + text);
        check(any(text, "Not supported yet, so left out: blocks.json"), "an ignored list is called out: " + text);
        long headings = lines.stream().filter(l -> l.tone() == ModView.Tone.HEADING).count();
        check(headings >= 3, "section headings present");

        // Loaded and healthy: good tone, no problems section
        Map<String, Long> loaded = new HashMap<>();
        loaded.put("ruby_tools", mod.pkg().crc);
        lines = ModView.details(mod, plan, loaded);
        check(lines.get(0).tone() == ModView.Tone.GOOD && lines.get(0).text().startsWith("Loaded in this session"), "loaded is good news");
        check(lines.stream().noneMatch(l -> l.text().equals("Why it is not loaded")), "no 'why not' section when it loads");

        // Blocked: reasons come through as errors
        Map<String, byte[]> needy = new LinkedHashMap<>();
        needy.put("info.json", bytes("{\"id\":\"needy\",\"name\":\"Needy\",\"version\":\"1.0.0\",\"requires\":[\"absent >=1.0\"]}"));
        store.install(zip(needy));
        lines = ModView.details(only(store, "needy"), store.plan(), new HashMap<>());
        check(lines.stream().anyMatch(l -> l.tone() == ModView.Tone.ERROR && l.text().contains("needs absent")), "blocked reason is an error line: " + lines);
        check(lines.stream().anyMatch(l -> l.text().equals("Why it is not loaded")), "blocked mod has a 'why not' section");
        check(lines.get(0).tone() == ModView.Tone.ERROR, "blocked status is an error tone");

        // Turned off: no 'why not' section, the status already says it
        store.setEnabled("needy", false);
        lines = ModView.details(only(store, "needy"), store.plan(), new HashMap<>());
        check(lines.stream().noneMatch(l -> l.text().equals("Why it is not loaded")), "turned off mods do not show the internal 'Turned off.' reason");
        check(lines.get(0).text().startsWith("Turned off"), "turned off sentence");

        // Unreadable stored file
        files.map.put("mods/broken.zip", bytes("nope"));
        lines = ModView.details(only(store, "broken"), store.plan(), new HashMap<>());
        check(lines.get(0).tone() == ModView.Tone.ERROR && lines.get(0).text().contains("could not be read"), "invalid file explained");

        // Many items are abbreviated
        Map<String, byte[]> many = goodMod("many_items");
        StringBuilder json = new StringBuilder("{\"items\":[");
        for (int i = 0; i < 15; i++) {
            if (i > 0) json.append(',');
            json.append("{\"id\":\"item").append(i).append("\",\"name\":\"Thing ").append(i).append("\",\"texture\":\"minecraft:item/stick\"}");
        }
        many.put("items.json", bytes(json.append("]}").toString()));
        check(store.install(zip(many)).ok(), "installs a mod with many items");
        lines = ModView.details(only(store, "many_items"), store.plan(), new HashMap<>());
        check(lines.stream().anyMatch(l -> l.text().startsWith("15 items:") && l.text().contains("and 3 more")), "long item lists are cut short: " + lines);
        check(lines.stream().anyMatch(l -> l.text().contains("Thing 11,") && !l.text().contains("Thing 12")), "exactly the first twelve names are listed: " + lines);

        // Summary
        List<ModStore.Installed> all = store.installed();
        String summary = ModView.summary(all, store.plan(), loaded);
        check(summary.contains(all.size() + " mods imported") && summary.contains("1 loaded now"), "summary: " + summary);
        Map<String, Long> staleCopy = new HashMap<>();
        staleCopy.put("ruby_tools", mod.pkg().crc + 1);
        check(ModView.summary(all, store.plan(), staleCopy).contains("1 loaded now"), "an old copy that is still loaded counts as loaded");
        check(ModView.summary(all, store.plan(), new HashMap<>()).contains("0 loaded now"), "nothing loaded yet");
    }
}
