package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.BlockListSetting;
import com.duox.advancedutilities.system.settings.NumberSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class AutoRightClick extends Module {

    // --- SETTINGS ---
    // Khai báo public để config dễ dàng, hoặc dùng getter
    public final NumberSetting range = new NumberSetting("Range", 4.0, 1.0, 6.0, 0.5);
    public final NumberSetting delay = new NumberSetting("Delay (Ticks)", 10.0, 1.0, 100.0, 1.0);
    public final NumberSetting clicks = new NumberSetting("Clicks/Block", 1.0, 1.0, 10.0, 1.0);
    public final BlockListSetting blocks = new BlockListSetting("Targets");
    // Internal
    private int clickTickCounter = 0;
    private int mapClearTickCounter = 0;
    private int scanTickCounter = 0;
    private final Map<BlockPos, Integer> clicksPerBlockMap = new HashMap<>();
    private final List<BlockPos> cachedTargetPositions = new ArrayList<>();

    public AutoRightClick() {
        super("AutoRightClick", "Auto right clicks blocks.", Category.PLAYER);
        // Đăng ký setting để GUI nhìn thấy
        addSetting(range);
        addSetting(delay);
        addSetting(clicks);
        addSetting(blocks);
    }

    @Override
    public void onEnable() {
        clicksPerBlockMap.clear();
        cachedTargetPositions.clear();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;

        mapClearTickCounter++;
        if (mapClearTickCounter >= 60) {
            clicksPerBlockMap.clear();
            mapClearTickCounter = 0;
        }

        scanTickCounter++;
        if (scanTickCounter >= 20) {
            performSpatialScan();
            scanTickCounter = 0;
        }

        clickTickCounter++;
        // Lấy giá trị từ Setting
        if (clickTickCounter < delay.getInt()) {
            return;
        }
        clickTickCounter = 0;

        performClicking();
    }

    private void performSpatialScan() {
        cachedTargetPositions.clear();
        BlockPos playerPos = Objects.requireNonNull(mc.player).blockPosition();
        int r = range.getInt(); // Lấy giá trị Range

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos targetPos = playerPos.offset(x, y, z);
                    BlockState state = Objects.requireNonNull(mc.level).getBlockState(targetPos);

                    // Logic check Block
                    if (blocks.contains(state.getBlock())) {
                        cachedTargetPositions.add(targetPos);
                    }
                }
            }
        }
    }

    private void performClicking() {
        for (BlockPos targetPos : cachedTargetPositions) {
            int currentClicks = clicksPerBlockMap.getOrDefault(targetPos, 0);
            if (currentClicks < clicks.getInt()) {
                clickBlock(targetPos);
                clicksPerBlockMap.put(targetPos, currentClicks + 1);
                return;
            }
        }
    }

    // ... clickBlock giữ nguyên ...
    private void clickBlock(BlockPos pos) {
        if (mc.gameMode != null && mc.player != null) {
            BlockHitResult hitResult = new BlockHitResult(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), net.minecraft.core.Direction.UP, pos, false);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }
}