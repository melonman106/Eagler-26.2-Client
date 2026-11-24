package com.melonman106.vbvclient.mods;

import static com.melonman106.vbvclient.mods.TestKit.any;
import static com.melonman106.vbvclient.mods.TestKit.bytes;
import static com.melonman106.vbvclient.mods.TestKit.check;
import static com.melonman106.vbvclient.mods.TestKit.goodMod;
import static com.melonman106.vbvclient.mods.TestKit.png;
import static com.melonman106.vbvclient.mods.TestKit.read;
import static com.melonman106.vbvclient.mods.TestKit.zip;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PackageAndStoreTests {
    private PackageAndStoreTests() {}

    static void run() {
        packages();
        hostileZips();
        fabricZip();
        assets();
        store();
        planning();
        guard();
        snapshot();
    }

    static void packages() {
        ModPackage good = read(goodMod("ruby_tools"));
        check(good.ok(), "good mod is accepted: " + good.problems);
        check(good.manifest.id.equals("ruby_tools") && good.items.size() == 1, "id and items read");
        check(good.kinds.equals(List.of("item")), "kinds: " + good.kinds);
        check(good.icon != null, "icon read from pack.png");
        check(good.warnings.isEmpty(), "no warnings for a tidy mod: " + good.warnings);
        check(good.assets().containsKey("ruby_tools/textures/item/ruby_sword.png"), "assets are keyed without the assets/ prefix");

        // zipped folder: everything inside one top-level directory
        Map<String, byte[]> wrapped = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> e : goodMod("ruby_tools").entrySet()) wrapped.put("Ruby Tools/" + e.getKey(), e.getValue());
        ModPackage unwrapped = read(wrapped);
        check(unwrapped.ok() && unwrapped.items.size() == 1, "a zip with one wrapper folder is accepted: " + unwrapped.problems);

        Map<String, byte[]> noWrapperMatch = new LinkedHashMap<>();
        noWrapperMatch.put("a/info.json", goodMod("ruby_tools").get("info.json"));
        noWrapperMatch.put("b/items.json", goodMod("ruby_tools").get("items.json"));
        check(!read(noWrapperMatch).ok(), "two different top folders are not unwrapped");

        Map<String, byte[]> noPng = goodMod("ruby_tools");
        noPng.remove("pack.png");
        ModPackage withoutIcon = read(noPng);
        check(withoutIcon.ok() && withoutIcon.icon == null && any(withoutIcon.warnings, "icon"), "missing icon is only a warning");
        Map<String, byte[]> badIcon = goodMod("ruby_tools");
        badIcon.put("pack.png", bytes("not an image"));
        check(read(badIcon).ok() && any(read(badIcon).warnings, "not a PNG"), "non-PNG icon is only a warning");
        Map<String, byte[]> fatIcon = goodMod("ruby_tools");
        byte[] big = new byte[ModPackage.MAX_ICON_BYTES + 10];
        System.arraycopy(png(8, 8), 0, big, 0, 33);
        fatIcon.put("pack.png", big);
        check(read(fatIcon).ok() && read(fatIcon).icon == null, "oversized icon dropped");

        // texture problems
        Map<String, byte[]> missingTexture = goodMod("ruby_tools");
        missingTexture.remove("assets/ruby_tools/textures/item/ruby_sword.png");
        check(any(read(missingTexture).problems, "is missing from the zip"), "missing texture is a problem");
        Map<String, byte[]> fakeTexture = goodMod("ruby_tools");
        fakeTexture.put("assets/ruby_tools/textures/item/ruby_sword.png", bytes("GIF89a....................................."));
        check(any(read(fakeTexture).problems, "not a PNG"), "non-PNG texture is a problem");
        Map<String, byte[]> hugeTexture = goodMod("ruby_tools");
        hugeTexture.put("assets/ruby_tools/textures/item/ruby_sword.png", png(5000, 16));
        check(any(read(hugeTexture).problems, "pixels"), "huge texture is a problem");
        Map<String, byte[]> zeroTexture = goodMod("ruby_tools");
        zeroTexture.put("assets/ruby_tools/textures/item/ruby_sword.png", png(0, 0));
        check(any(read(zeroTexture).problems, "pixels"), "zero-size texture is a problem");
        Map<String, byte[]> outside = goodMod("ruby_tools");
        outside.put("items.json", bytes("{\"items\":[{\"id\":\"ruby_sword\",\"texture\":\"ruby_tools:gui/thing\"}]}"));
        check(any(read(outside).problems, "textures/item/ or textures/block/"), "texture outside item/ and block/ is a problem");
        Map<String, byte[]> vanilla = goodMod("ruby_tools");
        vanilla.put("items.json", bytes("{\"items\":[{\"id\":\"ruby_sword\",\"texture\":\"minecraft:item/diamond_sword\"}]}"));
        vanilla.remove("assets/ruby_tools/textures/item/ruby_sword.png");
        check(read(vanilla).ok(), "reusing a game texture needs no file");
        Map<String, byte[]> foreign = goodMod("ruby_tools");
        foreign.put("items.json", bytes("{\"items\":[{\"id\":\"ruby_sword\",\"texture\":\"othermod:item/x\"}]}"));
        check(any(read(foreign).problems, "othermod"), "a texture from another mod's namespace must be in the zip");
        Map<String, byte[]> block = goodMod("ruby_tools");
        block.put("items.json", bytes("{\"items\":[{\"id\":\"ruby_sword\",\"texture\":\"ruby_tools:block/ruby_ore\"}]}"));
        block.put("assets/ruby_tools/textures/block/ruby_ore.png", png(16, 16));
        check(read(block).ok(), "block textures are allowed");

        // content kinds
        Map<String, byte[]> future = goodMod("ruby_tools");
        future.put("blocks.json", bytes("{}"));
        future.put("hud.json", bytes("{}"));
        ModPackage withFuture = read(future);
        check(withFuture.ok() && withFuture.ignoredLists.equals(List.of("blocks.json", "hud.json")) && any(withFuture.warnings, "blocks.json was found"), "later content lists are reported and ignored");
        Map<String, byte[]> declaredNoFile = goodMod("ruby_tools");
        declaredNoFile.remove("items.json");
        check(any(read(declaredNoFile).problems, "no items.json"), "declaring items without items.json is a problem");
        Map<String, byte[]> declaredBlock = goodMod("ruby_tools");
        declaredBlock.put("info.json", bytes("{\"id\":\"ruby_tools\",\"name\":\"x\",\"version\":\"1\",\"provides\":[\"item\",\"entity\"]}"));
        check(read(declaredBlock).ok() && any(read(declaredBlock).warnings, "entities.json was not found"), "declared entity without its file warns");
        Map<String, byte[]> undeclared = goodMod("ruby_tools");
        undeclared.put("info.json", bytes("{\"id\":\"ruby_tools\",\"name\":\"x\",\"version\":\"1\",\"provides\":[\"block\"]}"));
        check(read(undeclared).ok() && any(read(undeclared).warnings, "does not list \"item\""), "items not declared warns");

        // assets only
        Map<String, byte[]> assetsOnly = new LinkedHashMap<>();
        assetsOnly.put("info.json", bytes("{\"id\":\"pretty_pack\",\"name\":\"Pretty\",\"version\":\"1\"}"));
        assetsOnly.put("assets/minecraft/textures/item/stick.png", png(16, 16));
        ModPackage only = read(assetsOnly);
        check(only.ok() && only.kinds.isEmpty() && only.items.isEmpty(), "an assets-only mod is fine");

        // bad lang dropped
        Map<String, byte[]> lang = goodMod("ruby_tools");
        lang.put("assets/ruby_tools/lang/en_us.json", bytes("{ nope"));
        ModPackage badLang = read(lang);
        check(badLang.ok() && !badLang.files.containsKey("assets/ruby_tools/lang/en_us.json") && any(badLang.warnings, "language file"), "broken language files are dropped with a warning");
    }

    static void hostileZips() {
        check(any(ModPackage.read(null).problems, "empty"), "null bytes");
        check(any(ModPackage.read(new byte[0]).problems, "empty"), "empty file");
        check(any(ModPackage.read(bytes("this is not a zip at all")).problems, "no files in it") || any(ModPackage.read(bytes("this is not a zip at all")).problems, "not a readable"), "garbage is not a zip");
        check(any(ModPackage.read(zip(new LinkedHashMap<>())).problems, "no files"), "a zip with no entries");

        Map<String, byte[]> slip = goodMod("ruby_tools");
        slip.put("../evil.json", bytes("{}"));
        check(any(read(slip).problems, "unsafe path"), "zip-slip path refused");
        Map<String, byte[]> slip2 = goodMod("ruby_tools");
        slip2.put("assets/ruby_tools/../../x.png", png(1, 1));
        check(any(read(slip2).problems, "unsafe path"), "embedded .. refused");
        Map<String, byte[]> abs = goodMod("ruby_tools");
        abs.put("/etc/passwd", bytes("x"));
        check(any(read(abs).problems, "unsafe path"), "absolute path refused");
        Map<String, byte[]> win = goodMod("ruby_tools");
        win.put("C:\\Windows\\x.json", bytes("x"));
        check(any(read(win).problems, "unsafe path"), "drive path refused");
        Map<String, byte[]> backslash = goodMod("ruby_tools");
        backslash.remove("assets/ruby_tools/textures/item/ruby_sword.png");
        backslash.put("assets\\ruby_tools\\textures\\item\\ruby_sword.png", png(16, 16));
        check(read(backslash).ok(), "backslash separators from Windows tools are accepted");

        Map<String, byte[]> bomb = goodMod("ruby_tools");
        bomb.put("assets/ruby_tools/big.bin", new byte[ModPackage.MAX_ENTRY_BYTES + 1]);
        byte[] bombZip = zip(bomb);
        check(bombZip.length < ModPackage.MAX_ZIP_BYTES, "the bomb is small when zipped");
        check(any(ModPackage.read(bombZip).problems, "too large when unpacked"), "oversized entry refused");
        Map<String, byte[]> total = goodMod("ruby_tools");
        for (int i = 0; i < 7; i++) total.put("assets/ruby_tools/big" + i + ".bin", new byte[ModPackage.MAX_ENTRY_BYTES]);
        check(any(ModPackage.read(zip(total)).problems, "too large when unpacked"), "oversized total refused");

        Map<String, byte[]> many = goodMod("ruby_tools");
        for (int i = 0; i < ModPackage.MAX_ENTRIES; i++) many.put("assets/ruby_tools/f" + i + ".txt", new byte[1]);
        check(any(ModPackage.read(zip(many)).problems, "more than"), "too many files refused");

        Map<String, byte[]> junk = goodMod("ruby_tools");
        junk.put("__MACOSX/._info.json", bytes("garbage"));
        junk.put("assets/ruby_tools/.DS_Store", bytes("garbage"));
        ModPackage junky = read(junk);
        check(junky.ok() && !junky.files.containsKey("assets/ruby_tools/.DS_Store"), "mac junk is ignored");

        Map<String, byte[]> classes = goodMod("ruby_tools");
        classes.put("com/example/Main.class", new byte[] {1, 2, 3});
        classes.put("lib/dep.jar", new byte[] {1, 2, 3});
        ModPackage withClasses = read(classes);
        check(withClasses.ok() && any(withClasses.warnings, "2 Java file(s)"), "class and jar files are ignored with a warning");
        check(withClasses.files.keySet().stream().noneMatch(k -> k.endsWith(".class") || k.endsWith(".jar")), "and never kept");

        Map<String, byte[]> badNames = goodMod("ruby_tools");
        badNames.put("assets/ruby_tools/textures/item/Upper.PNG", png(16, 16));
        ModPackage names = read(badNames);
        check(names.ok() && any(names.warnings, "not valid") && !names.files.containsKey("assets/ruby_tools/textures/item/Upper.PNG"), "invalid resource names are skipped, not fatal");

        Map<String, byte[]> stray = goodMod("ruby_tools");
        stray.put("README.txt", bytes("hi"));
        stray.put("data/ruby_tools/recipes/x.json", bytes("{}"));
        check(!read(stray).files.containsKey("README.txt") && !read(stray).files.containsKey("data/ruby_tools/recipes/x.json"), "files outside the known set are not kept");

        Map<String, byte[]> noManifest = new LinkedHashMap<>();
        noManifest.put("items.json", bytes("[]"));
        check(any(read(noManifest).problems, "no info.json"), "no manifest");
        Map<String, byte[]> bom = goodMod("ruby_tools");
        bom.put("info.json", ("\uFEFF" + new String(bom.get("info.json"), StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));
        check(read(bom).ok(), "a UTF-8 BOM in info.json is tolerated");
        Map<String, byte[]> both = goodMod("ruby_tools");
        both.put("fabric.mod.json", bytes("{\"id\":\"other_id\",\"version\":\"1\"}"));
        ModPackage preferred = read(both);
        check(preferred.ok() && preferred.manifest.id.equals("ruby_tools") && any(preferred.warnings, "info.json is used"), "info.json wins over fabric.mod.json");
    }

    static void fabricZip() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("fabric.mod.json", bytes("{\"schemaVersion\":1,\"id\":\"cool-mod\",\"version\":\"1.0.0\",\"name\":\"Cool\",\"icon\":\"assets/cool-mod/icon.png\","
            + "\"entrypoints\":{\"main\":[\"x.Y\"]},\"depends\":{\"fabricloader\":\">=0.15\",\"fabric-api\":\"*\",\"minecraft\":\"~1.21\"}}"));
        files.put("assets/cool-mod/icon.png", png(128, 128));
        files.put("items.json", bytes("[{\"id\":\"gem\",\"texture\":\"cool-mod:item/gem\"}]"));
        files.put("assets/cool-mod/textures/item/gem.png", png(16, 16));
        files.put("cool/Main.class", new byte[] {9});
        ModPackage pkg = read(files);
        check(pkg.ok(), "a fabric.mod.json mod with our items.json is accepted: " + pkg.problems);
        check(pkg.manifest.format == ModManifest.Format.FABRIC && pkg.icon != null, "fabric format and icon inside assets/");
        check(any(pkg.warnings, "Java code") && any(pkg.warnings, "Java file(s)"), "both Java warnings appear");
    }

    static void assets() {
        ModPackage pkg = read(goodMod("ruby_tools"));
        Map<String, byte[]> built = ModAssets.build(pkg);
        check(built.containsKey("ruby_tools/items/ruby_sword.json"), "item definition is synthesized");
        check(built.containsKey("ruby_tools/models/item/ruby_sword.json"), "item model is synthesized");
        JsonObject definition = JsonParser.parseString(new String(built.get("ruby_tools/items/ruby_sword.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        check(definition.getAsJsonObject("model").get("type").getAsString().equals("minecraft:model")
            && definition.getAsJsonObject("model").get("model").getAsString().equals("ruby_tools:item/ruby_sword"), "definition points at the item model");
        JsonObject model = JsonParser.parseString(new String(built.get("ruby_tools/models/item/ruby_sword.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        check(model.get("parent").getAsString().equals("minecraft:item/handheld")
            && model.getAsJsonObject("textures").get("layer0").getAsString().equals("ruby_tools:item/ruby_sword"), "model uses the handheld parent and the texture");
        JsonObject lang = JsonParser.parseString(new String(built.get("ruby_tools/lang/en_us.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        check(lang.get("item.ruby_tools.ruby_sword").getAsString().equals("Ruby Sword"), "display name in the language file");
        check(built.containsKey("ruby_tools/textures/vbv/icon.png"), "icon is served for the Mods screen");
        check(ModAssets.iconLocation("ruby_tools").equals("ruby_tools:textures/vbv/icon.png"), "icon location");
        check(built.containsKey("ruby_tools/textures/item/ruby_sword.png"), "the mod's own assets are included");

        Map<String, byte[]> custom = goodMod("ruby_tools");
        custom.put("assets/ruby_tools/models/item/ruby_sword.json", bytes("{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"ruby_tools:item/custom\"}}"));
        custom.put("assets/ruby_tools/lang/en_us.json", bytes("{\"item.ruby_tools.ruby_sword\":\"Super Sword\",\"other.key\":\"Kept\"}"));
        Map<String, byte[]> customBuilt = ModAssets.build(read(custom));
        check(new String(customBuilt.get("ruby_tools/models/item/ruby_sword.json"), StandardCharsets.UTF_8).contains("custom"), "the author's own model is never overwritten");
        JsonObject customLang = JsonParser.parseString(new String(customBuilt.get("ruby_tools/lang/en_us.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        check(customLang.get("item.ruby_tools.ruby_sword").getAsString().equals("Super Sword") && customLang.get("other.key").getAsString().equals("Kept"), "the author's own names are kept");

        Map<String, byte[]> twoItems = goodMod("ruby_tools");
        twoItems.put("items.json", bytes("[{\"id\":\"a\",\"texture\":\"minecraft:item/stick\"},{\"id\":\"b\",\"model\":\"handheld\",\"texture\":\"minecraft:item/stick\"}]"));
        Map<String, byte[]> twoBuilt = ModAssets.build(read(twoItems));
        check(new String(twoBuilt.get("ruby_tools/models/item/a.json"), StandardCharsets.UTF_8).contains("item/generated"), "generated parent by default");
        check(new String(twoBuilt.get("ruby_tools/models/item/b.json"), StandardCharsets.UTF_8).contains("item/handheld"), "handheld when asked");

        // pack.mcmeta: the game refuses supported_formats for formats above 64 and wants min/max, so write exactly that
        JsonObject meta = JsonParser.parseString(new String(ModAssets.packMeta(pkg, 88), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("pack");
        check(meta.get("description").getAsString().contains(pkg.manifest.name)
            && meta.get("description").getAsString().contains(pkg.manifest.version)
            && meta.get("description").getAsString().contains("imported mod"), "pack description names the mod and its version");
        check(meta.get("min_format").getAsInt() == ModAssets.FIRST_RANGE_FORMAT && meta.get("max_format").getAsInt() == 88, "pack format range runs from the first ranged format to the game's");
        check(ModAssets.FIRST_RANGE_FORMAT == 65, "formats above 64 are the first that use min_format/max_format");
        check(!meta.has("supported_formats") && !meta.has("pack_format"), "no legacy format fields (the game rejects them above format 64)");
        JsonObject oldGame = JsonParser.parseString(new String(ModAssets.packMeta(pkg, 40), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("pack");
        check(oldGame.get("max_format").getAsInt() == ModAssets.FIRST_RANGE_FORMAT, "the range never ends before it starts");

        ModPackage onlyAssets = read(Map.of("info.json", bytes("{\"id\":\"pretty_pack\",\"name\":\"P\",\"version\":\"1\"}")));
        check(ModAssets.build(onlyAssets).isEmpty(), "an empty mod builds an empty pack (no language file invented)");
    }

    private static TestKit.MemoryFiles memory() { return new TestKit.MemoryFiles(); }

    private static ModStore store(TestKit.MemoryFiles files) {
        return new ModStore(files, id -> id.equals("vbvclient") ? "2.0.0" : id.equals("herzmod") ? "1.0.0" : null);
    }

    static void store() {
        TestKit.MemoryFiles files = memory();
        ModStore store = store(files);
        ModStore.InstallResult first = store.install(zip(goodMod("ruby_tools")));
        check(first.ok() && first.id().equals("ruby_tools") && first.replacedVersion() == null, "first install: " + first.problems());
        check(files.map.containsKey("mods/ruby_tools.zip"), "stored as mods/<id>.zip");
        check(store.installed().size() == 1 && store.installed().get(0).enabled(), "listed and enabled by default");

        Map<String, byte[]> v2 = goodMod("ruby_tools");
        v2.put("info.json", bytes("{\"id\":\"ruby_tools\",\"name\":\"Ruby Tools\",\"version\":\"2.0.0\"}"));
        v2.put("items.json", bytes("[{\"id\":\"ruby_sword\"}]"));
        v2.put("assets/ruby_tools/textures/item/ruby_sword.png", png(16, 16));
        ModStore.InstallResult second = store.install(zip(v2));
        check(second.ok() && "1.0.0".equals(second.replacedVersion()), "reinstall reports the replaced version: " + second.problems());
        check(store.installed().size() == 1 && store.installed().get(0).pkg().manifest.version.equals("2.0.0"), "reinstall replaces");

        int before = files.map.size();
        ModStore.InstallResult bad = store.install(bytes("not a zip"));
        check(!bad.ok() && !bad.problems().isEmpty() && files.map.size() == before, "a bad zip writes nothing");
        Map<String, byte[]> clash = goodMod("vbvclient");
        clash.remove("assets/vbvclient/textures/item/ruby_sword.png");
        check(!store.install(zip(clash)).ok(), "reserved id is refused at import");
        Map<String, byte[]> nativeClash = goodMod("herzmod");
        ModStore.InstallResult nativeResult = store.install(zip(nativeClash));
        check(!nativeResult.ok() && any(nativeResult.problems(), "built-in mod"), "a built-in mod's id is refused: " + nativeResult.problems());
        files.failWrites = true;
        ModStore.InstallResult full = store.install(zip(goodMod("other_mod")));
        check(!full.ok() && any(full.problems(), "could not be saved"), "write failure reported");
        files.failWrites = false;

        store.install(zip(goodMod("alpha_mod")));
        check(store.setEnabled("alpha_mod", false), "turn off");
        check(store.installed().stream().filter(m -> m.id().equals("alpha_mod")).findFirst().get().enabled() == false, "turned off is remembered");
        check(store.installed().get(0).id().equals("alpha_mod") && store.installed().get(1).id().equals("ruby_tools"), "listed in id order");
        store.setEnabled("alpha_mod", true);
        check(store.installed().stream().allMatch(ModStore.Installed::enabled), "turned back on");
        store.setEnabled("alpha_mod", false);
        check(store.remove("alpha_mod") && !files.map.containsKey("mods/alpha_mod.zip"), "removed");
        store.install(zip(goodMod("alpha_mod")));
        check(store.installed().stream().filter(m -> m.id().equals("alpha_mod")).findFirst().get().enabled(), "a removed mod does not stay turned off when imported again");
        check(!store.remove("../state"), "remove refuses path-like ids");

        files.map.put("mods/broken.zip", bytes("nope"));
        files.map.put("mods/mismatch.zip", zip(goodMod("something_else")));
        files.map.put("mods/Bad Name.zip", zip(goodMod("whatever")));
        files.map.put("mods/readme.txt", bytes("ignored"));
        List<ModStore.Installed> list = store.installed();
        ModStore.Installed broken = list.stream().filter(m -> m.id().equals("broken")).findFirst().get();
        check(broken.pkg() == null && !broken.problems().isEmpty(), "a damaged stored zip is listed with its problems");
        ModStore.Installed mismatch = list.stream().filter(m -> m.id().equals("mismatch")).findFirst().get();
        check(mismatch.pkg() == null && any(mismatch.problems(), "named mismatch.zip"), "a file whose id disagrees with its name is flagged");
        check(list.stream().noneMatch(m -> m.id().contains(" ") || m.id().equals("readme")), "odd file names are skipped");
    }

    private static ModPackage mod(String id, String requiresJson) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("info.json", bytes("{\"id\":\"" + id + "\",\"name\":\"" + id + "\",\"version\":\"1.0.0\"" + (requiresJson == null ? "" : ",\"requires\":" + requiresJson) + "}"));
        return read(files);
    }

    private static void put(ModStore store, String id, String requiresJson) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("info.json", bytes("{\"id\":\"" + id + "\",\"name\":\"" + id + "\",\"version\":\"1.0.0\"" + (requiresJson == null ? "" : ",\"requires\":" + requiresJson) + "}"));
        ModStore.InstallResult result = store.install(zip(files));
        check(result.ok(), "installed " + id + ": " + result.problems());
    }

    static void planning() {
        check(mod("x_mod", null).ok(), "mod helper works");

        TestKit.MemoryFiles files = memory();
        ModStore store = store(files);
        put(store, "zeta", null);
        put(store, "alpha", null);
        put(store, "middle", "[\"alpha >=1.0\"]");
        ModStore.Plan plan = store.plan();
        List<String> order = plan.active().stream().map(p -> p.manifest.id).toList();
        check(order.equals(List.of("alpha", "middle", "zeta")), "active mods are in id order: " + order);
        check(!plan.safeMode() && plan.blocked().isEmpty(), "nothing blocked");

        store.setEnabled("alpha", false);
        plan = store.plan();
        check(!plan.isActive("alpha") && !plan.isActive("middle") && plan.isActive("zeta"), "turning off a mod blocks the one that needs it");
        check(any(plan.blocked().get("middle"), "needs alpha"), "and says why: " + plan.blocked().get("middle"));
        check(plan.blocked().get("alpha").equals(List.of("Turned off.")), "turned off reason");
        store.setEnabled("alpha", true);

        put(store, "needs_ghost", "[\"ghost\"]");
        put(store, "needs_needs_ghost", "[\"needs_ghost\"]");
        plan = store.plan();
        check(!plan.isActive("needs_ghost") && !plan.isActive("needs_needs_ghost"), "a missing requirement blocks, and the block cascades");
        check(any(plan.blocked().get("needs_ghost"), "not installed"), "missing reason");

        // The dependent sorts BEFORE the mod it needs, so one pass over the list is not enough to block it.
        put(store, "aaa_early", "[\"zzz_late\"]");
        put(store, "zzz_late", "[\"ghost\"]");
        plan = store.plan();
        check(!plan.isActive("zzz_late") && !plan.isActive("aaa_early"), "a block cascades back to mods that sort earlier");
        check(any(plan.blocked().get("aaa_early"), "needs zzz_late"), "and names the mod that is missing: " + plan.blocked().get("aaa_early"));

        put(store, "cycle_a", "[\"cycle_b\"]");
        put(store, "cycle_b", "[\"cycle_a\"]");
        plan = store.plan();
        check(plan.isActive("cycle_a") && plan.isActive("cycle_b"), "mods that need each other still load");

        put(store, "self_ref", "[\"self_ref\"]");
        check(!store.plan().isActive("self_ref"), "a mod cannot satisfy itself");

        put(store, "wants_old", "[\"alpha >=2.0\"]");
        check(any(store.plan().blocked().get("wants_old"), "found 1.0.0"), "a too-old requirement says what was found");

        put(store, "needs_native", "{\"vbvclient\":\">=2.0.0\"}");
        put(store, "needs_future_native", "{\"vbvclient\":\">=3.0.0\"}");
        plan = store.plan();
        check(plan.isActive("needs_native") && !plan.isActive("needs_future_native"), "built-in mods satisfy requirements by version");

        put(store, "fabric_user", "{\"fabricloader\":\">=0.15.0\",\"fabric-api\":\"*\",\"minecraft\":\"26.2\",\"fabric-resource-loader-v0\":\"*\",\"fabric-networking-api-v1\":\"*\"}");
        plan = store.plan();
        check(plan.isActive("fabric_user"), "a mod that needs the Fabric Loader and API loads");
        FabricFaker.Resolution resolution = plan.resolutions().get("fabric_user");
        check(resolution.faked().contains("fabricloader") && resolution.faked().contains("fabric-api") && resolution.faked().contains("fabric-networking-api-v1"), "the faked pieces are listed: " + resolution.faked());
        check(!resolution.faked().contains("fabric-resource-loader-v0"), "the really-backed module is not listed as faked");

        put(store, "breaks_alpha", "[]");
        Map<String, byte[]> breaker = new LinkedHashMap<>();
        breaker.put("fabric.mod.json", bytes("{\"id\":\"breaker\",\"version\":\"1.0.0\",\"breaks\":{\"alpha\":\"*\"}}"));
        check(store.install(zip(breaker)).ok(), "fabric.mod.json mod installs");
        check(!store.plan().isActive("breaker") && any(store.plan().blocked().get("breaker"), "cannot be used together"), "breaks blocks the breaking mod");

        // safe mode
        store.setSafeMode(true);
        ModStore.Plan safe = store.plan();
        check(safe.safeMode() && safe.active().isEmpty() && !safe.safeModeReason().isEmpty(), "safe mode loads nothing");
        check(store.safeMode(), "safe mode flag");
        store.setSafeMode(false);
        check(!store.plan().safeMode() && !store.plan().active().isEmpty(), "safe mode off again");
        store.setSafeMode(true, "Because the address said so.");
        check(store.plan().safeModeReason().equals("Because the address said so."), "safe mode can carry its own reason");
        store.setSafeMode(true, "A later reason");
        check(store.plan().safeModeReason().equals("Because the address said so."), "...and an existing reason is not overwritten");
        store.setSafeMode(false);
        check(!store.plan().safeMode(), "safe mode off after the reasoned one");

        // damaged state file falls back to defaults
        files.map.put("mods/state.json", bytes("{{{{"));
        check(!store.safeMode() && store.plan().isActive("zeta"), "damaged state means defaults");
        files.map.put("mods/state.json", bytes("[1,2]"));
        check(!store.safeMode(), "state of the wrong shape means defaults");
        files.map.put("mods/state.json", bytes("{\"safe_mode\":\"yes\",\"disabled\":\"zeta\"}"));
        check(!store.safeMode() && store.plan().isActive("zeta"), "wrong types in state mean defaults");
    }

    static void guard() {
        TestKit.MemoryFiles files = memory();
        ModStore store = store(files);
        put(store, "alpha", null);

        check(!store.beginRegistration(), "first start: guard does not trip");
        check(new String(files.map.get("mods/state.json"), StandardCharsets.UTF_8).contains("registering"), "guard marks registration as running");
        store.endRegistration();
        check(!new String(files.map.get("mods/state.json"), StandardCharsets.UTF_8).contains("registering"), "and clears it when finished");
        check(!store.beginRegistration() && !store.safeMode(), "a clean start keeps mods on");
        store.endRegistration();

        // crash: beginRegistration without endRegistration
        store.beginRegistration();
        ModStore second = store(files);
        check(second.beginRegistration(), "a start after an unfinished one trips the guard");
        check(second.safeMode() && second.plan().safeMode() && second.plan().safeModeReason().contains("stopped while loading mods"), "guard turns safe mode on with a reason");
        check(second.plan().active().isEmpty(), "no mods load in safe mode");
        check(!second.beginRegistration() || second.safeMode(), "safe mode stays on at the next start");
        check(store(files).safeMode(), "the integrated server realm sees the same safe mode");
        second.endRegistration();
        second.setSafeMode(false);
        check(!second.safeMode() && !second.beginRegistration(), "turning safe mode off re-arms the guard cleanly");
        check(second.plan().isActive("alpha"), "mods load again");
    }

    static void snapshot() {
        TestKit.MemoryFiles fresh = memory();
        ModStore first = store(fresh);
        check(first.nothingStored(), "a fresh store has nothing stored");
        check(first.readApplied().mods().isEmpty() && first.plan().active().isEmpty() && fresh.map.isEmpty(), "...and looking at it wrote nothing");
        first.setSafeMode(true);
        check(!first.nothingStored(), "any state counts as stored");
        TestKit.MemoryFiles imported = memory();
        store(imported).install(zip(goodMod("ruby_tools")));
        check(!store(imported).nothingStored(), "an imported mod counts as stored");
        store(imported).remove("ruby_tools");
        store(imported).apply(store(imported).plan());
        check(!store(imported).nothingStored(), "after the last mod is removed the snapshot still counts, so the next start rewrites it");

        TestKit.MemoryFiles sessionFiles = memory();
        ModStore sessionStore = store(sessionFiles);
        sessionStore.install(zip(goodMod("ruby_tools")));
        int before = sessionFiles.map.size();
        ModStore.Plan session = sessionStore.plan("Not this time (?safe).");
        check(session.safeMode() && session.active().isEmpty() && session.safeModeReason().equals("Not this time (?safe)."), "a session override loads nothing and says why");
        check(sessionFiles.map.size() == before && !sessionStore.safeMode(), "...and stores nothing: safe mode is not switched on for later");
        check(sessionStore.plan().active().size() == 1 && !sessionStore.plan().safeMode(), "the next normal start loads the mod again");
        check(sessionStore.plan(null).active().size() == 1, "a null reason is a normal plan");
        ModStore.Applied sessionApplied = sessionStore.apply(session);
        check(sessionApplied.mods().isEmpty() && sessionApplied.safeMode() && store(sessionFiles).readApplied().safeMode() && store(sessionFiles).readApplied().mods().isEmpty(), "the server worker follows a session override through the snapshot");

        TestKit.MemoryFiles files = memory();
        ModStore client = store(files);
        ModStore worker = store(files); // the integrated-server worker shares the same storage
        client.install(zip(goodMod("ruby_tools")));
        put(client, "alpha", null);

        check(worker.readApplied().mods().isEmpty() && worker.readApplied().problems().isEmpty(), "before the client has started, the worker loads nothing and reports nothing");
        check(client.restartNeeded(client.plan()), "mods exist but nothing is applied yet: restart needed");

        ModStore.Applied applied = client.apply(client.plan());
        check(applied.problems().isEmpty() && applied.mods().size() == 2 && !applied.safeMode(), "apply: " + applied.problems());
        check(applied.mods().get(0).manifest.id.equals("alpha") && applied.mods().get(1).manifest.id.equals("ruby_tools"), "applied in id order");
        check(!client.restartNeeded(client.plan()), "after apply nothing is pending");
        check(files.map.keySet().stream().filter(k -> k.startsWith("mods/applied/")).count() == 2, "one immutable copy per mod");

        ModStore.Applied seen = worker.readApplied();
        check(seen.problems().isEmpty() && seen.mods().size() == 2, "worker reads the same set: " + seen.problems());
        check(seen.mods().get(0).manifest.id.equals("alpha") && seen.mods().get(1).manifest.id.equals("ruby_tools"), "in the same order");
        check(seen.mods().get(1).items.size() == 1 && seen.mods().get(1).crc == applied.mods().get(1).crc, "with identical content");

        // The player imports a new version and turns another mod off while the game is running.
        Map<String, byte[]> v2 = goodMod("ruby_tools");
        v2.put("items.json", bytes("[{\"id\":\"ruby_sword\"},{\"id\":\"ruby_pickaxe\",\"texture\":\"minecraft:item/stick\"}]"));
        check(client.install(zip(v2)).ok(), "new version imported");
        client.setEnabled("alpha", false);
        check(client.restartNeeded(client.plan()), "restart is now needed");
        ModStore.Applied still = worker.readApplied();
        check(still.problems().isEmpty() && still.mods().size() == 2 && still.mods().get(1).items.size() == 1, "the worker still sees what the client registered, not the new files");
        check(still.mods().get(0).manifest.id.equals("alpha"), "including the mod that is now turned off");

        // Next start: the client applies the new plan, old copies are removed.
        ModStore.Applied next = client.apply(client.plan());
        check(next.mods().size() == 1 && next.mods().get(0).items.size() == 2, "next start applies the new set");
        check(files.map.keySet().stream().filter(k -> k.startsWith("mods/applied/")).count() == 1, "old copies are removed");
        check(!client.restartNeeded(client.plan()), "and nothing is pending again");
        check(worker.readApplied().mods().size() == 1, "worker follows");

        // Damage.
        String copy = files.map.keySet().stream().filter(k -> k.startsWith("mods/applied/")).findFirst().get();
        byte[] original = files.map.get(copy);
        files.map.put(copy, bytes("tampered"));
        ModStore.Applied damaged = worker.readApplied();
        check(damaged.mods().isEmpty() && any(damaged.problems(), "missing or damaged"), "a damaged copy is reported, not loaded: " + damaged.problems());
        files.map.remove(copy);
        check(any(worker.readApplied().problems(), "missing or damaged"), "a missing copy is reported");
        files.map.put(copy, original);
        check(worker.readApplied().problems().isEmpty(), "restored copy works again");
        files.map.put(copy, bytes("tampered"));
        ModStore.Applied repaired = client.apply(client.plan());
        check(repaired.problems().isEmpty() && worker.readApplied().problems().isEmpty() && worker.readApplied().mods().size() == 1, "the next start repairs a damaged copy: " + worker.readApplied().problems());
        files.map.put("mods/applied.json", bytes("{{{"));
        check(any(worker.readApplied().problems(), "damaged") && worker.readApplied().mods().isEmpty(), "damaged list reported");
        check(client.restartNeeded(client.plan()), "a damaged list means a restart is needed");
        files.map.put("mods/applied.json", bytes("{\"mods\":[{\"id\":\"../x\",\"crc\":\"zz\"},5]}"));
        check(any(worker.readApplied().problems(), "damaged") && worker.readApplied().mods().isEmpty(), "bad entries reported and skipped");

        // Safe mode applies nothing and the worker agrees.
        client.setSafeMode(true);
        ModStore.Applied safe = client.apply(client.plan());
        check(safe.mods().isEmpty() && safe.safeMode(), "safe mode applies nothing");
        check(worker.readApplied().mods().isEmpty() && worker.readApplied().safeMode(), "the worker sees safe mode");
        check(files.map.keySet().stream().noneMatch(k -> k.startsWith("mods/applied/")), "safe mode removes the copies");

        // Storage that refuses writes.
        client.setSafeMode(false);
        files.failWrites = true;
        ModStore.Applied full = client.apply(client.plan());
        check(full.mods().isEmpty() && !full.problems().isEmpty(), "when nothing can be saved nothing is registered: " + full.problems());
        files.failWrites = false;
        check(!files.map.containsKey("mods/applied.json"), "and a stale list is not left behind");
        check(worker.readApplied().mods().isEmpty(), "so the worker also loads nothing");
    }
}
