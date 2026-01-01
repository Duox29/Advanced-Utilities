package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.BooleanSetting;
import com.duox.advancedutilities.system.settings.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AutoStash extends Module {

    private final NumberSetting range = new NumberSetting("Range", 5.0, 1.0, 10.0, 0.5);
    private final BooleanSetting includeHotbar = new BooleanSetting("Include Hotbar", false);
    private final NumberSetting itemsPerTick = new NumberSetting("Items/Tick", 4.0, 1.0, 27.0, 1.0);

    private final Set<BlockPos> visitedChests = new HashSet<>();
    private State currentState = State.SCANNING;
    private BlockPos currentTarget = null;
    private int waitTimer = 0;

    private boolean silentMode = true;
    private int silentContainerId = -1;
    private MenuType<?> silentMenuType = null;
    private boolean containerReady = false;

    private enum State {
        SCANNING,
        OPENING,
        WAITING_FOR_OPEN,
        STASHING,
        CLOSING,
        FINISHED
    }

    public AutoStash() {
        super("AutoStash", "Silent stash: Opens chests invisibly, moves matching items via packets.",
                Category.WORLD);
        this.addSetting(range);
        this.addSetting(includeHotbar);
        this.addSetting(itemsPerTick);
    }

    @Override
    public void onEnable() {
        visitedChests.clear();
        currentState = State.SCANNING;
        currentTarget = null;
        silentMode = true;
        silentContainerId = -1;
        containerReady = false;
    }

    @Override
    public void onDisable() {
        if (silentContainerId != -1 && mc.player != null) {
            sendClosePacket();
        }
        silentContainerId = -1;
        containerReady = false;
    }

    public boolean isSilentMode() {
        return silentMode && isEnabled();
    }

    public void onSilentContainerOpen(int containerId, MenuType<?> menuType) {
        this.silentContainerId = containerId;
        this.silentMenuType = menuType;
        this.containerReady = true;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            this.setEnabled(false);
            return;
        }

        switch (currentState) {
            case SCANNING:
                scanAndTargetNext();
                break;
            case OPENING:
                openTargetSilent();
                break;
            case WAITING_FOR_OPEN:
                waitForContainer();
                break;
            case STASHING:
                performSilentStash();
                break;
            case CLOSING:
                closeSilent();
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
            currentState = State.FINISHED;
            return;
        }

        candidates.sort(Comparator.comparingDouble(be -> be.getBlockPos().distSqr(playerPos)));

        currentTarget = candidates.get(0).getBlockPos();
        currentState = State.OPENING;
    }

    private boolean isValidContainer(BlockEntity be) {
        return be instanceof ChestBlockEntity
                || be instanceof BarrelBlockEntity
                || be instanceof ShulkerBoxBlockEntity;
    }

    private void openTargetSilent() {
        if (currentTarget == null) {
            currentState = State.SCANNING;
            return;
        }

        markDoubleChestVisited(currentTarget);

        Vec3 center = Vec3.atCenterOf(currentTarget);
        BlockHitResult hitResult = new BlockHitResult(center, Direction.UP, currentTarget, false);

        containerReady = false;
        silentContainerId = -1;

        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);

        mc.player.swing(InteractionHand.MAIN_HAND);

        currentState = State.WAITING_FOR_OPEN;
        waitTimer = 40;
    }

    private void markDoubleChestVisited(BlockPos pos) {
        visitedChests.add(pos);

        BlockState state = mc.level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock) {
            if (state.hasProperty(ChestBlock.TYPE)) {
                ChestType type = state.getValue(ChestBlock.TYPE);
                if (type != ChestType.SINGLE) {
                    Direction facing = state.getValue(ChestBlock.FACING);
                    BlockPos neighborPos = getDoubleChestNeighbor(pos, type, facing);
                    if (neighborPos != null) {
                        visitedChests.add(neighborPos);
                    }
                }
            }
        }
    }

    private BlockPos getDoubleChestNeighbor(BlockPos pos, ChestType type, Direction facing) {
        Direction neighborDir;
        if (type == ChestType.LEFT) {
            neighborDir = facing.getClockWise();
        } else {
            neighborDir = facing.getCounterClockWise();
        }
        return pos.relative(neighborDir);
    }

    private void waitForContainer() {
        if (containerReady && silentContainerId != -1) {
            waitTimer = 2;
            currentState = State.STASHING;
            return;
        }

        waitTimer--;
        if (waitTimer <= 0) {
            visitedChests.add(currentTarget);
            currentState = State.SCANNING;
        }
    }

    private void performSilentStash() {
        if (waitTimer > 0) {
            waitTimer--;
            return;
        }

        LocalPlayer player = mc.player;
        AbstractContainerMenu menu = player.containerMenu;

        if (menu == player.inventoryMenu) {
            currentState = State.SCANNING;
            return;
        }

        int totalSlots = menu.slots.size();
        int playerSlots = 36;
        int containerSlots = totalSlots - playerSlots;

        if (containerSlots <= 0) {
            currentState = State.CLOSING;
            return;
        }

        Set<net.minecraft.world.item.Item> chestItemTypes = new HashSet<>();
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty()) {
                chestItemTypes.add(stack.getItem());
            }
        }

        int startInv = containerSlots;
        int endInv = includeHotbar.getValue() ? totalSlots : containerSlots + 27;

        List<Integer> slotsToMove = new ArrayList<>();
        for (int i = startInv; i < endInv; i++) {
            Slot slot = menu.getSlot(i);
            if (slot.hasItem()) {
                ItemStack stack = slot.getItem();
                if (chestItemTypes.contains(stack.getItem())) {
                    slotsToMove.add(i);
                }
            }
        }

        int itemsThisTick = Math.min(slotsToMove.size(), itemsPerTick.getInt());
        for (int i = 0; i < itemsThisTick; i++) {
            int slotId = slotsToMove.get(i);
            sendQuickMovePacket(menu, slotId);
        }

        if (slotsToMove.size() <= itemsThisTick) {
            currentState = State.CLOSING;
        }
    }

    private void sendQuickMovePacket(AbstractContainerMenu menu, int slotId) {
        int containerId = menu.containerId;
        int stateId = menu.getStateId();

        mc.player.connection.send(new ServerboundContainerClickPacket(
                containerId,
                stateId,
                slotId,
                0,
                ClickType.QUICK_MOVE,
                ItemStack.EMPTY,
                new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>()
        ));
    }

    private void closeSilent() {
        sendClosePacket();
        silentContainerId = -1;
        containerReady = false;
        currentState = State.SCANNING;
    }

    private void sendClosePacket() {
        if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) {
            mc.player.connection.send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
            mc.player.containerMenu = mc.player.inventoryMenu;
        }
    }

}
