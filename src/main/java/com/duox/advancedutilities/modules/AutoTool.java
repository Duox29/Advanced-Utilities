package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.BooleanSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class AutoTool extends Module {

    private static final float NO_SCORE = -1_000_000.0f;
    private static final float CORRECT_TOOL_BONUS = 1000.0f;
    private static final float EPSILON = 0.0001f;

    private final BooleanSetting hotbarOnly = new BooleanSetting("Hotbar Only", true);

    public AutoTool() {
        super("Auto Tool", "Automatically swaps to the most suitable tool.", Category.PLAYER);
        addSetting(hotbarOnly);
    }

    @Override
    public void onTick() {
        if (mc == null || mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }

        if (mc.player.isCreative() || mc.player.isSpectator()) {
            return;
        }

        if (mc.screen != null) {
            return;
        }

        if (!mc.options.keyAttack.isDown()) {
            return;
        }

        if (!(mc.hitResult instanceof BlockHitResult blockHitResult)) {
            return;
        }

        BlockPos pos = blockHitResult.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);

        if (state.isAir()) {
            return;
        }

        if (state.getDestroySpeed(mc.level, pos) < 0.0f) {
            return;
        }

        int bestInventorySlot = findBestInventorySlot(state);
        if (bestInventorySlot == -1) {
            return;
        }

        int selectedHotbarSlot = mc.player.getInventory().selected;

        if (bestInventorySlot == selectedHotbarSlot) {
            return;
        }

        if (bestInventorySlot >= 0 && bestInventorySlot <= 8) {
            mc.player.getInventory().selected = bestInventorySlot;
            return;
        }

        if (!hotbarOnly.getValue()) {
            swapInventorySlotIntoSelectedHotbar(bestInventorySlot, selectedHotbarSlot);
        }
    }

    private int findBestInventorySlot(BlockState state) {
        int limit = hotbarOnly.getValue() ? 9 : 36;

        int bestSlot = -1;
        float bestScore = NO_SCORE;
        ItemStack bestStack = ItemStack.EMPTY;

        for (int i = 0; i < limit; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            float score = scoreStack(stack, state);

            if (score <= NO_SCORE / 2.0f) {
                continue;
            }

            if (score > bestScore + EPSILON
                    || (Math.abs(score - bestScore) <= EPSILON && hasMoreRemainingDurability(stack, bestStack))) {
                bestScore = score;
                bestSlot = i;
                bestStack = stack;
            }
        }

        return bestSlot;
    }

    private float scoreStack(ItemStack stack, BlockState state) {
        if (stack.isEmpty()) {
            return NO_SCORE;
        }

        float destroySpeed = stack.getItem().getDestroySpeed(stack, state);
        boolean correctTool = stack.getItem().isCorrectToolForDrops(stack, state);

        if (!correctTool && destroySpeed <= 1.0f) {
            return NO_SCORE;
        }

        float score = destroySpeed;

        if (correctTool) {
            score += CORRECT_TOOL_BONUS;
        }

        if (stack.isDamageableItem()) {
            int maxDamage = stack.getMaxDamage();
            if (maxDamage > 0) {
                int remaining = maxDamage - stack.getDamageValue();
                score += (remaining / (float) maxDamage) * 0.01f;
            }
        }

        return score;
    }

    private boolean hasMoreRemainingDurability(ItemStack a, ItemStack b) {
        return getRemainingDurability(a) > getRemainingDurability(b);
    }

    private int getRemainingDurability(ItemStack stack) {
        if (stack.isEmpty()) {
            return -1;
        }

        if (!stack.isDamageableItem()) {
            return Integer.MAX_VALUE;
        }

        return stack.getMaxDamage() - stack.getDamageValue();
    }

    private void swapInventorySlotIntoSelectedHotbar(int inventorySlot, int selectedHotbarSlot) {
        int containerSlot = toContainerSlot(inventorySlot);

        mc.gameMode.handleInventoryMouseClick(
                mc.player.inventoryMenu.containerId,
                containerSlot,
                selectedHotbarSlot,
                ClickType.SWAP,
                mc.player
        );
    }

    private int toContainerSlot(int inventorySlot) {
        if (inventorySlot < 9) {
            return 36 + inventorySlot;
        }
        return inventorySlot;
    }
}