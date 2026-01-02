package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.gui.StorageScreen;
import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.utils.CacheUtils;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class StorageManager extends Module {
    // Request Queue: Item ID -> Quantity needed
    private final Map<String, Integer> requestQueue = new HashMap<>();

    // Execution Queue: Chest Pos -> List of Item IDs to take from it
    private final Map<BlockPos, Map<String, Integer>> retrievalPlan = new HashMap<>();
    private Iterator<Map.Entry<BlockPos, Map<String, Integer>>> retrievalIterator;
    private Map.Entry<BlockPos, Map<String, Integer>> currentTargetEntry;

    // State Machine
    private enum State {
        IDLE,
        PLANNING,
        OPENING_CHEST,
        WAITING_FOR_OPEN,
        WITHDRAWING,
        CLOSING_CHEST
    }

    private State currentState = State.IDLE;
    private BlockPos currentTarget = null;
    private int waitTimer = 0;
    private int silentContainerId = -1;
    private boolean containerReady = false;

    public StorageManager() {
        super("StorageManager", "Manage items from cached chests.", Category.WORLD);
    }

    @Override
    public void onEnable() {
        if (mc.player == null) return;

        // If we have pending requests, start processing them
        if (!requestQueue.isEmpty()) {
            startRetrieval();
        } else {
            // Otherwise just open the GUI
            mc.setScreen(new StorageScreen(this));
            // We keep it enabled to process tasks if any, or it will be disabled by GUI if needed
        }
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            this.setEnabled(false);
            return;
        }

        switch (currentState) {
            case PLANNING:
                calculateRetrievalPlan();
                break;
            case OPENING_CHEST:
                openTargetSilent();
                break;
            case WAITING_FOR_OPEN:
                waitForContainer();
                break;
            case WITHDRAWING:
                performWithdrawal();
                break;
            case CLOSING_CHEST:
                closeSilent();
                break;
            case IDLE:
                // If the user closed the GUI but we have things to do?
                // Currently onEnable triggers GUI or Retrieval.
                // If GUI adds items, it calls startRetrieval which sets state to PLANNING.
                break;
        }
    }

    // --- Public API for GUI ---

    public void addToRequestQueue(String itemId, int quantity) {
        requestQueue.put(itemId, requestQueue.getOrDefault(itemId, 0) + quantity);
    }

    public void clearRequestQueue() {
        requestQueue.clear();
    }

    public Map<String, Integer> getRequestQueue() {
        return requestQueue;
    }

    public void startRetrieval() {
        if (requestQueue.isEmpty()) {
            sendMessage("Queue is empty.");
            return;
        }
        currentState = State.PLANNING;
    }

    // --- Internal Logic ---

    private void resetState() {
        currentState = State.IDLE;
        currentTarget = null;
        silentContainerId = -1;
        containerReady = false;
        retrievalPlan.clear();
        retrievalIterator = null;
        currentTargetEntry = null;
    }

    private void calculateRetrievalPlan() {
        retrievalPlan.clear();
        Map<String, Map<String, Integer>> cache = AutoStash.getChestCache();

        // Clone request queue to track remaining needs
        Map<String, Integer> remainingNeeds = new HashMap<>(requestQueue);

        BlockPos playerPos = mc.player.blockPosition();

        // Simple greedy approach: Find closest chest with needed item
        // Get all chests, sort by distance
        List<String> sortedChests = new ArrayList<>(cache.keySet());
        sortedChests.sort(Comparator.comparingDouble(s -> {
            BlockPos p = stringToPos(s);
            return p.distSqr(playerPos);
        }));

        for (String chestPosStr : sortedChests) {
            Map<String, Integer> contents = cache.get(chestPosStr);
            BlockPos chestPos = stringToPos(chestPosStr);

            for (Iterator<Map.Entry<String, Integer>> it = remainingNeeds.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Integer> req = it.next();
                String itemId = req.getKey();
                int needed = req.getValue();

                if (contents.containsKey(itemId)) {
                    int available = contents.get(itemId);
                    int toTake = Math.min(needed, available);

                    if (toTake > 0) {
                        retrievalPlan.computeIfAbsent(chestPos, k -> new HashMap<>()).put(itemId, toTake);

                        int newNeeded = needed - toTake;
                        if (newNeeded <= 0) {
                            it.remove(); // Fulfilled
                        } else {
                            req.setValue(newNeeded);
                        }
                    }
                }
            }
        }

        if (!remainingNeeds.isEmpty()) {
            sendMessage("Warning: Cannot find all items. Missing: " + remainingNeeds);
        }

        if (retrievalPlan.isEmpty()) {
            sendMessage("Could not find any items to retrieve.");
            currentState = State.IDLE;
            requestQueue.clear(); // Clear queue as we failed or done
            this.setEnabled(false); // Disable module
            return;
        }

        retrievalIterator = retrievalPlan.entrySet().iterator();
        moveToNextTarget();
    }

    private void moveToNextTarget() {
        if (retrievalIterator != null && retrievalIterator.hasNext()) {
            currentTargetEntry = retrievalIterator.next();
            currentTarget = currentTargetEntry.getKey();
            currentState = State.OPENING_CHEST;
        } else {
            sendMessage("Retrieval complete.");
            requestQueue.clear();
            currentState = State.IDLE;
            this.setEnabled(false);
        }
    }

    private void openTargetSilent() {
        if (currentTarget == null) return;

        Vec3 center = Vec3.atCenterOf(currentTarget);
        BlockHitResult hitResult = new BlockHitResult(center, Direction.UP, currentTarget, false);

        containerReady = false;
        silentContainerId = -1;
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
        mc.player.swing(InteractionHand.MAIN_HAND);

        currentState = State.WAITING_FOR_OPEN;
        waitTimer = 20;
    }

    private void waitForContainer() {
        if (containerReady && silentContainerId != -1) {
            currentState = State.WITHDRAWING;
            return;
        }
        waitTimer--;
        if (waitTimer <= 0) {
            // Timeout
            sendMessage("Timeout opening chest at " + currentTarget);
            currentState = State.CLOSING_CHEST;
        }
    }

    public void onSilentContainerOpen(int containerId, MenuType<?> menuType) {
        this.silentContainerId = containerId;
        this.containerReady = true;
    }
    public boolean isSilentMode() {
        return this.isEnabled();
    }
    private void performWithdrawal() {
        AbstractContainerMenu menu = mc.player.containerMenu;
        int containerSlots = menu.slots.size() - 36;

        if (containerSlots <= 0) {
            currentState = State.CLOSING_CHEST;
            return;
        }

        Map<String, Integer> itemsToTake = currentTargetEntry.getValue();

        // Iterate through chest slots
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (stack.isEmpty()) continue;

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

            if (itemsToTake.containsKey(itemId)) {
                int needed = itemsToTake.get(itemId);
                if (needed <= 0) continue;

                int inSlot = stack.getCount();

                sendQuickMovePacket(menu, i);

                updateCache(itemId, inSlot);
                // Decrement needed count (approximation)
                itemsToTake.put(itemId, needed - inSlot);
            }
        }

        currentState = State.CLOSING_CHEST;
    }

    private void updateCache(String itemId, int amountTaken) {
        if (currentTarget == null) return;

        String chestJsonKey = CacheUtils.posToString(currentTarget);
        Map<String, Map<String, Integer>> globalCache = AutoStash.getChestCache();
        if (globalCache.containsKey(chestJsonKey)) {
            Map<String, Integer> chestContents =  globalCache.get(chestJsonKey);

            if(chestContents.containsKey(itemId) && chestContents != null) {
                int currentAmount =  chestContents.get(itemId);
                int newAmount = currentAmount - amountTaken;

                if (newAmount <= 0) {
                    chestContents.remove(itemId);
                } else {
                    chestContents.put(itemId, newAmount);
                }
            }
        }
    }
    private void closeSilent() {
        if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) {
            mc.player.connection.send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
            mc.player.containerMenu = mc.player.inventoryMenu;
        }
        silentContainerId = -1;
        containerReady = false;

        moveToNextTarget();
    }

    private void sendQuickMovePacket(AbstractContainerMenu menu, int slotId) {
        mc.player.connection.send(new ServerboundContainerClickPacket(
                menu.containerId, menu.getStateId(), slotId, 0, ClickType.QUICK_MOVE,
                menu.getSlot(slotId).getItem().copy(),
                new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>()
        ));
    }

    private void sendMessage(String message) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§6[StorageManager] §r" + message), false);
        }
    }

    private BlockPos stringToPos(String s) {
        String[] parts = s.split(",");
        return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }
}
