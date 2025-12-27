package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.mixin.IMultiPlayerGameModeAccessor;
import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.NumberSetting;

import java.util.Objects;

public class NoBreakDelay extends Module {
    // Slider Range: 0 (Nhanh nhất) -> 5 (Chậm/Mặc định), bước nhảy 1
    private final NumberSetting delay = new NumberSetting("Delay", 0, 0, 5, 1);
    public NoBreakDelay() {
        super("NoBreakDelay", "Removes block breaking delay", Category.PLAYER);
        this.addSetting(delay);
    }
    @Override
    public void onTick() {
        if (mc.gameMode != null) {
            IMultiPlayerGameModeAccessor accessor = (IMultiPlayerGameModeAccessor) mc.gameMode;
            // Reset delay về 0 mỗi tick
            int currentDelay = accessor.getDestroyDelay();
            int maxAllowed = delay.getInt();
            if (currentDelay > maxAllowed) {
                ((IMultiPlayerGameModeAccessor) Objects.requireNonNull(mc.gameMode)).setDestroyDelay(maxAllowed);
            }
        }
    }

}