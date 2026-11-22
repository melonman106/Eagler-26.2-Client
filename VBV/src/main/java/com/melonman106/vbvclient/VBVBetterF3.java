package com.melonman106.vbvclient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Native BetterF3-style debug overlay.
 *
 * <p>The vanilla F3 screen is a flat list. This replaces it with the layout the BetterF3 mod is
 * known for: colour-coded section titles, {@code Label: Value} rows where the label is dim and the
 * value bright, a left/right split, and far more information than the old VBV overlay had - the
 * targeted block or entity, the system, the server and the frame breakdown.</p>
 *
 * <p>Sections can be switched off individually in Options &gt; Mods &gt; BetterF3, and the compact
 * layout drops the extra spacing for small screens.</p>
 */
public final class VBVBetterF3 {
    /** Section titles, in the gold the mod uses. */
    private static final int TITLE = 0xFFFFC24A;
    private static final int LABEL = 0xFF9AA6B2;
    private static final int VALUE = 0xFFF2F2F2;
    private static final int GOOD = 0xFF8CFF8C;
    private static final int WARN = 0xFFFFC04A;
    private static final int BAD = 0xFFFF7070;
    private static final int ACCENT = 0xFF5ED0FF;

    private static boolean showPerformance = true;
    private static boolean showWorld = true;
    private static boolean showPlayer = true;
    private static boolean showTarget = true;
    private static boolean showSystem = true;
    private static boolean compact;

    private VBVBetterF3() {}

    public static void load(boolean performance, boolean world, boolean player, boolean target,
                            boolean system, boolean compactLayout) {
        showPerformance = performance;
        showWorld = world;
        showPlayer = player;
        showTarget = target;
        showSystem = system;
        compact = compactLayout;
    }

    public static boolean showPerformance() { return showPerformance; }
    public static boolean showWorld() { return showWorld; }
    public static boolean showPlayer() { return showPlayer; }
    public static boolean showTarget() { return showTarget; }
    public static boolean showSystem() { return showSystem; }
    public static boolean isCompact() { return compact; }

    public static void togglePerformance() { showPerformance = !showPerformance; }
    public static void toggleWorld() { showWorld = !showWorld; }
    public static void togglePlayer() { showPlayer = !showPlayer; }
    public static void toggleTarget() { showTarget = !showTarget; }
    public static void toggleSystem() { showSystem = !showSystem; }
    public static void toggleCompact() { compact = !compact; }

    private record Row(String label, String value, int color) {}

    private static final class Section {
        final String title;
        final List<Row> rows = new ArrayList<>();

        Section(String title) {
            this.title = title;
        }

        void add(String label, String value) {
            rows.add(new Row(label, value, VALUE));
        }

        void add(String label, String value, int color) {
            rows.add(new Row(label, value, color));
        }
    }

    /** Draws the whole overlay; called from {@code VBVExtraFeatures.renderDebug}. */
    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        List<Section> left = new ArrayList<>();
        List<Section> right = new ArrayList<>();
        if (showPlayer) {
            left.add(playerSection(mc, player));
        }
        if (showWorld) {
            left.add(worldSection(mc, player));
        }
        if (showTarget) {
            Section target = targetSection(mc, player);
            if (target != null) {
                left.add(target);
            }
        }
        if (showSystem) {
            right.add(systemSection(mc));
        }
        if (showPerformance) {
            right.add(performanceSection(mc));
        }
        Font font = mc.font;
        int lineHeight = font.lineHeight + (compact ? 0 : 1);
        int columnWidth = Math.max(120, graphics.guiWidth() / 2 - 8);
        drawColumn(graphics, font, left, 4, 4, lineHeight, columnWidth);
        drawColumn(graphics, font, right, graphics.guiWidth() / 2 + 4, 4, lineHeight, columnWidth);
    }

    private static Section playerSection(Minecraft mc, LocalPlayer player) {
        Section section = new Section("Player");
        section.add("Name", player.getName().getString());
        section.add("Health", String.format(Locale.ROOT, "%.1f / %.1f", player.getHealth(), player.getMaxHealth()),
            player.getHealth() <= 6.0F ? BAD : VALUE);
        section.add("Food", player.getFoodData().getFoodLevel() + " / 20");
        section.add("Armour", Integer.toString(player.getArmorValue()));
        section.add("XP", player.experienceLevel + " (level)");
        section.add("Mode", mc.gameMode == null ? "unknown" : mc.gameMode.getPlayerMode().getName());
        section.add("Sprinting", onOff(player.isSprinting()));
        section.add("Sneaking", onOff(player.isCrouching()));
        section.add("On ground", onOff(player.onGround()));
        section.add("Fall distance", String.format(Locale.ROOT, "%.2f", player.fallDistance));
        return section;
    }

    private static Section worldSection(Minecraft mc, LocalPlayer player) {
        BlockPos pos = player.blockPosition();
        Section section = new Section("World");
        section.add("Dimension", String.valueOf(mc.level.dimension()));
        section.add("Biome", mc.level.getBiome(pos).unwrapKey()
            .map(key -> key.identifier().toString()).orElse("unknown"));
        section.add("Day", Long.toString(Math.floorDiv(mc.level.getOverworldClockTime(), 24000L)));
        section.add("Time", Long.toString(Math.floorMod(mc.level.getOverworldClockTime(), 24000L)));
        section.add("Weather", mc.level.isThundering() ? "thunder"
            : (mc.level.isRaining() ? "rain" : "clear"));
        section.add("Sky light", mc.level.getBrightness(LightLayer.SKY, pos) + " / 15");
        section.add("Block light", mc.level.getBrightness(LightLayer.BLOCK, pos) + " / 15");
        section.add("Chunk", Math.floorDiv(pos.getX(), 16) + " " + Math.floorDiv(pos.getY(), 16) + " "
            + Math.floorDiv(pos.getZ(), 16));
        return section;
    }

    /** The block or entity under the crosshair, with the same data Jade shows. */
    private static Section targetSection(Minecraft mc, LocalPlayer player) {
        Entity entity = mc.crosshairPickEntity;
        if (entity != null && entity.isAlive()) {
            Section section = new Section("Targeted entity");
            section.add("Type", entity.getType().getDescription().getString());
            section.add("Name", entity.getName().getString());
            if (entity instanceof LivingEntity living) {
                section.add("Health", String.format(Locale.ROOT, "%.1f / %.1f",
                    living.getHealth(), living.getMaxHealth()));
            }
            section.add("Position", entity.blockPosition().getX() + " " + entity.blockPosition().getY()
                + " " + entity.blockPosition().getZ());
            return section;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        Section section = new Section("Targeted block");
        section.add("Block", state.getBlock().getName().getString());
        float hardness = state.getDestroySpeed(mc.level, pos);
        section.add("Hardness", hardness < 0.0F ? "unbreakable" : String.format(Locale.ROOT, "%.1f", hardness));
        section.add("Position", pos.getX() + " " + pos.getY() + " " + pos.getZ());
        return section;
    }

    private static Section systemSection(Minecraft mc) {
        Runtime runtime = Runtime.getRuntime();
        // The watchdog reads the browser's own heap counters; Runtime.maxMemory() is constant-folded
        // to a meaningless value on TeaVM, so it is only the fallback.
        long usedMb = VBVMemory.usedMb();
        long maxMb = VBVMemory.maxMb();
        Section section = new Section("System");
        section.add("Client", "Eaglercraft 26.2 | VBV 2.0");
        section.add("Runtime", System.getProperty("java.version", "browser"));
        section.add("Platform", System.getProperty("os.name", "browser"));
        section.add("Memory", usedMb + " / " + maxMb + " MB"
            + (maxMb > 0 ? String.format(Locale.ROOT, " (%.0f%%)", usedMb * 100.0D / Math.max(1L, maxMb)) : "")
            + (VBVMemory.usingBrowserStats() ? " browser" : ""),
            maxMb > 0 && usedMb * 100L / Math.max(1L, maxMb) > 85L ? WARN : VALUE);
        section.add("Window", mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight()
            + " (scale " + mc.getWindow().getGuiScale() + ")");
        section.add("Cores", Integer.toString(runtime.availableProcessors()));
        return section;
    }

    private static Section performanceSection(Minecraft mc) {
        Section section = new Section("Rendering");
        int fps = mc.getFps();
        section.add("FPS", Integer.toString(fps), fps >= 50 ? GOOD : (fps >= 30 ? WARN : BAD));
        section.add("Render distance", mc.options.renderDistance().get() + " chunks");
        section.add("Effective", mc.options.getEffectiveRenderDistance() + " chunks");
        section.add("Simulation", mc.options.simulationDistance().get() + " chunks");
        int entities = 0;
        for (Object ignored : mc.level.entitiesForRendering()) {
            entities++;
        }
        section.add("Entities", Integer.toString(entities));
        section.add("Frame", VBVPerf.phaseLine(), ACCENT);
        section.add("LOD", VBVLod.name() + (VBVLod.isActive() ? " (" + VBVLod.rangeChunks() + " chunks)" : ""));
        String connection = mc.getCurrentServer() == null ? "Singleplayer" : mc.getCurrentServer().ip;
        section.add("Connection", connection);
        return section;
    }

    private static void drawColumn(GuiGraphicsExtractor graphics, Font font, List<Section> sections,
                                   int x, int y, int lineHeight, int maxWidth) {
        int bottom = graphics.guiHeight() - 2;
        for (Section section : sections) {
            if (y + lineHeight > bottom) {
                return;
            }
            graphics.text(font, section.title, x, y, TITLE, true);
            y += lineHeight;
            for (Row row : section.rows) {
                if (y + lineHeight > bottom) {
                    return;
                }
                String prefix = row.label() + ": ";
                int prefixWidth = font.width(prefix);
                graphics.text(font, prefix, x, y, LABEL, true);
                String value = fit(font, row.value(), Math.max(0, maxWidth - prefixWidth));
                graphics.text(font, value, x + prefixWidth, y, row.color(), true);
                y += lineHeight;
            }
            y += compact ? 1 : lineHeight / 2;
        }
    }

    /** Trims a value so a long one cannot run into the other column. */
    private static String fit(Font font, String text, int maxWidth) {
        if (maxWidth <= 0 || text == null) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    private static String onOff(boolean value) {
        return value ? "yes" : "no";
    }
}
