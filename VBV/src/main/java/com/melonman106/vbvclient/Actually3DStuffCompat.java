package com.melonman106.vbvclient;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Native Eaglercraft implementation of the lightweight first-person pose
 * adjustments from Actually 3D Stuff's Hold My Items Lua add-ons.
 *
 * This ports whole-item/hand transforms only. The original scripts also
 * animate individual model elements and particles, which need a per-element
 * animation API and are not emulated here.
 */
public final class Actually3DStuffCompat {
    private Actually3DStuffCompat() {}

    public static void apply(PoseStack pose, ItemStack stack, AbstractClientPlayer player,
                             boolean mainHand, float partialTick) {
        if (stack == null || stack.isEmpty() || player == null) return;

        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        float side = mainHand ? 1.0F : -1.0F;
        float pitch = player.getXRot();
        float yaw = player.getYRot();

        if (isAny(id,
                "minecraft:name_tag", "minecraft:vine", "minecraft:glow_lichen",
                "minecraft:twisting_vines", "minecraft:weeping_vines",
                "minecraft:hanging_roots", "minecraft:pale_hanging_moss",
                "minecraft:glow_item_frame")
                || id.endsWith("_banner_pattern")) {
            pose.translate(0.0F, 0.35F, -0.05F);
        }

        if (id.endsWith("_candle")) {
            pose.mulPose(Axis.ZP.rotationDegrees(10.0F * side));
            pose.translate(0.0F, 0.0F, 0.1F);
        }
        if (isAny(id, "minecraft:wheat_seeds", "minecraft:pumpkin_seeds",
                "minecraft:melon_seeds", "minecraft:beetroot_seeds",
                "minecraft:cocoa_beans", "minecraft:torchflower_seeds")) {
            pose.translate(0.0F, 0.35F, -0.05F);
            pose.mulPose(Axis.XP.rotationDegrees(Mth.clamp(-pitch, -20.0F, 90.0F) * 0.12F));
        }

        if (isAny(id, "minecraft:fishing_rod", "minecraft:carrot_on_a_stick",
                "minecraft:warped_fungus_on_a_stick")) {
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.clamp(pitch, -25.0F, 90.0F) * 0.18F));
            pose.mulPose(Axis.YP.rotationDegrees(Mth.clamp(yaw * 0.08F, -12.0F, 12.0F)));
            pose.translate(-0.1F * side, 0.0F, 0.0F);
        }

        if ("minecraft:brush".equals(id)) {
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.clamp(yaw * 0.08F, -20.0F, 20.0F)));
            pose.translate(0.0F, -0.02F, 0.0F);
        }
        if ("minecraft:stick".equals(id)) {
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.clamp(pitch / 10.0F, -5.0F, 0.0F)));
        }
        if ("minecraft:string".equals(id)) {
            pose.mulPose(Axis.XP.rotationDegrees(Mth.clamp(-pitch, 0.0F, 70.0F) * 0.35F));
        }

        if (isAny(id, "minecraft:paper", "minecraft:map")) {
            pose.mulPose(Axis.XP.rotationDegrees(Mth.clamp(pitch, -25.0F, 30.0F) * 0.18F));
        }
        if ("minecraft:name_tag".equals(id)) {
            pose.mulPose(Axis.XP.rotationDegrees(Mth.clamp(pitch, -90.0F, 90.0F) * 0.12F));
            pose.mulPose(Axis.ZP.rotationDegrees(-yaw * 0.05F));
        }
        if ("minecraft:writable_book".equals(id)) {
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.clamp(pitch, -45.0F, -10.0F) * 0.2F));
        }
        if ("minecraft:armor_stand".equals(id)) {
            pose.mulPose(Axis.ZP.rotationDegrees(-yaw * 0.05F));
        }
    }

    private static boolean isAny(String id, String... candidates) {
        for (String candidate : candidates) {
            if (candidate.equals(id)) return true;
        }
        return false;
    }
}
