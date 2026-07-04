package com.duox.advancedutilities.modules.finder;

import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Constants;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.BlockListSetting;
import com.duox.advancedutilities.system.settings.BooleanSetting;
import com.duox.advancedutilities.system.settings.EntityListSetting;
import com.duox.advancedutilities.system.settings.NumberSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

public class Finder extends Module {

    private final BooleanSetting searchBlocks = new BooleanSetting("Search Blocks", true);
    private final BooleanSetting searchEntities = new BooleanSetting("Search Entities", true);
    private final NumberSetting range = new NumberSetting(
            "Range",
            Constants.FINDER_DEFAULT_RANGE,
            Constants.FINDER_MIN_RANGE,
            Constants.FINDER_MAX_RANGE,
            16
    );
    private final NumberSetting limit = new NumberSetting(
            "Max Results",
            Constants.FINDER_DEFAULT_LIMIT,
            Constants.FINDER_MIN_LIMIT,
            Constants.FINDER_MAX_LIMIT,
            10
    );
    private final NumberSetting chunksPerScan = new NumberSetting("Chunks Per Scan", 4, 1, 32, 1);
    private final NumberSetting movementThreshold = new NumberSetting("Movement Threshold", 8, 0, 64, 1);

    private final BlockListSetting blockList = new BlockListSetting("Blocks");
    private final EntityListSetting entityList = new EntityListSetting("Entities");

    private final BlockScanner blockScanner = new BlockScanner();
    private final EntityScanner entityScanner = new EntityScanner();

    private volatile FinderSnapshot snapshot = FinderSnapshot.EMPTY;
    private boolean snapshotDirty = true;
    private int snapshotVersion = 0;

    private BlockPos lastTickPos = BlockPos.ZERO;
    private float adaptiveMultiplier = 1.0f;

    public Finder() {
        super("Finder", "Searches for specific blocks and entities globally.", Category.RENDER);
        addSetting(searchBlocks);
        addSetting(blockList);
        addSetting(searchEntities);
        addSetting(entityList);
        addSetting(range);
        addSetting(limit);
        addSetting(chunksPerScan);
        addSetting(movementThreshold);
    }

    @Override
    public void onEnable() {
        blockScanner.clear();
        entityScanner.clear();
        snapshot = FinderSnapshot.EMPTY;
        snapshotDirty = true;
        adaptiveMultiplier = 1.0f;
    }

    @Override
    public void onDisable() {
        blockScanner.clear();
        entityScanner.clear();
        clearSnapshot();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            if (!snapshot.isEmpty()) clearSnapshot();
            return;
        }

        BlockPos playerPos = mc.player.blockPosition();
        updateAdaptiveThrottle(playerPos);
        lastTickPos = playerPos;

        if (searchBlocks.getValue()) {
            if (blockScanner.needsRestart(playerPos, movementThreshold.getInt())) {
                blockScanner.adjustOrigin(playerPos, range.getInt(), blockList.getBlocks(), mc.player.chunkPosition());
                snapshotDirty = true;
            }
            int effectiveChunks = Math.round(chunksPerScan.getInt() * adaptiveMultiplier);
            blockScanner.scanNextBatch(mc.level, playerPos, Math.max(1, effectiveChunks), limit.getInt(), range.getInt());
            if (blockScanner.isDirty()) {
                snapshotDirty = true;
                blockScanner.clearDirty();
            }
        } else {
            if (blockScanner.isScanning() || blockScanner.getSize() > 0) {
                blockScanner.clear();
                snapshotDirty = true;
            }
        }

        entityScanner.tick();
        if (entityScanner.isReady(Constants.FINDER_SCAN_INTERVAL_TICKS)) {
            if (searchEntities.getValue()) {
                entityScanner.rescan(mc.level, mc.player, entityList, range.getValue(), limit.getInt());
            } else {
                entityScanner.clearResults();
            }
            if (entityScanner.isDirty()) {
                snapshotDirty = true;
                entityScanner.clearDirty();
            }
        }

        if (snapshotDirty) publishSnapshot();
    }

    private void updateAdaptiveThrottle(BlockPos currentPos) {
        double dist = Math.sqrt(lastTickPos.distSqr(currentPos));
        if (dist > 0.3) {
            adaptiveMultiplier = Math.max(0.25f, adaptiveMultiplier * 0.95f);
        } else if (dist < 0.1) {
            adaptiveMultiplier = Math.min(1.0f, adaptiveMultiplier * 1.05f);
        }
    }

    private void publishSnapshot() {
        snapshotVersion++;
        snapshot = new FinderSnapshot(
                blockScanner.getBlockPositions(),
                entityScanner.getTargets(),
                snapshotVersion
        );
        snapshotDirty = false;
    }

    private void clearSnapshot() {
        snapshot = FinderSnapshot.EMPTY;
        snapshotDirty = false;
    }

    public void onChunkLoad(ChunkPos pos) {
        if (!isEnabled() || !searchBlocks.getValue() || mc.player == null) return;
        BlockPos chunkCenter = new BlockPos(pos.getMinBlockX() + 8, 64, pos.getMinBlockZ() + 8);
        if (mc.player.blockPosition().distSqr(chunkCenter) <= (double) range.getInt() * range.getInt()) {
            blockScanner.addChunk(pos);
            snapshotDirty = true;
        }
    }

    public FinderSnapshot getSnapshot() {
        return snapshot;
    }
}
