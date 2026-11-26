package com.melonman106.vbvclient;

import com.melonman106.vbvclient.punchy.BonePose;
import com.melonman106.vbvclient.punchy.Mat4;
import com.melonman106.vbvclient.punchy.PunchyController;
import com.melonman106.vbvclient.punchy.PunchyLibrary;
import com.melonman106.vbvclient.punchy.PunchyRig;
import com.melonman106.vbvclient.punchy.PunchyRules;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.joml.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Punchy resource-pack support: first-person arm and item animations driven by a pack's Blockbench animation files.
 *
 * <p>The pack supplies {@code assets/minecraft/punchy/animations/*.animation.json} (the clips) and
 * {@code assets/minecraft/punchy/compat/**.json} (which clip plays for which action and item); an optional
 * {@code punchy/geo/player.geo.json} replaces the built-in arm rig. All parsing and maths live in the game-free
 * {@code punchy} package (plain-JVM tested); this class only reads the pack, collects the player's state and
 * draws the result through the vanilla arm model.
 *
 * <p>While switched on and a pack is loaded, {@link #submit} replaces vanilla's whole first-person arm-and-item
 * pose, except for maps (vanilla keeps drawing those) and an invisible player. When no clip is playing the rig's
 * rest pose is drawn, so an idle hand does not jump when an animation starts.
 */
public final class VBVPunchy {
    private static final String LOG = "[VBV Punchy] ";
    private static final int RIGHT = 0;
    private static final int LEFT = 1;

    /** Item offsets are in hundredths of a block, in the camera-aligned frame vanilla draws held items in. */
    public static final int OFFSET_MIN = -40;
    public static final int OFFSET_MAX = 40;

    private static boolean enabled = false;
    private static int offsetX = 0;
    private static int offsetY = 0;
    private static int offsetZ = 0;

    private static PunchyLibrary library = new PunchyLibrary();
    private static PunchyRules rules = new PunchyRules();
    private static PunchyRig rig = PunchyRig.defaultRig();
    private static boolean loaded = false;
    private static boolean customRig = false;
    private static String status = "No Punchy pack loaded";

    private static final PunchyController[] controllers = new PunchyController[2];
    private static final boolean[] attackDown = new boolean[2];
    private static final boolean[] useDown = new boolean[2];
    private static final long[] lastNanos = new long[2];
    private static final PunchyController.Input input = new PunchyController.Input();
    private static final Map<String, BonePose> posed = new HashMap<>();

    // The last stack's categories, so the lookup is not repeated every frame.
    private static Item categoryItem;
    private static List<String> categoryList = List.of("EMPTY_HAND");
    private static final List<String> EMPTY_HAND = List.of("EMPTY_HAND");

    private VBVPunchy() {}

    // ------------------------------------------------------------------------------------------------ settings

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static void toggle() {
        enabled = !enabled;
    }

    /** True when the renderer should use Punchy: switched on and a pack with clips and rules is loaded. */
    public static boolean isActive() {
        return enabled && loaded;
    }

    public static String status() {
        return status;
    }

    public static int getOffsetX() { return offsetX; }
    public static int getOffsetY() { return offsetY; }
    public static int getOffsetZ() { return offsetZ; }

    public static void setOffsets(int x, int y, int z) {
        offsetX = Math.max(OFFSET_MIN, Math.min(OFFSET_MAX, x));
        offsetY = Math.max(OFFSET_MIN, Math.min(OFFSET_MAX, y));
        offsetZ = Math.max(OFFSET_MIN, Math.min(OFFSET_MAX, z));
    }

    public static void resetOffsets() {
        offsetX = 0;
        offsetY = 0;
        offsetZ = 0;
    }

    // ----------------------------------------------------------------------------------------------- loading

    /** Called from the entity renderer dispatcher's reload (every resource reload): reads the pack's Punchy files. */
    public static void reload(ResourceManager manager) {
        try {
            PunchyLibrary lib = new PunchyLibrary();
            PunchyRules ruleSet = new PunchyRules();
            int files = 0;

            List<Map.Entry<Identifier, Resource>> animations = new ArrayList<>(
                manager.listResources("punchy/animations", id -> id.getPath().endsWith(".json")).entrySet());
            animations.sort((a, b) -> a.getKey().toString().compareTo(b.getKey().toString()));
            for (Map.Entry<Identifier, Resource> e : animations) {
                String text = read(e.getValue());
                if (text != null) {
                    lib.add(e.getKey().getPath(), text);
                    files++;
                }
            }

            // default_tuning.json first: the more specific compat files override it.
            List<Map.Entry<Identifier, Resource>> compat = new ArrayList<>(
                manager.listResources("punchy/compat", id -> id.getPath().endsWith(".json")).entrySet());
            compat.sort((a, b) -> {
                boolean da = a.getKey().getPath().endsWith("default_tuning.json");
                boolean db = b.getKey().getPath().endsWith("default_tuning.json");
                if (da != db) return da ? -1 : 1;
                return a.getKey().toString().compareTo(b.getKey().toString());
            });
            for (Map.Entry<Identifier, Resource> e : compat) {
                String text = read(e.getValue());
                if (text != null) ruleSet.add(e.getKey().getPath(), text);
            }

            PunchyRig geo = null;
            Optional<Resource> geoFile = manager.getResource(Identifier.withDefaultNamespace("punchy/geo/player.geo.json"));
            if (geoFile.isEmpty()) geoFile = manager.getResource(Identifier.fromNamespaceAndPath("punchy", "geo/player.geo.json"));
            if (geoFile.isPresent()) {
                String text = read(geoFile.get());
                if (text != null) geo = PunchyRig.parse(text);
            }

            library = lib;
            rules = ruleSet;
            customRig = geo != null;
            rig = geo != null ? geo : PunchyRig.defaultRig();
            loaded = lib.size() > 0 && !ruleSet.isEmpty();
            controllers[RIGHT] = null;
            controllers[LEFT] = null;
            categoryItem = null;
            if (loaded) {
                status = lib.size() + " animations, " + ruleSet.entryCount() + " item rules" + (customRig ? " (pack rig)" : "");
                if (!lib.problems().isEmpty() || !ruleSet.problems().isEmpty()) {
                    status += ", " + (lib.problems().size() + ruleSet.problems().size()) + " file(s) skipped";
                }
            } else {
                status = files == 0 ? "No Punchy pack loaded" : "Pack has no usable animations/rules";
            }
            System.out.println(LOG + status + " [" + files + " animation file(s)]");
            for (Map.Entry<String, String> p : lib.problems().entrySet()) System.err.println(LOG + p.getKey() + ": " + p.getValue());
            for (Map.Entry<String, String> p : ruleSet.problems().entrySet()) System.err.println(LOG + p.getKey() + ": " + p.getValue());
        } catch (Exception error) {
            loaded = false;
            status = "Failed to load: " + error;
            System.err.println(LOG + status);
        }
    }

    private static String read(Resource resource) {
        try (BufferedReader reader = resource.openAsReader()) {
            StringBuilder out = new StringBuilder();
            char[] buffer = new char[8192];
            int n;
            while ((n = reader.read(buffer)) > 0) out.append(buffer, 0, n);
            return out.toString();
        } catch (Exception error) {
            System.err.println(LOG + "could not read a pack file: " + error);
            return null;
        }
    }

    // ------------------------------------------------------------------------------------------------ rendering

    /**
     * Draws one hand (arm and held item) from the animation state. Returns false to let vanilla draw it.
     * Called from {@code ItemInHandRenderer.submitArmWithItem} before anything else is submitted.
     */
    public static boolean submit(ItemInHandRenderer renderer, AbstractClientPlayer player, InteractionHand hand,
                                 HumanoidArm arm, ItemStack stack, PoseStack poseStack,
                                 SubmitNodeCollector collector, int light) {
        if (!enabled || !loaded) return false;
        try {
            if (!stack.isEmpty() && stack.has(DataComponents.MAP_ID)) return false;
            if (player.isInvisible()) return false;
            boolean main = hand == InteractionHand.MAIN_HAND;
            // Vanilla draws nothing for an empty off hand either.
            if (stack.isEmpty() && !main) return true;

            int side = arm == HumanoidArm.LEFT ? LEFT : RIGHT;
            boolean rightArm = side == RIGHT;
            PunchyController controller = controllers[side];
            if (controller == null) {
                controller = new PunchyController(library, rules, rightArm);
                controllers[side] = controller;
            }
            long now = System.nanoTime();
            float dt = lastNanos[side] == 0L ? 0.0F : (now - lastNanos[side]) / 1.0E9F;
            lastNanos[side] = now;
            fillInput(player, hand, main, side, stack);
            controller.update(input, dt);
            for (String effect : controller.drainSounds()) playSound(effect);

            // The camera bones move the viewpoint in the original mod; here the viewpoint is the game's own,
            // so only the body and arm animation is kept.
            posed.clear();
            for (Map.Entry<String, BonePose> e : controller.poses().entrySet()) {
                String bone = e.getKey();
                if (!bone.equals(PunchyRig.CAMERA) && !bone.equals("camera_animation")) posed.put(bone, e.getValue());
            }
            float[][] world = rig.solve(posed);

            Minecraft minecraft = Minecraft.getInstance();
            AvatarRenderer<AbstractClientPlayer> avatar = minecraft.getEntityRenderDispatcher().getPlayerRenderer(player);
            PlayerModel model = avatar.getModel();
            ModelPart part = rightArm ? model.rightArm : model.leftArm;
            part.resetPose();
            part.visible = true;
            boolean sleeve = player.isModelPartShown(rightArm ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE);
            model.leftSleeve.visible = sleeve;
            model.rightSleeve.visible = sleeve;

            // The arm: vanilla's arm model, placed where the rig's arm bone is.
            float[] armMatrix = rig.attachment(world, rightArm ? PunchyRig.RIGHT_ARM : PunchyRig.LEFT_ARM, true);
            // ModelPart.render adds its own pivot translation first; take it back out so the pivot lands on the bone.
            armMatrix = Mat4.mul(armMatrix, Mat4.translation(-part.x / 16.0F, -part.y / 16.0F, -part.z / 16.0F));
            poseStack.pushPose();
            poseStack.mulPose(toMatrix(armMatrix));
            collector.submitModelPart(part, poseStack, RenderTypes.entityTranslucent(player.getSkin().body().texturePath()),
                light, OverlayTexture.NO_OVERLAY, null);
            poseStack.popPose();

            // The item: vanilla's own first-person item model in the frame of the rig's item grip.
            if (!stack.isEmpty() && !controller.itemHidden()) {
                float[] item = rig.attachment(world, rightArm ? PunchyRig.GRIP_RIGHT : PunchyRig.GRIP_LEFT, true);
                item = Mat4.mul(item, Mat4.scaling(-1.0F, -1.0F, 1.0F));
                // The pack's item axis is the grip's -Z; vanilla item models point up in their own frame.
                item = Mat4.mul(item, Mat4.rotationX(-90.0F));
                poseStack.pushPose();
                poseStack.mulPose(toMatrix(item));
                poseStack.translate(offsetX / 100.0F * (rightArm ? 1.0F : -1.0F), offsetY / 100.0F, offsetZ / 100.0F);
                renderer.renderItem(player, stack, rightArm ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                    poseStack, collector, light);
                poseStack.popPose();
            }
            return true;
        } catch (Exception error) {
            // Never break the frame: fall back to vanilla and stay off until the next reload.
            System.err.println(LOG + "rendering failed, switching Punchy off: " + error);
            enabled = false;
            status = "Switched off after an error: " + error;
            return false;
        }
    }

    private static void fillInput(AbstractClientPlayer player, InteractionHand hand, boolean main, int side, ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        PunchyController.Input in = input;
        in.itemId = stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        in.categories = categories(stack);

        boolean attack = main && mc.gui.screen() == null && mc.options.keyAttack.isDown();
        boolean use = main && mc.gui.screen() == null && mc.options.keyUse.isDown();
        in.attackPressed = attack && !attackDown[side];
        in.usePressed = use && !useDown[side];
        attackDown[side] = attack;
        useDown[side] = use;

        HitResult hit = mc.hitResult;
        boolean block = hit instanceof BlockHitResult && hit.getType() == HitResult.Type.BLOCK;
        // The game does not expose "is breaking a block", so: attack held on a block in survival.
        in.mining = attack && block && !player.isCreative() && !player.isUsingItem();

        in.using = player.isUsingItem() && player.getUsedItemHand() == hand;
        in.useKind = in.using ? useKind(stack) : "OTHER";
        in.crouching = player.isCrouching();
        in.criticalHit = player.fallDistance > 0.0F && !player.onGround() && !player.isInWater() && !player.onClimbable();
    }

    private static String useKind(ItemStack stack) {
        switch (stack.getUseAnimation().name()) {
            case "BOW": return "BOW";
            case "CROSSBOW": return "CROSSBOW";
            case "BLOCK": return "BLOCK";
            case "EAT": return "EAT";
            case "DRINK": return "DRINK";
            case "SPEAR":
            case "TRIDENT": return "SPEAR";
            default: return "OTHER";
        }
    }

    /** The compat categories a stack belongs to, least specific first. Rules for exact item ids are found by id. */
    private static List<String> categories(ItemStack stack) {
        if (stack.isEmpty()) return EMPTY_HAND;
        Item item = stack.getItem();
        if (item == categoryItem) return categoryList;
        List<String> out = new ArrayList<>(4);
        if (item instanceof BlockItem blockItem) {
            out.add("BLOCK");
            if (net.minecraft.world.level.block.Block.isShapeFullBlock(
                blockItem.getBlock().defaultBlockState().getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO))) {
                out.add("FULL_BLOCK");
            }
        }
        if (stack.is(ItemTags.SWORDS)) out.add("SWORD");
        if (stack.is(ItemTags.PICKAXES)) out.add("PICKAXE");
        if (stack.is(ItemTags.AXES)) out.add("AXE");
        if (stack.is(ItemTags.SHOVELS)) out.add("SHOVEL");
        if (stack.is(ItemTags.HOES)) out.add("HOE");
        if (stack.is(ItemTags.SPEARS) || item == Items.TRIDENT) out.add("SPEAR");
        if (item == Items.BOW) out.add("BOW");
        if (item == Items.CROSSBOW) out.add("CROSSBOW");
        if (item == Items.FISHING_ROD) out.add("FISHING_ROD");
        if (item == Items.ARROW) out.add("ARROW");
        if (item == Items.FLINT_AND_STEEL) out.add("FLINT");
        if (stack.is(ItemTags.BOATS)) out.add("BOAT");
        if (item == Items.CHEST) out.add("CHEST");
        if (item == Items.LANTERN || item == Items.SOUL_LANTERN) out.add("LANTERN");
        categoryItem = item;
        categoryList = List.copyOf(out);
        return categoryList;
    }

    private static void playSound(String effect) {
        try {
            Identifier id = Identifier.fromNamespaceAndPath("punchy", "animationssounds/" + effect);
            SoundManager sounds = Minecraft.getInstance().getSoundManager();
            if (sounds.getSoundEvent(id) == null) return;
            sounds.play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(id), 1.0F));
        } catch (Exception ignored) {
            // A missing sound must never interrupt rendering.
        }
    }

    /** Row-major {@code float[16]} (column vectors) to JOML, which takes its arguments column by column. */
    private static Matrix4f toMatrix(float[] a) {
        return new Matrix4f(
            a[0], a[4], a[8], a[12],
            a[1], a[5], a[9], a[13],
            a[2], a[6], a[10], a[14],
            a[3], a[7], a[11], a[15]);
    }
}
