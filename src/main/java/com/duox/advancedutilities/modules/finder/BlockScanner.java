package com.duox.advancedutilities.modules.finder;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Main-thread facade over {@link ScannerWorker}. All methods are cheap, non-blocking
 * message posts; results are consumed via {@link #poll(long)} which returns an
 * immutable result array only when the worker actually produced different data.
 */
public class BlockScanner {

    /** Immutable result set handed from the worker to the main thread. */
    public record Publication(long[] positions, long seq) {}

    private final ScannerWorker worker = new ScannerWorker();

    /** Full reset + initial spiral scan of the square around the center chunk. */
    public void configure(ClientLevel level, List<Block> targets, int range, int maxResults,
                          BlockPos center, int sectionsPerChunk) {
        worker.configure(level, targets, range, maxResults, center, sectionsPerChunk);
    }

    public void updateLimit(int maxResults) {
        worker.updateLimit(maxResults);
    }

    private long lastPostedPos = Long.MIN_VALUE;
    private int lastPostedCX = Integer.MIN_VALUE;
    private int lastPostedCZ = Integer.MIN_VALUE;

    /** Cheap; call every tick. Posts to the worker only on a chunk crossing or a
     *  movement of more than {@code CENTER_HYSTERESIS} blocks - steady-state walking
     *  produces zero allocations and zero worker wake-ups. */
    public void updateCenter(BlockPos pos, ChunkPos chunk) {
        long packed = pos.asLong();
        if (chunk.x == lastPostedCX && chunk.z == lastPostedCZ && nearLastPosted(pos)) return;
        lastPostedPos = packed;
        lastPostedCX = chunk.x;
        lastPostedCZ = chunk.z;
        worker.updateCenter(pos, chunk.x, chunk.z);
    }

    private boolean nearLastPosted(BlockPos pos) {
        if (lastPostedPos == Long.MIN_VALUE) return false;
        int dx = pos.getX() - BlockPos.getX(lastPostedPos);
        int dy = pos.getY() - BlockPos.getY(lastPostedPos);
        int dz = pos.getZ() - BlockPos.getZ(lastPostedPos);
        long hysteresisSq = (long) CENTER_HYSTERESIS * CENTER_HYSTERESIS;
        return (long) dx * dx + (long) dy * dy + (long) dz * dz <= hysteresisSq;
    }

    private static final int CENTER_HYSTERESIS = 4;

    public void setBatchChunks(int chunksPerScan) {
        worker.updateBatchChunks(chunksPerScan);
    }

    public void addChunk(ChunkPos pos) {
        worker.addChunk(pos.x, pos.z);
    }

    public void removeChunk(ChunkPos pos) {
        worker.removeChunk(pos.x, pos.z);
    }

    /** @return the latest publication if newer than lastSeq, otherwise null. */
    public Publication poll(long lastSeq) {
        ScannerWorker.Publication p = worker.poll(lastSeq);
        return p == null ? null : new Publication(p.positions(), p.seq());
    }

    public void clear() {
        worker.clear();
    }

    public void shutdown() {
        worker.shutdown();
    }
}
