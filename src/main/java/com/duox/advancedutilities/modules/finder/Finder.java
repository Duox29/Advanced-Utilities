package com.duox.advancedutilities.modules.finder;

import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Constants;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.BlockListSetting;
import com.duox.advancedutilities.system.settings.BooleanSetting;
import com.duox.advancedutilities.system.settings.EntityListSetting;
import com.duox.advancedutilities.system.settings.NumberSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import java.util.*;

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

    private final ArrayDeque<ChunkPos> pendingChunks = new ArrayDeque<>();
    private final HashSet<Long> foundBlockKeys = new HashSet<>();
    private final ArrayList<AABB> blockBoxes = new ArrayList<>();

    private List<FinderSnapshot.EntityRenderTarget> entityTargets = List.of();
    private volatile FinderSnapshot snapshot = FinderSnapshot.EMPTY;

    private BlockPos scanOrigin;
    private Set<Block> cachedTargetBlocks = Set.of();
    private int entityTickCounter = 0;

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
        resetAll();
        entityTickCounter = Constants.FINDER_SCAN_INTERVAL_TICKS;
    }

    @Override
    public void onDisable() {
        resetAll();
        snapshot = FinderSnapshot.EMPTY;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) {
            snapshot = FinderSnapshot.EMPTY;
            return;
        }

        if (searchBlocks.getValue()) {
            restartBlockScanIfNeeded();
            scanNextChunkBatch();
        } else {
            clearBlockResults();
        }

        if (entityTickCounter++ >= Constants.FINDER_SCAN_INTERVAL_TICKS) {
            if (searchEntities.getValue()) {
                rescanEntities();
            } else {
                entityTargets = List.of();
                publishSnapshot();
            }
            entityTickCounter = 0;
        }
    }

    public FinderSnapshot getSnapshot() {
        return snapshot;
    }

    private void resetAll() {
        pendingChunks.clear();
        foundBlockKeys.clear();
        blockBoxes.clear();
        entityTargets = List.of();
        scanOrigin = null;
        cachedTargetBlocks = Set.of();
    }

    private void clearBlockResults() {
        if (!blockBoxes.isEmpty() || !pendingChunks.isEmpty()) {
            pendingChunks.clear();
            foundBlockKeys.clear();
            blockBoxes.clear();
            scanOrigin = null;
            publishSnapshot();
        }
    }

    private void restartBlockScanIfNeeded() {
        BlockPos playerPos = mc.player.blockPosition();

        boolean restart = false;
        if (scanOrigin == null) {
            restart = true;
        } else {
            int threshold = movementThreshold.getInt();
            restart = scanOrigin.distSqr(playerPos) > (double) threshold * threshold;
        }

        if (!restart) return;

        pendingChunks.clear();
        foundBlockKeys.clear();
        blockBoxes.clear();

        cachedTargetBlocks = Set.copyOf(blockList.getBlocks());
        scanOrigin = playerPos.immutable();

        if (cachedTargetBlocks.isEmpty()) {
            publishSnapshot();
            return;
        }

        int radiusChunks = Math.max(0, range.getInt() / 16);
        enqueueChunksSpiral(mc.player.chunkPosition(), radiusChunks);
        publishSnapshot();
    }

    private void enqueueChunksSpiral(ChunkPos center, int radius) {
        pendingChunks.add(new ChunkPos(center.x, center.z));

        for (int ring = 1; ring <= radius; ring++) {
            int minX = center.x - ring;
            int maxX = center.x + ring;
            int minZ = center.z - ring;
            int maxZ = center.z + ring;

            for (int x = minX; x <= maxX; x++) pendingChunks.add(new ChunkPos(x, minZ));
            for (int z = minZ + 1; z <= maxZ; z++) pendingChunks.add(new ChunkPos(maxX, z));
            for (int x = maxX - 1; x >= minX; x--) pendingChunks.add(new ChunkPos(x, maxZ));
            for (int z = maxZ - 1; z > minZ; z--) pendingChunks.add(new ChunkPos(minX, z));
        }
    }

    private void scanNextChunkBatch() {
        if (mc.player == null || mc.level == null) return;
        if (pendingChunks.isEmpty()) return;
        if (cachedTargetBlocks.isEmpty()) return;
        if (blockBoxes.size() >= limit.getInt()) return;

        int processed = 0;
        int maxChunks = chunksPerScan.getInt();
        int maxResults = limit.getInt();
        long rangeSq = (long) range.getInt() * range.getInt();
        BlockPos playerPos = mc.player.blockPosition();

        while (processed < maxChunks && !pendingChunks.isEmpty() && blockBoxes.size() < maxResults) {
            ChunkPos chunkPos = pendingChunks.pollFirst();
            processed++;

            if (!mc.level.hasChunk(chunkPos.x, chunkPos.z)) continue;

            LevelChunk chunk = mc.level.getChunk(chunkPos.x, chunkPos.z);
            scanChunk(chunk, playerPos, rangeSq, maxResults);
        }

        publishSnapshot();
    }

    private void scanChunk(LevelChunk chunk, BlockPos playerPos, long rangeSq, int maxResults) {
        LevelChunkSection[] sections = chunk.getSections();

        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir()) continue;

            int baseY = chunk.getSectionYFromSectionIndex(i) * 16;

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        if (blockBoxes.size() >= maxResults) return;

                        BlockState state = section.getBlockState(x, y, z);
                        if (!cachedTargetBlocks.contains(state.getBlock())) continue;

                        int worldX = chunk.getPos().getMinBlockX() + x;
                        int worldY = baseY + y;
                        int worldZ = chunk.getPos().getMinBlockZ() + z;

                        double distSq = playerPos.distToCenterSqr(
                                worldX + 0.5D,
                                worldY + 0.5D,
                                worldZ + 0.5D
                        );                        if (distSq > rangeSq) continue;

                        long key = BlockPos.asLong(worldX, worldY, worldZ);
                        if (!foundBlockKeys.add(key)) continue;

                        blockBoxes.add(new AABB(worldX, worldY, worldZ, worldX + 1, worldY + 1, worldZ + 1));
                    }
                }
            }
        }
    }

    private void rescanEntities() {
        if (mc.player == null || mc.level == null) return;

        double r = range.getValue();
        int max = limit.getInt();

        AABB area = mc.player.getBoundingBox().inflate(r);
        List<Entity> all = mc.level.getEntities(mc.player, area);

        all.removeIf(entity -> !entityList.contains(entity.getType()));
        all.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(mc.player)));

        ArrayList<FinderSnapshot.EntityRenderTarget> results = new ArrayList<>(Math.min(max, all.size()));
        for (Entity entity : all) {
            if (results.size() >= max) break;
            results.add(FinderSnapshot.EntityRenderTarget.from(entity));
        }

        entityTargets = List.copyOf(results);
        publishSnapshot();
    }

    private void publishSnapshot() {
        snapshot = new FinderSnapshot(
                List.copyOf(blockBoxes),
                List.copyOf(entityTargets)
        );
    }
}