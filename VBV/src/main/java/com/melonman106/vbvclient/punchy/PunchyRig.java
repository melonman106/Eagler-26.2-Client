package com.melonman106.vbvclient.punchy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The bone skeleton Punchy animations are authored against (a Bedrock {@code geometry.player} file whose bones are
 * {@code camera_animation > camera > body > right_arm/left_arm > item_* > itemgrip_*}), and the maths that turns an
 * animation pose into placements in first-person camera space.
 *
 * <h3>Conventions</h3>
 * <ul>
 *   <li>The geometry is Y-up with the player's right at -X and "forward" at -Z, in pixels. Vanilla's
 *       {@code ModelPart}s are the same layout with Y flipped (Y down). Everything here works in that
 *       Java layout: pivots and animation positions are converted with {@code y -> -y}.</li>
 *   <li>Rotation numbers (rest pose and animation) are applied exactly like {@code ModelPart} does:
 *       {@code Rz * Ry * Rx}, angles in degrees, added together. This is what makes {@code right_arm}'s
 *       rest rotation of about {@code (-100, -6, -9)} point the arm forward and its item upward.</li>
 *   <li>A bone's world transform is
 *       {@code parent * T(position) * T(pivot) * R(rest + rotation) * S(scale) * T(-pivot)}.</li>
 *   <li>Camera space is vanilla's first-person space: X right, Y up, -Z forward, in blocks, origin at the eye.
 *       The rig's {@code camera} bone pivot is the eye; the Java layout maps to it by {@code (x, y, z) -> (-x, -y, z)}.</li>
 * </ul>
 */
public final class PunchyRig {
    public static final String CAMERA = "camera";
    public static final String RIGHT_ARM = "right_arm";
    public static final String LEFT_ARM = "left_arm";
    public static final String ITEM_RIGHT = "item_right";
    public static final String ITEM_LEFT = "item_left";
    public static final String GRIP_RIGHT = "itemgrip_right";
    public static final String GRIP_LEFT = "itemgrip_left";

    private static final float[] FLIP = Mat4.scaling(-1.0F, -1.0F, 1.0F);
    private static final float PX = 1.0F / 16.0F;

    private static final class Bone {
        final String name;
        final String parent;
        /** Pivot in Java layout (Y flipped), pixels. */
        final float[] pivot;
        /** Rest rotation, degrees. */
        final float[] rest;
        int parentIndex = -1;

        Bone(String name, String parent, float[] pivot, float[] rest) {
            this.name = name;
            this.parent = parent;
            this.pivot = pivot;
            this.rest = rest;
        }
    }

    /** Parents come before children. */
    private final Bone[] bones;
    private final Map<String, Integer> index = new HashMap<>();
    private final float[] cameraOrigin;

    private PunchyRig(List<Bone> list) {
        // Order so every parent precedes its children, whatever the file's order.
        Map<String, Bone> byName = new HashMap<>();
        for (Bone b : list) byName.put(b.name, b);
        List<Bone> ordered = new ArrayList<>();
        java.util.Set<String> done = new java.util.HashSet<>();
        for (Bone b : list) visit(b, byName, done, ordered, 0);
        this.bones = ordered.toArray(new Bone[0]);
        for (int i = 0; i < bones.length; i++) index.put(bones[i].name, i);
        for (Bone b : bones) b.parentIndex = b.parent == null ? -1 : index.getOrDefault(b.parent, -1);
        Integer cam = index.get(CAMERA);
        this.cameraOrigin = cam == null ? new float[]{0.0F, -28.0F, 0.0F} : bones[cam].pivot.clone();
    }

    private static void visit(Bone b, Map<String, Bone> byName, java.util.Set<String> done, List<Bone> out, int depth) {
        if (done.contains(b.name) || depth > 64) return;
        Bone parent = b.parent == null ? null : byName.get(b.parent);
        if (parent != null) visit(parent, byName, done, out, depth + 1);
        if (done.add(b.name)) out.add(b);
    }

    public boolean has(String bone) {
        return index.containsKey(bone);
    }

    public int boneCount() {
        return bones.length;
    }

    /** True when the rig has everything first-person rendering needs. */
    public boolean usable() {
        return has(RIGHT_ARM) && has(LEFT_ARM) && has(GRIP_RIGHT) && has(GRIP_LEFT);
    }

    // ------------------------------------------------------------------------------------------------- solving

    /** World matrices (Java layout, pixels) for every bone, in the rig's own order. {@code poses} may lack bones. */
    public float[][] solve(Map<String, BonePose> poses) {
        float[][] world = new float[bones.length][];
        for (int i = 0; i < bones.length; i++) {
            Bone b = bones[i];
            BonePose p = poses == null ? null : poses.get(b.name);
            float[] local = Mat4.identity();
            if (p != null) local = Mat4.mul(local, Mat4.translation(p.pos[0], -p.pos[1], p.pos[2]));
            local = Mat4.mul(local, Mat4.translation(b.pivot[0], b.pivot[1], b.pivot[2]));
            float rx = b.rest[0] + (p == null ? 0.0F : p.rot[0]);
            float ry = b.rest[1] + (p == null ? 0.0F : p.rot[1]);
            float rz = b.rest[2] + (p == null ? 0.0F : p.rot[2]);
            local = Mat4.mul(local, Mat4.rotationZYX(rx, ry, rz));
            if (p != null) local = Mat4.mul(local, Mat4.scaling(p.scale[0], p.scale[1], p.scale[2]));
            local = Mat4.mul(local, Mat4.translation(-b.pivot[0], -b.pivot[1], -b.pivot[2]));
            world[i] = b.parentIndex < 0 ? local : Mat4.mul(world[b.parentIndex], local);
        }
        return world;
    }

    /**
     * The matrix that places something modelled around {@code bone}'s pivot into camera space.
     * Local axes are the bone's own (Java layout). {@code inBlocks} says whether local coordinates are blocks
     * (what {@code ModelPart} cubes use) or model pixels. Output is blocks, X right / Y up / -Z forward.
     */
    public float[] attachment(float[][] world, String bone, boolean inBlocks) {
        Integer i = index.get(bone);
        if (i == null) return Mat4.identity();
        Bone b = bones[i];
        float[] m = Mat4.mul(FLIP, Mat4.scaling(PX, PX, PX));
        m = Mat4.mul(m, Mat4.translation(-cameraOrigin[0], -cameraOrigin[1], -cameraOrigin[2]));
        m = Mat4.mul(m, world[i]);
        m = Mat4.mul(m, Mat4.translation(b.pivot[0], b.pivot[1], b.pivot[2]));
        if (inBlocks) m = Mat4.mul(m, Mat4.scaling(16.0F, 16.0F, 16.0F));
        return m;
    }

    /** A rig point (pixels, Java layout, as given by {@link #pivotJava}) in camera space after posing. */
    public float[] toCamera(float[][] world, String bone, float x, float y, float z) {
        Integer i = index.get(bone);
        if (i == null) return new float[]{0, 0, 0};
        float[] w = Mat4.apply(world[i], x, y, z);
        return new float[]{-(w[0] - cameraOrigin[0]) * PX, -(w[1] - cameraOrigin[1]) * PX, (w[2] - cameraOrigin[2]) * PX};
    }

    /** A bone's pivot in Java layout, pixels. */
    public float[] pivotJava(String bone) {
        Integer i = index.get(bone);
        return i == null ? new float[]{0, 0, 0} : bones[i].pivot.clone();
    }

    // ------------------------------------------------------------------------------------------------ loading

    /** Parses a Bedrock geometry file (format 1.12.0: {@code minecraft:geometry[0].bones}). Returns null if unusable. */
    public static PunchyRig parse(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return null;
            JsonElement geometry = root.getAsJsonObject().get("minecraft:geometry");
            if (geometry == null || !geometry.isJsonArray() || geometry.getAsJsonArray().isEmpty()) return null;
            JsonObject first = geometry.getAsJsonArray().get(0).getAsJsonObject();
            JsonElement boneArray = first.get("bones");
            if (boneArray == null || !boneArray.isJsonArray()) return null;
            List<Bone> list = new ArrayList<>();
            for (JsonElement e : boneArray.getAsJsonArray()) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                if (!o.has("name")) continue;
                float[] pivot = vec(o.get("pivot"));
                float[] rest = vec(o.get("rotation"));
                list.add(new Bone(o.get("name").getAsString(),
                    o.has("parent") ? o.get("parent").getAsString() : null,
                    new float[]{pivot[0], -pivot[1], pivot[2]}, rest));
            }
            PunchyRig rig = new PunchyRig(list);
            return rig.usable() ? rig : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static float[] vec(JsonElement element) {
        float[] out = new float[3];
        if (element != null && element.isJsonArray()) {
            JsonArray a = element.getAsJsonArray();
            for (int i = 0; i < 3 && i < a.size(); i++) {
                if (a.get(i).isJsonPrimitive() && a.get(i).getAsJsonPrimitive().isNumber()) out[i] = a.get(i).getAsFloat();
            }
        }
        return out;
    }

    /**
     * The fallback skeleton, used when the pack carries no {@code punchy/geo/player.geo.json}. Only the bones
     * first-person placement needs: the camera chain, both arms and their item attachment points. The numbers are
     * the shoulder pivots and rest rotations the animations are authored around.
     */
    public static PunchyRig defaultRig() {
        List<Bone> list = new ArrayList<>();
        list.add(new Bone("camera_animation", null, new float[]{0, -28, 0}, new float[3]));
        list.add(new Bone(CAMERA, "camera_animation", new float[]{0, -28, 0}, new float[3]));
        list.add(new Bone("body", CAMERA, new float[]{0, -12, 0}, new float[3]));
        list.add(new Bone(RIGHT_ARM, "body", new float[]{-11, -16.6F, -7}, new float[]{-100.03983F, -6.46188F, -8.56324F}));
        list.add(new Bone(ITEM_RIGHT, RIGHT_ARM, new float[]{-12, -8.5F, -6}, new float[3]));
        list.add(new Bone(GRIP_RIGHT, ITEM_RIGHT, new float[]{-12, -8.5F, -6}, new float[3]));
        list.add(new Bone(LEFT_ARM, "body", new float[]{11, -16.6F, -7}, new float[]{-100.0398F, 6.4619F, 8.5632F}));
        list.add(new Bone(ITEM_LEFT, LEFT_ARM, new float[]{12, -8.5F, -6}, new float[3]));
        list.add(new Bone(GRIP_LEFT, ITEM_LEFT, new float[]{12, -8.5F, -6}, new float[3]));
        return new PunchyRig(list);
    }
}
