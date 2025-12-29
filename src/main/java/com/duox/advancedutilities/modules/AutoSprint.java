package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Module;
import net.minecraft.client.KeyMapping;

public class AutoSprint extends Module {
    public AutoSprint() {
        super("Auto Sprint", "Automatically sprints when moving forward", Category.PLAYER);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        // Check if moving forward and not sneaking/colliding/using item
        if (mc.player.input.hasForwardImpulse() && 
            !mc.player.isShiftKeyDown() && 
            !mc.player.horizontalCollision &&
            !mc.player.isUsingItem()) {
            
            mc.player.setSprinting(true);
        }
    }
    
    @Override
    public void onDisable() {
        if (mc.player != null) {
            mc.player.setSprinting(false);
        }
    }
}
