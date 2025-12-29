package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.mixin.IMultiPlayerGameModeAccessor;
import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.BlockListSetting;
import com.duox.advancedutilities.system.settings.NumberSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Objects;

public class NoBreakDelay extends Module {
    // Slider Range: 0 (Nhanh nhất) -> 5 (Chậm/Mặc định), bước nhảy 1
    private final NumberSetting delay = new NumberSetting("Delay", 0, 0, 5, 1);
    private final BlockListSetting excludedBlocks = new BlockListSetting("Exclude");

    public NoBreakDelay() {
        super("NoBreakDelay", "Removes block breaking delay", Category.PLAYER);
        this.addSetting(delay);
        this.addSetting(excludedBlocks);
    }
    @Override
    public void onTick() {
        if (mc.gameMode != null) {
            boolean shouldReduceDelay = false;

            // Check if we are looking at a valid block that should have reduced delay
            if (mc.level != null && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHit = (BlockHitResult) mc.hitResult;
                BlockPos pos = blockHit.getBlockPos();
                BlockState state = mc.level.getBlockState(pos);
                
                // Only reduce delay if looking at a non-air block that is NOT excluded
                // This prevents applying the reduction when looking at air (e.g. just broke a block)
                // or when looking at an excluded block.
                if (!state.isAir() && !excludedBlocks.contains(state.getBlock())) {
                    shouldReduceDelay = true;
                }
            }

            if (shouldReduceDelay) {
                IMultiPlayerGameModeAccessor accessor = (IMultiPlayerGameModeAccessor) mc.gameMode;
                int currentDelay = accessor.getDestroyDelay();
                int maxAllowed = delay.getInt();
                if (currentDelay > maxAllowed) {
                    ((IMultiPlayerGameModeAccessor) Objects.requireNonNull(mc.gameMode)).setDestroyDelay(maxAllowed);
                }
            }
        }
    }
}
