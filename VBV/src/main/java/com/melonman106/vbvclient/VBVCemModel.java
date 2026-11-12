package com.melonman106.vbvclient;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * OptiFine custom entity model (.jem / .jpm) parser. It turns the JSON into parts and boxes that
 * are already in vanilla model space, so the game-side binder ({@link VBVCem}) only has to build
 * {@code PartDefinition}s from them. It depends on Gson only, so the maths can be tested alone.
 *
 * <p>Conventions (worked out from real Blockbench-exported packs, see VBV/README.md):</p>
 * <ul>
 *   <li>JEM space is vanilla model space shifted by (0, 24, 0): y = 0 is the model root.</li>
 *   <li>{@code invertAxis} flips the box coordinates of the listed axes ({@code -c - size}); the
 *       translate values are not flipped.</li>
 *   <li>Top-level parts: {@code translate} is the pivot, boxes are absolute.</li>
 *   <li>Submodels: boxes are relative to the submodel's own pivot. A submodel of a top-level part
 *       is placed from the model origin; deeper submodels are placed from their parent's pivot.
 *       For a flipped y axis the y translate counts upwards.</li>
 *   <li>Rotations are in degrees; for flipped axes they are mapped into vanilla's convention.</li>
 * </ul>
 */
final class VBVCemModel {
    /** Polygon order of vanilla's Cube: down, up, west, north, east, south. */
    static final int DOWN = 0;
    static final int UP = 1;
    static final int WEST = 2;
    static final int NORTH = 3;
    static final int EAST = 4;
    static final int SOUTH = 5;

    private static final String[] FACE_KEYS = {"uvDown", "uvUp", "uvNorth", "uvSouth", "uvWest", "uvEast"};
    private static final int MAX_DEPTH = 16;

    static final class Box {
        /** Minimum corner relative to the part pivot, vanilla model space. */
        float x, y, z, w, h, d;
        float grow;
        int u, v;
        boolean mirror;
        /** Vanilla polygon index -> {u0, v0, u1, v1} in texture pixels; null entries use the standard layout. */
        final float[][] faceUv = new float[6][];

        boolean hasFaceUv() {
            for (float[] face : faceUv) {
                if (face != null) {
                    return true;
                }
            }
            return false;
        }
    }

    static final class Part {
        String name;
        String id;
        /** Pose relative to the parent part (top level: absolute pivot in vanilla model space). */
        float x, y, z;
        /** Absolute pivot in vanilla model space. */
        float absX, absY, absZ;
        /** Radians, vanilla convention. */
        float xRot, yRot, zRot;
        boolean attach;
        boolean isTop;
        final List<Box> boxes = new ArrayList<>();
        final List<Part> children = new ArrayList<>();
    }

    int texW;
    int texH;
    String texture;
    final List<Part> parts = new ArrayList<>();

    /** @param loader resolves a {@code .jpm} reference to its JSON, or null when it cannot be read */
    static VBVCemModel parse(JsonObject root, Function<String, JsonObject> loader) {
        VBVCemModel model = new VBVCemModel();
        float[] size = floats(root, "textureSize", 2);
        if (size != null && size[0] >= 1.0F && size[1] >= 1.0F) {
            model.texW = Math.round(size[0]);
            model.texH = Math.round(size[1]);
        }
        model.texture = string(root, "texture");
        JsonElement models = root.get("models");
        if (models != null && models.isJsonArray()) {
            for (JsonElement entry : models.getAsJsonArray()) {
                if (!entry.isJsonObject()) {
                    continue;
                }
                JsonObject merged = merge(entry.getAsJsonObject(), loader);
                if (string(merged, "part") == null) {
                    continue;
                }
                model.parts.add(parsePart(merged, loader, null, 0));
            }
        }
        return model;
    }

    /** A {@code "model": "x.jpm"} entry takes its boxes and submodels from the .jpm; the entry's own fields win. */
    private static JsonObject merge(JsonObject entry, Function<String, JsonObject> loader) {
        String ref = string(entry, "model");
        if (ref == null) {
            return entry;
        }
        JsonObject sub = loader.apply(ref);
        if (sub == null) {
            return entry;
        }
        JsonObject merged = new JsonObject();
        for (Map.Entry<String, JsonElement> field : sub.entrySet()) {
            merged.add(field.getKey(), field.getValue());
        }
        for (Map.Entry<String, JsonElement> field : entry.entrySet()) {
            if (!"model".equals(field.getKey())) {
                merged.add(field.getKey(), field.getValue());
            }
        }
        return merged;
    }

    private static Part parsePart(JsonObject o, Function<String, JsonObject> loader, Part parent, int depth) {
        boolean top = parent == null;
        Part part = new Part();
        part.name = string(o, "part");
        part.id = string(o, "id");
        part.attach = bool(o, "attach");
        String invert = string(o, "invertAxis");
        boolean ix = invert != null && invert.indexOf('x') >= 0;
        boolean iy = invert != null && invert.indexOf('y') >= 0;
        boolean iz = invert != null && invert.indexOf('z') >= 0;
        float[] t = floats(o, "translate", 3);
        if (t == null) {
            t = new float[3];
        }
        float[] r = floats(o, "rotate", 3);
        if (r == null) {
            r = new float[3];
        }

        if (top) {
            part.absX = t[0];
            part.absY = t[1] + 24.0F;
            part.absZ = t[2];
            part.x = part.absX;
            part.y = part.absY;
            part.z = part.absZ;
        } else {
            boolean parentTop = parent.isTop;
            float baseX = parentTop ? 0.0F : parent.absX;
            float baseY = parentTop ? 24.0F : parent.absY;
            float baseZ = parentTop ? 0.0F : parent.absZ;
            part.absX = baseX + t[0];
            part.absY = baseY + (iy ? -t[1] : t[1]);
            part.absZ = baseZ + t[2];
            part.x = part.absX - parent.absX;
            part.y = part.absY - parent.absY;
            part.z = part.absZ - parent.absZ;
        }
        part.isTop = top;

        // A reflection flips the sense of rotations about the other axes: rotation vectors are
        // pseudo-vectors, so they pick up det(S) * S. For the usual "xy" this is (-rx, -ry, +rz).
        float sx = ix ? -1.0F : 1.0F;
        float sy = iy ? -1.0F : 1.0F;
        float sz = iz ? -1.0F : 1.0F;
        float det = sx * sy * sz;
        part.xRot = (float) Math.toRadians(det * sx * r[0]);
        part.yRot = (float) Math.toRadians(det * sy * r[1]);
        part.zRot = (float) Math.toRadians(det * sz * r[2]);

        boolean mirror = false;
        String mirrorTexture = string(o, "mirrorTexture");
        if (mirrorTexture != null && mirrorTexture.indexOf('u') >= 0) {
            mirror = true;
        }

        JsonElement boxes = o.get("boxes");
        if (boxes != null && boxes.isJsonArray()) {
            for (JsonElement element : boxes.getAsJsonArray()) {
                if (element.isJsonObject()) {
                    Box box = parseBox(element.getAsJsonObject(), part, top, ix, iy, iz, mirror);
                    if (box != null) {
                        part.boxes.add(box);
                    }
                }
            }
        }

        JsonElement submodels = o.get("submodels");
        if (submodels != null && submodels.isJsonArray() && depth < MAX_DEPTH) {
            for (JsonElement element : submodels.getAsJsonArray()) {
                if (element.isJsonObject()) {
                    JsonObject merged = merge(element.getAsJsonObject(), loader);
                    Part child = parsePart(merged, loader, part, depth + 1);
                    part.children.add(child);
                }
            }
        }
        return part;
    }

    private static Box parseBox(JsonObject b, Part part, boolean top, boolean ix, boolean iy, boolean iz, boolean mirror) {
        float[] c = floats(b, "coordinates", 6);
        if (c == null) {
            return null;
        }
        Box box = new Box();
        float lx = ix ? -c[0] - c[3] : c[0];
        float ly = iy ? -c[1] - c[4] : c[1];
        float lz = iz ? -c[2] - c[5] : c[2];
        if (top) {
            lx -= part.absX;
            ly = ly + 24.0F - part.absY;
            lz -= part.absZ;
        }
        box.x = lx;
        box.y = ly;
        box.z = lz;
        box.w = c[3];
        box.h = c[4];
        box.d = c[5];
        box.grow = number(b, "sizeAdd", 0.0F);
        box.mirror = mirror;
        float[] offset = floats(b, "textureOffset", 2);
        if (offset != null) {
            box.u = Math.round(offset[0]);
            box.v = Math.round(offset[1]);
        }
        for (int face = 0; face < FACE_KEYS.length; face++) {
            float[] rect = floats(b, FACE_KEYS[face], 4);
            if (rect == null) {
                continue;
            }
            int target = targetPolygon(face, ix, iy, iz);
            boolean reversed = (face == 0 || face == 1) && iy;
            box.faceUv[target] = reversed
                ? new float[]{rect[2], rect[3], rect[0], rect[1]}
                : rect;
        }
        return box;
    }

    /** Maps a JEM face key (down, up, north, south, west, east) to vanilla's polygon index. */
    private static int targetPolygon(int face, boolean ix, boolean iy, boolean iz) {
        switch (face) {
            case 0: return iy ? UP : DOWN;
            case 1: return iy ? DOWN : UP;
            case 2: return iz ? SOUTH : NORTH;
            case 3: return iz ? NORTH : SOUTH;
            case 4: return ix ? EAST : WEST;
            default: return ix ? WEST : EAST;
        }
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static boolean bool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
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
