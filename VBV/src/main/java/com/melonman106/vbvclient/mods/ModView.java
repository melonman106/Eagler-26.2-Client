package com.melonman106.vbvclient.mods;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What the Mods screen says about an imported mod. It is kept free of game classes so the wording and the status
 * logic are checked by the plain-JVM tests; the screen only decides colours and layout.
 */
public final class ModView {
    /** Where an imported mod stands, comparing what the game loaded at start-up with what would load next time. */
    public enum Status {
        /** Registered in this session and still planned. */
        LOADED("Loaded"),
        /** Not registered in this session, but will be after a restart. */
        WILL_LOAD("Loads after restart"),
        /** Registered in this session, but a newer copy is stored; the new one loads after a restart. */
        WILL_UPDATE("Updated copy loads after restart"),
        /** Registered in this session, but will not be after a restart (turned off, removed dependency, safe mode). */
        WILL_UNLOAD("Turns off after restart"),
        DISABLED("Turned off"),
        /** Enabled, but something it needs is missing. */
        BLOCKED("Not loaded"),
        SAFE_MODE("Not loaded (safe mode)"),
        /** The stored file could not be read or failed its checks. */
        INVALID("Invalid");

        public final String label;

        Status(String label) {
            this.label = label;
        }
    }

    public enum Tone { HEADING, NORMAL, GOOD, WARN, ERROR }

    public record Line(String text, Tone tone) {}

    private ModView() {}

    /**
     * @param loaded the mods registered in this session: id to the CRC of the copy that was registered
     */
    public static Status status(ModStore.Installed mod, ModStore.Plan plan, Map<String, Long> loaded) {
        if (mod.pkg() == null || !mod.problems().isEmpty()) return Status.INVALID;
        Long loadedCrc = loaded.get(mod.id());
        boolean planned = plan.isActive(mod.id());
        if (loadedCrc != null) {
            if (!planned) return Status.WILL_UNLOAD;
            return loadedCrc == mod.pkg().crc ? Status.LOADED : Status.WILL_UPDATE;
        }
        if (planned) return Status.WILL_LOAD;
        if (plan.safeMode() && mod.enabled()) return Status.SAFE_MODE;
        return mod.enabled() ? Status.BLOCKED : Status.DISABLED;
    }

    /** True when the screen should offer "Turn off" rather than "Turn on". */
    public static boolean canTurnOff(ModStore.Installed mod) {
        return mod.pkg() != null && mod.enabled();
    }

    /** How a faked Fabric piece is backed, in words a player can act on. */
    public static String backing(FabricFaker.Backing backing) {
        switch (backing) {
            case REAL: return "real";
            case PARTIAL: return "partly real";
            default: return "faked (reported as present, does nothing)";
        }
    }

    /** The lines of the details pane for one installed mod. */
    public static List<Line> details(ModStore.Installed mod, ModStore.Plan plan, Map<String, Long> loaded) {
        List<Line> out = new ArrayList<>();
        Status status = status(mod, plan, loaded);
        ModPackage pkg = mod.pkg();

        if (pkg == null || pkg.manifest == null) {
            out.add(new Line("This file could not be read as a mod.", Tone.ERROR));
            for (String problem : mod.problems()) out.add(new Line(problem, Tone.ERROR));
            return out;
        }
        ModManifest m = pkg.manifest;

        out.add(new Line(statusSentence(status), statusTone(status)));
        if (!m.description.isEmpty()) out.add(new Line(m.description, Tone.NORMAL));

        out.add(new Line("Mod", Tone.HEADING));
        out.add(new Line("ID: " + m.id + "   Version: " + m.version, Tone.NORMAL));
        if (!m.authors.isEmpty()) out.add(new Line("By " + String.join(", ", m.authors), Tone.NORMAL));
        out.add(new Line("Made as a " + (m.format == ModManifest.Format.FABRIC ? "Fabric mod (fabric.mod.json)" : "VBV mod (info.json)"), Tone.NORMAL));
        out.add(new Line("Survival: " + (m.survival ? "yes" : "no") + "   Multiplayer: " + (m.multiplayer ? "yes" : "no") + " (the author's setting)", Tone.NORMAL));

        out.add(new Line("Adds", Tone.HEADING));
        if (pkg.items.isEmpty()) {
            out.add(new Line(pkg.content != null && pkg.content.hasContent()
                ? "No ordinary items; the mod adds " + String.join(", ", pkg.content.kinds()) + "."
                : "No items. Its textures and language files are still applied.", Tone.NORMAL));
        } else {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < pkg.items.size() && i < 12; i++) {
                if (i > 0) names.append(", ");
                names.append(pkg.items.get(i).name());
            }
            if (pkg.items.size() > 12) names.append(", and ").append(pkg.items.size() - 12).append(" more");
            out.add(new Line(pkg.items.size() + (pkg.items.size() == 1 ? " item: " : " items: ") + names, Tone.NORMAL));
        }
        if (pkg.content != null && pkg.content.hasContent()) {
            out.add(new Line("Also adds: " + String.join(", ", pkg.content.kinds()) + ".", Tone.NORMAL));
        }
        if (!pkg.ignoredLists.isEmpty()) {
            out.add(new Line("Not supported yet, so left out: " + String.join(", ", pkg.ignoredLists), Tone.WARN));
        }

        List<FabricFaker.Dependency> deps = m.dependencies;
        if (!deps.isEmpty()) {
            out.add(new Line("Needs", Tone.HEADING));
            for (FabricFaker.Dependency dep : deps) {
                FabricFaker.Provided virtual = FabricFaker.find(dep.id());
                String kind = dep.kind() == FabricFaker.Kind.REQUIRES ? "" : " (" + dep.kind().name().toLowerCase(java.util.Locale.ROOT) + ")";
                String range = dep.range().toString();
                if (virtual != null) {
                    out.add(new Line(virtual.name() + " " + range + kind + ": " + backing(virtual.backing()) + ", reports " + virtual.version()
                        + (virtual.note().isEmpty() ? "" : ". " + virtual.note()), Tone.NORMAL));
                } else {
                    out.add(new Line(dep.id() + " " + range + kind, Tone.NORMAL));
                }
            }
        }

        List<String> problems = new ArrayList<>();
        List<String> why = plan.blocked().get(mod.id());
        if (why != null && status != Status.DISABLED) problems.addAll(why);
        FabricFaker.Resolution resolution = plan.resolutions().get(mod.id());
        List<String> warnings = new ArrayList<>();
        if (resolution != null) warnings.addAll(resolution.warnings());
        warnings.addAll(pkg.warnings);

        if (!problems.isEmpty()) {
            out.add(new Line("Why it is not loaded", Tone.HEADING));
            for (String line : problems) out.add(new Line(line, Tone.ERROR));
        }
        if (!warnings.isEmpty()) {
            out.add(new Line("Notes", Tone.HEADING));
            for (String line : warnings) out.add(new Line(line, Tone.WARN));
        }
        return out;
    }

    private static String statusSentence(Status status) {
        switch (status) {
            case LOADED: return "Loaded in this session.";
            case WILL_LOAD: return "Will load the next time the game starts.";
            case WILL_UPDATE: return "A newer copy is stored. It loads the next time the game starts; this session keeps the old one.";
            case WILL_UNLOAD: return "Is loaded now, but will not load the next time the game starts.";
            case DISABLED: return "Turned off. It will not load.";
            case SAFE_MODE: return "Safe mode is on, so no imported mod loads.";
            case INVALID: return "This mod is not valid and will not load.";
            default: return "This mod will not load.";
        }
    }

    private static Tone statusTone(Status status) {
        switch (status) {
            case LOADED: return Tone.GOOD;
            case WILL_LOAD:
            case WILL_UPDATE:
            case WILL_UNLOAD:
            case DISABLED:
            case SAFE_MODE: return Tone.NORMAL;
            default: return Tone.ERROR;
        }
    }

    /** The one-line summary shown under the title of the screen. */
    public static String summary(List<ModStore.Installed> all, ModStore.Plan plan, Map<String, Long> loaded) {
        int on = 0;
        for (ModStore.Installed mod : all) {
            Status s = status(mod, plan, loaded);
            if (s == Status.LOADED || s == Status.WILL_UPDATE) on++;
        }
        return all.size() + (all.size() == 1 ? " mod imported, " : " mods imported, ") + on + " loaded now";
    }
}
