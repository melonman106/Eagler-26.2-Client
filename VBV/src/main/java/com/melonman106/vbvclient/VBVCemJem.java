package com.melonman106.vbvclient;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Reader for OptiFine custom entity models ({@code .jem} with {@code .jpm} part files).
 *
 * <p>It only depends on Gson, so it can be tested without the game. The result is already
 * "prepared": translations, rotations and box coordinates are flipped for the axes listed in
 * {@code invertAxis} and angles are in radians, so the binder ({@link VBVCem}) can build parts
 * straight from it. The interpretation follows the behaviour documented by the OptiFine format and
 * implemented by Entity Model Features (LGPL-3.0, https://github.com/Traben-0/Entity_Model_Features),
 * which was used as a behavioural reference; no code is copied from it.</p>
 */
final class VBVCemJem {
    /** The six face keys of a box, in this order. */
    static final int UV_DOWN = 0;
    static final int UV_UP = 1;
    static final int UV_NORTH = 2;
    static final int UV_SOUTH = 3;
    static final int UV_WEST = 4;
    static final int UV_EAST = 5;
    private static final String[] UV_KEYS = {"uvDown", "uvUp", "uvNorth", "uvSouth", "uvWest", "uvEast"};
    private static final int MAX_DEPTH = 24;

    static final class Box {
        /** Minimum corner and size in the part's own frame, already flipped for invertAxis. */
        float x, y, z, w, h, d;
        float growX, growY, growZ;
        /** True when the box uses the standard unwrapped layout starting at (u, v). */
        boolean standard;
        int u, v;
        /** Per face {u0, v0, u1, v1} in pixels; a null entry means that face is not drawn. */
        final float[][] uv = new float[6][];
    }

    static final class Part {
        /** Vanilla part this top-level entry belongs to (OptiFine name), null for submodels. */
        String part;
        /** Unique id, always prefixed with {@code EMF_} like the reference implementation. */
        String id;
        boolean attach;
        float scale = 1.0F;
        /** Pose relative to the parent, flipped for invertAxis; angles in radians. */
        float x, y, z, xRot, yRot, zRot;
        boolean mirrorU;
        boolean mirrorV;
        final List<Box> boxes = new ArrayList<>();
        final List<Part> children = new ArrayList<>();
        /** {key, expression} pairs, whitespace stripped and {@code this}/{@code part} resolved. */
        final List<String[]> animations = new ArrayList<>();
    }

    int texW;
    int texH;
    String texture;
    final List<Part> parts = new ArrayList<>();

    private final Set<String> usedIds = new HashSet<>();
    private int anonymous;

    /** @param loader returns the JSON of a {@code .jpm} reference, or null when it cannot be read */
    static VBVCemJem parse(JsonObject root, Function<String, JsonObject> loader) {
        VBVCemJem jem = new VBVCemJem();
        float[] size = floats(root, "textureSize", 2);
        if (size != null && size[0] >= 1.0F && size[1] >= 1.0F) {
            jem.texW = Math.round(size[0]);
            jem.texH = Math.round(size[1]);
        }
        jem.texture = string(root, "texture");
        JsonElement models = root.get("models");
        if (models != null && models.isJsonArray()) {
            for (JsonElement entry : models.getAsJsonArray()) {
                if (!entry.isJsonObject()) {
                    continue;
                }
                JsonObject merged = merge(entry.getAsJsonObject(), loader);
                String vanilla = string(merged, "part");
                if (vanilla == null || vanilla.isEmpty()) {
                    continue;
                }
                Part part = jem.parsePart(merged, loader, true, 0);
                part.part = vanilla;
                readAnimations(merged, part);
                jem.parts.add(part);
            }
        }
        return jem;
    }

    /**
     * A {@code "model": "x.jpm"} entry takes every field it does not define itself from the .jpm
     * (the entry's own part / id / attach always win).
     */
    private static JsonObject merge(JsonObject entry, Function<String, JsonObject> loader) {
        String ref = string(entry, "model");
        if (ref == null || ref.isEmpty()) {
            return entry;
        }
        JsonObject jpm = loader.apply(ref);
        if (jpm == null) {
            return entry;
        }
        JsonObject merged = new JsonObject();
        for (Map.Entry<String, JsonElement> field : entry.entrySet()) {
            if (!"model".equals(field.getKey())) {
                merged.add(field.getKey(), field.getValue());
            }
        }
        for (Map.Entry<String, JsonElement> field : jpm.entrySet()) {
            String key = field.getKey();
            if ("part".equals(key) || "id".equals(key) || "attach".equals(key) || "model".equals(key)) {
                continue;
            }
            JsonElement own = merged.get(key);
            if (own == null || isEmpty(own)) {
                merged.add(key, field.getValue());
            }
        }
        return merged;
    }

    private static boolean isEmpty(JsonElement e) {
        if (e.isJsonArray()) {
            return e.getAsJsonArray().size() == 0;
        }
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            return e.getAsString().trim().isEmpty();
        }
        return e.isJsonNull();
    }

    private Part parsePart(JsonObject o, Function<String, JsonObject> loader, boolean top, int depth) {
        Part part = new Part();
        String rawId = string(o, "id");
        part.id = uniqueId(rawId);
        part.attach = bool(o, "attach");
        part.scale = number(o, "scale", 1.0F);

        String invert = string(o, "invertAxis");
        if (invert == null) {
            invert = "";
        }
        boolean ix = invert.indexOf('x') >= 0;
        boolean iy = invert.indexOf('y') >= 0;
        boolean iz = invert.indexOf('z') >= 0;

        float[] t = floats(o, "translate", 3);
        if (t == null) {
            t = new float[3];
        }
        float[] r = floats(o, "rotate", 3);
        if (r == null) {
            r = new float[3];
        }
        part.x = ix ? -t[0] : t[0];
        part.y = iy ? -t[1] : t[1];
        part.z = iz ? -t[2] : t[2];
        part.xRot = (float) Math.toRadians(ix ? -r[0] : r[0]);
        part.yRot = (float) Math.toRadians(iy ? -r[1] : r[1]);
        part.zRot = (float) Math.toRadians(iz ? -r[2] : r[2]);

        String mirror = string(o, "mirrorTexture");
        if (mirror != null) {
            part.mirrorU = mirror.indexOf('u') >= 0;
            part.mirrorV = mirror.indexOf('v') >= 0;
        }

        JsonElement boxes = o.get("boxes");
        if (boxes != null && boxes.isJsonArray()) {
            for (JsonElement element : boxes.getAsJsonArray()) {
                if (element.isJsonObject()) {
                    Box box = parseBox(element.getAsJsonObject(), ix, iy, iz);
                    if (box != null) {
                        part.boxes.add(box);
                    }
                }
            }
        }

        if (depth < MAX_DEPTH) {
            List<JsonObject> subs = new ArrayList<>();
            JsonElement single = o.get("submodel");
            if (single != null && single.isJsonObject()) {
                subs.add(single.getAsJsonObject());
            }
            JsonElement many = o.get("submodels");
            if (many != null && many.isJsonArray()) {
                for (JsonElement element : many.getAsJsonArray()) {
                    if (element.isJsonObject()) {
                        subs.add(element.getAsJsonObject());
                    }
                }
            }
            for (JsonObject sub : subs) {
                part.children.add(parsePart(merge(sub, loader), loader, false, depth + 1));
            }
        }
        return part;
    }

    private String uniqueId(String raw) {
        String base = "EMF_" + (raw == null || raw.trim().isEmpty() ? "part" + (anonymous++) : raw.trim());
        String id = base;
        int n = 1;
        while (!usedIds.add(id)) {
            id = base + "#" + n++;
        }
        return id;
    }

    private static Box parseBox(JsonObject b, boolean ix, boolean iy, boolean iz) {
        float[] c = floats(b, "coordinates", 6);
        if (c == null) {
            return null;
        }
        Box box = new Box();
        box.x = ix ? -c[0] - c[3] : c[0];
        box.y = iy ? -c[1] - c[4] : c[1];
        box.z = iz ? -c[2] - c[5] : c[2];
        box.w = c[3];
        box.h = c[4];
        box.d = c[5];

        float add = number(b, "sizeAdd", 0.0F);
        box.growX = number(b, "sizeAddX", add);
        box.growY = number(b, "sizeAddY", add);
        box.growZ = number(b, "sizeAddZ", add);
        float[] adds = floats(b, "sizesAdd", 3);
        if (adds != null) {
            box.growX = adds[0];
            box.growY = adds[1];
            box.growZ = adds[2];
        }

        float[] offset = floats(b, "textureOffset", 2);
        if (offset != null) {
            box.standard = true;
            box.u = (int) offset[0];
            box.v = (int) offset[1];
            return box;
        }
        for (int face = 0; face < UV_KEYS.length; face++) {
            float[] rect = floats(b, UV_KEYS[face], 4);
            if (rect == null) {
                rect = legacy(b, face);
            }
            box.uv[face] = rect;
        }
        return box;
    }

    /** Older exporters used uvFront/uvBack/uvLeft/uvRight. */
    private static float[] legacy(JsonObject b, int face) {
        switch (face) {
            case UV_NORTH:
                float[] front = floats(b, "uvFront", 4);
                return front != null ? front : floats(b, "uvLeft", 4);
            case UV_SOUTH:
                return floats(b, "uvBack", 4);
            case UV_EAST:
                return floats(b, "uvRight", 4);
            default:
                return null;
        }
    }

    /** Collects the {@code animations} list of a top-level part into resolved {key, expression} pairs. */
    private static void readAnimations(JsonObject o, Part part) {
        JsonElement list = o.get("animations");
        if (list == null || !list.isJsonArray()) {
            return;
        }
        for (JsonElement item : list.getAsJsonArray()) {
            if (!item.isJsonObject()) {
                continue;
            }
            for (Map.Entry<String, JsonElement> line : item.getAsJsonObject().entrySet()) {
                JsonElement value = line.getValue();
                if (value == null || !value.isJsonPrimitive()) {
                    continue;
                }
                String key = resolve(line.getKey(), part);
                String expression = resolve(value.getAsString(), part);
                if (!key.isEmpty() && !expression.isEmpty()) {
                    part.animations.add(new String[]{key, expression});
                }
            }
        }
    }

    /** Strips whitespace and replaces the {@code this} / {@code part} shorthands. */
    static String resolve(String text, Part part) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (!Character.isWhitespace(ch)) {
                out.append(ch);
            }
        }
        String result = out.toString();
        result = replaceWord(result, "this", part.id);
        if (part.part != null) {
            result = replaceWord(result, "part", part.part);
        }
        return result;
    }

    private static boolean wordChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '.' || ch == ':';
    }

    /** Replaces whole-word occurrences (a word is delimited by anything but letters, digits, _ . :). */
    private static String replaceWord(String text, String word, String replacement) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            if (text.startsWith(word, i)) {
                boolean before = i == 0 || !wordChar(text.charAt(i - 1));
                int end = i + word.length();
                // "this.tx" / "part.rx": the word may be followed by a dot, but not by more name characters
                boolean after = end < text.length()
                    && !(Character.isLetterOrDigit(text.charAt(end)) || text.charAt(end) == '_');
                if (before && after) {
                    out.append(replacement);
                    i = end;
                    continue;
                }
            }
            out.append(text.charAt(i));
            i++;
        }
        return out.toString();
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static boolean bool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            return false;
        }
        if (e.getAsJsonPrimitive().isBoolean()) {
            return e.getAsBoolean();
        }
        return "true".equalsIgnoreCase(e.getAsString());
    }

    private static float number(JsonObject o, String key, float fallback) {
        JsonElement e = o.get(key);
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            return e.getAsFloat();
        }
        return fallback;
    }

    private static float[] floats(JsonObject o, String key, int count) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonArray()) {
            return null;
        }
        JsonArray array = e.getAsJsonArray();
        if (array.size() < count) {
            return null;
        }
        float[] out = new float[count];
        for (int i = 0; i < count; i++) {
            JsonElement item = array.get(i);
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isNumber()) {
                return null;
            }
            out[i] = item.getAsFloat();
        }
        return out;
    }
}
