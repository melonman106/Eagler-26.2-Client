package com.melonman106.vbvclient.mods;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** A data-defined status effect that heals its holder on a repeating interval. */
public final class ModMobEffect extends MobEffect {
    private final int interval;
    private final float amount;

    public ModMobEffect(MobEffectCategory category, int color, int interval, float amount) {
        super(category, color);
        this.interval = interval;
        this.amount = amount;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
        if (mob.getHealth() < mob.getMaxHealth()) {
            mob.heal(amount + Math.max(0, amplification));
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
        int ticks = Math.max(1, interval >> Math.min(3, Math.max(0, amplification)));
        return tickCount % ticks == 0;
    }
}
