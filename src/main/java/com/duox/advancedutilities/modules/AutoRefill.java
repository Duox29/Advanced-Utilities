package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.BooleanSetting;
import com.duox.advancedutilities.system.settings.NumberSetting;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Arrays;

public class AutoRefill extends Module {

    private static final int HOTBAR_SIZE = 9;
    private static final int MAIN_INVENTORY_START = 9;
    private static final int MAIN_INVENTORY_END = 35;
    private static final int HOTBAR_START = 0;
    private static final int HOTBAR_END = 8;
    private static final int OFFHAND_INVENTORY_SLOT = 40;
    private static final int OFFHAND_CONTAINER_SLOT = 45;

    private final NumberSetting minItem = new NumberSetting("Min Item", 0.0, 0.0, 63.0, 1.0);
    private final BooleanSetting refillTotemOffhand = new BooleanSetting("Refill Totem Offhand", true);

    /**
     * Desired item identity for each hotbar slot.
     * Do not overwrite immediately when current item becomes a remainder item:
     * water_bucket -> bucket
     * honey_bottle -> glass_bottle
     * potion -> glass_bottle
     * stew -> bowl
     */
    private final ItemStack[] desiredHotbar = new ItemStack[HOTBAR_SIZE];

    private int actionCooldown = 0;
    private int postScreenDelay = 0;
    private boolean screenWasOpen = false;

    public AutoRefill() {
        super("Auto Refill", "Automatically refills hotbar items and totem offhand.", Category.PLAYER);

        addSetting(minItem);
        addSetting(refillTotemOffhand);

        clearDesiredHotbar();
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @Override
    public void onTick() {
        Player player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            return;
        }

        boolean blockingScreenOpen = mc.screen != null && !(mc.screen instanceof ChatScreen);
        if (blockingScreenOpen) {
            screenWasOpen = true;
            clearDesiredHotbar();
            actionCooldown = 0;
            postScreenDelay = 3;
            return;
        }

        if (screenWasOpen) {
            screenWasOpen = false;
        }

        if (postScreenDelay > 0) {
            postScreenDelay--;
            return;
        }

        if (!player.containerMenu.getCarried().isEmpty()) {
            return;
        }

        if (actionCooldown > 0) {
            actionCooldown--;
            learnDesiredHotbar(player.getInventory());
            return;
        }

        if (refillTotemOffhand.getValue() && tryRefillTotemOffhand(player)) {
            actionCooldown = 2;
            return;
        }

        for (int hotbarSlot = 0; hotbarSlot < HOTBAR_SIZE; hotbarSlot++) {
            if (tryRefillHotbarSlot(player, hotbarSlot)) {
                actionCooldown = 2;
                return;
            }
        }

        learnDesiredHotbar(player.getInventory());
    }

    private void resetState() {
        clearDesiredHotbar();
        actionCooldown = 0;
        postScreenDelay = 0;
        screenWasOpen = false;
    }

    private void clearDesiredHotbar() {
        Arrays.fill(desiredHotbar, ItemStack.EMPTY);
    }

    private void learnDesiredHotbar(Inventory inv) {
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            ItemStack current = inv.getItem(i);
            ItemStack desired = desiredHotbar[i];

            if (current.isEmpty()) {
                continue;
            }

            if (desired.isEmpty() || ItemStack.isSameItemSameComponents(current, desired)) {
                desiredHotbar[i] = current.copy();
            }
        }
    }

    private boolean tryRefillHotbarSlot(Player player, int hotbarSlot) {
        Inventory inv = player.getInventory();
        ItemStack current = inv.getItem(hotbarSlot);
        ItemStack desired = desiredHotbar[hotbarSlot];

        if (desired.isEmpty()) {
            return false;
        }

        RefillReason reason = getRefillReason(current, desired);
        if (reason == RefillReason.NONE) {
            return false;
        }

        int sourceInvSlot = findBestMatchingInventorySlot(inv, desired, hotbarSlot, current, reason);
        if (sourceInvSlot == -1) {
            return false;
        }

        return swapSlots(player, toContainerSlot(sourceInvSlot), toContainerSlot(hotbarSlot));
    }

    private RefillReason getRefillReason(ItemStack current, ItemStack desired) {
        if (current.isEmpty()) {
            return RefillReason.EMPTY;
        }

        if (!ItemStack.isSameItemSameComponents(current, desired)) {
            return RefillReason.WRONG_ITEM;
        }

        if (shouldApplyMinRule(desired)) {
            int min = (int) Math.round(minItem.getValue());
            if (min > 0 && current.getCount() <= min) {
                return RefillReason.LOW_COUNT;
            }
        }

        return RefillReason.NONE;
    }

    private boolean shouldApplyMinRule(ItemStack desired) {
        return !desired.isEmpty() && desired.getMaxStackSize() > 1;
    }

    private boolean tryRefillTotemOffhand(Player player) {
        Inventory inv = player.getInventory();
        ItemStack offhand = inv.offhand.getFirst();

        if (!offhand.isEmpty()) {
            return false;
        }

        int sourceInvSlot = findBestTotemSlot(inv);
        if (sourceInvSlot == -1) {
            return false;
        }

        return swapSlots(player, toContainerSlot(sourceInvSlot), OFFHAND_CONTAINER_SLOT);
    }

    private int findBestMatchingInventorySlot(Inventory inv,
                                              ItemStack desired,
                                              int excludedHotbarSlot,
                                              ItemStack currentHotbarStack,
                                              RefillReason reason) {
        SlotCandidate best = new SlotCandidate(-1, -1);

        best = findBestMatchingInRange(inv, desired, MAIN_INVENTORY_START, MAIN_INVENTORY_END,
                excludedHotbarSlot, currentHotbarStack, reason, best);

        best = findBestMatchingInRange(inv, desired, HOTBAR_START, HOTBAR_END,
                excludedHotbarSlot, currentHotbarStack, reason, best);

        return best.slot;
    }

    private SlotCandidate findBestMatchingInRange(Inventory inv,
                                                  ItemStack desired,
                                                  int start,
                                                  int end,
                                                  int excludedHotbarSlot,
                                                  ItemStack currentHotbarStack,
                                                  RefillReason reason,
                                                  SlotCandidate best) {
        int bestSlot = best.slot;
        int bestCount = best.count;

        for (int slot = start; slot <= end; slot++) {
            if (slot == excludedHotbarSlot) {
                continue;
            }

            ItemStack candidate = inv.getItem(slot);
            if (shouldSkipMatchingCandidate(candidate, desired, currentHotbarStack, reason, bestCount)) {
                continue;
            }

            bestSlot = slot;
            bestCount = candidate.getCount();
        }

        return new SlotCandidate(bestSlot, bestCount);
    }

    private boolean shouldSkipMatchingCandidate(ItemStack candidate,
                                                ItemStack desired,
                                                ItemStack currentHotbarStack,
                                                RefillReason reason,
                                                int currentBestCount) {
        if (candidate.isEmpty()) {
            return true;
        }

        if (!ItemStack.isSameItemSameComponents(candidate, desired)) {
            return true;
        }

        if (reason == RefillReason.LOW_COUNT) {
            if (!currentHotbarStack.isEmpty() && candidate.getCount() <= currentHotbarStack.getCount()) {
                return true;
            }
        }

        return candidate.getCount() <= currentBestCount;
    }

    private int findBestTotemSlot(Inventory inv) {
        SlotCandidate best = new SlotCandidate(-1, -1);

        best = findBestTotemInRange(inv, MAIN_INVENTORY_START, MAIN_INVENTORY_END, best);
        best = findBestTotemInRange(inv, HOTBAR_START, HOTBAR_END, best);

        return best.slot;
    }

    private SlotCandidate findBestTotemInRange(Inventory inv, int start, int end, SlotCandidate best) {
        int bestSlot = best.slot;
        int bestCount = best.count;

        for (int slot = start; slot <= end; slot++) {
            ItemStack candidate = inv.getItem(slot);
            if (candidate.isEmpty()) {
                continue;
            }
            if (!candidate.is(Items.TOTEM_OF_UNDYING)) {
                continue;
            }
            if (candidate.getCount() <= bestCount) {
                continue;
            }

            bestSlot = slot;
            bestCount = candidate.getCount();
        }

        return new SlotCandidate(bestSlot, bestCount);
    }

    private boolean swapSlots(Player player, int sourceContainerSlot, int targetContainerSlot) {
        if (sourceContainerSlot == targetContainerSlot || mc.gameMode == null) {
            return false;
        }

        try {
            mc.gameMode.handleInventoryMouseClick(
                    player.containerMenu.containerId,
                    sourceContainerSlot,
                    0,
                    ClickType.PICKUP,
                    player
            );

            mc.gameMode.handleInventoryMouseClick(
                    player.containerMenu.containerId,
                    targetContainerSlot,
                    0,
                    ClickType.PICKUP,
                    player
            );

            if (!player.containerMenu.getCarried().isEmpty()) {
                mc.gameMode.handleInventoryMouseClick(
                        player.containerMenu.containerId,
                        sourceContainerSlot,
                        0,
                        ClickType.PICKUP,
                        player
                );
            }

            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private int toContainerSlot(int inventorySlot) {
        if (inventorySlot >= HOTBAR_START && inventorySlot <= HOTBAR_END) {
            return 36 + inventorySlot;
        }
        if (inventorySlot >= MAIN_INVENTORY_START && inventorySlot <= MAIN_INVENTORY_END) {
            return inventorySlot;
        }
        if (inventorySlot == OFFHAND_INVENTORY_SLOT) {
            return OFFHAND_CONTAINER_SLOT;
        }
        return inventorySlot;
    }

    private enum RefillReason {
        NONE,
        EMPTY,
        WRONG_ITEM,
        LOW_COUNT
    }

    private record SlotCandidate(int slot, int count) {
    }
}