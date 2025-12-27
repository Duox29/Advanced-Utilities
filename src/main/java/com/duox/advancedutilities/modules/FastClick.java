package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.mixin.IMinecraftAccessor;
import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.NumberSetting;

public class FastClick extends Module {
    // Slider Range: 0 (Nhanh nhất) -> 5 (Chậm/Mặc định), bước nhảy 1
    private final NumberSetting delay = new NumberSetting("Delay", 0, 0, 5, 1);
    public FastClick() {
        super("FastClick", "Removes right-click delay (FastPlace).", Category.PLAYER);
        this.addSetting(delay);
    }

    @Override
    public void onTick() {
        // Safety check
        if (mc.player == null || mc.level == null) return;

        // Ép kiểu Minecraft instance về Interface Accessor
        IMinecraftAccessor accessor = (IMinecraftAccessor) mc;
        //Set delay = 0
        int currentDelay = accessor.getRightClickDelay();
        int maxAllowed = delay.getInt();

        if (currentDelay > maxAllowed) {
            accessor.setRightClickDelay(maxAllowed);
        }
    }
}