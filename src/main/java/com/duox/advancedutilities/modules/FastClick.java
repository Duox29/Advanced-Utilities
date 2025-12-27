package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.mixin.IMinecraftAccessor;
import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Module;

public class FastClick extends Module {

    public FastClick() {
        super("FastClick", "Removes right-click delay (FastPlace).", Category.PLAYER);
    }

    @Override
    public void onTick() {
        // Safety check
        if (mc.player == null || mc.level == null) return;

        // Ép kiểu Minecraft instance về Interface Accessor
        IMinecraftAccessor accessor = (IMinecraftAccessor) mc;
        //Set delay = 0
        accessor.setRightClickDelay(0);
    }
}