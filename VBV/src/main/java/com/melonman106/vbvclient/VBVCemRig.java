package com.melonman106.vbvclient;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.FelineRenderState;
import net.minecraft.client.renderer.entity.state.FoxRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PandaRenderState;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.client.renderer.entity.state.ZombifiedPiglinRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The animation half of a custom entity model: the expression lines of the {@code .jem}/{@code .jpm}
 * compiled once, and run after the vanilla model has posed itself, the same order OptiFine and
 * Entity Model Features use. Lines write straight into the {@link ModelPart} fields (translate,
 * rotate, scale, visibility) or into {@code var.*} / {@code varb.*} variables.
 *
 * <p>Inputs come from the entity's render state and, when the render state carries a reference to it
 * (see patch_cem_hooks.py), from the live entity: held items, using / blocking / climbing / crawling,
 * swing arm, handedness, movement input, water depth above, flying and so on. {@code nbt()} tests are
 * answered by {@link VBVCemNbt}. Any other input reads as 0 / false.</p>
 *
 * <p>{@code var.*} variables live in a {@link VarStore} that every model of one entity family shares (the
 * player, its armor layers, cape and elytra all read the same values) and that keeps a separate set of
 * values for every entity, so several players or cows on screen do not disturb one another's smoothing.</p>
 */
final class VBVCemRig {
    private static final String LOG = "[VBV CEM] ";

    // Inputs, filled from the render state before every run.
    static final String[] INPUTS = {
        "limb_swing", "limb_speed", "age", "head_yaw", "head_pitch", "swing_progress", "hurt_time", "death_time",
        "health", "max_health", "pos_x", "pos_y", "pos_z", "rot_x", "rot_y", "id", "frame_time", "frame_counter",
        "time", "is_child", "is_riding", "is_in_water", "is_on_ground", "is_hurt", "is_alive", "is_sitting",
        "is_aggressive", "is_sprinting", "is_sneaking", "is_burning", "is_invisible", "is_swimming", "is_tamed",
        "is_in_lava", "is_on_shoulder", "is_ridden", "is_glowing", "is_wet", "is_sleeping", "player_pos_x",
        "player_pos_y", "player_pos_z", "rule_index", "dimension", "is_on_head", "is_in_hand", "is_in_item_frame",
        "is_in_ground", "is_in_gui", "is_in_first_person_hand", "is_paused", "is_flying", "is_gliding",
        "is_jumping", "is_leashed", "is_baby", "is_angry", "health_percent", "swing_progress_main", "pitch", "yaw",
        "is_first_person_hand", "is_right_handed", "is_using_item", "is_swinging_right_arm", "is_swinging_left_arm",
        "is_holding_item_right", "is_holding_item_left", "is_blocking", "is_climbing", "is_crawling",
        "fluid_depth_up", "move_forward", "move_strafing"
    };
    private static final int LIMB_SWING = idx("limb_swing");
    private static final int LIMB_SPEED = idx("limb_speed");
    private static final int AGE = idx("age");
    private static final int HEAD_YAW = idx("head_yaw");
    private static final int HEAD_PITCH = idx("head_pitch");
    private static final int SWING_PROGRESS = idx("swing_progress");
    private static final int HURT_TIME = idx("hurt_time");
    private static final int DEATH_TIME = idx("death_time");
    private static final int HEALTH = idx("health");
    private static final int MAX_HEALTH = idx("max_health");
    private static final int POS_X = idx("pos_x");
    private static final int POS_Y = idx("pos_y");
    private static final int POS_Z = idx("pos_z");
    private static final int ROT_X = idx("rot_x");
    private static final int ROT_Y = idx("rot_y");
    private static final int ID = idx("id");
    private static final int FRAME_TIME = idx("frame_time");
    private static final int FRAME_COUNTER = idx("frame_counter");
    private static final int TIME = idx("time");
    private static final int IS_CHILD = idx("is_child");
    private static final int IS_RIDING = idx("is_riding");
    private static final int IS_IN_WATER = idx("is_in_water");
    private static final int IS_ON_GROUND = idx("is_on_ground");
    private static final int IS_HURT = idx("is_hurt");
    private static final int IS_ALIVE = idx("is_alive");
    private static final int IS_SITTING = idx("is_sitting");
    private static final int IS_AGGRESSIVE = idx("is_aggressive");
    private static final int IS_SPRINTING = idx("is_sprinting");
    private static final int IS_SNEAKING = idx("is_sneaking");
    private static final int IS_BURNING = idx("is_burning");
    private static final int IS_INVISIBLE = idx("is_invisible");
    private static final int IS_SWIMMING = idx("is_swimming");
    private static final int IS_TAMED = idx("is_tamed");
    private static final int IS_BABY = idx("is_baby");
    private static final int IS_ANGRY = idx("is_angry");
    private static final int HEALTH_PERCENT = idx("health_percent");
    private static final int PITCH = idx("pitch");
    private static final int YAW = idx("yaw");
    private static final int IS_SLEEPING = idx("is_sleeping");
    private static final int IS_GLIDING = idx("is_gliding");
    private static final int IS_FLYING = idx("is_flying");
    private static final int IS_GLOWING = idx("is_glowing");
    private static final int IS_JUMPING = idx("is_jumping");
    private static final int IS_PAUSED = idx("is_paused");
    private static final int IS_IN_LAVA = idx("is_in_lava");
    private static final int IS_WET = idx("is_wet");
    private static final int IS_RIDDEN = idx("is_ridden");
    private static final int IS_RIGHT_HANDED = idx("is_right_handed");
    private static final int IS_USING_ITEM = idx("is_using_item");
    private static final int IS_SWINGING_RIGHT = idx("is_swinging_right_arm");
    private static final int IS_SWINGING_LEFT = idx("is_swinging_left_arm");
    private static final int IS_HOLDING_RIGHT = idx("is_holding_item_right");
    private static final int IS_HOLDING_LEFT = idx("is_holding_item_left");
    private static final int IS_BLOCKING = idx("is_blocking");
    private static final int IS_CLIMBING = idx("is_climbing");
    private static final int IS_CRAWLING = idx("is_crawling");
    private static final int FLUID_DEPTH_UP = idx("fluid_depth_up");
    private static final int MOVE_FORWARD = idx("move_forward");
    private static final int MOVE_STRAFING = idx("move_strafing");

    private static int idx(String name) {
        for (int i = 0; i < INPUTS.length; i++) {
            if (INPUTS[i].equals(name)) {
                return i;
            }
        }
        throw new IllegalStateException("missing input " + name);
    }

    private static final Map<String, float[]> GLOBAL_VARS = new HashMap<>();

    /**
     * The {@code var.*} / {@code varb.*} slots of one entity family. The slot arrays are what the compiled
     * lines read and write; {@link #enter} swaps the values of the entity being animated in and out, so
     * each entity keeps its own variables.
     */
    static final class VarStore {
        private final Map<String, float[]> slots = new HashMap<>();
        private final ArrayList<float[]> slotList = new ArrayList<>();
        // Int-keyed maps and a small array pool keep this off the allocator: it runs for every
        // animated entity on every frame, in a browser heap that is capped at about 2 GB.
        private final Int2ObjectOpenHashMap<float[]> saved = new Int2ObjectOpenHashMap<>();
        private final Int2IntOpenHashMap lastSeen = new Int2IntOpenHashMap();
        private final ArrayDeque<float[]> pool = new ArrayDeque<>();
        private int owner = Integer.MIN_VALUE;

        float[] slot(String key) {
            float[] slot = slots.get(key);
            if (slot == null) {
                slot = new float[1];
                slots.put(key, slot);
                slotList.add(slot);
            }
            return slot;
        }

        /** Makes the slots hold the variables of this entity. */
        void enter(int entityId, int frame) {
            int count = slotList.size();
            if (count == 0) {
                // A model with no var.* lines needs no per-entity state at all.
                owner = entityId;
                return;
            }
            lastSeen.put(entityId, frame);
            if (entityId == owner) {
                return;
            }
            if (owner != Integer.MIN_VALUE) {
                float[] snapshot = take(count);
                for (int i = 0; i < count; i++) {
                    snapshot[i] = slotList.get(i)[0];
                }
                recycle(saved.put(owner, snapshot), count);
            }
            float[] next = saved.get(entityId);
            for (int i = 0; i < count; i++) {
                slotList.get(i)[0] = next != null && i < next.length ? next[i] : 0.0F;
            }
            owner = entityId;
            if (lastSeen.size() > 64) {
                // Forget entities that have not been drawn for a while (unloaded, far away or dead).
                var entries = lastSeen.int2IntEntrySet().iterator();
                while (entries.hasNext()) {
                    var entry = entries.next();
                    int id = entry.getIntKey();
                    if (id != owner && frame - entry.getIntValue() > 900) {
                        recycle(saved.remove(id), -1);
                        entries.remove();
                    }
                }
            }
        }

        /** A snapshot array of exactly this many slots, reused once it is no longer stored. */
        private float[] take(int count) {
            while (!pool.isEmpty()) {
                float[] candidate = pool.poll();
                if (candidate.length == count) return candidate;
            }
            return new float[count];
        }

        /** Returns a replaced snapshot to the pool; a length change (new slots) simply drops it. */
        private void recycle(float[] array, int count) {
            if (array == null || pool.size() >= 64) return;
            if (count >= 0 && array.length != count) return;
            pool.add(array);
        }
    }

    private final String entity;
    private final VarStore store;
    private Entity current;
    private boolean needsFluidDepth;
    /** True for an armor layer model: it takes over the pose of the entity's animated main model first. */
    boolean armor;
    private final float[] in = new float[INPUTS.length];
    private final Map<String, Integer> inputIndex = new HashMap<>();
    private final Map<String, ModelPart> parts = new HashMap<>();
    private final Function<String, ModelPart> vanillaLookup;
    private final Line[] lines;
    private final Map<String, Boolean> warned = new HashMap<>();
    private int skipped;
    boolean broken;

    private interface Writer {
        void set(float value);
    }

    private static final class Line {
        final Writer target;
        final VBVCemExpr.Num expression;
        final boolean bool;

        Line(Writer target, VBVCemExpr.Num expression, boolean bool) {
            this.target = target;
            this.expression = expression;
            this.bool = bool;
        }
    }

    /**
     * @param named parts addressable from expressions: OptiFine part names of the replaced vanilla parts
     *              and the {@code EMF_} ids of the custom parts
     * @param lookup finds any other vanilla part by its vanilla name (may return null)
     * @param source the {key, expression} lines, in the order they must run
     */
    VBVCemRig(String entity, VarStore store, Map<String, ModelPart> named, Function<String, ModelPart> lookup, java.util.List<String[]> source) {
        this.entity = entity;
        this.store = store;
        this.parts.putAll(named);
        this.vanillaLookup = lookup;
        for (int i = 0; i < INPUTS.length; i++) {
            inputIndex.put(INPUTS[i], i);
        }
        java.util.List<Line> compiled = new java.util.ArrayList<>();
        for (String[] line : source) {
            try {
                Line built = compile(line[0], line[1]);
                if (built != null) {
                    compiled.add(built);
                }
            } catch (RuntimeException error) {
                skipped++;
                warn("line-" + line[0], "skipped the animation line '" + line[0] + "': " + error.getMessage());
            }
        }
        this.lines = compiled.toArray(new Line[0]);
    }

    int lineCount() {
        return lines.length;
    }

    int skippedCount() {
        return skipped;
    }

    private void warn(String key, String message) {
        if (warned.size() < 40 && warned.put(key, Boolean.TRUE) == null) {
            System.out.println(LOG + entity + ": " + message);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Compiling
    // ---------------------------------------------------------------------------------------

    private Line compile(String key, String expression) {
        if (key.startsWith("render.")) {
            return null; // shadow size, leash offsets ...: not supported, harmless to drop
        }
        VBVCemExpr.Num value = VBVCemExpr.compile(expression, this::resolve,
            (path, test) -> VBVCemNbt.create(path, test, () -> current));
        boolean bool = key.startsWith("varb.") || key.startsWith("global_varb.");
        if (bool || key.startsWith("var.") || key.startsWith("global_var.")) {
            float[] slot = slot(key);
            return new Line(v -> slot[0] = Float.isNaN(v) ? 0.0F : v, value, bool);
        }
        int dot = key.lastIndexOf('.');
        if (dot <= 0) {
            throw new IllegalArgumentException("not a variable or part property: " + key);
        }
        String property = key.substring(dot + 1);
        ModelPart part = part(key.substring(0, dot));
        if (part == null) {
            warn("part-" + key.substring(0, dot), "no part named '" + key.substring(0, dot) + "' for the animation line '" + key + "'");
            return null;
        }
        Writer writer = writer(part, property);
        if (writer == null) {
            throw new IllegalArgumentException("unknown part property: " + property);
        }
        return new Line(writer, value, "visible".equals(property) || "visible_boxes".equals(property));
    }

    private float[] slot(String key) {
        if (key.startsWith("global_")) {
            float[] slot = GLOBAL_VARS.get(key);
            if (slot == null) {
                slot = new float[1];
                GLOBAL_VARS.put(key, slot);
            }
            return slot;
        }
        return store.slot(key);
    }

    private VBVCemExpr.Num resolve(String name) {
        Integer input = inputIndex.get(name);
        if (input != null) {
            int index = input;
            if (index == FLUID_DEPTH_UP) {
                needsFluidDepth = true;
            }
            return () -> in[index];
        }
        if ("abilities.flying".equals(name)) {
            return () -> in[IS_FLYING];
        }
        if (name.startsWith("var.") || name.startsWith("varb.") || name.startsWith("global_var.") || name.startsWith("global_varb.")) {
            float[] slot = slot(name);
            return () -> slot[0];
        }
        if (name.startsWith("render.")) {
            return () -> 0.0F;
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            ModelPart part = part(name.substring(0, dot));
            VBVCemExpr.Num reader = part == null ? null : reader(part, name.substring(dot + 1));
            if (reader != null) {
                return reader;
            }
            if (part == null) {
                warn("read-" + name, "'" + name + "' names no part, reads as 0");
            }
            return () -> 0.0F;
        }
        warn("input-" + name, "'" + name + "' is not available in this game, reads as 0");
        return () -> 0.0F;
    }

    private ModelPart part(String id) {
        ModelPart part = parts.get(id);
        if (part == null) {
            part = parts.get("EMF_" + id);
        }
        if (part == null && id.indexOf(':') >= 0) {
            String last = id.substring(id.lastIndexOf(':') + 1);
            part = parts.get(last);
            if (part == null) {
                part = parts.get("EMF_" + last);
            }
        }
        if (part == null && vanillaLookup != null) {
            part = vanillaLookup.apply(VBVCemNames.vanilla(entity, id));
        }
        return part;
    }

    private static VBVCemExpr.Num reader(ModelPart p, String property) {
        switch (property) {
            case "tx": return () -> p.x;
            case "ty": return () -> p.y;
            case "tz": return () -> p.z;
            case "rx": return () -> p.xRot;
            case "ry": return () -> p.yRot;
            case "rz": return () -> p.zRot;
            case "sx": return () -> p.xScale;
            case "sy": return () -> p.yScale;
            case "sz": return () -> p.zScale;
            case "visible": return () -> p.visible ? 1.0F : 0.0F;
            case "visible_boxes": return () -> p.skipDraw ? 0.0F : 1.0F;
            default: return null;
        }
    }

    private static Writer writer(ModelPart p, String property) {
        switch (property) {
            case "tx": return v -> { if (Float.isFinite(v)) p.x = v; };
            case "ty": return v -> { if (Float.isFinite(v)) p.y = v; };
            case "tz": return v -> { if (Float.isFinite(v)) p.z = v; };
            case "rx": return v -> { if (Float.isFinite(v)) p.xRot = v; };
            case "ry": return v -> { if (Float.isFinite(v)) p.yRot = v; };
            case "rz": return v -> { if (Float.isFinite(v)) p.zRot = v; };
            case "sx": return v -> { if (Float.isFinite(v)) p.xScale = v; };
            case "sy": return v -> { if (Float.isFinite(v)) p.yScale = v; };
            case "sz": return v -> { if (Float.isFinite(v)) p.zScale = v; };
            case "visible": return v -> p.visible = v != 0.0F;
            case "visible_boxes": return v -> p.skipDraw = v == 0.0F;
            default: return null;
        }
    }

    // ---------------------------------------------------------------------------------------
    // Running
    // ---------------------------------------------------------------------------------------

    /** Copies what the render state knows into the inputs; everything else stays 0. */
    void fill(Object state, float frameTime, int frameCounter) {
        float[] v = in;
        // Re-zero first so a value an earlier entity type set never leaks into this one.
        java.util.Arrays.fill(v, 0.0F);
        v[FRAME_TIME] = frameTime;
        v[FRAME_COUNTER] = frameCounter & 0xFFFFFF;
        v[HEALTH] = 20.0F;
        v[MAX_HEALTH] = 20.0F;
        v[HEALTH_PERCENT] = 1.0F;
        v[IS_ALIVE] = 1.0F;
        current = null;
        if (!(state instanceof EntityRenderState)) {
            store.enter(0, frameCounter);
            return;
        }
        EntityRenderState e = (EntityRenderState) state;
        Entity live = e.vbvEntity;
        store.enter(live == null ? 0 : live.getId(), frameCounter);
        v[AGE] = e.ageInTicks;
        v[TIME] = e.ageInTicks;
        v[POS_X] = (float) e.x;
        v[POS_Y] = (float) e.y;
        v[POS_Z] = (float) e.z;
        v[ID] = e.entityType == null ? 0.0F : (float) (e.entityType.hashCode() & 0xFFFF);
        v[IS_BURNING] = bool(e.displayFireAnimation);
        v[IS_INVISIBLE] = bool(e.isInvisible);
        v[IS_SNEAKING] = bool(e.isDiscrete);
        v[IS_GLOWING] = bool(e.appearsGlowing());
        if (state instanceof LivingEntityRenderState) {
            LivingEntityRenderState l = (LivingEntityRenderState) state;
            v[LIMB_SWING] = l.walkAnimationPos;
            v[LIMB_SPEED] = l.walkAnimationSpeed;
            v[HEAD_YAW] = l.yRot;
            v[HEAD_PITCH] = l.xRot;
            v[YAW] = l.yRot;
            v[PITCH] = l.xRot;
            v[ROT_Y] = (float) Math.toRadians(l.bodyRot);
            v[DEATH_TIME] = l.deathTime;
            v[IS_ALIVE] = l.deathTime > 0.0F ? 0.0F : 1.0F;
            v[IS_CHILD] = bool(l.isBaby);
            v[IS_BABY] = bool(l.isBaby);
            v[IS_IN_WATER] = bool(l.isInWater);
            v[IS_ON_GROUND] = bool(!l.isInWater);
            v[IS_HURT] = bool(l.hasRedOverlay);
            v[HURT_TIME] = l.hasRedOverlay ? 10.0F : 0.0F;
        }
        if (state instanceof ArmedEntityRenderState) {
            v[SWING_PROGRESS] = ((ArmedEntityRenderState) state).attackTime;
        }
        if (state instanceof HumanoidRenderState) {
            HumanoidRenderState h = (HumanoidRenderState) state;
            v[IS_RIDING] = bool(h.isPassenger);
            v[IS_SNEAKING] = bool(h.isCrouching);
            v[IS_SWIMMING] = bool(h.isVisuallySwimming);
            v[IS_GLIDING] = bool(h.isFallFlying);
        }
        if (live != null) {
            current = live;
            try {
                fillFromEntity(live, v);
            } catch (RuntimeException error) {
                // A value the live entity cannot give (unloaded level, odd subclass) stays what the state said.
            }
        }
        if (state instanceof WolfRenderState) {
            v[IS_SITTING] = bool(((WolfRenderState) state).isSitting);
            v[IS_ANGRY] = bool(((WolfRenderState) state).isAngry);
            v[IS_AGGRESSIVE] = v[IS_ANGRY];
        } else if (state instanceof FelineRenderState) {
            FelineRenderState f = (FelineRenderState) state;
            v[IS_SITTING] = bool(f.isSitting);
            v[IS_SPRINTING] = bool(f.isSprinting);
        } else if (state instanceof FoxRenderState) {
            v[IS_SITTING] = bool(((FoxRenderState) state).isSitting);
            v[IS_SLEEPING] = bool(((FoxRenderState) state).isSleeping);
        } else if (state instanceof PandaRenderState) {
            v[IS_SITTING] = bool(((PandaRenderState) state).isSitting);
        } else if (state instanceof ZombieRenderState) {
            v[IS_AGGRESSIVE] = bool(((ZombieRenderState) state).isAggressive);
        } else if (state instanceof ZombifiedPiglinRenderState) {
            v[IS_AGGRESSIVE] = bool(((ZombifiedPiglinRenderState) state).isAggressive);
        } else if (state instanceof SkeletonRenderState) {
            v[IS_AGGRESSIVE] = bool(((SkeletonRenderState) state).isAggressive);
        } else if (state instanceof IllagerRenderState) {
            v[IS_AGGRESSIVE] = bool(((IllagerRenderState) state).isAggressive);
        }
    }

    private static float bool(boolean value) {
        return value ? 1.0F : 0.0F;
    }

    /** Values that only the live entity knows. Anything the entity does not have is left as it was. */
    private void fillFromEntity(Entity entity, float[] v) {
        v[ID] = entity.getId();
        v[IS_ON_GROUND] = bool(entity.onGround());
        v[IS_IN_WATER] = bool(entity.isInWater());
        v[IS_IN_LAVA] = bool(entity.isInLava());
        v[IS_WET] = bool(entity.isInWaterOrRain());
        v[IS_RIDING] = bool(entity.isPassenger());
        v[IS_RIDDEN] = bool(entity.isVehicle());
        v[IS_PAUSED] = bool(Minecraft.getInstance().isPaused());
        if (needsFluidDepth) {
            v[FLUID_DEPTH_UP] = fluidDepthUp(entity);
        }
        if (!(entity instanceof LivingEntity)) {
            return;
        }
        LivingEntity living = (LivingEntity) entity;
        v[HEALTH] = living.getHealth();
        v[MAX_HEALTH] = living.getMaxHealth();
        v[HEALTH_PERCENT] = living.getMaxHealth() > 0.0F ? living.getHealth() / living.getMaxHealth() : 1.0F;
        v[HURT_TIME] = living.hurtTime;
        v[IS_SLEEPING] = bool(living.isSleeping());
        v[IS_CLIMBING] = bool(living.onClimbable());
        v[IS_USING_ITEM] = bool(living.isUsingItem());
        v[IS_BLOCKING] = bool(living.isBlocking());
        v[IS_CRAWLING] = bool(living.isVisuallyCrawling());
        v[IS_JUMPING] = bool(!living.onGround() && living.getDeltaMovement().y > 0.0D);
        v[MOVE_FORWARD] = living.zza;
        v[MOVE_STRAFING] = living.xxa;
        HumanoidArm main = living.getMainArm();
        v[IS_RIGHT_HANDED] = bool(main == HumanoidArm.RIGHT);
        // EMF's meaning of is_swinging_*_arm, which the packs are written against: true while the arm is
        // swinging OR the player is using an item with it (bow draw, eating, shield block). Packs such as
        // Fresh Animations pose a blocking shield arm from these two, so a swing-only version leaves the
        // shield arm and the shield turned the wrong way round.
        v[IS_SWINGING_RIGHT] = 0.0F;
        v[IS_SWINGING_LEFT] = 0.0F;
        boolean usingItem = living.isUsingItem();
        if (v[SWING_PROGRESS] != 0.0F || usingItem) {
            InteractionHand hand = usingItem ? living.getUsedItemHand()
                : (living.swingingArm != null ? living.swingingArm : InteractionHand.MAIN_HAND);
            HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? main : main.getOpposite();
            v[IS_SWINGING_RIGHT] = bool(arm == HumanoidArm.RIGHT);
            v[IS_SWINGING_LEFT] = bool(arm == HumanoidArm.LEFT);
        }
        v[IS_HOLDING_RIGHT] = bool(!living.getItemHeldByArm(HumanoidArm.RIGHT).isEmpty());
        v[IS_HOLDING_LEFT] = bool(!living.getItemHeldByArm(HumanoidArm.LEFT).isEmpty());
        if (living instanceof Player) {
            v[IS_FLYING] = bool(((Player) living).getAbilities().flying);
        }
    }

    /** How many fluid blocks stack up from the block the entity's feet are in. */
    private static float fluidDepthUp(Entity entity) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        pos.set(entity.getX(), entity.getY(), entity.getZ());
        int depth = 0;
        for (int i = 0; i < 8; i++) {
            if (entity.level().getFluidState(pos).isEmpty()) {
                break;
            }
            depth++;
            pos.move(Direction.UP);
        }
        return depth;
    }

    /** Runs every line in order. */
    void run() {
        for (Line line : lines) {
            float value = line.expression.get();
            line.target.set(line.bool ? (value != 0.0F ? 1.0F : 0.0F) : value);
        }
    }
}
