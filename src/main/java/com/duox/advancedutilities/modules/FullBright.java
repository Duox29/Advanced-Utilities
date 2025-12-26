package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Module;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class FullBright extends Module {

    public FullBright() {
        super("FullBright", "Night Vision Mode", Category.RENDER);
    }

    @Override
    public void onEnable() {
        // Không cần làm gì khi enable, logic sẽ chạy trong onTick
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

        // Apply Night Vision liên tục (Duration thấp để không bị hiện icon quá lâu nếu tắt mod)
        // false, false: Ẩn các hạt potion (particles) và ẩn icon trên màn hình
        mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false));
    }
}