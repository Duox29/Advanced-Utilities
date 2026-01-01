package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.BooleanSetting;
import com.duox.advancedutilities.system.settings.NumberSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AutoStash extends Module {

    private final NumberSetting range = new NumberSetting("Range", 5.0, 1.0, 10.0, 0.5);
    private final BooleanSetting includeHotbar = new BooleanSetting("Include Hotbar", false);

    // One-Shot & State Variables
    private final Set<BlockPos> visitedChests = new HashSet<>();
    private State currentState = State.SCANNING;
    private BlockPos currentTarget = null;
    private int containerOpenWaitTimer = 0;

    // To handle multiple chests in one run, we keep a queue or just scan loop
    // Since we need to wait for server response, we handle one at a time.

    private enum State {
        SCANNING,
        OPENING,
        WAITING_FOR_OPEN,
        STASHING,
        FINISHED
    }

    public AutoStash() {
        super("AutoStash", "One-Shot: Scans nearby chests, stashes matching items instantly, then disables.",
                Category.WORLD);
        this.addSetting(range);
        this.addSetting(includeHotbar);
    }

    @Override
    public void onEnable() {
        visitedChests.clear();
        currentState = State.SCANNING;
        currentTarget = null;

        // Optional: Notify user
        if (mc.player != null) {
            // mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("AutoStash
            // Started..."), true);
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            this.setEnabled(false);
            return;
        }

        // Fast State Machine - executed every tick
        switch (currentState) {
            case SCANNING:
                scanAndTargetNext();
                break;
            case OPENING:
                openTarget();
                break;
            case WAITING_FOR_OPEN:
                checkForContainer();
                break;
            case STASHING:
                performStash();
                break;
            case FINISHED:
                this.setEnabled(false);
                break;
        }
    }

    private void scanAndTargetNext() {
        BlockPos playerPos = mc.player.blockPosition();
        int r = range.getInt();

        List<BlockEntity> candidates = new ArrayList<>();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    // Skip if already processed in this session
                    if (visitedChests.contains(pos))
                        continue;

                    BlockEntity be = mc.level.getBlockEntity(pos);
                    if (isValidContainer(be)) {
                        candidates.add(be);
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            currentState = State.FINISHED; // No more chests
            return;
        }

        // Sort by distance to minimize rotation snaps (if we added rotations)
        candidates.sort(Comparator.comparingDouble(be -> be.getBlockPos().distSqr(playerPos)));

        currentTarget = candidates.get(0).getBlockPos();
        currentState = State.OPENING;
    }

    private boolean isValidContainer(BlockEntity be) {
        return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity;
    }

    private void openTarget() {
        if (currentTarget == null) {
            currentState = State.SCANNING;
            return;
        }

        // Interact
        Vec3 center = Vec3.atCenterOf(currentTarget);
        BlockHitResult hitResult = new BlockHitResult(center, Direction.UP, currentTarget, false);

        // Client-side open request
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);

        // Wait for server to open the GUI
        currentState = State.WAITING_FOR_OPEN;
        containerOpenWaitTimer = 20; // 1 second timeout for lag
    }

    private void checkForContainer() {
        // Check if a container menu is open and it's not the default player inventory
        // container
        if (mc.player.containerMenu != mc.player.inventoryMenu
                && mc.player.containerMenu.stillValid(mc.player)) {
            currentState = State.STASHING;
        } else {
            containerOpenWaitTimer--;
            if (containerOpenWaitTimer <= 0) {
                // Timeout: Close whatever might be partially open and skip this chest
                mc.player.closeContainer();
                visitedChests.add(currentTarget);
                currentState = State.SCANNING;
            }
        }
    }

    private void performStash() {
        AbstractContainerMenu menu = mc.player.containerMenu;

        // Auto-detect container size
        // Usually: Total slots - 36 (Player Inv 27 + Hotbar 9)
        int totalSlots = menu.slots.size();
        int playerSlots = 36;
        int containerSlots = totalSlots - playerSlots;

        if (containerSlots <= 0) {
            // Weird state, close and skip
            closeAndNext();
            return;
        }

        // 1. Scan Chest for Item Types
        Set<net.minecraft.world.item.Item> chestItemTypes = new HashSet<>();
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty()) {
                chestItemTypes.add(stack.getItem());
            }
        }

        // 2. Scan Player Inventory (Excluding Hotbar)
        // Player Inventory in container view:
        // ContainerSlots ... (ContainerSlots + 27) -> Main Inventory
        // (ContainerSlots + 27) ... End -> Hotbar

        int startInv = containerSlots;
        int endInv = includeHotbar.getValue() ? totalSlots : containerSlots + 27; // If includeHotbar is true, scan everything. Else stop before hotbar

        // Loop through all player input slots
        for (int i = startInv; i < endInv; i++) {
            Slot slot = menu.getSlot(i);
            if (slot.hasItem()) {
                ItemStack stack = slot.getItem();
                if (chestItemTypes.contains(stack.getItem())) {
                    // Match! Move it instantly.
                    // ClickType.QUICK_MOVE = Shift Click
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, i, 0, ClickType.QUICK_MOVE, mc.player);
                }
            }
        }

        // 3. Close Immediately
        closeAndNext();
    }

    private void closeAndNext() {
        // Send close
        mc.player.closeContainer();

        // Try to quiet animation?
        // Hard to do without mixins, but closing immediately limits the time the chest
        // is open.

        visitedChests.add(currentTarget);
        currentState = State.SCANNING; // Go to next chest
    }
}
