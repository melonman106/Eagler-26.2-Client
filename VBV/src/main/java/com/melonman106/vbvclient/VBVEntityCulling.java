package com.melonman106.vbvclient;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BeaconBeamOwner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Entity Culling for Eaglercraft: skips drawing entities and block entities (chests, signs,
 * banners...) that are completely hidden behind solid blocks, so crowded farms, villages and
 * storage rooms cost much less to render.
 *
 * <p>For each candidate it casts a few rays from the camera to points on its bounding box through
 * the block grid. If every ray hits a fully solid block first, it is hidden. Results are cached for
 * a handful of frames and the number of block lookups per frame is capped, so the check itself stays
 * cheap. When in doubt it keeps drawing: close things, big things, glowing things, the player and
 * anything it could not finish checking are never culled. Entities still tick normally; only
 * rendering is skipped.</p>
 */
public final class VBVEntityCulling {
    /** Entities and chests closer than this are always drawn. */
    private static final double NEAR_DISTANCE = 10.0;
    private static final double NEAR_DISTANCE_SQR = NEAR_DISTANCE * NEAR_DISTANCE;
    /** Beyond this the ray walk would be long; just draw it. */
    private static final double FAR_DISTANCE_SQR = 96.0 * 96.0;
    /** Very large entities (dragon, big modded mobs) are never culled. */
    private static final double MAX_CULLED_SIZE = 6.0;
    /** Sample points sit this far outside the box so a sliver of visible edge keeps it alive. */
    private static final double MARGIN = 0.25;
    private static final int RECHECK_FRAMES = 6;
    private static final int STEPS_PER_FRAME = 3200;
    private static final int PURGE_INTERVAL_FRAMES = 600;
    private static final int PURGE_AGE_FRAMES = 240;

    private static final class Cached {
        boolean visible = true;
        int checkedFrame;
    }

    private static final Int2ObjectOpenHashMap<Cached> ENTITIES = new Int2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<Cached> BLOCK_ENTITIES = new Long2ObjectOpenHashMap<>();
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();

    private static boolean enabled = true;
    private static ClientLevel level;
    private static int frame;
    private static int stepsLeft;

    // Counters for the performance meter (last finished frame).
    private static int entitiesSeen;
    private static int entitiesCulled;
    private static int blockEntitiesSeen;
    private static int blockEntitiesCulled;
    private static int lastEntitiesSeen;
    private static int lastEntitiesCulled;
    private static int lastBlockEntitiesSeen;
    private static int lastBlockEntitiesCulled;

    private VBVEntityCulling() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            ENTITIES.clear();
            BLOCK_ENTITIES.clear();
        }
    }

    public static void toggle() {
        setEnabled(!enabled);
    }

    public static int lastEntitiesSeen() {
        return lastEntitiesSeen;
    }

    public static int lastEntitiesCulled() {
        return lastEntitiesCulled;
    }

    public static int lastBlockEntitiesSeen() {
        return lastBlockEntitiesSeen;
    }

    public static int lastBlockEntitiesCulled() {
        return lastBlockEntitiesCulled;
    }

    /** Called once per frame before entities are collected. */
    public static void beginFrame(ClientLevel current) {
        lastEntitiesSeen = entitiesSeen;
        lastEntitiesCulled = entitiesCulled;
        lastBlockEntitiesSeen = blockEntitiesSeen;
        lastBlockEntitiesCulled = blockEntitiesCulled;
        entitiesSeen = 0;
        entitiesCulled = 0;
        blockEntitiesSeen = 0;
        blockEntitiesCulled = 0;
        frame++;
        stepsLeft = STEPS_PER_FRAME;
        if (current != level) {
            level = current;
            ENTITIES.clear();
            BLOCK_ENTITIES.clear();
        } else if (frame % PURGE_INTERVAL_FRAMES == 0) {
            purge();
        }
    }

    /** True if the entity should be drawn. */
    public static boolean isVisible(Entity entity, double camX, double camY, double camZ) {
        if (!enabled || level == null || entity == null) {
            return true;
        }
        entitiesSeen++;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (entity == mc.player || entity == mc.getCameraEntity() || entity.isCurrentlyGlowing()
                || (mc.player != null && entity.hasIndirectPassenger(mc.player))) {
                return true;
            }
            AABB box = entity.getBoundingBox();
            if (box.getXsize() > MAX_CULLED_SIZE || box.getYsize() > MAX_CULLED_SIZE || box.getZsize() > MAX_CULLED_SIZE) {
                return true;
            }
            double cx = (box.minX + box.maxX) * 0.5;
            double cy = (box.minY + box.maxY) * 0.5;
            double cz = (box.minZ + box.maxZ) * 0.5;
            double distanceSqr = distanceSqr(camX, camY, camZ, cx, cy, cz);
            if (distanceSqr < NEAR_DISTANCE_SQR || distanceSqr > FAR_DISTANCE_SQR) {
                return true;
            }
            int id = entity.getId();
            Cached cached = ENTITIES.get(id);
            if (cached == null) {
                cached = new Cached();
                cached.checkedFrame = frame - RECHECK_FRAMES - 1;
                ENTITIES.put(id, cached);
            }
            if (frame - cached.checkedFrame >= RECHECK_FRAMES && stepsLeft > 0) {
                Boolean result = test(box, camX, camY, camZ);
                if (result != null) {
                    cached.visible = result;
                    cached.checkedFrame = frame;
                }
            }
            if (!cached.visible) {
                entitiesCulled++;
            }
            return cached.visible;
        } catch (Throwable t) {
            // Never let a culling bug break rendering: switch it off.
            enabled = false;
            System.err.println("[VBV Entity Culling] disabled after an error: " + t);
            return true;
        }
    }

    /** True if the block entity should be drawn. */
    public static boolean isBlockEntityVisible(BlockEntity blockEntity, double camX, double camY, double camZ) {
        if (!enabled || level == null || blockEntity == null) {
            return true;
        }
        blockEntitiesSeen++;
        try {
            // Beacon and gateway beams and end portals reach far past their own block.
            if (blockEntity instanceof BeaconBeamOwner || blockEntity instanceof TheEndPortalBlockEntity) {
                return true;
            }
            BlockPos pos = blockEntity.getBlockPos();
            double distanceSqr = distanceSqr(camX, camY, camZ, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distanceSqr < NEAR_DISTANCE_SQR || distanceSqr > FAR_DISTANCE_SQR) {
                return true;
            }
            long key = pos.asLong();
            Cached cached = BLOCK_ENTITIES.get(key);
            if (cached == null) {
                cached = new Cached();
                cached.checkedFrame = frame - RECHECK_FRAMES - 1;
                BLOCK_ENTITIES.put(key, cached);
            }
            if (frame - cached.checkedFrame >= RECHECK_FRAMES && stepsLeft > 0) {
                AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
                Boolean result = test(box, camX, camY, camZ);
                if (result != null) {
                    cached.visible = result;
                    cached.checkedFrame = frame;
                }
            }
            if (!cached.visible) {
                blockEntitiesCulled++;
            }
            return cached.visible;
        } catch (Throwable t) {
            enabled = false;
            System.err.println("[VBV Entity Culling] disabled after an error: " + t);
            return true;
        }
    }

    /**
     * Returns TRUE if any sample point can be seen, FALSE if all are hidden, or null when the
     * frame's lookup budget ran out before an answer was known.
     */
    private static Boolean test(AABB box, double camX, double camY, double camZ) {
        double x0 = box.minX - MARGIN;
        double y0 = box.minY - MARGIN;
        double z0 = box.minZ - MARGIN;
        double x1 = box.maxX + MARGIN;
        double y1 = box.maxY + MARGIN;
        double z1 = box.maxZ + MARGIN;
        double mx = (x0 + x1) * 0.5;
        double my = (y0 + y1) * 0.5;
        double mz = (z0 + z1) * 0.5;
        // Camera inside a solid block (clipping through a wall): do not cull anything.
        CURSOR.set(Mth.floor(camX), Mth.floor(camY), Mth.floor(camZ));
        if (level.getBlockState(CURSOR).isSolidRender()) {
            return Boolean.TRUE;
        }
        // The middle first (most visible things are clear straight away), then the corners.
        int outcome = ray(camX, camY, camZ, mx, my, mz);
        if (outcome != 0) {
            return outcome > 0 ? Boolean.TRUE : null;
        }
        for (int i = 0; i < 8; i++) {
            double tx = (i & 1) == 0 ? x0 : x1;
            double ty = (i & 2) == 0 ? y0 : y1;
            double tz = (i & 4) == 0 ? z0 : z1;
            outcome = ray(camX, camY, camZ, tx, ty, tz);
            if (outcome != 0) {
                return outcome > 0 ? Boolean.TRUE : null;
            }
        }
        return Boolean.FALSE;
    }

    /**
     * Walks the block grid from the camera to the target. Returns 1 if nothing solid is in the way,
     * 0 if a solid block blocks it, or -1 if the step budget ran out.
     */
    private static int ray(double sx, double sy, double sz, double tx, double ty, double tz) {
        int x = Mth.floor(sx);
        int y = Mth.floor(sy);
        int z = Mth.floor(sz);
        int endX = Mth.floor(tx);
        int endY = Mth.floor(ty);
        int endZ = Mth.floor(tz);
        double dx = tx - sx;
        double dy = ty - sy;
        double dz = tz - sz;
        int stepX = dx > 0 ? 1 : (dx < 0 ? -1 : 0);
        int stepY = dy > 0 ? 1 : (dy < 0 ? -1 : 0);
        int stepZ = dz > 0 ? 1 : (dz < 0 ? -1 : 0);
        double deltaX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dx);
        double deltaY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dy);
        double deltaZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dz);
        double maxX = stepX == 0 ? Double.MAX_VALUE : (stepX > 0 ? (x + 1 - sx) : (sx - x)) * deltaX;
        double maxY = stepY == 0 ? Double.MAX_VALUE : (stepY > 0 ? (y + 1 - sy) : (sy - y)) * deltaY;
        double maxZ = stepZ == 0 ? Double.MAX_VALUE : (stepZ > 0 ? (z + 1 - sz) : (sz - z)) * deltaZ;
        int guard = Math.abs(endX - x) + Math.abs(endY - y) + Math.abs(endZ - z) + 3;
        while (guard-- > 0) {
            if (x == endX && y == endY && z == endZ) {
                return 1;
            }
            if (maxX < maxY && maxX < maxZ) {
                x += stepX;
                maxX += deltaX;
            } else if (maxY < maxZ) {
                y += stepY;
                maxY += deltaY;
            } else {
                z += stepZ;
                maxZ += deltaZ;
            }
            if (--stepsLeft < 0) {
                return -1;
            }
            if (x == endX && y == endY && z == endZ) {
                return 1;
            }
            CURSOR.set(x, y, z);
            BlockState state = level.getBlockState(CURSOR);
            if (state.isSolidRender()) {
                return 0;
            }
        }
        return 1;
    }

    private static double distanceSqr(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Forgets entries that have not been looked at for a while (despawned or far away). */
    private static void purge() {
        for (int id : ENTITIES.keySet().toIntArray()) {
            Cached cached = ENTITIES.get(id);
            if (cached != null && frame - cached.checkedFrame > PURGE_AGE_FRAMES) {
                ENTITIES.remove(id);
            }
        }
        for (long key : BLOCK_ENTITIES.keySet().toLongArray()) {
            Cached cached = BLOCK_ENTITIES.get(key);
            if (cached != null && frame - cached.checkedFrame > PURGE_AGE_FRAMES) {
                BLOCK_ENTITIES.remove(key);
            }
        }
    }
}
