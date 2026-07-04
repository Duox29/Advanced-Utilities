package com.duox.advancedutilities.modules.finder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.*;

public class BlockScanner {

    private static final int INITIAL_CAPACITY = 256;

    private final ArrayDeque<ChunkPos> pendingChunks = new ArrayDeque<>();
    private final HashSet<Long> foundBlockKeys = new HashSet<>();
    private final HashSet<ChunkPos> scannedChunks = new HashSet<>();

    private long[] buffer = new long[INITIAL_CAPACITY];
    private int size = 0;

    private BlockPos scanOrigin;
    private Set<Block> lastTargetBlocks = Set.of();

    private boolean[] targetStateIdMap = new boolean[0];

    private boolean dirty = false;

    public void restart(BlockPos playerPos, int range, List<Block> targetBlocks, ChunkPos centerChunk) {
        pendingChunks.clear();
        scannedChunks.clear();
        foundBlockKeys.clear();
        clearPositions();

        lastTargetBlocks = Set.copyOf(targetBlocks);
        buildTargetStateMap(targetBlocks);
        scanOrigin = playerPos.immutable();

        if (targetStateIdMap.length == 0) return;

        int radiusChunks = Math.max(0, range / 16);
        enqueueChunksSpiral(centerChunk, radiusChunks);
        dirty = true;
    }

    public void adjustOrigin(BlockPos playerPos, int range, List<Block> targetBlocks, ChunkPos centerChunk) {
        if (scanOrigin == null) {
            restart(playerPos, range, targetBlocks, centerChunk);
            return;
        }

        Set<Block> newTargets = Set.copyOf(targetBlocks);
        boolean blocksChanged = !newTargets.equals(lastTargetBlocks);
        lastTargetBlocks = newTargets;

        scanOrigin = playerPos.immutable();
        buildTargetStateMap(targetBlocks);

        long rangeSq = (long) range * range;
        int writeIdx = 0;
        for (int i = 0; i < size; i++) {
            long packed = buffer[i];
            int x = BlockPos.getX(packed);
            int y = BlockPos.getY(packed);
            int z = BlockPos.getZ(packed);

            double dx = x + 0.5 - (playerPos.getX() + 0.5);
            double dy = y + 0.5 - (playerPos.getY() + 0.5);
            double dz = z + 0.5 - (playerPos.getZ() + 0.5);

            if (dx * dx + dy * dy + dz * dz <= rangeSq) {
                buffer[writeIdx++] = packed;
            } else {
                foundBlockKeys.remove(packed);
            }
        }
        size = writeIdx;

        scannedChunks.clear();

        int radiusChunks = Math.max(0, range / 16);
        enqueueChunksSpiral(centerChunk, radiusChunks);

        dirty = true;
    }

    public void addChunk(ChunkPos pos) {
        if (!scannedChunks.contains(pos) && !pendingChunks.contains(pos)) {
            pendingChunks.addFirst(pos);
        }
    }

    private void buildTargetStateMap(List<Block> targetBlocks) {
        int maxId = 0;
        for (Block block : targetBlocks) {
            maxId = Math.max(maxId, Block.getId(block.defaultBlockState()) + 1);
        }
        targetStateIdMap = new boolean[maxId];
        for (Block block : targetBlocks) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                int id = Block.getId(state);
                if (id >= 0 && id < targetStateIdMap.length) {
                    targetStateIdMap[id] = true;
                }
            }
        }
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

    public void scanNextBatch(Level level, BlockPos playerPos, int maxChunks, int maxResults, int range) {
        if (level == null || pendingChunks.isEmpty() || targetStateIdMap.length == 0) return;
        if (size >= maxResults) return;

        int processed = 0;
        long rangeSq = (long) range * range;

        while (processed < maxChunks && !pendingChunks.isEmpty() && size < maxResults) {
            ChunkPos chunkPos = pendingChunks.pollFirst();
            processed++;

            if (!level.hasChunk(chunkPos.x, chunkPos.z)) {
                pendingChunks.addLast(chunkPos);
                continue;
            }

            scannedChunks.add(chunkPos);

            LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
            scanChunk(chunk, playerPos, range, rangeSq, maxResults);
        }

        dirty = true;
    }

    private void scanChunk(LevelChunk chunk, BlockPos playerPos, int range, long rangeSq, int maxResults) {
        LevelChunkSection[] sections = chunk.getSections();
        int playerY = playerPos.getY();

        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir()) continue;

            int baseY = chunk.getSectionYFromSectionIndex(i) * 16;

            if (baseY + 16 < playerY - range || baseY > playerY + range) continue;

            for (int x = 0; x < 16 && size < maxResults; x++) {
                for (int y = 0; y < 16 && size < maxResults; y++) {
                    for (int z = 0; z < 16 && size < maxResults; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        int id = Block.getId(state);
                        if (id < 0 || id >= targetStateIdMap.length || !targetStateIdMap[id]) continue;

                        int worldX = chunk.getPos().getMinBlockX() + x;
                        int worldY = baseY + y;
                        int worldZ = chunk.getPos().getMinBlockZ() + z;

                        if (playerPos.distToCenterSqr(worldX + 0.5, worldY + 0.5, worldZ + 0.5) > rangeSq) continue;

                        long key = BlockPos.asLong(worldX, worldY, worldZ);
                        if (!foundBlockKeys.add(key)) continue;

                        addPosition(key);
                    }
                }
            }
        }
    }

    private void addPosition(long key) {
        if (size >= buffer.length) {
            long[] newBuf = new long[buffer.length * 2];
            System.arraycopy(buffer, 0, newBuf, 0, buffer.length);
            buffer = newBuf;
        }
        buffer[size++] = key;
    }

    private void clearPositions() {
        size = 0;
    }

    public void clear() {
        pendingChunks.clear();
        scannedChunks.clear();
        foundBlockKeys.clear();
        clearPositions();
        scanOrigin = null;
        lastTargetBlocks = Set.of();
        targetStateIdMap = new boolean[0];
        dirty = false;
    }

    public boolean needsRestart(BlockPos playerPos, int movementThreshold) {
        if (scanOrigin == null) return true;
        if (movementThreshold <= 0) return false;
        return scanOrigin.distSqr(playerPos) > (double) movementThreshold * movementThreshold;
    }

    public boolean isScanning() {
        return !pendingChunks.isEmpty();
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    public long[] getBlockPositions() {
        long[] result = new long[size];
        System.arraycopy(buffer, 0, result, 0, size);
        return result;
    }

    public int getSize() {
        return size;
    }

    public BlockPos getScanOrigin() {
        return scanOrigin;
    }
}
