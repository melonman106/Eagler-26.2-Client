package com.melonman106.vbvclient;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.BufferedReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;

/**
 * OptiFine-style custom entity models: a resource pack can ship {@code optifine/cem/<entity>.jem}
 * (and {@code .jpm} part files) to replace the model of an entity, including its animation formulas
 * (this is what the "Fresh Animations" pack is made of).
 *
 * <p>How a model is put together follows the OptiFine format as implemented by Entity Model
 * Features (LGPL-3.0, https://github.com/Traben-0/Entity_Model_Features, used as a behavioural
 * reference only): every top-level JEM part is attached as a child of the vanilla part it names; unless
 * it says {@code attach} the vanilla boxes of that part are removed and the vanilla child parts the
 * pack does not mention are hidden. Submodels become child parts. Custom UV boxes only draw the faces
 * they give UVs for. After the vanilla model has posed itself, the pack's animation lines run and write into the
 * parts ({@link VBVCemRig}).</p>
 *
 * <p>Not supported (ignored, vanilla behaviour kept): {@code .properties} variant rules, random entity
 * textures, per-part textures, armor / layer models (only each entity's main model), sprites, leash and
 * shadow settings, and any animation input the game's entity state does not carry (it reads as 0).</p>
 *
 * <p>The hooks are {@code EntityModelSet.bakeLayer}, {@code EntityRenderDispatcher.onResourceManagerReload},
 * {@code TextureManager.getTexture} and {@code ModelFeatureRenderer.prepareModel} (see patch_cem_hooks.py).
 * Anything unexpected switches that model back to the vanilla one and logs why.</p>
 */
public final class VBVCem {
    private static final String LOG = "[VBV CEM] ";
    private static final String CEM_DIR = "optifine/cem/";
    private static final long FRAME_GAP_NANOS = 4_000_000L;

    /** Vanilla default texture of each entity whose renderer uses a single fixed texture. */
    private static final Map<String, String> VANILLA_TEXTURES = new HashMap<>();

    static {
        VANILLA_TEXTURES.put("armor_stand", "textures/entity/armorstand/armorstand.png");
        VANILLA_TEXTURES.put("creeper", "textures/entity/creeper/creeper.png");
        VANILLA_TEXTURES.put("skeleton", "textures/entity/skeleton/skeleton.png");
        VANILLA_TEXTURES.put("enderman", "textures/entity/enderman/enderman.png");
        VANILLA_TEXTURES.put("spider", "textures/entity/spider/spider.png");
        VANILLA_TEXTURES.put("iron_golem", "textures/entity/iron_golem/iron_golem.png");
        VANILLA_TEXTURES.put("snow_golem", "textures/entity/snow_golem/snow_golem.png");
        VANILLA_TEXTURES.put("blaze", "textures/entity/blaze/blaze.png");
        VANILLA_TEXTURES.put("villager", "textures/entity/villager/villager.png");
        VANILLA_TEXTURES.put("witch", "textures/entity/witch/witch.png");
        VANILLA_TEXTURES.put("silverfish", "textures/entity/silverfish/silverfish.png");
        VANILLA_TEXTURES.put("endermite", "textures/entity/endermite/endermite.png");
        VANILLA_TEXTURES.put("phantom", "textures/entity/phantom/phantom.png");
        VANILLA_TEXTURES.put("bat", "textures/entity/bat/bat.png");
        VANILLA_TEXTURES.put("sheep", "textures/entity/sheep/sheep.png");
    }

    private static boolean enabled = true;
    private static boolean animations = true;
    private static ResourceManager manager;
    /** Entity name -> parsed model, or null when the pack has none (so the lookup happens once). */
    private static final Map<String, VBVCemJem> models = new HashMap<>();
    private static final Map<Identifier, Identifier> redirects = new HashMap<>();
    private static final Set<String> reported = new HashSet<>();
    /** Baked root part -> its animation rig. */
    private static final Map<ModelPart, VBVCemRig> rigs = new IdentityHashMap<>();

    private static long lastCall;
    private static long frameStart;
    private static int frameCounter;
    private static float frameTime = 0.016F;

    private VBVCem() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static void toggle() {
        enabled = !enabled;
    }

    public static boolean isAnimationsEnabled() {
        return animations;
    }

    public static void setAnimationsEnabled(boolean value) {
        animations = value;
    }

    public static void toggleAnimations() {
        animations = !animations;
    }

    /** Called at the start of every entity renderer rebuild (resource reload). */
    public static void reload(ResourceManager resourceManager) {
        manager = resourceManager;
        models.clear();
        redirects.clear();
        reported.clear();
        rigs.clear();
    }

    /** Called by {@code TextureManager.getTexture}; maps a vanilla entity texture to the pack's JEM texture. */
    public static Identifier redirect(Identifier id) {
        if (redirects.isEmpty()) {
            return id;
        }
        Identifier target = redirects.get(id);
        return target != null ? target : id;
    }

    /** Called by {@code EntityModelSet.bakeLayer} in place of {@code layer.bakeRoot()}. */
    public static ModelPart bake(ModelLayerLocation id, LayerDefinition layer) {
        if (enabled && manager != null) {
            String name = null;
            try {
                name = entityName(id);
                if (name != null) {
                    VBVCemJem jem = modelFor(name);
                    if (jem != null && !jem.parts.isEmpty()) {
                        return bakeWith(name, jem, layer);
                    }
                }
            } catch (Throwable error) {
                // One broken pack model must not take the other entities down: fall back to the
                // vanilla model for this entity only and remember not to retry it this reload.
                if (name != null) {
                    models.put(name, null);
                    String vanilla = VANILLA_TEXTURES.get(name);
                    if (vanilla != null) {
                        redirects.remove(Identifier.withDefaultNamespace(vanilla));
                    }
                }
                System.err.println(LOG + "ignored the custom model for " + name + ": " + error);
            }
        }
        return layer.bakeRoot();
    }

    /**
     * Called by {@code ModelFeatureRenderer.prepareModel} right after the model posed itself for an
     * entity: runs the pack's animation lines for that model, if it has any.
     */
    public static void animate(Model<?> model, Object state) {
        if (rigs.isEmpty() || !enabled || !animations) {
            return;
        }
        VBVCemRig rig = rigs.get(model.root());
        if (rig == null || rig.broken) {
            return;
        }
        try {
            long now = System.nanoTime();
            if (now - lastCall > FRAME_GAP_NANOS) {
                frameCounter++;
                float seconds = frameStart == 0L ? 0.016F : (now - frameStart) / 1.0e9F;
                frameTime = Math.max(0.001F, Math.min(0.25F, seconds));
                frameStart = now;
            }
            lastCall = now;
            rig.fill(state, frameTime, frameCounter);
            rig.run();
        } catch (Throwable error) {
            rig.broken = true;
            System.err.println(LOG + "stopped the animations of one model after an error: " + error);
        }
    }

    private static String entityName(ModelLayerLocation id) {
        if (!"main".equals(id.layer()) || !"minecraft".equals(id.model().getNamespace())) {
            return null;
        }
        return id.model().getPath();
    }

    private static VBVCemJem modelFor(String name) {
        if (models.containsKey(name)) {
            return models.get(name);
        }
        VBVCemJem model = null;
        JsonObject json = readJson(CEM_DIR + name + ".jem");
        if (json != null) {
            try {
                model = VBVCemJem.parse(json, VBVCem::loadJpm);
                if (model.texW <= 0 || model.texH <= 0) {
                    System.out.println(LOG + name + ".jem has no textureSize and is ignored");
                    model = null;
                }
            } catch (RuntimeException error) {
                System.err.println(LOG + name + ".jem could not be parsed and is ignored: " + error);
                model = null;
            }
        }
        models.put(name, model);
        if (model != null) {
            registerTexture(name, model);
        }
        return model;
    }

    private static JsonObject loadJpm(String reference) {
        String ref = reference.trim();
        if (ref.startsWith("./")) {
            ref = ref.substring(2);
        }
        if (!ref.endsWith(".jpm")) {
            ref = ref + ".jpm";
        }
        String path = ref.startsWith("optifine/") ? ref : CEM_DIR + ref;
        return readJson(path);
    }

    private static JsonObject readJson(String path) {
        Optional<Resource> resource = manager.getResource(Identifier.withDefaultNamespace(path));
        if (resource.isEmpty()) {
            return null;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            return GsonHelper.parse(reader);
        } catch (Exception error) {
            System.err.println(LOG + path + " could not be read: " + error);
            return null;
        }
    }

    private static void registerTexture(String name, VBVCemJem model) {
        String vanilla = VANILLA_TEXTURES.get(name);
        if (vanilla == null || model.texture == null) {
            return;
        }
        String texture = model.texture.trim();
        if (texture.isEmpty()) {
            return;
        }
        Identifier custom;
        try {
            if (texture.indexOf(':') >= 0) {
                custom = Identifier.parse(texture);
            } else if (texture.startsWith("./")) {
                custom = Identifier.withDefaultNamespace(CEM_DIR + texture.substring(2));
            } else if (texture.indexOf('/') < 0) {
                custom = Identifier.withDefaultNamespace(CEM_DIR + texture);
            } else {
                custom = Identifier.withDefaultNamespace(texture);
            }
            if (!custom.getPath().endsWith(".png")) {
                custom = custom.withPath(custom.getPath() + ".png");
            }
        } catch (RuntimeException error) {
            return;
        }
        if (manager.getResource(custom).isEmpty()) {
            report("texture-" + name, "texture " + custom + " named by " + name + ".jem was not found; keeping the vanilla texture");
            return;
        }
        redirects.put(Identifier.withDefaultNamespace(vanilla), custom);
        System.out.println(LOG + name + ": texture " + vanilla + " -> " + custom);
    }

    private static void report(String key, String message) {
        if (reported.add(key)) {
            System.out.println(LOG + message);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Binding a parsed model onto a vanilla layer
    // ---------------------------------------------------------------------------------------

    private static ModelPart bakeWith(String name, VBVCemJem jem, LayerDefinition layer) {
        final Binding binding = new Binding(name, jem);
        final MeshDefinition[] captured = new MeshDefinition[1];
        layer.apply(mesh -> {
            MeshDefinition copy = mesh.transformed(pose -> pose);
            binding.install(copy.getRoot());
            captured[0] = copy;
            return copy;
        });
        ModelPart root = LayerDefinition.create(captured[0], jem.texW, jem.texH).bakeRoot();
        binding.patchFaces(root);
        binding.registerRig(root);
        return root;
    }

    /** Everything that is known while one model is being put together. */
    private static final class Binding {
        final String entity;
        final VBVCemJem jem;
        /** Joined path of the parts a custom box list sits in -> boxes with their own UVs ("path#index"). */
        final Map<String, VBVCemJem.Box> patches = new HashMap<>();
        /** Custom part id -> names from the root down to it. */
        final Map<String, List<String>> customPaths = new HashMap<>();
        /** OptiFine part name -> names from the root down to the vanilla part it replaces. */
        final Map<String, List<String>> vanillaPaths = new HashMap<>();
        /** Copy-on-write copies of the vanilla definitions on the paths that were changed. */
        final Map<String, PartDefinition> copies = new HashMap<>();
        int skippedParts;

        Binding(String entity, VBVCemJem jem) {
            this.entity = entity;
            this.jem = jem;
        }

        void install(PartDefinition root) {
            // 1. Which vanilla part does each top-level JEM part belong to? (before anything is changed)
            Set<String> specified = new HashSet<>();
            Map<VBVCemJem.Part, List<String>> targets = new java.util.LinkedHashMap<>();
            for (VBVCemJem.Part part : jem.parts) {
                List<String> path = locate(root, part.part);
                if (path == null) {
                    skippedParts++;
                    report("part-" + entity + "-" + part.part, entity + ".jem: no part named '" + part.part + "' in the vanilla model, skipped");
                    continue;
                }
                targets.put(part, path);
                vanillaPaths.putIfAbsent(part.part, path);
                specified.add(path.isEmpty() ? "root" : path.get(path.size() - 1));
            }

            // 2. Change the vanilla definitions (on copies) and add the custom parts.
            Set<String> cleaned = new HashSet<>();
            for (Map.Entry<VBVCemJem.Part, List<String>> entry : targets.entrySet()) {
                VBVCemJem.Part part = entry.getKey();
                List<String> path = entry.getValue();
                String key = String.join("/", path);
                PartDefinition target = copyOf(root, path);
                if (!part.attach && cleaned.add(key)) {
                    if (!path.isEmpty()) {
                        // Drop the vanilla boxes of this part, keep its pose and children.
                        PartDefinition parent = copyOf(root, path.subList(0, path.size() - 1));
                        target = parent.addOrReplaceChild(path.get(path.size() - 1), CubeListBuilder.create(), poseOf(target));
                        copies.put(key, target);
                    }
                    // Vanilla child parts the pack does not mention disappear with it.
                    List<String> hide = new ArrayList<>();
                    for (Map.Entry<String, PartDefinition> child : target.getChildren()) {
                        if (!specified.contains(child.getKey()) && !child.getKey().startsWith("EMF_")) {
                            hide.add(child.getKey());
                        }
                    }
                    for (String childName : hide) {
                        target.clearChild(childName).clearRecursively();
                    }
                }
                addCustom(target, path, part);
            }
        }

        private void addCustom(PartDefinition parent, List<String> parentPath, VBVCemJem.Part part) {
            List<String> path = new ArrayList<>(parentPath);
            path.add(part.id);
            PartPose pose = new PartPose(part.x, part.y, part.z, part.xRot, part.yRot, part.zRot, part.scale, part.scale, part.scale);
            PartDefinition definition = parent.addOrReplaceChild(part.id, boxes(part, path), pose);
            customPaths.putIfAbsent(part.id, path);
            for (VBVCemJem.Part child : part.children) {
                addCustom(definition, path, child);
            }
        }

        private CubeListBuilder boxes(VBVCemJem.Part part, List<String> path) {
            CubeListBuilder builder = CubeListBuilder.create();
            String key = "/" + String.join("/", path);
            int index = 0;
            for (VBVCemJem.Box box : part.boxes) {
                builder.texOffs(box.u, box.v).mirror(box.standard && part.mirrorU)
                    .addBox(box.x, box.y, box.z, box.w, box.h, box.d, new CubeDeformation(box.growX, box.growY, box.growZ));
                if (!box.standard) {
                    patches.put(key + "#" + index, box);
                }
                index++;
            }
            return builder;
        }

        /** The copy of the vanilla definition at a path, made once; the shared originals are never changed. */
        private PartDefinition copyOf(PartDefinition root, List<String> path) {
            if (path.isEmpty()) {
                return root;
            }
            String key = String.join("/", path);
            PartDefinition known = copies.get(key);
            if (known != null) {
                return known;
            }
            PartDefinition parent = copyOf(root, path.subList(0, path.size() - 1));
            String name = path.get(path.size() - 1);
            PartDefinition copy = parent.getChild(name).transformed(pose -> pose);
            parent.addOrReplaceChild(name, copy);
            copies.put(key, copy);
            return copy;
        }

        /** Finds the vanilla part an OptiFine part name stands for; null when it does not exist. */
        private List<String> locate(PartDefinition root, String optifineName) {
            if ("root".equals(optifineName)) {
                return new ArrayList<>();
            }
            String mapped = VBVCemNames.vanilla(entity, optifineName);
            List<String> path = find(root, mapped);
            if (path == null && !mapped.equals(optifineName)) {
                path = find(root, optifineName);
            }
            if (path == null) {
                for (String alias : aliases(optifineName)) {
                    path = find(root, alias);
                    if (path != null) {
                        break;
                    }
                }
            }
            return path;
        }

        /** Applies the custom face UVs (and hides the faces without one) on the baked cubes. */
        void patchFaces(ModelPart root) {
            if (patches.isEmpty()) {
                return;
            }
            final int w = jem.texW;
            final int h = jem.texH;
            root.visit(new PoseStack(), (pose, partPath, cubeIndex, cube) -> {
                VBVCemJem.Box box = patches.get(partPath + "#" + cubeIndex);
                if (box != null) {
                    patchCube(cube, box, w, h);
                }
            });
        }

        void registerRig(ModelPart root) {
            Map<String, ModelPart> named = new HashMap<>();
            named.put("root", root);
            for (Map.Entry<String, List<String>> entry : vanillaPaths.entrySet()) {
                ModelPart part = walk(root, entry.getValue());
                if (part != null) {
                    named.putIfAbsent(entry.getKey(), part);
                }
            }
            for (Map.Entry<String, List<String>> entry : customPaths.entrySet()) {
                ModelPart part = walk(root, entry.getValue());
                if (part != null) {
                    named.putIfAbsent(entry.getKey(), part);
                }
            }
            List<String[]> lines = new ArrayList<>();
            for (VBVCemJem.Part part : jem.parts) {
                lines.addAll(part.animations);
            }
            VBVCemRig rig = null;
            if (!lines.isEmpty()) {
                rig = new VBVCemRig(entity, named, root.createPartLookup(), lines);
                rigs.put(root, rig);
            }
            System.out.println(LOG + entity + ".jem: " + customPaths.size() + " custom parts, "
                + (jem.parts.size() - skippedParts) + "/" + jem.parts.size() + " vanilla parts replaced, "
                + (rig == null ? 0 : rig.lineCount()) + " animation lines"
                + (rig != null && rig.skippedCount() > 0 ? " (" + rig.skippedCount() + " skipped)" : ""));
        }
    }

    private static ModelPart walk(ModelPart root, List<String> path) {
        ModelPart current = root;
        for (String name : path) {
            if (!current.hasChild(name)) {
                return null;
            }
            current = current.getChild(name);
        }
        return current;
    }

    /** Breadth-first search for a part by name; returns the names from the root down to it. */
    private static List<String> find(PartDefinition root, String name) {
        ArrayDeque<PartDefinition> queue = new ArrayDeque<>();
        ArrayDeque<List<String>> paths = new ArrayDeque<>();
        queue.add(root);
        paths.add(new ArrayList<>());
        while (!queue.isEmpty()) {
            PartDefinition current = queue.poll();
            List<String> currentPath = paths.poll();
            for (Map.Entry<String, PartDefinition> child : current.getChildren()) {
                List<String> childPath = new ArrayList<>(currentPath);
                childPath.add(child.getKey());
                if (child.getKey().equals(name)) {
                    return childPath;
                }
                queue.add(child.getValue());
                paths.add(childPath);
            }
        }
        return null;
    }

    private static PartPose poseOf(PartDefinition definition) {
        final PartPose[] out = new PartPose[1];
        definition.transformed(pose -> {
            out[0] = pose;
            return pose;
        });
        return out[0];
    }

    /** Older names that some resource packs and OptiFine tables use, tried when the usual lookup fails. */
    private static String[] aliases(String name) {
        switch (name) {
            case "headwear": return new String[]{"hat"};
            case "right": return new String[]{"right_body_stick"};
            case "left": return new String[]{"left_body_stick"};
            case "waist": return new String[]{"shoulder_stick"};
            case "base": return new String[]{"base_plate"};
            case "leg1": return new String[]{"right_hind_leg"};
            case "leg2": return new String[]{"left_hind_leg"};
            case "leg3": return new String[]{"right_front_leg"};
            case "leg4": return new String[]{"left_front_leg"};
            default: return new String[0];
        }
    }

    // ---------------------------------------------------------------------------------------
    // Custom UV boxes
    // ---------------------------------------------------------------------------------------

    /**
     * Corner indices of the polygon drawn for each JEM face key (down, up, north, south, west, east).
     * The JEM keys are given for flipped y/x axes, which is how every exporter writes them, so "up" is
     * the box's lower-y face and "west" its higher-x face, as in Entity Model Features.
     */
    private static final int[][] FACE_CORNERS = {
        {7, 6, 2, 3}, // uvDown
        {0, 1, 5, 4}, // uvUp
        {1, 0, 3, 2}, // uvNorth
        {4, 5, 6, 7}, // uvSouth
        {5, 1, 2, 6}, // uvWest
        {0, 4, 7, 3}  // uvEast
    };
    private static final Direction[] FACE_FACING = {
        Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    /**
     * Replaces the six polygons of a baked cube: faces with a UV rectangle are drawn from it, every
     * other face collapses to nothing (all four vertices on one corner). The game's cube renderer
     * takes positions from the vertices' corner indices, so those are kept.
     */
    private static void patchCube(ModelPart.Cube cube, VBVCemJem.Box box, int texW, int texH) {
        if (cube.polygons.length != 6) {
            return;
        }
        ModelPart.Vertex[] corner = new ModelPart.Vertex[8];
        for (ModelPart.Polygon polygon : cube.polygons) {
            for (ModelPart.Vertex vertex : polygon.vertices()) {
                corner[vertex.corner()] = vertex;
            }
        }
        for (ModelPart.Vertex vertex : corner) {
            if (vertex == null) {
                return;
            }
        }
        for (int face = 0; face < 6; face++) {
            float[] rect = box.uv[face];
            ModelPart.Vertex[] vertices = new ModelPart.Vertex[4];
            if (rect == null) {
                for (int i = 0; i < 4; i++) {
                    vertices[i] = corner[0];
                }
                cube.polygons[face] = new ModelPart.Polygon(vertices, 0.0F, 0.0F, 0.0F, 0.0F, texW, texH, false, Direction.DOWN);
            } else {
                for (int i = 0; i < 4; i++) {
                    vertices[i] = corner[FACE_CORNERS[face][i]];
                }
                cube.polygons[face] = new ModelPart.Polygon(vertices, rect[0], rect[1], rect[2], rect[3], texW, texH, false, FACE_FACING[face]);
            }
        }
    }
}
