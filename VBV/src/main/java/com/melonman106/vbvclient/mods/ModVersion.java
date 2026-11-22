package com.melonman106.vbvclient.mods;

import java.util.ArrayList;
import java.util.List;

/**
 * Fabric-style version numbers and version ranges, without any game classes so they can be tested on a plain JVM.
 *
 * <p>Versions are dotted numbers with an optional "-pre" part and an optional "+build" part ("1.2.3-beta+build").
 * Ranges are what fabric.mod.json writes in "depends": {@code *}, {@code 1.2.x}, {@code >=1.0}, {@code <2},
 * {@code ~1.2} (same minor), {@code ^1.2} (same major), several comparators separated by spaces (all must hold)
 * and alternatives separated by {@code ||}. A version that cannot be read as numbers only matches an exact
 * string, or "any".</p>
 */
public final class ModVersion implements Comparable<ModVersion> {
    private final String raw;
    private final int[] parts;
    private final String pre;
    private final boolean numeric;

    private ModVersion(String raw, int[] parts, String pre, boolean numeric) {
        this.raw = raw;
        this.parts = parts;
        this.pre = pre;
        this.numeric = numeric;
    }

    public static ModVersion parse(String text) {
        String raw = text == null ? "" : text.trim();
        String body = raw;
        int plus = body.indexOf('+');
        if (plus >= 0) body = body.substring(0, plus);
        String pre = "";
        int dash = body.indexOf('-');
        if (dash >= 0) {
            pre = body.substring(dash + 1);
            body = body.substring(0, dash);
        }
        if (body.isEmpty()) return new ModVersion(raw, new int[0], pre, false);
        String[] pieces = body.split("\\.", -1);
        int[] numbers = new int[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            String piece = pieces[i];
            if (piece.isEmpty() || piece.length() > 9) return new ModVersion(raw, new int[0], pre, false);
            for (int c = 0; c < piece.length(); c++) {
                if (piece.charAt(c) < '0' || piece.charAt(c) > '9') return new ModVersion(raw, new int[0], pre, false);
            }
            numbers[i] = Integer.parseInt(piece);
        }
        return new ModVersion(raw, numbers, pre, true);
    }

    public String raw() { return raw; }
    public boolean isNumeric() { return numeric; }

    private int part(int i) { return i < parts.length ? parts[i] : 0; }

    @Override
    public int compareTo(ModVersion other) {
        int length = Math.max(parts.length, other.parts.length);
        for (int i = 0; i < length; i++) {
            int a = part(i);
            int b = other.part(i);
            if (a != b) return a < b ? -1 : 1;
        }
        // A pre-release ("1.0-beta") is older than the release ("1.0").
        if (pre.isEmpty() && other.pre.isEmpty()) return 0;
        if (pre.isEmpty()) return 1;
        if (other.pre.isEmpty()) return -1;
        return pre.compareTo(other.pre);
    }

    @Override
    public boolean equals(Object o) { return o instanceof ModVersion v && compareTo(v) == 0 && numeric == v.numeric && (numeric || raw.equals(v.raw)); }

    @Override
    public int hashCode() { return numeric ? java.util.Arrays.hashCode(parts) : raw.hashCode(); }

    @Override
    public String toString() { return raw; }

    /** A parsed range: a list of alternatives, each a list of comparators that must all hold. */
    public static final class Range {
        private enum Op { ANY, EQ, GE, GT, LE, LT, WILD }

        private record Comparator(Op op, ModVersion version, int wildcardAt) {}

        private final String text;
        private final List<List<Comparator>> alternatives;

        private Range(String text, List<List<Comparator>> alternatives) {
            this.text = text;
            this.alternatives = alternatives;
        }

        public static Range any() { return parse("*"); }

        /** Join several alternatives as fabric.mod.json does when "depends" has an array. */
        public static Range parseAny(List<String> ranges) {
            if (ranges == null || ranges.isEmpty()) return any();
            return parse(String.join(" || ", ranges));
        }

        public static Range parse(String text) {
            String source = text == null ? "" : text.trim();
            List<List<Comparator>> alternatives = new ArrayList<>();
            for (String alternative : source.split("\\|\\|", -1)) {
                List<Comparator> all = new ArrayList<>();
                for (String token : alternative.trim().split("\\s+")) {
                    if (token.isEmpty()) continue;
                    all.addAll(comparators(token));
                }
                if (all.isEmpty()) all.add(new Comparator(Op.ANY, null, -1));
                alternatives.add(all);
            }
            if (alternatives.isEmpty()) alternatives.add(List.of(new Comparator(Op.ANY, null, -1)));
            return new Range(source.isEmpty() ? "*" : source, alternatives);
        }

        private static List<Comparator> comparators(String token) {
            if (token.equals("*") || token.equalsIgnoreCase("x")) return List.of(new Comparator(Op.ANY, null, -1));
            if (token.startsWith(">=")) return List.of(new Comparator(Op.GE, ModVersion.parse(token.substring(2)), -1));
            if (token.startsWith("<=")) return List.of(new Comparator(Op.LE, ModVersion.parse(token.substring(2)), -1));
            if (token.startsWith(">")) return List.of(new Comparator(Op.GT, ModVersion.parse(token.substring(1)), -1));
            if (token.startsWith("<")) return List.of(new Comparator(Op.LT, ModVersion.parse(token.substring(1)), -1));
            if (token.startsWith("=")) return exactOrWild(token.substring(1));
            if (token.startsWith("~")) {
                ModVersion base = ModVersion.parse(token.substring(1));
                return List.of(
                    new Comparator(Op.GE, base, -1),
                    new Comparator(Op.LT, bump(base, base.parts.length <= 1 ? 0 : 1), -1));
            }
            if (token.startsWith("^")) {
                ModVersion base = ModVersion.parse(token.substring(1));
                int at = base.parts.length > 0 && base.parts[0] == 0 && base.parts.length > 1 ? 1 : 0;
                return List.of(new Comparator(Op.GE, base, -1), new Comparator(Op.LT, bump(base, at), -1));
            }
            return exactOrWild(token);
        }

        private static List<Comparator> exactOrWild(String text) {
            String[] pieces = text.split("\\.", -1);
            for (int i = 0; i < pieces.length; i++) {
                if (pieces[i].equals("x") || pieces[i].equals("X") || pieces[i].equals("*")) {
                    return List.of(new Comparator(Op.WILD, ModVersion.parse(String.join(".", java.util.Arrays.copyOfRange(pieces, 0, i))), i));
                }
            }
            return List.of(new Comparator(Op.EQ, ModVersion.parse(text), -1));
        }

        /** The smallest version above everything that shares the first {@code at + 1} parts of {@code base}. */
        private static ModVersion bump(ModVersion base, int at) {
            int[] copy = new int[at + 1];
            for (int i = 0; i <= at; i++) copy[i] = base.part(i);
            copy[at]++;
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < copy.length; i++) text.append(i == 0 ? "" : ".").append(copy[i]);
            return ModVersion.parse(text.toString());
        }

        public boolean matches(ModVersion version) {
            for (List<Comparator> all : alternatives) {
                boolean ok = true;
                for (Comparator c : all) {
                    if (!test(c, version)) { ok = false; break; }
                }
                if (ok) return true;
            }
            return false;
        }

        private static boolean test(Comparator c, ModVersion v) {
            if (c.op == Op.ANY) return true;
            if (!v.numeric || !c.version.numeric) {
                // Names that are not numbers only ever match themselves.
                return c.op == Op.EQ && v.raw.equals(c.version.raw);
            }
            switch (c.op) {
                case EQ: return v.compareTo(c.version) == 0;
                case GE: return v.compareTo(c.version) >= 0;
                case GT: return v.compareTo(c.version) > 0;
                case LE: return v.compareTo(c.version) <= 0;
                case LT: return v.compareTo(c.version) < 0;
                case WILD:
                    for (int i = 0; i < c.wildcardAt; i++) if (v.part(i) != c.version.part(i)) return false;
                    return true;
                default: return false;
            }
        }

        @Override
        public String toString() { return text; }
    }
}
