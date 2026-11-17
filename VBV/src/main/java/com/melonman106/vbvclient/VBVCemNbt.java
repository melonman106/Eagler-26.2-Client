package com.melonman106.vbvclient;

import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * {@code nbt(path, test)} for custom entity model animations. Resource packs such as Fresh Animations
 * (Player Add-on) use it to look at what an entity is holding or doing: {@code nbt(SelectedItem.id,
 * raw:iregex:.*shield.*)}, {@code nbt(abilities.flying, 1)}, {@code nbt(SleepingX, exists:true)}.
 *
 * <p>A real entity NBT tree is not available to a client, so only the paths packs are known to use are
 * answered, from the live entity: the main-hand item id, the equipment slots, a short inventory listing
 * (main hand and off hand), whether the entity sleeps, and the player's flying flags. Any other path is
 * treated as missing: {@code exists:true} is false and every comparison fails.</p>
 *
 * <p>Test syntax (as in OptiFine's NBT matching): {@code exists:true|false}; {@code raw:} followed by
 * {@code iregex:}, {@code regex:}, {@code ipattern:}, {@code pattern:} (with * and ? wildcards) or a plain
 * text; or just a number or text, compared for equality.</p>
 */
final class VBVCemNbt {
    private static final int SELECTED_ID = 1;
    private static final int INVENTORY = 2;
    private static final int EQUIP_MAIN = 3;
    private static final int EQUIP_OFF = 4;
    private static final int EQUIP_HEAD = 5;
    private static final int EQUIP_CHEST = 6;
    private static final int EQUIP_LEGS = 7;
    private static final int EQUIP_FEET = 8;
    private static final int SLEEPING = 9;
    private static final int FLYING = 10;
    private static final int MAY_FLY = 11;

    private VBVCemNbt() {}

    static VBVCemExpr.Num create(String path, String test, Supplier<Entity> entity) {
        final int kind = kindOf(path);
        final String spec = test == null ? "" : test.trim();
        if (spec.startsWith("exists:")) {
            final boolean wanted = "true".equalsIgnoreCase(spec.substring("exists:".length()).trim());
            return () -> (value(kind, entity.get()) != null) == wanted ? 1.0F : 0.0F;
        }
        if (spec.startsWith("raw:")) {
            final java.util.function.Predicate<String> matcher = matcher(spec.substring("raw:".length()).trim());
            return () -> {
                String value = value(kind, entity.get());
                return value != null && matcher.test(value) ? 1.0F : 0.0F;
            };
        }
        // Plain value: numbers compare as numbers, everything else as text.
        Float number = null;
        try {
            number = Float.valueOf(spec);
        } catch (NumberFormatException ignored) {
            // text comparison
        }
        if (number != null) {
            final float wanted = number.floatValue();
            return () -> {
                String value = value(kind, entity.get());
                if (value == null) {
                    return 0.0F;
                }
                try {
                    return Float.parseFloat(value) == wanted ? 1.0F : 0.0F;
                } catch (NumberFormatException e) {
                    return 0.0F;
                }
            };
        }
        final String wantedText = spec;
        return () -> {
            String value = value(kind, entity.get());
            return value != null && value.equals(wantedText) ? 1.0F : 0.0F;
        };
    }

    private static int kindOf(String path) {
        switch (path.trim()) {
            case "SelectedItem.id":
            case "SelectedItem":
                return SELECTED_ID;
            case "Inventory":
                return INVENTORY;
            case "equipment.mainhand":
                return EQUIP_MAIN;
            case "equipment.offhand":
                return EQUIP_OFF;
            case "equipment.head":
                return EQUIP_HEAD;
            case "equipment.chest":
                return EQUIP_CHEST;
            case "equipment.legs":
                return EQUIP_LEGS;
            case "equipment.feet":
                return EQUIP_FEET;
            case "SleepingX":
                return SLEEPING;
            case "abilities.flying":
                return FLYING;
            case "abilities.mayfly":
                return MAY_FLY;
            default:
                return 0;
        }
    }

    /** The text the path reads as for this entity, or null when the path has no value. */
    private static String value(int kind, Entity entity) {
        if (kind == 0 || entity == null) {
            return null;
        }
        try {
            if (kind == SLEEPING) {
                return entity instanceof LivingEntity && ((LivingEntity) entity).isSleeping() ? "1" : null;
            }
            if (kind == FLYING || kind == MAY_FLY) {
                if (!(entity instanceof Player)) {
                    return null;
                }
                Player player = (Player) entity;
                return (kind == FLYING ? player.getAbilities().flying : player.getAbilities().mayfly) ? "1" : "0";
            }
            if (!(entity instanceof LivingEntity)) {
                return null;
            }
            LivingEntity living = (LivingEntity) entity;
            switch (kind) {
                case SELECTED_ID:
                case EQUIP_MAIN:
                    return idOf(living.getMainHandItem());
                case EQUIP_OFF:
                    return idOf(living.getOffhandItem());
                case EQUIP_HEAD:
                    return idOf(living.getItemBySlot(EquipmentSlot.HEAD));
                case EQUIP_CHEST:
                    return idOf(living.getItemBySlot(EquipmentSlot.CHEST));
                case EQUIP_LEGS:
                    return idOf(living.getItemBySlot(EquipmentSlot.LEGS));
                case EQUIP_FEET:
                    return idOf(living.getItemBySlot(EquipmentSlot.FEET));
                case INVENTORY:
                    return inventory(living);
                default:
                    return null;
            }
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static String idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Looks like the Inventory list of a player's NBT, for the slots a pack asks about (off hand is Slot:-106b). */
    private static String inventory(LivingEntity living) {
        StringBuilder out = new StringBuilder("[");
        append(out, "-106b", idOf(living.getOffhandItem()));
        append(out, "0b", idOf(living.getMainHandItem()));
        append(out, "103b", idOf(living.getItemBySlot(EquipmentSlot.HEAD)));
        append(out, "102b", idOf(living.getItemBySlot(EquipmentSlot.CHEST)));
        append(out, "101b", idOf(living.getItemBySlot(EquipmentSlot.LEGS)));
        append(out, "100b", idOf(living.getItemBySlot(EquipmentSlot.FEET)));
        return out.append(']').toString();
    }

    private static void append(StringBuilder out, String slot, String id) {
        if (id == null) {
            return;
        }
        if (out.length() > 1) {
            out.append(',');
        }
        out.append("{Slot:").append(slot).append(",id:\"").append(id).append("\",count:1}");
    }

    private static java.util.function.Predicate<String> matcher(String spec) {
        try {
            if (spec.startsWith("iregex:")) {
                final Pattern pattern = Pattern.compile(spec.substring("iregex:".length()), Pattern.CASE_INSENSITIVE);
                return text -> pattern.matcher(text).matches();
            }
            if (spec.startsWith("regex:")) {
                final Pattern pattern = Pattern.compile(spec.substring("regex:".length()));
                return text -> pattern.matcher(text).matches();
            }
            if (spec.startsWith("ipattern:")) {
                final Pattern pattern = Pattern.compile(glob(spec.substring("ipattern:".length())), Pattern.CASE_INSENSITIVE);
                return text -> pattern.matcher(text).matches();
            }
            if (spec.startsWith("pattern:")) {
                final Pattern pattern = Pattern.compile(glob(spec.substring("pattern:".length())));
                return text -> pattern.matcher(text).matches();
            }
        } catch (RuntimeException badPattern) {
            return text -> false;
        }
        final String wanted = spec;
        return text -> text.equals(wanted);
    }

    /** Turns a * and ? wildcard text into a regular expression. */
    private static String glob(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '*') {
                out.append(".*");
            } else if (ch == '?') {
                out.append('.');
            } else if ("\\.[]{}()+-^$|".indexOf(ch) >= 0) {
                out.append('\\').append(ch);
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }
}
