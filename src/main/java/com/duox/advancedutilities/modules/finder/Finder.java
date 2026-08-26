package com.duox.advancedutilities.modules.finder;

import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Constants;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.BlockListSetting;
import com.duox.advancedutilities.system.settings.BooleanSetting;
import com.duox.advancedutilities.system.settings.EntityListSetting;
import com.duox.advancedutilities.system.settings.NumberSetting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Searches for configured blocks and entities within range and publishes immutable
 * snapshots consumed by {@code GlFinderRenderBackend}.
 *
 * Threading model: all heavy work happens on the ScannerWorker thread; this module's
 * onTick only posts cheap commands and swaps in published results when they actually
 * changed. No per-tick allocations on the happy path.
 */
public class Finder extends Module {

    private final BooleanSetting searchBlocks = new BooleanSetting("Search Blocks", true);
    private final BooleanSetting searchEntities = new BooleanSetting("Search Entities", true);
    private final NumberSetting range = new NumberSetting(
            "Range", Constants.FINDER_DEFAULT_RANGE,
            Constants.FINDER_MIN_RANGE, Constants.FINDER_MAX_RANGE, 16);
    private final NumberSetting limit = new NumberSetting(
            "Max Results", Constants.FINDER_DEFAULT_LIMIT,
            Constants.FINDER_MIN_LIMIT, Constants.FINDER_MAX_LIMIT, 10);
    private final NumberSetting chunksPerScan = new NumberSetting("Chunks Per Scan", 4, 1, 32, 1);

    private final BlockListSetting blockList = new BlockListSetting("Blocks");
    private final EntityListSetting entityList = new EntityListSetting("Entities");

    private final BlockScanner blockScanner = new BlockScanner();
    private final EntityScanner entityScanner = new EntityScanner();

    /** volatile: written on the client thread, read on the render thread. */
    private volatile FinderSnapshot snapshot = FinderSnapshot.EMPTY;
    private long consumedSeq = 0;
    private int version = 0;
    private long[] currentBlocks = FinderSnapshot.EMPTY.blockPositions();

    // Config signature: reconfigure the worker only when something actually changed.
    private ClientLevel trackedLevel;
    private boolean trackedSearchBlocks;
    private List<Block> lastTargets = List.of();
    private int lastRange = -1;
    private int lastLimit = -1;
    private int lastChunksPerScan = -1;

    public Finder() {
        super("Finder", "Searches for specific blocks and entities globally.", Category.RENDER);
        addSetting(searchBlocks);
        addSetting(blockList);
        addSetting(searchEntities);
        addSetting(entityList);
        addSetting(range);
        addSetting(limit);
        addSetting(chunksPerScan);
    }

    @Override
    public void onEnable() {
        // Force a fresh configure on the next tick.
        trackedLevel = null;
        lastTargets = List.of();
        lastRange = -1;
        lastLimit = -1;
        currentBlocks = FinderSnapshot.EMPTY.blockPositions();
        consumedSeq = 0;
        snapshot = FinderSnapshot.EMPTY;
    }

    @Override
    public void onDisable() {
        blockScanner.clear();
        entityScanner.clear();
        snapshot = FinderSnapshot.EMPTY;
        currentBlocks = FinderSnapshot.EMPTY.blockPositions();
    }

    @Override
    public void onTick() {
        ClientLevel level = mc.level;
        if (mc.player == null || level == null) {
            if (!snapshot.isEmpty()) {
                snapshot = FinderSnapshot.EMPTY;
                currentBlocks = FinderSnapshot.EMPTY.blockPositions();
            }
            blockScanner.clear();
            trackedLevel = null;
            return;
        }

        BlockPos playerPos = mc.player.blockPosition();
        ChunkPos playerChunk = mc.player.chunkPosition();
        boolean dirty = false;

        // ---- blocks: keep worker config in sync with settings/world ----
        boolean wantBlocks = searchBlocks.getValue();
        syncBlockConfig(level, playerPos, playerChunk, wantBlocks);

        if (wantBlocks) {
            blockScanner.updateCenter(playerPos, playerChunk); // no-op unless chunk changed
            BlockScanner.Publication pub = blockScanner.poll(consumedSeq);
            if (pub != null) {
                consumedSeq = pub.seq();
                currentBlocks = pub.positions();
                dirty = true;
            }
        } else if (trackedSearchBlocks) { // just toggled off -> drop results once
            blockScanner.clear();
            currentBlocks = FinderSnapshot.EMPTY.blockPositions();
            dirty = true;
        }
        trackedSearchBlocks = wantBlocks;

        // ---- entities ----
        entityScanner.tick();
        if (entityScanner.isReady(Constants.FINDER_SCAN_INTERVAL_TICKS)) {
            if (searchEntities.getValue()) {
                dirty |= entityScanner.rescan(level, mc.player, entityList,
                        range.getValue(), limit.getInt());
            } else {
                dirty |= entityScanner.clearResults();
            }
        }

        if (dirty) publish();
    }

    private void syncBlockConfig(ClientLevel level, BlockPos playerPos, ChunkPos playerChunk,
                                 boolean wantBlocks) {
        int rangeVal = range.getInt();
        int limitVal = limit.getInt();
        int batchVal = chunksPerScan.getInt();

        if (batchVal != lastChunksPerScan) {
            lastChunksPerScan = batchVal;
            blockScanner.setBatchChunks(batchVal);
        }

        if (!wantBlocks) return; // worker stays idle; results cleared by toggle handling

        // getBlocksView() is cached inside the setting; equals() is identity-cheap.
        List<Block> targets = blockList.getBlocksView();
        boolean targetsChanged = !targets.equals(lastTargets);

        // Limit changes reconfigure too: rare, and guarantees consistent saturation
        // behavior without a separate soft-update path.
        if (level != trackedLevel || targetsChanged || rangeVal != lastRange || limitVal != lastLimit) {
            trackedLevel = level;
            lastTargets = List.copyOf(targets);
            lastRange = rangeVal;
            lastLimit = limitVal;
            currentBlocks = FinderSnapshot.EMPTY.blockPositions();
            consumedSeq = 0;
            blockScanner.configure(level, targets, rangeVal, limitVal, playerPos,
                    level.getSectionsCount());
        }
    }

    private void publish() {
        snapshot = new FinderSnapshot(currentBlocks, entityScanner.getTargets(), ++version);
    }

    /** Called from the render thread via ModuleRenderer when a chunk loads. */
    public void onChunkLoad(ChunkPos pos) {
        if (!isEnabled() || !searchBlocks.getValue()) return;
        blockScanner.addChunk(pos);
    }

    /** Called from the render thread via ModuleRenderer when a chunk unloads.
     *  Prevents stale highlights after leaving an area or changing dimension. */
    public void onChunkUnload(ChunkPos pos) {
        blockScanner.removeChunk(pos);
    }

    public FinderSnapshot getSnapshot() {
        return snapshot;
    }
}
