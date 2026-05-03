package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.BlockListSetting;
import com.duox.advancedutilities.system.settings.BooleanSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class AutoTool extends Module {

    private static final float NO_SCORE = -1_000_000.0f;
    private static final float CORRECT_TOOL_BONUS = 1000.0f;
    private static final float EPSILON = 0.0001f;

    private final BooleanSetting hotbarOnly = new BooleanSetting("Hotbar Only", true);
    private final BooleanSetting preferSilkTouch = new BooleanSetting("Prefer Silk Touch", false);
    private final BooleanSetting restorePreviousSlot = new BooleanSetting("Restore Previous Slot", true);
    private final BlockListSetting ignoreBlocks = new BlockListSetting("Ignore Blocks");

    private boolean miningSessionActive = false;
    private int originalSelectedHotbarSlot = -1;
    private boolean selectionChanged = false;

    private boolean inventorySwapActive = false;
    private int swappedInventorySlot = -1; // inventory 9..35
    private int swappedHotbarSlot = -1;    // hotbar 0..8

    public AutoTool() {
        super("Auto Tool", "Automatically swaps to the most suitable tool.", Category.PLAYER);
        addSetting(hotbarOnly);
        addSetting(preferSilkTouch);
        addSetting(restorePreviousSlot);
        addSetting(ignoreBlocks);
    }

    @Override
    public void onTick() {
        if (mc == null || mc.player == null || mc.level == null || mc.gameMode == null) {
            stopMiningSession();
            return;
        }

        if (mc.player.isCreative() || mc.player.isSpectator()) {
            stopMiningSession();
            return;
        }

        if (mc.screen != null) {
            stopMiningSession();
            return;
        }

        if (!(mc.hitResult instanceof BlockHitResult blockHitResult)) {
            stopMiningSession();
            return;
        }

        if (!mc.options.keyAttack.isDown()) {
            stopMiningSession();
            return;
        }

        BlockPos pos = blockHitResult.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);

        if (state.isAir()) {
            stopMiningSession();
            return;
        }

        if (state.getDestroySpeed(mc.level, pos) < 0.0f) {
            stopMiningSession();
            return;
        }

        if (isIgnoredBlock(state)) {
            stopMiningSession();
            return;
        }

        if (!miningSessionActive) {
            beginMiningSession();
        }

        int bestInventorySlot = findBestInventorySlot(state);
        if (bestInventorySlot == -1) {
            return;
        }

        int selectedHotbarSlot = mc.player.getInventory().selected;

        if (inventorySwapActive && bestInventorySlot != swappedHotbarSlot) {
            restoreInventorySwap();
            bestInventorySlot = findBestInventorySlot(state);
            if (bestInventorySlot == -1) {
                return;
            }
            selectedHotbarSlot = mc.player.getInventory().selected;
        }

        if (bestInventorySlot == selectedHotbarSlot) {
            return;
        }

        if (bestInventorySlot >= 0 && bestInventorySlot <= 8) {
            if (mc.player.getInventory().selected != bestInventorySlot) {
                mc.player.getInventory().selected = bestInventorySlot;
                selectionChanged = true;
            }
            return;
        }

        if (!hotbarOnly.getValue()) {
            swapInventorySlotIntoHotbar(bestInventorySlot, selectedHotbarSlot);
            inventorySwapActive = true;
            swappedInventorySlot = bestInventorySlot;
            swappedHotbarSlot = selectedHotbarSlot;
            selectionChanged = true;
        }
    }

    private void beginMiningSession() {
        miningSessionActive = true;
        originalSelectedHotbarSlot = mc.player.getInventory().selected;
        selectionChanged = false;

        inventorySwapActive = false;
        swappedInventorySlot = -1;
        swappedHotbarSlot = -1;
    }

    private void stopMiningSession() {
        if (!miningSessionActive) {
            return;
        }

        if (restorePreviousSlot.getValue()) {
            restoreInventorySwap();

            if (selectionChanged
                    && originalSelectedHotbarSlot >= 0
                    && originalSelectedHotbarSlot <= 8
                    && mc != null
                    && mc.player != null) {
                mc.player.getInventory().selected = originalSelectedHotbarSlot;
            }
        }

        clearRuntimeState();
    }

    private void clearRuntimeState() {
        miningSessionActive = false;
        originalSelectedHotbarSlot = -1;
        selectionChanged = false;

        inventorySwapActive = false;
        swappedInventorySlot = -1;
        swappedHotbarSlot = -1;
    }

    private boolean isIgnoredBlock(BlockState state) {
        Block block = state.getBlock();
        return ignoreBlocks.contains(block);
    }

    private int findBestInventorySlot(BlockState state) {
        int limit = hotbarOnly.getValue() ? 9 : 36;
        int bestSlot = -1;
        float bestScore = NO_SCORE;

        for (int i = 0; i < limit; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            float score = scoreStack(stack, state);

            if (score <= NO_SCORE / 2.0f) continue;

            if (score > bestScore + EPSILON) {
                bestScore = score;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    private float scoreStack(ItemStack stack, BlockState state) {
        if (stack.isEmpty()) return NO_SCORE;

        var enchantmentRegistry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

        float destroySpeed = stack.getDestroySpeed(state);
        boolean correctTool = stack.isCorrectToolForDrops(state);

        Holder<Enchantment> silkTouchHolder = enchantmentRegistry.getOrThrow(Enchantments.SILK_TOUCH);
        Holder<Enchantment> fortuneHolder = enchantmentRegistry.getOrThrow(Enchantments.FORTUNE);

        int silkLevel = stack.getEnchantmentLevel(silkTouchHolder);
        int fortuneLevel = stack.getEnchantmentLevel(fortuneHolder);

        boolean preferSilk = shouldPreferSilk(state);
        boolean preferFortune = shouldPreferFortune(state);

        if (!correctTool && destroySpeed <= 1.0f) {
            if (!(preferSilk && silkLevel > 0)) {
                return NO_SCORE;
            }
        }

        float score = destroySpeed;

        if (correctTool) {
            score += CORRECT_TOOL_BONUS;
        }

        if (preferSilk) {
            if (silkLevel > 0) {
                score += 20.0f + silkLevel;
            } else {
                score -= 10.0f;
            }
        } else if (preferFortune) {
            if (fortuneLevel > 0) {
                score += 18.0f + fortuneLevel;
            } else if (silkLevel > 0) {
                score -= 3.0f;
            }
        }

        return score;
    }

    private boolean shouldPreferSilk(BlockState state) {
        if (isFortunePreferredBlock(state)) {
            return preferSilkTouch.getValue();
        }
        return isSilkPreferredUtilityBlock(state);
    }

    private boolean shouldPreferFortune(BlockState state) {
        return isFortunePreferredBlock(state) && !preferSilkTouch.getValue();
    }

    private boolean isFortunePreferredBlock(BlockState state) {
        return state.is(BlockTags.COAL_ORES)
                || state.is(BlockTags.COPPER_ORES)
                || state.is(BlockTags.DIAMOND_ORES)
                || state.is(BlockTags.EMERALD_ORES)
                || state.is(BlockTags.GOLD_ORES)
                || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.LAPIS_ORES)
                || state.is(BlockTags.REDSTONE_ORES)
                || state.is(Blocks.NETHER_QUARTZ_ORE)
                || state.is(Blocks.NETHER_GOLD_ORE);
    }

    private boolean isSilkPreferredUtilityBlock(BlockState state) {
        return state.is(Blocks.GLASS)
                || state.is(Blocks.TINTED_GLASS)
                || state.is(Blocks.GLASS_PANE)

                || state.is(Blocks.WHITE_STAINED_GLASS)
                || state.is(Blocks.ORANGE_STAINED_GLASS)
                || state.is(Blocks.MAGENTA_STAINED_GLASS)
                || state.is(Blocks.LIGHT_BLUE_STAINED_GLASS)
                || state.is(Blocks.YELLOW_STAINED_GLASS)
                || state.is(Blocks.LIME_STAINED_GLASS)
                || state.is(Blocks.PINK_STAINED_GLASS)
                || state.is(Blocks.GRAY_STAINED_GLASS)
                || state.is(Blocks.LIGHT_GRAY_STAINED_GLASS)
                || state.is(Blocks.CYAN_STAINED_GLASS)
                || state.is(Blocks.PURPLE_STAINED_GLASS)
                || state.is(Blocks.BLUE_STAINED_GLASS)
                || state.is(Blocks.BROWN_STAINED_GLASS)
                || state.is(Blocks.GREEN_STAINED_GLASS)
                || state.is(Blocks.RED_STAINED_GLASS)
                || state.is(Blocks.BLACK_STAINED_GLASS)

                || state.is(Blocks.WHITE_STAINED_GLASS_PANE)
                || state.is(Blocks.ORANGE_STAINED_GLASS_PANE)
                || state.is(Blocks.MAGENTA_STAINED_GLASS_PANE)
                || state.is(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE)
                || state.is(Blocks.YELLOW_STAINED_GLASS_PANE)
                || state.is(Blocks.LIME_STAINED_GLASS_PANE)
                || state.is(Blocks.PINK_STAINED_GLASS_PANE)
                || state.is(Blocks.GRAY_STAINED_GLASS_PANE)
                || state.is(Blocks.LIGHT_GRAY_STAINED_GLASS_PANE)
                || state.is(Blocks.CYAN_STAINED_GLASS_PANE)
                || state.is(Blocks.PURPLE_STAINED_GLASS_PANE)
                || state.is(Blocks.BLUE_STAINED_GLASS_PANE)
                || state.is(Blocks.BROWN_STAINED_GLASS_PANE)
                || state.is(Blocks.GREEN_STAINED_GLASS_PANE)
                || state.is(Blocks.RED_STAINED_GLASS_PANE)
                || state.is(Blocks.BLACK_STAINED_GLASS_PANE)

                || state.is(Blocks.ICE)
                || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.BLUE_ICE)
                || state.is(Blocks.BOOKSHELF)
                || state.is(Blocks.ENDER_CHEST);
    }

    private void restoreInventorySwap() {
        if (!inventorySwapActive) {
            return;
        }

        if (mc == null || mc.player == null || mc.gameMode == null) {
            return;
        }

        if (swappedInventorySlot < 9 || swappedInventorySlot > 35) {
            return;
        }

        if (swappedHotbarSlot < 0 || swappedHotbarSlot > 8) {
            return;
        }

        swapInventorySlotIntoHotbar(swappedInventorySlot, swappedHotbarSlot);

        inventorySwapActive = false;
        swappedInventorySlot = -1;
        swappedHotbarSlot = -1;
    }

    private void swapInventorySlotIntoHotbar(int inventorySlot, int hotbarSlot) {
        int containerSlot = toContainerSlot(inventorySlot);

        mc.gameMode.handleInventoryMouseClick(
                mc.player.inventoryMenu.containerId,
                containerSlot,
                hotbarSlot,
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

    @Override
    public void onDisable() {
        stopMiningSession();
        super.onDisable();
    }
}