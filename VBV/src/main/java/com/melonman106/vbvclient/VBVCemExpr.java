package com.melonman106.vbvclient;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;

/**
 * Compiler for the animation expressions of OptiFine custom entity models, e.g.
 * {@code body.ty = 17.5 + sin(age / 10) * clamp(limb_speed, 0, 1)}.
 *
 * <p>Everything is a float; booleans are 1 and 0. Operators (lowest to highest precedence):
 * {@code || && == != < <= > >= + - * / %}, then unary {@code - + !}. Names that are not functions are
 * handed to a {@link Resolver}; unknown functions or names make {@link #compile} throw so the caller
 * can skip just that one line. It only uses the Java standard library and {@code Mth}'s sine table.</p>
 */
final class VBVCemExpr {
    /** A compiled expression or variable read. */
    interface Num {
        float get();
    }

    /** A value known at compile time; lets the builders below fold whole sub-trees into one number. */
    private static final class Const implements Num {
        final float value;

        Const(float value) {
            this.value = value;
        }

        @Override
        public float get() {
            return value;
        }
    }

    private static boolean isConst(Num n) {
        return n instanceof Const;
    }

    private static float val(Num n) {
        return ((Const) n).value;
    }

    /** Looks a variable up by its full name (for example {@code limb_swing}, {@code var.x}, {@code body.rx}). */
    interface Resolver {
        /** @return the reader, or null when the name is unknown */
        Num resolve(String name);
    }

    /**
     * Builds the reader of an {@code nbt(path, test)} call. The two texts are handed over unparsed
     * (the test is written in the NBT matching syntax, for example {@code raw:iregex:.*shield.*}, which is
     * not part of the expression grammar); the result reads 1 or 0.
     */
    interface NbtFactory {
        Num create(String path, String test);
    }

    private final String text;
    private final Resolver resolver;
    private final NbtFactory nbt;
    private int pos;

    private VBVCemExpr(String text, Resolver resolver, NbtFactory nbt) {
        this.text = text;
        this.resolver = resolver;
        this.nbt = nbt;
    }

    static Num compile(String expression, Resolver resolver) {
        return compile(expression, resolver, null);
    }

    static Num compile(String expression, Resolver resolver, NbtFactory nbt) {
        VBVCemExpr parser = new VBVCemExpr(expression, resolver, nbt);
        Num result = parser.parseOr();
        parser.skipSpace();
        if (parser.pos != parser.text.length()) {
            throw parser.error("unexpected '" + parser.text.charAt(parser.pos) + "'");
        }
        return result;
    }

    // ---------------------------------------------------------------------------------------
    // Parser
    // ---------------------------------------------------------------------------------------

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at " + pos + " in '" + text + "'");
    }

    private void skipSpace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private boolean accept(String token) {
        skipSpace();
        if (text.startsWith(token, pos)) {
            pos += token.length();
            return true;
        }
        return false;
    }

    /** Accepts a one-character operator unless it is the start of a longer one (for example '<' of '<='). */
    private boolean acceptSingle(char op, char notFollowedBy) {
        skipSpace();
        if (pos < text.length() && text.charAt(pos) == op
            && !(pos + 1 < text.length() && text.charAt(pos + 1) == notFollowedBy)) {
            pos++;
            return true;
        }
        return false;
    }

    private Num parseOr() {
        Num left = parseAnd();
        while (accept("||")) {
            left = or(left, parseAnd());
        }
        return left;
    }

    private Num parseAnd() {
        Num left = parseEquality();
        while (accept("&&")) {
            left = and(left, parseEquality());
        }
        return left;
    }

    private Num parseEquality() {
        Num left = parseCompare();
        while (true) {
            if (accept("==")) {
                left = eq(left, parseCompare(), false);
            } else if (accept("!=")) {
                left = eq(left, parseCompare(), true);
            } else {
                return left;
            }
        }
    }

    private Num parseCompare() {
        Num left = parseSum();
        while (true) {
            if (accept("<=")) {
                left = cmp(left, parseSum(), 0);
            } else if (accept(">=")) {
                left = cmp(left, parseSum(), 1);
            } else if (acceptSingle('<', '=')) {
                left = cmp(left, parseSum(), 2);
            } else if (acceptSingle('>', '=')) {
                left = cmp(left, parseSum(), 3);
            } else {
                return left;
            }
        }
    }

    private Num parseSum() {
        Num left = parseProduct();
        while (true) {
            skipSpace();
            if (pos < text.length() && text.charAt(pos) == '+') {
                pos++;
                left = arith(left, parseProduct(), '+');
            } else if (pos < text.length() && text.charAt(pos) == '-') {
                pos++;
                left = arith(left, parseProduct(), '-');
            } else {
                return left;
            }
        }
    }

    private Num parseProduct() {
        Num left = parseUnary();
        while (true) {
            skipSpace();
            if (pos < text.length() && text.charAt(pos) == '*') {
                pos++;
                left = arith(left, parseUnary(), '*');
            } else if (pos < text.length() && text.charAt(pos) == '/') {
                pos++;
                left = arith(left, parseUnary(), '/');
            } else if (pos < text.length() && text.charAt(pos) == '%') {
                pos++;
                left = arith(left, parseUnary(), '%');
            } else {
                return left;
            }
        }
    }

    private Num parseUnary() {
        skipSpace();
        if (pos < text.length()) {
            char ch = text.charAt(pos);
            if (ch == '-') {
                pos++;
                Num inner = parseUnary();
                if (isConst(inner)) {
                    return new Const(-val(inner));
                }
                return () -> -inner.get();
            }
            if (ch == '+') {
                pos++;
                return parseUnary();
            }
            if (ch == '!' && !(pos + 1 < text.length() && text.charAt(pos + 1) == '=')) {
                pos++;
                Num inner = parseUnary();
                if (isConst(inner)) {
                    return new Const(val(inner) == 0.0F ? 1.0F : 0.0F);
                }
                return () -> inner.get() == 0.0F ? 1.0F : 0.0F;
            }
        }
        return parsePrimary();
    }

    private Num parsePrimary() {
        skipSpace();
        if (pos >= text.length()) {
            throw error("unexpected end");
        }
        char ch = text.charAt(pos);
        if (ch == '(') {
            pos++;
            Num inner = parseOr();
            if (!accept(")")) {
                throw error("missing ')'");
            }
            return inner;
        }
        if (Character.isDigit(ch) || (ch == '.' && pos + 1 < text.length() && Character.isDigit(text.charAt(pos + 1)))) {
            return number();
        }
        if (Character.isLetter(ch) || ch == '_') {
            int start = pos;
            while (pos < text.length() && isNameChar(text.charAt(pos))) {
                pos++;
            }
            String name = text.substring(start, pos);
            skipSpace();
            if (pos < text.length() && text.charAt(pos) == '(') {
                if ("nbt".equals(name)) {
                    return nbtCall();
                }
                pos++;
                List<Num> args = new ArrayList<>();
                if (!accept(")")) {
                    do {
                        args.add(parseOr());
                    } while (accept(","));
                    if (!accept(")")) {
                        throw error("missing ')' after the arguments of " + name);
                    }
                }
                return function(name, args);
            }
            return constantOrVariable(name);
        }
        throw error("unexpected '" + ch + "'");
    }

    /** Reads {@code nbt( path , test )} with the position on the opening parenthesis. */
    private Num nbtCall() {
        int open = pos;
        int depth = 0;
        int comma = -1;
        int i = pos;
        for (; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
                if (depth == 0) {
                    break;
                }
            } else if (ch == ',' && depth == 1 && comma < 0) {
                comma = i;
            }
        }
        if (i >= text.length()) {
            throw error("missing ')' after the arguments of nbt");
        }
        String path = text.substring(open + 1, comma < 0 ? i : comma).trim();
        String test = comma < 0 ? "" : text.substring(comma + 1, i).trim();
        pos = i + 1;
        if (nbt == null) {
            throw error("unknown function 'nbt'");
        }
        return nbt.create(path, test);
    }

    private static boolean isNameChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '.' || ch == ':' || ch == '#';
    }

    private Num number() {
        int start = pos;
        while (pos < text.length() && (Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '.')) {
            pos++;
        }
        if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
            int save = pos;
            pos++;
            if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                pos++;
            }
            if (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                }
            } else {
                pos = save;
            }
        }
        if (pos < text.length() && (text.charAt(pos) == 'f' || text.charAt(pos) == 'F')) {
            String literal = text.substring(start, pos);
            pos++;
            return constant(parse(literal));
        }
        return constant(parse(text.substring(start, pos)));
    }

    private float parse(String literal) {
        try {
            return Float.parseFloat(literal);
        } catch (NumberFormatException e) {
            throw error("bad number '" + literal + "'");
        }
    }

    private Num constantOrVariable(String name) {
        switch (name) {
            case "pi":
                return constant((float) Math.PI);
            case "true":
                return constant(1.0F);
            case "false":
                return constant(0.0F);
            default:
                break;
        }
        Num variable = resolver.resolve(name);
        if (variable == null) {
            throw error("unknown variable '" + name + "'");
        }
        return variable;
    }

    // ---------------------------------------------------------------------------------------
    // Building blocks
    // ---------------------------------------------------------------------------------------

    private static Num constant(float value) {
        return new Const(value);
    }

    private static Num or(Num a, Num b) {
        return () -> a.get() != 0.0F || b.get() != 0.0F ? 1.0F : 0.0F;
    }

    private static Num and(Num a, Num b) {
        return () -> a.get() != 0.0F && b.get() != 0.0F ? 1.0F : 0.0F;
    }

    private static Num eq(Num a, Num b, boolean negate) {
        return negate ? () -> a.get() != b.get() ? 1.0F : 0.0F : () -> a.get() == b.get() ? 1.0F : 0.0F;
    }

    private static Num cmp(Num a, Num b, int kind) {
        switch (kind) {
            case 0:
                return () -> a.get() <= b.get() ? 1.0F : 0.0F;
            case 1:
                return () -> a.get() >= b.get() ? 1.0F : 0.0F;
            case 2:
                return () -> a.get() < b.get() ? 1.0F : 0.0F;
            default:
                return () -> a.get() > b.get() ? 1.0F : 0.0F;
        }
    }

    private static Num arith(Num a, Num b, char op) {
        boolean ca = isConst(a);
        boolean cb = isConst(b);
        if (ca && cb) {
            float x = val(a);
            float y = val(b);
            switch (op) {
                case '+': return new Const(x + y);
                case '-': return new Const(x - y);
                case '*': return new Const(x * y);
                case '/': return new Const(x / y);
                default: return new Const(x % y);
            }
        }
        if (cb) {
            final float k = val(b);
            switch (op) {
                case '+': return () -> a.get() + k;
                case '-': return () -> a.get() - k;
                case '*': return () -> a.get() * k;
                case '/': return () -> a.get() / k;
                default: return () -> a.get() % k;
            }
        }
        if (ca) {
            final float k = val(a);
            switch (op) {
                case '+': return () -> k + b.get();
                case '-': return () -> k - b.get();
                case '*': return () -> k * b.get();
                case '/': return () -> k / b.get();
                default: return () -> k % b.get();
            }
        }
        switch (op) {
            case '+':
                return () -> a.get() + b.get();
            case '-':
                return () -> a.get() - b.get();
            case '*':
                return () -> a.get() * b.get();
            case '/':
                return () -> a.get() / b.get();
            default:
                return () -> a.get() % b.get();
        }
    }

    // ---------------------------------------------------------------------------------------
    // Sine / cosine
    // ---------------------------------------------------------------------------------------
    // These are the game's own table functions (Mth.sin / Mth.cos), bit for bit, on purpose: Fresh Animations
    // detects the vanilla attack swing by testing body.ry == sin(sqrt(swing_progress)*pi*2)*0.2, and the vanilla
    // pose it compares with was computed by Mth.sin, so any other sine (an interpolated table, Math.sin) never
    // compares equal and the arm swing never starts. The table lookup also stays inside WebAssembly, where
    // Math.sin and Math.cos are calls out to JavaScript and the pack calls them hundreds of times per entity.

    static float sin(float x) {
        return Mth.sin(x);
    }

    static float cos(float x) {
        return Mth.cos(x);
    }

    /** Folds a one-argument function of a constant into a constant. */
    private static Num fold1(Num a, java.util.function.DoubleUnaryOperator op, Num live) {
        return isConst(a) ? new Const((float) op.applyAsDouble(val(a))) : live;
    }

    private Num function(String name, List<Num> args) {
        int n = args.size();
        Num a = n > 0 ? args.get(0) : null;
        Num b = n > 1 ? args.get(1) : null;
        Num c = n > 2 ? args.get(2) : null;
        switch (name) {
            case "sin":
                need(name, n, 1);
                return fold1(a, x -> Mth.sin(x), () -> sin(a.get()));
            case "cos":
                need(name, n, 1);
                return fold1(a, x -> Mth.cos(x), () -> cos(a.get()));
            case "tan":
                need(name, n, 1);
                return fold1(a, Math::tan, () -> (float) Math.tan(a.get()));
            case "asin":
                need(name, n, 1);
                return () -> (float) Math.asin(a.get());
            case "acos":
                need(name, n, 1);
                return () -> (float) Math.acos(a.get());
            case "atan":
                need(name, n, 1);
                return () -> (float) Math.atan(a.get());
            case "atan2":
                need(name, n, 2);
                return () -> (float) Math.atan2(a.get(), b.get());
            case "torad":
                need(name, n, 1);
                return fold1(a, Math::toRadians, () -> (float) Math.toRadians(a.get()));
            case "todeg":
                need(name, n, 1);
                return fold1(a, Math::toDegrees, () -> (float) Math.toDegrees(a.get()));
            case "abs":
                need(name, n, 1);
                return fold1(a, Math::abs, () -> Math.abs(a.get()));
            case "sqrt":
                need(name, n, 1);
                return fold1(a, Math::sqrt, () -> (float) Math.sqrt(a.get()));
            case "exp":
                need(name, n, 1);
                return () -> (float) Math.exp(a.get());
            case "log":
                need(name, n, 1);
                return () -> (float) Math.log(a.get());
            case "floor":
                need(name, n, 1);
                return () -> (float) Math.floor(a.get());
            case "ceil":
                need(name, n, 1);
                return () -> (float) Math.ceil(a.get());
            case "round":
                need(name, n, 1);
                return () -> (float) Math.round(a.get());
            case "frac":
                need(name, n, 1);
                return () -> a.get() - (float) Math.floor(a.get());
            case "signum":
                need(name, n, 1);
                return () -> Math.signum(a.get());
            case "wraprad":
                need(name, n, 1);
                return () -> {
                    float v = a.get();
                    if (!Float.isFinite(v)) {
                        return 0.0F;
                    }
                    float turn = (float) (Math.PI * 2.0);
                    return v - turn * (float) Math.floor((v + (float) Math.PI) / turn);
                };
            case "wrapdeg":
                need(name, n, 1);
                return () -> {
                    float v = a.get();
                    return Float.isFinite(v) ? v - 360.0F * (float) Math.floor((v + 180.0F) / 360.0F) : 0.0F;
                };
            case "log10":
                need(name, n, 1);
                return () -> (float) Math.log10(a.get());
            case "sinh":
                need(name, n, 1);
                return () -> (float) Math.sinh(a.get());
            case "cosh":
                need(name, n, 1);
                return () -> (float) Math.cosh(a.get());
            case "tanh":
                need(name, n, 1);
                return () -> (float) Math.tanh(a.get());
            case "pow":
                need(name, n, 2);
                return () -> (float) Math.pow(a.get(), b.get());
            case "fmod":
                need(name, n, 2);
                return () -> {
                    float y = b.get();
                    return y == 0.0F ? 0.0F : a.get() - y * (float) Math.floor(a.get() / y);
                };
            case "min":
                atLeast(name, n, 2);
                return () -> {
                    float best = args.get(0).get();
                    for (int i = 1; i < args.size(); i++) {
                        best = Math.min(best, args.get(i).get());
                    }
                    return best;
                };
            case "max":
                atLeast(name, n, 2);
                return () -> {
                    float best = args.get(0).get();
                    for (int i = 1; i < args.size(); i++) {
                        best = Math.max(best, args.get(i).get());
                    }
                    return best;
                };
            case "clamp":
                need(name, n, 3);
                return () -> {
                    float v = a.get();
                    float lo = b.get();
                    float hi = c.get();
                    return v < lo ? lo : Math.min(v, hi);
                };
            case "lerp":
                need(name, n, 3);
                return () -> {
                    float from = b.get();
                    return from + a.get() * (c.get() - from);
                };
            case "between":
                need(name, n, 3);
                return () -> a.get() >= b.get() && a.get() <= c.get() ? 1.0F : 0.0F;
            case "equals":
                atLeast(name, n, 2);
                return () -> {
                    float eps = args.size() > 2 ? args.get(2).get() : 0.0F;
                    return Math.abs(a.get() - b.get()) <= eps ? 1.0F : 0.0F;
                };
            case "in":
                atLeast(name, n, 2);
                return () -> {
                    float v = a.get();
                    for (int i = 1; i < args.size(); i++) {
                        if (args.get(i).get() == v) {
                            return 1.0F;
                        }
                    }
                    return 0.0F;
                };
            case "random":
                if (n == 0) {
                    return () -> (float) Math.random();
                }
                return () -> random(a.get());
            case "if":
            case "ifb":
                atLeast(name, n, 3);
                if (n == 3) {
                    if (isConst(a)) {
                        return val(a) != 0.0F ? b : c;
                    }
                    return () -> a.get() != 0.0F ? b.get() : c.get();
                }
                return () -> {
                    int i = 0;
                    while (i + 1 < args.size()) {
                        if (args.get(i).get() != 0.0F) {
                            return args.get(i + 1).get();
                        }
                        i += 2;
                    }
                    return i < args.size() ? args.get(i).get() : 0.0F;
                };
            case "print":
            case "printb":
                atLeast(name, n, 1);
                return args.get(n - 1);
            default:
                throw error("unknown function '" + name + "'");
        }
    }

    private void need(String name, int actual, int expected) {
        if (actual != expected) {
            throw error(name + "() takes " + expected + " argument(s), got " + actual);
        }
    }

    private void atLeast(String name, int actual, int expected) {
        if (actual < expected) {
            throw error(name + "() takes at least " + expected + " argument(s), got " + actual);
        }
    }

    /** A repeatable pseudo random value in [0, 1) for a seed, so every entity gets its own phase. */
    static float random(float seed) {
        int h = Float.floatToIntBits(seed) * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return (h >>> 8) / 16777216.0F;
    }
}
