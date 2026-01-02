package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.BooleanSetting;
import com.duox.advancedutilities.system.settings.NumberSetting;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import com.duox.advancedutilities.utils.CacheUtils;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.*;

import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;

public class AutoStash extends Module {
    // --- Settings ---
    private final NumberSetting range = new NumberSetting("Range", 5.0, 1.0, 10.0, 0.5);
    private final BooleanSetting includeHotbar = new BooleanSetting("Include Hotbar", false);
    private final NumberSetting itemsPerTick = new NumberSetting("Items/Tick", 4.0, 1.0, 27.0, 1.0);
    private final BooleanSetting rebuildCache = new BooleanSetting("Rebuild Cache Next Run", false);

    // --- Cache Data Structures ---
    // Key: Chest Position string "x,y,z"
    // Value: Map of Item ResourceLocation string -> Quantity
    private static Map<String, Map<String, Integer>> chestCache = new HashMap<>();

    public static Map<String, Map<String, Integer>> getChestCache() {
        return chestCache;
    }

    // --- Runtime Variables ---
    private State currentState = State.IDLE;
    private BlockPos currentTarget = null;
    private int waitTimer = 0;
    private int silentContainerId = -1;
    private boolean containerReady = false;

    // For Scanning/Rebuilding
    private List<BlockPos> scanQueue = new ArrayList<>();

    // For Stashing
    private Map<BlockPos, List<Integer>> stashQueue = new HashMap<>(); // Chest Pos -> List of Inventory Slot IDs to move
    private Iterator<Map.Entry<BlockPos, List<Integer>>> stashIterator;
    private Map.Entry<BlockPos, List<Integer>> currentStashEntry;

    private enum State {
        IDLE,
        // Rebuild Cache States
        SCANNING_WORLD,
        OPENING_FOR_SCAN,
        WAITING_FOR_SCAN_OPEN,
        SCANNING_CONTENTS,
        CLOSING_AFTER_SCAN,

        // Smart Stash States
        CALCULATING_STASH,
        OPENING_FOR_STASH,
        WAITING_FOR_STASH_OPEN,
        STASHING_ITEMS,
        CLOSING_AFTER_STASH
    }

    public AutoStash() {
        super("AutoStash", "Scans chests to build a cache, then intelligently stashes items.", Category.WORLD);
        this.addSetting(range);
        this.addSetting(includeHotbar);
        this.addSetting(itemsPerTick);
        this.addSetting(rebuildCache);
    }

    @Override
    public void onEnable() {
        resetState();
        if (rebuildCache.getValue()) {
            startRebuildCache();
        } else {
            startSmartStash();
        }
    }

    @Override
    public void onDisable() {
        if (silentContainerId != -1 && mc.player != null) {
            sendClosePacket();
        }
        resetState();
    }

    private void resetState() {
        currentState = State.IDLE;
        currentTarget = null;
        silentContainerId = -1;
        containerReady = false;
        scanQueue.clear();
        stashQueue.clear();
        stashIterator = null;
        currentStashEntry = null;
    }

    public boolean isSilentMode() {
        return this.isEnabled();
    }

    public void onSilentContainerOpen(int containerId, MenuType<?> menuType) {
        this.silentContainerId = containerId;
        this.containerReady = true;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            this.setEnabled(false);
            return;
        }

        switch (currentState) {
            case SCANNING_WORLD:
                processScanQueue();
                break;
            case OPENING_FOR_SCAN:
                openTargetSilent();
                break;
            case WAITING_FOR_SCAN_OPEN:
                waitForContainer(State.SCANNING_CONTENTS);
                break;
            case SCANNING_CONTENTS:
                scanContainerContents();
                break;
            case CLOSING_AFTER_SCAN:
                closeSilent(State.SCANNING_WORLD);
                break;

            case CALCULATING_STASH:
                // Logic is done in startSmartStash, but if we need to retry or something
                break;
            case OPENING_FOR_STASH:
                openTargetSilent();
                break;
            case WAITING_FOR_STASH_OPEN:
                waitForContainer(State.STASHING_ITEMS);
                break;
            case STASHING_ITEMS:
                performStash();
                break;
            case CLOSING_AFTER_STASH:
                closeSilent(State.OPENING_FOR_STASH); // Loop back to next chest
                break;

            case IDLE:
            default:
                break;
        }
    }

    // ============================================================================================
    // REBUILD CACHE LOGIC
    // ============================================================================================

    private void startRebuildCache() {
        chestCache.clear(); // Clear memory cache
        scanQueue.clear();

        BlockPos playerPos = mc.player.blockPosition();
        int r = range.getInt();

        // Find all chests in range
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    BlockEntity be = mc.level.getBlockEntity(pos);
                    if (isValidContainer(be)) {
                        if (isDuplicateDoubleChest(be)) continue;
                        scanQueue.add(pos);
                    }
                }
            }
        }

        // Sort by distance
        scanQueue.sort(Comparator.comparingDouble(pos -> pos.distSqr(playerPos)));

        currentState = State.SCANNING_WORLD;
    }

    private void processScanQueue() {
        if (scanQueue.isEmpty()) {
            // Done scanning
            Path cacheFile = CacheUtils.getCacheFilePath(mc,"autostash");
            CacheUtils.saveToJson(cacheFile, chestCache);
            rebuildCache.setValue(false);
            sendMessage("Cache rebuild complete. Saved to disk.");
            this.setEnabled(false);
            return;
        }

        currentTarget = scanQueue.remove(0);
        // Skip if we already visited this logical double chest part
        // (Not strictly necessary if we key by pos, but good optimization)

        currentState = State.OPENING_FOR_SCAN;
    }

    private void scanContainerContents() {
        AbstractContainerMenu menu = mc.player.containerMenu;
        int containerSlots = menu.slots.size() - 36;

        if (containerSlots > 0) {
            Map<String, Integer> contents = new HashMap<>();

            for (int i = 0; i < containerSlots; i++) {
                ItemStack stack = menu.getSlot(i).getItem();
                if (!stack.isEmpty()) {
                    String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    contents.put(itemId, contents.getOrDefault(itemId, 0) + stack.getCount());
                }
            }

            // Save to cache
            String posKey =CacheUtils.posToString(currentTarget);
            chestCache.put(posKey, contents);

            // Handle double chests: Also map the other half to the same contents?
            // Or just rely on scanning both halves. Scanning both halves is safer but redundant.
            // For now, simple scan of every block is robust.
        }

        currentState = State.CLOSING_AFTER_SCAN;
    }

    // ============================================================================================
    // SMART STASH LOGIC
    // ============================================================================================

    private void startSmartStash() {
        Path cacheFile = CacheUtils.getCacheFilePath(mc, "autostash");
        Type type = new TypeToken<Map<String, Map<String, Integer>>>(){}.getType();

        Map<String, Map<String, Integer>> loaded = CacheUtils.loadFromJson(cacheFile, type);
        if (chestCache.isEmpty()) {
            sendMessage("§cCache is empty. Please run Rebuild Cache first.");
            this.setEnabled(false);
            return;
        }

        calculateStashPlan();

        if (stashQueue.isEmpty()) {
            sendMessage("Nothing to stash.");
            this.setEnabled(false);
            return;
        }

        stashIterator = stashQueue.entrySet().iterator();
        moveToNextStashTarget();
    }

    private void calculateStashPlan() {
        stashQueue.clear();
        LocalPlayer player = mc.player;
        // Use Inventory directly to get correct indices (0-8 hotbar, 9-35 main)
        // inventoryMenu has different slot mapping (0-4 crafting, 5-8 armor, etc)

        int startInv = includeHotbar.getValue() ? 0 : 9;

        // Iterate player inventory (0-35)
        for (int i = startInv; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

            // Find best chest for this item
            BlockPos bestChest = findBestChest(itemId);

            if (bestChest != null) {
                stashQueue.computeIfAbsent(bestChest, k -> new ArrayList<>()).add(i); // Store slot ID to move
            }
        }
    }

    private BlockPos findBestChest(String itemId) {
        BlockPos bestPos = null;
        int maxCount = -1;

        BlockPos playerPos = mc.player.blockPosition();
        double rangeSq = Math.pow(range.getValue(), 2);

        for (Map.Entry<String, Map<String, Integer>> entry : chestCache.entrySet()) {
            String posStr = entry.getKey();
            Map<String, Integer> contents = entry.getValue();

            if (contents.containsKey(itemId)) {
                BlockPos pos = CacheUtils.stringToPos(posStr);
                // Check range
                if (pos.distSqr(playerPos) > rangeSq) continue;

                int count = contents.get(itemId);
                if (count > maxCount) {
                    maxCount = count;
                    bestPos = pos;
                }
            }
        }
        return bestPos;
    }

    private void moveToNextStashTarget() {
        if (stashIterator != null && stashIterator.hasNext()) {
            currentStashEntry = stashIterator.next();
            currentTarget = currentStashEntry.getKey();
            currentState = State.OPENING_FOR_STASH;
        } else {
            // Done
            this.setEnabled(false);
        }
    }

    private void performStash() {
        AbstractContainerMenu menu = mc.player.containerMenu;
        int containerSlots = menu.slots.size() - 36;

        if (containerSlots <= 0) {
             currentState = State.CLOSING_AFTER_STASH;
             return;
        }

        List<Integer> slotsToMove = currentStashEntry.getValue();
        int limit = itemsPerTick.getInt();
        int moves = 0;

        // Create a copy to avoid modification exceptions if we were iterating directly,
        // though here we are iterating a managed list from the queue map entry
        Iterator<Integer> it = slotsToMove.iterator();
        while (it.hasNext() && moves < limit) {
             int invSlotIndex = it.next();

             int menuSlotId;
             if (invSlotIndex < 9) {
                 menuSlotId = containerSlots + 27 + invSlotIndex;
             } else {
                 menuSlotId = containerSlots + (invSlotIndex - 9);
             }

             // Verify item is still what we expect (rudimentary check)
             Slot slot = menu.getSlot(menuSlotId);
             if (slot.hasItem()) {
                 sendQuickMovePacket(menu, menuSlotId);
                 moves++;
             }
             it.remove(); // Remove from pending list
        }

        if (slotsToMove.isEmpty()) {
            currentState = State.CLOSING_AFTER_STASH;
        }
        // Else stay in STASHING_ITEMS to continue next tick
    }

    // ============================================================================================
    // COMMON HELPERS
    // ============================================================================================

    private void openTargetSilent() {
        if (currentTarget == null) return;

        Vec3 center = Vec3.atCenterOf(currentTarget);
        BlockHitResult hitResult = new BlockHitResult(center, Direction.UP, currentTarget, false);

        containerReady = false;
        silentContainerId = -1;
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
        mc.player.swing(InteractionHand.MAIN_HAND);

        currentState = (currentState == State.OPENING_FOR_SCAN) ? State.WAITING_FOR_SCAN_OPEN : State.WAITING_FOR_STASH_OPEN;
        waitTimer = 20;
    }

    private void waitForContainer(State nextState) {
        if (containerReady && silentContainerId != -1) {
            currentState = nextState;
            return;
        }
        waitTimer--;
        if (waitTimer <= 0) {
            // Timeout, skip this chest
            if (currentState == State.WAITING_FOR_SCAN_OPEN) {
                currentState = State.SCANNING_WORLD; // Next scan
            } else {
                currentState = State.CLOSING_AFTER_STASH; // Next stash target
            }
        }
    }

    private void closeSilent(State nextState) {
        sendClosePacket();
        silentContainerId = -1;
        containerReady = false;

        if (nextState == State.OPENING_FOR_STASH) {
             moveToNextStashTarget();
        } else {
            currentState = nextState;
        }
    }

    private void sendMessage(String message) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§b[AutoStash] §r" + message), false);
        }
    }

    private boolean isValidContainer(BlockEntity be) {
        return be instanceof ChestBlockEntity ||
                be instanceof BarrelBlockEntity ||
                be instanceof ShulkerBoxBlockEntity;
    }

    private boolean isDuplicateDoubleChest(BlockEntity be) {
        if (be instanceof ChestBlockEntity) {
            net.minecraft.world.level.block.state.BlockState state = be.getBlockState();
            if (state.hasProperty(ChestBlock.TYPE)) {
                // Only scan SINGLE or RIGHT part of double chest to avoid duplicates
                // Opening the RIGHT part usually opens the full double chest
                return state.getValue(ChestBlock.TYPE) == ChestType.LEFT;
            }
        }
        return false;
    }

    private void sendQuickMovePacket(AbstractContainerMenu menu, int slotId) {
        mc.player.connection.send(new ServerboundContainerClickPacket(
                menu.containerId, menu.getStateId(), slotId, 0, ClickType.QUICK_MOVE,
                menu.getSlot(slotId).getItem().copy(),
                new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>()
        ));
    }

    private void sendClosePacket() {
        if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) {
            mc.player.connection.send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
            mc.player.containerMenu = mc.player.inventoryMenu;
        }
    }
}
