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

    private final NumberSetting minItem = new NumberSetting("Min Item", 0.0, 0.0, 63.0, 1.0);
    private final BooleanSetting refillTotemOffhand = new BooleanSetting("Refill Totem Offhand", true);

    /**
     * Desired item identity for each hotbar slot.
     * Important: do NOT overwrite this immediately when the current item changes
     * into a remainder/container item like:
     * water_bucket -> bucket
     * honey_bottle -> glass_bottle
     * potion -> glass_bottle
     * stew -> bowl
     */
    private final ItemStack[] desiredHotbar = new ItemStack[9];

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
        if (player == null || mc.level == null || mc.gameMode == null) return;

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
            learnDesiredHotbar(player);
            return;
        }

        if (refillTotemOffhand.getValue() && tryRefillTotemOffhand(player)) {
            actionCooldown = 2;
            return;
        }

        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            if (tryRefillHotbarSlot(player, hotbarSlot)) {
                actionCooldown = 2;
                return;
            }
        }

        learnDesiredHotbar(player);
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

    /**
     * Learn current hotbar layout carefully.
     *
     * Rules:
     * - If desired is empty and current is non-empty: learn it.
     * - If desired and current are the same item+components: refresh desired.
     * - If current differs from desired: keep old desired.
     *
     * This is the key fix for water/lava/milk bucket and similar items.
     */
    private void learnDesiredHotbar(Player player) {
        Inventory inv = player.getInventory();

        for (int i = 0; i < 9; i++) {
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

        int sourceInvSlot = findBestMatchingInventorySlot(player, desired, hotbarSlot, current, reason);
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

    /**
     * Min Item should only affect stackable items.
     * Non-stackable items like buckets, potions, tools, totems... are excluded.
     */
    private boolean shouldApplyMinRule(ItemStack desired) {
        return !desired.isEmpty() && desired.getMaxStackSize() > 1;
    }

    private boolean tryRefillTotemOffhand(Player player) {
        Inventory inv = player.getInventory();
        ItemStack offhand = inv.offhand.getFirst();

        // Totem should refill only when offhand is empty.
        // Min Item does not apply here.
        if (!offhand.isEmpty()) {
            return false;
        }

        int sourceInvSlot = findBestTotemSlot(player);
        if (sourceInvSlot == -1) {
            return false;
        }

        return swapSlots(player, toContainerSlot(sourceInvSlot), 45);
    }

    private int findBestMatchingInventorySlot(Player player,
                                              ItemStack desired,
                                              int excludedHotbarSlot,
                                              ItemStack currentHotbarStack,
                                              RefillReason reason) {
        Inventory inv = player.getInventory();

        int bestSlot = -1;
        int bestCount = -1;

        for (int slot = 9; slot < 36; slot++) {
            ItemStack candidate = inv.getItem(slot);
            if (candidate.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(candidate, desired)) continue;
            if (!isUsefulCandidate(candidate, currentHotbarStack, reason)) continue;

            if (candidate.getCount() > bestCount) {
                bestCount = candidate.getCount();
                bestSlot = slot;
            }
        }

        for (int slot = 0; slot < 9; slot++) {
            if (slot == excludedHotbarSlot) continue;

            ItemStack candidate = inv.getItem(slot);
            if (candidate.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(candidate, desired)) continue;
            if (!isUsefulCandidate(candidate, currentHotbarStack, reason)) continue;

            if (candidate.getCount() > bestCount) {
                bestCount = candidate.getCount();
                bestSlot = slot;
            }
        }

        return bestSlot;
    }

    private boolean isUsefulCandidate(ItemStack candidate, ItemStack currentHotbarStack, RefillReason reason) {
        if (reason == RefillReason.EMPTY || reason == RefillReason.WRONG_ITEM) {
            return true;
        }

        if (reason == RefillReason.LOW_COUNT) {
            if (currentHotbarStack.isEmpty()) return true;
            return candidate.getCount() > currentHotbarStack.getCount();
        }

        return false;
    }

    private int findBestTotemSlot(Player player) {
        Inventory inv = player.getInventory();

        int bestSlot = -1;
        int bestCount = -1;

        for (int slot = 9; slot < 36; slot++) {
            ItemStack candidate = inv.getItem(slot);
            if (candidate.isEmpty()) continue;
            if (!candidate.is(Items.TOTEM_OF_UNDYING)) continue;

            if (candidate.getCount() > bestCount) {
                bestCount = candidate.getCount();
                bestSlot = slot;
            }
        }

        for (int slot = 0; slot < 9; slot++) {
            ItemStack candidate = inv.getItem(slot);
            if (candidate.isEmpty()) continue;
            if (!candidate.is(Items.TOTEM_OF_UNDYING)) continue;

            if (candidate.getCount() > bestCount) {
                bestCount = candidate.getCount();
                bestSlot = slot;
            }
        }

        return bestSlot;
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
        if (inventorySlot >= 0 && inventorySlot <= 8) {
            return 36 + inventorySlot;
        }
        if (inventorySlot >= 9 && inventorySlot <= 35) {
            return inventorySlot;
        }
        if (inventorySlot == 40) {
            return 45;
        }
        return inventorySlot;
    }

    private enum RefillReason {
        NONE,
        EMPTY,
        WRONG_ITEM,
        LOW_COUNT
    }
}