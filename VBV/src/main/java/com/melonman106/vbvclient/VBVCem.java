package com.melonman106.vbvclient;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.BufferedReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 * (and {@code .jpm} part files) to replace parts of an entity's model.
 *
 * <p>Supported: boxes (size add, texture offset, per-face UVs, mirrorTexture u), submodels, translate,
 * rotate, invertAxis, {@code .jpm} references, {@code attach}, and the JEM {@code texture}/{@code textureSize}.
 * Not supported (ignored, the vanilla behaviour is kept): {@code animations}, per-part textures,
 * {@code .properties} variant rules (name/nbt/biome...), random entity textures and sprites.</p>
 *
 * <p>The hooks are {@code EntityModelSet.bakeLayer}, {@code EntityRenderDispatcher.onResourceManagerReload}
 * and {@code TextureManager.getTexture} (see patch_cem_hooks.py). Anything unexpected switches the
 * feature off for the rest of the session and the vanilla model is baked instead.</p>
 */
public final class VBVCem {
    private static final String LOG = "[VBV CEM] ";
    private static final String CEM_DIR = "optifine/cem/";
    private static final Direction[] POLYGON_FACING = {
        Direction.DOWN, Direction.UP, Direction.WEST, Direction.NORTH, Direction.EAST, Direction.SOUTH
    };

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
    private static ResourceManager manager;
    /** Entity name -> parsed model, or null when the pack has none (so the lookup happens once). */
    private static final Map<String, VBVCemModel> models = new HashMap<>();
    private static final Map<Identifier, Identifier> redirects = new HashMap<>();
    private static final Set<String> reported = new HashSet<>();

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

    /** Called at the start of every entity renderer rebuild (resource reload). */
    public static void reload(ResourceManager resourceManager) {
        manager = resourceManager;
        models.clear();
        redirects.clear();
        reported.clear();
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
                    VBVCemModel model = modelFor(name);
                    if (model != null && !model.parts.isEmpty()) {
                        return bakeWith(name, model, layer);
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

    private static String entityName(ModelLayerLocation id) {
        if (!"main".equals(id.layer()) || !"minecraft".equals(id.model().getNamespace())) {
            return null;
        }
        return id.model().getPath();
    }

    private static VBVCemModel modelFor(String name) {
        if (models.containsKey(name)) {
            return models.get(name);
        }
        VBVCemModel model = null;
        JsonObject json = readJson(CEM_DIR + name + ".jem");
        if (json != null) {
            try {
                model = VBVCemModel.parse(json, VBVCem::loadJpm);
            } catch (RuntimeException error) {
                System.err.println(LOG + name + ".jem could not be parsed and is ignored: " + error);
            }
        }
        models.put(name, model);
        if (model != null) {
            System.out.println(LOG + "loaded " + name + ".jem (" + model.parts.size() + " parts)");
            registerTexture(name, model);
        }
        return model;
    }

    private static JsonObject loadJpm(String reference) {
        String ref = reference.trim();
        if (ref.startsWith("./")) {
            ref = ref.substring(2);
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

    private static void registerTexture(String name, VBVCemModel model) {
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

    private static ModelPart bakeWith(String name, VBVCemModel model, LayerDefinition layer) {
        final Map<String, VBVCemModel.Box> patches = new HashMap<>();
        final MeshDefinition[] captured = new MeshDefinition[1];
        LayerDefinition applied = layer.apply(mesh -> {
            MeshDefinition copy = mesh.transformed(pose -> pose);
            for (VBVCemModel.Part part : model.parts) {
                install(name, copy.getRoot(), part, patches);
            }
            captured[0] = copy;
            return copy;
        });
        LayerDefinition result = model.texW > 0 ? LayerDefinition.create(captured[0], model.texW, model.texH) : applied;
        ModelPart root = result.bakeRoot();
        if (model.texW > 0 && !patches.isEmpty()) {
            patchFaces(root, patches, model.texW, model.texH);
        }
        return root;
    }

    private static void install(String entity, PartDefinition root, VBVCemModel.Part part, Map<String, VBVCemModel.Box> patches) {
        List<String> path = null;
        for (String candidate : aliases(part.name)) {
            path = find(root, candidate);
            if (path != null) {
                break;
            }
        }
        if (path == null) {
            report("part-" + entity + "-" + part.name, entity + ".jem: no part named '" + part.name + "' in the vanilla model, skipped");
            return;
        }

        // Copy the path down to the target so the shared vanilla definitions are never modified,
        // and add up the ancestors' pivots to express the JEM's absolute pivot relative to them.
        PartDefinition parent = root;
        float parentX = 0.0F;
        float parentY = 0.0F;
        float parentZ = 0.0F;
        StringBuilder pathText = new StringBuilder();
        for (int i = 0; i < path.size() - 1; i++) {
            String segment = path.get(i);
            PartDefinition copy = parent.getChild(segment).transformed(pose -> pose);
            parent.addOrReplaceChild(segment, copy);
            PartPose pose = poseOf(copy);
            parentX += pose.x();
            parentY += pose.y();
            parentZ += pose.z();
            parent = copy;
            pathText.append('/').append(segment);
        }
        String targetName = path.get(path.size() - 1);
        pathText.append('/').append(targetName);
        String targetPath = pathText.toString();
        PartPose vanillaPose = poseOf(parent.getChild(targetName));

        if (part.attach) {
            // Keep the vanilla boxes and add the JEM ones next to them.
            float vx = parentX + vanillaPose.x();
            float vy = parentY + vanillaPose.y();
            float vz = parentZ + vanillaPose.z();
            PartDefinition target = parent.getChild(targetName);
            String extra = "vbv_attach_" + part.name;
            PartDefinition attached = target.addOrReplaceChild(
                extra, boxes(part, targetPath + "/" + extra, patches),
                PartPose.offsetAndRotation(part.absX - vx, part.absY - vy, part.absZ - vz, part.xRot, part.yRot, part.zRot)
            );
            addChildren(attached, part, targetPath + "/" + extra, patches);
            return;
        }

        PartDefinition target = parent.addOrReplaceChild(
            targetName, boxes(part, targetPath, patches),
            PartPose.offsetAndRotation(part.x - parentX, part.y - parentY, part.z - parentZ, part.xRot, part.yRot, part.zRot)
        );
        addChildren(target, part, targetPath, patches);
    }

    private static void addChildren(PartDefinition target, VBVCemModel.Part part, String targetPath, Map<String, VBVCemModel.Box> patches) {
        int index = 0;
        for (VBVCemModel.Part child : part.children) {
            String label = child.id != null ? child.id : (child.name != null ? child.name : "sub" + index);
            String childName = "vbv_" + label;
            String childPath = targetPath + "/" + childName;
            PartDefinition built = target.addOrReplaceChild(
                childName, boxes(child, childPath, patches),
                PartPose.offsetAndRotation(child.x, child.y, child.z, child.xRot, child.yRot, child.zRot)
            );
            addChildren(built, child, childPath, patches);
            index++;
        }
    }

    private static CubeListBuilder boxes(VBVCemModel.Part part, String path, Map<String, VBVCemModel.Box> patches) {
        CubeListBuilder builder = CubeListBuilder.create();
        int index = 0;
        for (VBVCemModel.Box box : part.boxes) {
            builder.texOffs(box.u, box.v).mirror(box.mirror)
                .addBox(box.x, box.y, box.z, box.w, box.h, box.d, new CubeDeformation(box.grow));
            if (box.hasFaceUv()) {
                patches.put(path + "#" + index, box);
            }
            index++;
        }
        return builder;
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

    /** OptiFine's part names -> the names used by the 26.x models (first one found wins). */
    private static String[] aliases(String name) {
        switch (name) {
            case "headwear": return new String[]{"headwear", "hat"};
            case "right": return new String[]{"right", "right_body_stick"};
            case "left": return new String[]{"left", "left_body_stick"};
            case "waist": return new String[]{"waist", "shoulder_stick"};
            case "base": return new String[]{"base", "base_plate"};
            case "leg1": return new String[]{"leg1", "right_hind_leg"};
            case "leg2": return new String[]{"leg2", "left_hind_leg"};
            case "leg3": return new String[]{"leg3", "right_front_leg"};
            case "leg4": return new String[]{"leg4", "left_front_leg"};
            default: return new String[]{name};
        }
    }

    /** Applies per-face UV rectangles to cubes baked from boxes that declared them. */
    private static void patchFaces(ModelPart root, Map<String, VBVCemModel.Box> patches, int texW, int texH) {
        root.visit(new PoseStack(), (pose, partPath, cubeIndex, cube) -> {
            VBVCemModel.Box box = patches.get(partPath + "#" + cubeIndex);
            if (box == null || cube.polygons.length != 6) {
                return;
            }
            boolean mirror = box.mirror;
            for (int i = 0; i < 6; i++) {
                float[] rect = box.faceUv[i];
                if (rect == null) {
                    continue;
                }
                ModelPart.Polygon old = cube.polygons[i];
                ModelPart.Vertex[] vertices = old.vertices().clone();
                if (mirror) {
                    // The Polygon constructor reverses mirrored vertices; undo that before rebuilding.
                    for (int a = 0, b = vertices.length - 1; a < b; a++, b--) {
                        ModelPart.Vertex swap = vertices[a];
                        vertices[a] = vertices[b];
                        vertices[b] = swap;
                    }
                }
                cube.polygons[i] = new ModelPart.Polygon(vertices, rect[0], rect[1], rect[2], rect[3], texW, texH, mirror, POLYGON_FACING[i]);
            }
        });
    }
}
