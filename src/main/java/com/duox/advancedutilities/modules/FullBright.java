package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Constants;
import com.duox.advancedutilities.system.Module;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/*
 * Provides full brightness by applying night vision effect.
 * The effect is continuously reapplied while the module is enabled.
 */
public class FullBright extends Module {

    public FullBright() {
        super("FullBright", "Night Vision Mode", Category.RENDER);
    }

    @Override
    public void onEnable() {
        // Logic runs in onTick
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            mc.player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        // Apply Night Vision continuously
        // Parameters: duration, amplifier, ambient, showParticles, showIcon
        mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 
                Constants.FULLBRIGHT_NIGHT_VISION_DURATION, 0, false, false));
    }
}