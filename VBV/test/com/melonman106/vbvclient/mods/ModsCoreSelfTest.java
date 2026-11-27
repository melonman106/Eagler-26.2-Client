package com.melonman106.vbvclient.mods;

import static com.melonman106.vbvclient.mods.TestKit.check;

import java.util.List;
import java.util.Map;

/**
 * Plain-JVM checks for the parts of the importable-mods system that use no game classes.
 * Run with VBV/test/run-mods-core-tests.sh (compiles only the pure classes, so no game sources are needed).
 */
public final class ModsCoreSelfTest {
    private ModsCoreSelfTest() {}

    public static void main(String[] args) {
        versions();
        faker();
        ManifestAndItemTests.run();
        PackageAndStoreTests.run();
        ViewTests.run();
        ContentTests.run();
        EventTests.run();
        ExampleTests.run();
        System.out.println(TestKit.checks + " checks, " + TestKit.failures + " failed");
        if (TestKit.failures > 0) System.exit(1);
    }

    private static boolean in(String range, String version) {
        return ModVersion.Range.parse(range).matches(ModVersion.parse(version));
    }

    static void versions() {
        check(ModVersion.parse("1.2.3").compareTo(ModVersion.parse("1.10.0")) < 0, "1.2.3 < 1.10.0 (numeric, not text)");
        check(ModVersion.parse("1.0").compareTo(ModVersion.parse("1.0.0")) == 0, "1.0 == 1.0.0");
        check(ModVersion.parse("1.0-beta").compareTo(ModVersion.parse("1.0")) < 0, "prerelease older than release");
        check(ModVersion.parse("1.0").compareTo(ModVersion.parse("1.0-beta")) > 0, "release newer than prerelease");
        check(ModVersion.parse("1.0-alpha").compareTo(ModVersion.parse("1.0-beta")) < 0, "alpha before beta");
        check(ModVersion.parse("1.0-beta").compareTo(ModVersion.parse("1.0-beta")) == 0, "same prerelease equal");
        check(ModVersion.parse("1.0-beta").compareTo(ModVersion.parse("0.9.9")) > 0, "prerelease of 1.0 newer than 0.9.9");
        check(ModVersion.parse("0.121.0+26.2").compareTo(ModVersion.parse("0.121.0")) == 0, "build metadata ignored");
        check(!ModVersion.parse("latest").isNumeric(), "words are not numeric");
        check(!ModVersion.parse("").isNumeric(), "empty is not numeric");
        check(!ModVersion.parse("1..2").isNumeric(), "empty part is not numeric");
        check(!ModVersion.parse("99999999999").isNumeric(), "huge part is not numeric (no overflow)");

        check(in("*", "1.2.3"), "* matches anything");
        check(in("*", "latest"), "* matches a word");
        check(in("", "1.0"), "empty range matches anything");
        check(in(">=0.15.0", "0.16.14"), ">=0.15.0 vs 0.16.14");
        check(!in(">=0.15.0", "0.14.9"), ">=0.15.0 vs 0.14.9");
        check(in(">0.15.0", "0.15.1"), ">0.15.0 vs 0.15.1");
        check(!in(">0.15.0", "0.15.0"), ">0.15.0 vs 0.15.0");
        check(in("<=1.0", "1.0.0"), "<=1.0 vs 1.0.0");
        check(!in("<1.0", "1.0.0"), "<1.0 vs 1.0.0");
        check(in("=1.2.3", "1.2.3"), "=1.2.3");
        check(in("1.2.3", "1.2.3"), "bare version is exact");
        check(!in("1.2.3", "1.2.4"), "bare version is exact (no)");

        check(in("~1.21.4", "1.21.9"), "~1.21.4 vs 1.21.9");
        check(!in("~1.21.4", "1.22.0"), "~1.21.4 vs 1.22.0");
        check(!in("~1.21.4", "1.21.3"), "~1.21.4 vs 1.21.3");
        check(in("~1", "1.9.9"), "~1 stays in major 1");
        check(!in("~1", "2.0.0"), "~1 excludes 2.0.0");
        check(in("^1.2", "1.9.0"), "^1.2 vs 1.9.0");
        check(!in("^1.2", "2.0.0"), "^1.2 vs 2.0.0");
        check(in("^0.119", "0.119.5"), "^0.119 vs 0.119.5");
        check(!in("^0.119", "0.120.0"), "^0.119 stops at next minor");
        check(in("1.x", "1.77.0"), "1.x vs 1.77.0");
        check(!in("1.x", "2.0.0"), "1.x vs 2.0.0");
        check(in("26.2.x", "26.2.4"), "26.2.x vs 26.2.4");
        check(!in("26.2.x", "26.3.0"), "26.2.x vs 26.3.0");

        check(in(">=1.0 <2.0", "1.5"), "AND inside range");
        check(!in(">=1.0 <2.0", "2.0"), "AND excludes upper bound");
        check(in("<1.0 || >=3.0", "3.1"), "OR second branch");
        check(in("<1.0 || >=3.0", "0.5"), "OR first branch");
        check(!in("<1.0 || >=3.0", "2.0"), "OR neither branch");
        check(in(ModVersion.Range.parseAny(List.of(">=26", "1.x")).toString(), "1.4"), "parseAny joins alternatives");
        check(ModVersion.Range.parseAny(List.of()).matches(ModVersion.parse("9")), "parseAny of nothing is any");

        check(in("latest", "latest"), "word equals itself");
        check(!in("latest", "1.0"), "word does not match a number");
        check(!in(">=1.0", "snapshot"), "comparison never matches a word");
    }

    static void faker() {
        check(FabricFaker.find("fabricloader") != null, "fabricloader exists");
        check(FabricFaker.find("fabric-loader") == FabricFaker.find("fabricloader"), "fabric-loader alias");
        check(FabricFaker.find("Fabric") == FabricFaker.find("fabric-api"), "fabric alias, any case");
        check(FabricFaker.find("fabric-api").backing() == FabricFaker.Backing.PARTIAL, "fabric-api is partial");
        check(FabricFaker.find("fabricloader").backing() == FabricFaker.Backing.FAKED, "loader is faked");
        check(FabricFaker.find("fabric-resource-loader-v0").backing() == FabricFaker.Backing.REAL, "resource loader is real");
        check(FabricFaker.find("fabric-item-group-api-v1").backing() == FabricFaker.Backing.PARTIAL, "item groups partial");
        check(FabricFaker.find("fabric-networking-api-v1").backing() == FabricFaker.Backing.FAKED, "networking faked");
        check(FabricFaker.find("nothing-like-it") == null, "unknown id is not provided");
        check(FabricFaker.find(null) == null, "null id is not provided");
        check(FabricFaker.isReserved("fabric-whatever"), "fabric-* is reserved");
        check(FabricFaker.isReserved("vbvclient") && FabricFaker.isReserved("minecraft") && FabricFaker.isReserved("c"), "client/game ids reserved");
        check(!FabricFaker.isReserved("coolmod"), "ordinary id is free");
        check(FabricFaker.isReserved("fabric_api") && FabricFaker.isReserved("fabric_loader") && FabricFaker.isReserved("fabric"), "alternative spellings of the faked mods are reserved");
        check(!FabricFaker.isReserved("fabric_user") && !FabricFaker.isReserved("fabrication"), "only real Fabric names are reserved, not anything that starts with fabric");
        check(FabricFaker.all().size() > 30, "module table has the modules");
        for (FabricFaker.Provided p : FabricFaker.all()) {
            check(p.id() != null && p.name() != null && p.version() != null && p.note() != null && !p.note().isEmpty(), "complete entry " + p.id());
        }

        Map<String, String> installed = Map.of("othermod", "2.1.0", "vbvclient", "2.0.0");

        FabricFaker.Resolution a = FabricFaker.resolve(List.of(
            dep("fabricloader", ">=0.15.0", FabricFaker.Kind.REQUIRES),
            dep("fabric-api", "*", FabricFaker.Kind.REQUIRES),
            dep("minecraft", "~26.2", FabricFaker.Kind.REQUIRES),
            dep("java", ">=21", FabricFaker.Kind.REQUIRES)), installed::get);
        check(a.ok(), "typical fabric mod depends resolve");
        check(a.warnings().isEmpty(), "no warnings for a fitting mod: " + a.warnings());
        check(a.faked().contains("fabricloader") && a.faked().contains("fabric-api"), "faked list says what was faked: " + a.faked());

        FabricFaker.Resolution b = FabricFaker.resolve(List.of(
            dep("fabricloader", ">=9.0.0", FabricFaker.Kind.REQUIRES),
            dep("fabric-api", ">=9.0.0", FabricFaker.Kind.REQUIRES)), installed::get);
        check(b.ok(), "an impossible Fabric version is still accepted");
        check(b.warnings().size() == 2, "...but warned about: " + b.warnings());

        FabricFaker.Resolution c = FabricFaker.resolve(List.of(dep("minecraft", "1.20.1", FabricFaker.Kind.REQUIRES), dep("java", ">=99", FabricFaker.Kind.REQUIRES)), installed::get);
        check(c.ok() && c.warnings().size() == 2, "old game or java version only warns: " + c.warnings());

        FabricFaker.Resolution d = FabricFaker.resolve(List.of(dep("othermod", ">=2.0", FabricFaker.Kind.REQUIRES)), installed::get);
        check(d.ok(), "installed mod satisfies");
        FabricFaker.Resolution e = FabricFaker.resolve(List.of(dep("othermod", ">=3.0", FabricFaker.Kind.REQUIRES)), installed::get);
        check(!e.ok() && e.errors().get(0).contains("found 2.1.0"), "too old installed mod fails: " + e.errors());
        FabricFaker.Resolution f = FabricFaker.resolve(List.of(dep("missing", "*", FabricFaker.Kind.REQUIRES)), installed::get);
        check(!f.ok() && f.errors().get(0).contains("not installed"), "missing mod fails: " + f.errors());
        FabricFaker.Resolution g = FabricFaker.resolve(List.of(dep("missing", "*", FabricFaker.Kind.RECOMMENDS)), installed::get);
        check(g.ok() && g.warnings().size() == 1, "missing recommended mod only warns");
        FabricFaker.Resolution h = FabricFaker.resolve(List.of(dep("othermod", "*", FabricFaker.Kind.BREAKS)), installed::get);
        check(!h.ok(), "breaks an installed mod fails");
        FabricFaker.Resolution i = FabricFaker.resolve(List.of(dep("othermod", "*", FabricFaker.Kind.CONFLICTS)), installed::get);
        check(i.ok() && i.warnings().size() == 1, "conflicts only warns");
        FabricFaker.Resolution j = FabricFaker.resolve(List.of(dep("fabric-api", "*", FabricFaker.Kind.BREAKS), dep("fabricloader", "*", FabricFaker.Kind.CONFLICTS)), installed::get);
        check(j.ok() && j.warnings().isEmpty(), "the fake Fabric pieces never conflict or break anything");
        FabricFaker.Resolution k = FabricFaker.resolve(List.of(dep("fabric-resource-loader-v0", "*", FabricFaker.Kind.REQUIRES)), installed::get);
        check(k.ok() && k.faked().isEmpty(), "a really-backed module is not listed as faked");
        FabricFaker.Resolution l = FabricFaker.resolve(List.of(dep("fabric-loader", "*", FabricFaker.Kind.REQUIRES)), installed::get);
        check(l.ok() && l.faked().contains("fabricloader"), "alias resolves to the normalised id");
    }

    private static FabricFaker.Dependency dep(String id, String range, FabricFaker.Kind kind) {
        return new FabricFaker.Dependency(id, ModVersion.Range.parse(range), kind);
    }
}
