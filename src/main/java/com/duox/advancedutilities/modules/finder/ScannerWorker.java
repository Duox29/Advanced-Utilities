package com.duox.advancedutilities.modules.finder;

import com.duox.advancedutilities.system.Constants;
import com.duox.advancedutilities.mixin.IPalettedContainerAccessor;
import com.duox.advancedutilities.mixin.IPalettedContainerDataAccessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * Dedicated background worker that scans loaded chunk sections for target blocks.
 *
 * Design goals (modeled after Wurst/Cheatutils style scanners):
 * - Never blocks the main thread: communication happens through immutable command
 *   messages and a single volatile {@link Publication} slot (no locks on hot paths).
 * - Incremental coverage: following the player only enqueues/prunes the delta band,
 *   it never restarts the whole region.
 * - Palette fast path: reads the section's PalettedContainer storage directly and
 *   matches palette indices against a small boolean table instead of doing a virtual
 *   getBlockState dispatch + Block.getId for every cell.
 * - Primitive-only collections (fastutil) everywhere hot: zero boxing, zero per-tick
 *   garbage on the main thread.
 */
final class ScannerWorker {

    record Publication(long[] positions, long seq) {
        static final Publication EMPTY = new Publication(new long[0], 0L);
    }

    private record Config(
            ClientLevel level,
            boolean[] targetStateIds,
            int range,
            long rangeSq,
            int maxResults,
            int sectionsPerChunk,
            int radiusChunks
    ) {}

    private static final Logger LOGGER = LogManager.getLogger();

    /** 6 bits for the section index => supports worlds up to 1024 blocks tall. */
    private static final int SECTION_INDEX_BITS = 6;
    private static final int MAX_SECTION_INDEX = (1 << SECTION_INDEX_BITS) - 1;

    private static final long IDLE_PARK_NANOS = 1_000_000L; // 1 ms
    private static final int REFRESH_SECTIONS_PER_PASS = Constants.FINDER_REFRESH_SECTIONS_PER_PASS;
    private static final long REFRESH_MIN_INTERVAL_NANOS = Constants.FINDER_REFRESH_MIN_INTERVAL_NANOS;

    // ---- shared / main-thread facing ----
    private final ConcurrentLinkedQueue<Runnable> commands = new ConcurrentLinkedQueue<>();
    private final AtomicLong seqGenerator = new AtomicLong();
    private volatile Publication publication = Publication.EMPTY;
    private volatile boolean running = true;
    private volatile int batchSections = 96;

    // ---- worker-thread owned ----
    private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue(1024);
    private final LongOpenHashSet queued = new LongOpenHashSet(1024);
    private final Long2ObjectOpenHashMap<long[]> results = new Long2ObjectOpenHashMap<>(1024);
    private final LongArrayList scratch = new LongArrayList(256);
    private final LongArrayList tmpKeys = new LongArrayList(256);
    private final Thread thread;

    private Config cfg;
    private int centerX, centerY, centerZ;   // player block coords
    private int centerCX, centerCZ;          // player chunk coords
    private long totalCount;
    private long lastRefreshNanos;
    private int refreshOffset;
    private boolean fastPathEnabled = true;

    ScannerWorker() {
        this.thread = new Thread(this::runLoop, "AU-Finder-Scanner");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    // ==================== main-thread API (non-blocking) ====================

    void configure(ClientLevel level, List<Block> targets, int range, int maxResults,
                   BlockPos center, int sectionsPerChunk) {
        boolean[] stateIds = buildStateIdMap(targets);
        int radius = Math.max(0, Math.min(2048, (range + 15) / 16));
        Config c = new Config(level, stateIds, range, (long) range * range, maxResults, sectionsPerChunk, radius);
        int cx = center.getX() >> 4, cz = center.getZ() >> 4;
        post(() -> applyConfigure(c, cx, cz, center));
    }

    void updateLimit(int maxResults) {
        post(() -> {
            if (cfg != null && cfg.maxResults() != maxResults) {
                cfg = new Config(cfg.level(), cfg.targetStateIds(), cfg.range(), cfg.rangeSq(),
                        maxResults, cfg.sectionsPerChunk(), cfg.radiusChunks());
            }
        });
    }

    void updateBatchChunks(int chunks) {
        this.batchSections = Math.max(1, chunks) << 4; // one chunk ~= 16 sections on average
    }

    void updateCenter(BlockPos pos, int chunkX, int chunkZ) {
        post(() -> applyUpdateCenter(pos.getX(), pos.getY(), pos.getZ(), chunkX, chunkZ));
    }

    void addChunk(int chunkX, int chunkZ) {
        post(() -> enqueueChunkIfInside(chunkX, chunkZ));
    }

    void removeChunk(int chunkX, int chunkZ) {
        post(() -> applyRemoveChunk(chunkX, chunkZ));
    }

    /** Clears all results and queues but keeps the worker alive and configured. */
    void clear() {
        post(this::applyClear);
    }

    void shutdown() {
        running = false;
        commands.clear();
        LockSupport.unpark(thread);
    }

    /** Returns the newest publication whose seq is greater than lastSeq, else null. */
    Publication poll(long lastSeq) {
        Publication p = publication;
        return p.seq() > lastSeq ? p : null;
    }

    private void post(Runnable r) {
        if (running) {
            commands.add(r);
            LockSupport.unpark(thread);
        }
    }

    // ==================== worker loop ====================

    private void runLoop() {
        while (running) {
            try {
                boolean mutated = false;
                Runnable cmd;
                while ((cmd = commands.poll()) != null) {
                    cmd.run();
                    mutated = true;
                }
                mutated |= drainScanQueue();
                if (mutated) {
                    publish();
                    continue; // keep draining before considering a park
                }
                // Park whenever a wake produced no mutation. This also covers the
                // stalled case (queue full of not-yet-loaded chunks) - requeueing
                // them in a hot spin would burn a core for nothing.
                maybeRefreshStale();
                LockSupport.parkNanos(IDLE_PARK_NANOS);
            } catch (Throwable t) {
                LOGGER.warn("Finder scanner worker error: {}", t.toString());
                LockSupport.parkNanos(50_000_000L); // back off on repeated failures
            }
        }
    }

    private boolean drainScanQueue() {
        Config c = cfg;
        if (c == null || c.targetStateIds().length == 0 || c.level() == null) {
            if (!queue.isEmpty()) {
                queue.clear();
                queued.clear();
            }
            return false;
        }
        int budget = batchSections;
        int visitsLeft = queue.size();
        boolean mutated = false;

        while (budget > 0 && visitsLeft-- > 0 && !queue.isEmpty()) {
            if (totalCount >= c.maxResults()) break; // saturated; resumes after pruning

            long key = queue.dequeueLong();
            queued.remove(key);

            long chunkKey = key >> SECTION_INDEX_BITS;
            int idx = (int) (key & MAX_SECTION_INDEX);
            int cx = ChunkPos.getX(chunkKey);
            int cz = ChunkPos.getZ(chunkKey);

            if (outsideSquare(cx, cz, c.radiusChunks())) continue; // stale entry, dropped for free

            LevelChunk chunk = c.level().getChunkSource().getChunk(cx, cz, false);
            if (chunk == null) {
                enqueueInternal(key); // not loaded yet; retry later at the tail
                continue;
            }
            budget--;
            mutated |= scanSection(chunk, c, cx, cz, idx);
        }
        return mutated;
    }

    private boolean scanSection(LevelChunk chunk, Config c, int cx, int cz, int idx) {
        long key = sectionKey(cx, cz, idx);
        LevelChunkSection[] sections = chunk.getSections();
        if (idx >= sections.length) {
            return replaceResult(key, null);
        }
        LevelChunkSection section = sections[idx];
        if (section == null || section.hasOnlyAir()) {
            return replaceResult(key, null);
        }

        int baseY = chunk.getMinBuildHeight() + (idx << 4);
        if (baseY + 15 < centerY - c.range() || baseY > centerY + c.range()) {
            return replaceResult(key, null); // vertical band cull
        }

        scratch.clear();
        if (fastPathEnabled) {
            try {
                fastScan(section, c, baseY, cx, cz);
            } catch (Throwable t) {
                fastPathEnabled = false;
                scratch.clear();
                LOGGER.info("Finder palette fast path disabled ({}); using fallback", t.toString());
                slowScan(section, c, baseY, cx, cz);
            }
        } else {
            slowScan(section, c, baseY, cx, cz);
        }

        long[] arr = scratch.isEmpty() ? null : scratch.toLongArray();
        return replaceResult(key, arr);
    }

    /**
     * Fast path: read the PalettedContainer's raw bit storage and match palette
     * indices against a boolean table. Avoids coordinate math, the virtual palette
     * dispatch and Block.getId for every non-matching cell (~4096 cells become pure
     * integer array work).
     */
    private void fastScan(LevelChunkSection section, Config c, int baseY, int cx, int cz) throws Throwable {
        PalettedContainer<BlockState> states = section.getStates();
        Object data = ((IPalettedContainerAccessor<BlockState>) (Object) states).getData();
        if (!(data instanceof IPalettedContainerDataAccessor acc)) {
            throw new IllegalStateException("PalettedContainer data accessor not applied");
        }
        BitStorage storage = acc.getStorage();
        @SuppressWarnings("unchecked")
        Palette<BlockState> palette = (Palette<BlockState>) acc.getPalette();

        // Pass 1: highest palette index actually stored in this section.
        int maxIndex = -1;
        for (int i = 0; i < 4096; i++) {
            int v = storage.get(i);
            if (v > maxIndex) maxIndex = v;
        }
        if (maxIndex < 0) return;

        // Pass 2: build match table (palette index -> is target).
        boolean[] table = new boolean[maxIndex + 1];
        boolean[] targets = c.targetStateIds();
        boolean any = false;
        try {
            for (int v = 0; v <= maxIndex; v++) {
                BlockState s = palette.valueFor(v); // null for unused linear slots
                if (s != null) {
                    int gid = Block.getId(s);
                    any |= (table[v] = gid >= 0 && gid < targets.length && targets[gid]);
                }
            }
        } catch (RuntimeException ignored) {
            // Single-value palettes throw IllegalStateException and sparse palettes
            // throw MissingPaletteEntryException for unused slots. Unmatched default
            // (false) is correct for every slot no stored cell references.
        }
        if (!any) return;

        // Pass 3: decode matching cells into packed world positions.
        int wxBase = cx << 4;
        int wzBase = cz << 4;
        long rangeSq = c.rangeSq();
        long[] out = new long[16];
        int outCnt = 0;
        for (int i = 0; i < 4096; i++) {
            if (!table[storage.get(i)]) continue;
            int wy = baseY + (i >> 8);
            int wz = wzBase + ((i >> 4) & 15);
            int wx = wxBase + (i & 15);
            int dx = wx - centerX;
            int dy = wy - centerY;
            int dz = wz - centerZ;
            if ((long) dx * dx + (long) dy * dy + (long) dz * dz <= rangeSq) {
                if (outCnt == out.length) {
                    long[] grown = new long[outCnt << 1];
                    System.arraycopy(out, 0, grown, 0, outCnt);
                    out = grown;
                }
                out[outCnt++] = BlockPos.asLong(wx, wy, wz);
            }
        }
        for (int i = 0; i < outCnt; i++) scratch.add(out[i]);
    }

    /** Public-API fallback path; kept for resilience if the accessor mixin ever fails. */
    private void slowScan(LevelChunkSection section, Config c, int baseY, int cx, int cz) {
        boolean[] targets = c.targetStateIds();
        long rangeSq = c.rangeSq();
        int wxBase = cx << 4;
        int wzBase = cz << 4;
        for (int y = 0; y < 16; y++) {
            int wy = baseY + y;
            for (int z = 0; z < 16; z++) {
                int wz = wzBase + z;
                for (int x = 0; x < 16; x++) {
                    BlockState s = section.getBlockState(x, y, z);
                    int gid = Block.getId(s);
                    if (gid < 0 || gid >= targets.length || !targets[gid]) continue;
                    int wx = wxBase + x;
                    int dx = wx - centerX;
                    int dy = wy - centerY;
                    int dz = wz - centerZ;
                    if ((long) dx * dx + (long) dy * dy + (long) dz * dz <= rangeSq) {
                        scratch.add(BlockPos.asLong(wx, wy, wz));
                    }
                }
            }
        }
    }

    // ==================== commands (executed on worker) ====================

    private void applyConfigure(Config newCfg, int cx, int cz, BlockPos center) {
        cfg = newCfg;
        centerCX = cx;
        centerCZ = cz;
        centerX = center.getX();
        centerZ = center.getZ();
        centerY = center.getY();
        queue.clear();
        queued.clear();
        results.clear();
        totalCount = 0;
        refreshOffset = 0;
        if (newCfg.targetStateIds().length > 0) {
            enqueueSquare(newCfg.radiusChunks());
        }
    }

    private void applyUpdateCenter(int px, int py, int pz, int ncx, int ncz) {
        int oldCx = centerCX, oldCz = centerCZ;
        centerX = px;
        centerY = py;
        centerZ = pz;
        if (ncx == oldCx && ncz == oldCz) return;
        centerCX = ncx;
        centerCZ = ncz;
        Config c = cfg;
        if (c == null) return;
        int r = c.radiusChunks();

        // Prune results outside the new square (saturation lifts automatically).
        collectAndPrune(r);

        // Enqueue only newly-entering chunks (delta bands), never the whole square.
        if (c.targetStateIds().length > 0) {
            int minCx = ncx - r, maxCx = ncx + r;
            int minCz = ncz - r, maxCz = ncz + r;
            for (int x = minCx; x <= maxCx; x++) {
                if (x >= oldCx - r && x <= oldCx + r) continue; // column already covered horizontally
                for (int z = minCz; z <= maxCz; z++) enqueueChunkIfInside(x, z);
            }
            for (int z = minCz; z <= maxCz; z++) {
                if (z >= oldCz - r && z <= oldCz + r) continue;
                for (int x = minCx; x <= maxCx; x++) enqueueChunkIfInside(x, z);
            }
        }
    }

    private void collectAndPrune(int radius) {
        tmpKeys.clear();
        for (LongIterator it = results.keySet().iterator(); it.hasNext(); ) {
            long key = it.nextLong();
            long ck = key >> SECTION_INDEX_BITS;
            if (outsideSquare(ChunkPos.getX(ck), ChunkPos.getZ(ck), radius)) tmpKeys.add(key);
        }
        for (int i = 0; i < tmpKeys.size(); i++) {
            long key = tmpKeys.getLong(i);
            long[] removed = results.remove(key);
            if (removed != null) {
                totalCount -= removed.length;
            }
        }
        // Drop queued entries that left the square (FIFO copies are re-validated at dequeue).
        tmpKeys.clear();
        for (LongIterator it = queued.iterator(); it.hasNext(); ) {
            long key = it.nextLong();
            long ck = key >> SECTION_INDEX_BITS;
            if (outsideSquare(ChunkPos.getX(ck), ChunkPos.getZ(ck), radius)) tmpKeys.add(key);
        }
        for (int i = 0; i < tmpKeys.size(); i++) {
            queued.remove(tmpKeys.getLong(i));
        }
    }

    private void applyRemoveChunk(int cx, int cz) {
        long ck = ChunkPos.asLong(cx, cz);
        boolean removedAny = false;
        tmpKeys.clear();
        for (LongIterator it = results.keySet().iterator(); it.hasNext(); ) {
            long key = it.nextLong();
            if ((key >> SECTION_INDEX_BITS) == ck) tmpKeys.add(key);
        }
        for (int i = 0; i < tmpKeys.size(); i++) {
            long[] removed = results.remove(tmpKeys.getLong(i));
            if (removed != null) {
                totalCount -= removed.length;
                removedAny = true;
            }
        }
        tmpKeys.clear();
        for (LongIterator it = queued.iterator(); it.hasNext(); ) {
            long key = it.nextLong();
            if ((key >> SECTION_INDEX_BITS) == ck) tmpKeys.add(key);
        }
        for (int i = 0; i < tmpKeys.size(); i++) {
            queued.remove(tmpKeys.getLong(i));
        }
    }

    private void applyClear() {
        queue.clear();
        queued.clear();
        results.clear();
        totalCount = 0;
        refreshOffset = 0;
    }

    /** Staggered round-robin rescan of already-scanned sections so block edits show up
     *  without any extra event hooks. Only runs while idle and rate-limited. */
    private void maybeRefreshStale() {
        Config c = cfg;
        if (c == null || results.isEmpty() || totalCount >= c.maxResults()) return;
        long now = System.nanoTime();
        if (now - lastRefreshNanos < REFRESH_MIN_INTERVAL_NANOS) return;
        lastRefreshNanos = now;

        long[] keys = results.keySet().toLongArray();
        int n = Math.min(REFRESH_SECTIONS_PER_PASS, keys.length);
        if (refreshOffset >= keys.length) refreshOffset = 0;
        for (int i = 0; i < n; i++) {
            enqueueInternal(keys[(refreshOffset + i) % keys.length]); // atomic replace on rescan, no flicker
        }
        refreshOffset += n;
    }

    // ==================== helpers ====================

    private void enqueueSquare(int radius) {
        int ccx = centerCX, ccz = centerCZ;
        enqueueChunkIfInside(ccx, ccz);
        for (int ring = 1; ring <= radius; ring++) {
            int minX = ccx - ring, maxX = ccx + ring;
            int minZ = ccz - ring, maxZ = ccz + ring;
            for (int x = minX; x <= maxX; x++) enqueueChunkIfInside(x, minZ);
            for (int z = minZ + 1; z <= maxZ; z++) enqueueChunkIfInside(maxX, z);
            for (int x = maxX - 1; x >= minX; x--) enqueueChunkIfInside(x, maxZ);
            for (int z = maxZ - 1; z > minZ; z--) enqueueChunkIfInside(minX, z);
        }
    }

    private void enqueueChunkIfInside(int cx, int cz) {
        Config c = cfg;
        if (c == null || outsideSquare(cx, cz, c.radiusChunks())) return;
        int sections = Math.min(c.sectionsPerChunk(), MAX_SECTION_INDEX + 1);
        for (int idx = 0; idx < sections; idx++) {
            enqueueInternal(sectionKey(cx, cz, idx));
        }
    }

    private void enqueueInternal(long key) {
        if (queued.add(key)) {
            queue.enqueue(key);
        }
    }

    private boolean outsideSquare(int cx, int cz, int radius) {
        return cx < centerCX - radius || cx > centerCX + radius
                || cz < centerCZ - radius || cz > centerCZ + radius;
    }

    private boolean replaceResult(long key, long[] arr) {
        if (arr == null) {
            long[] old = results.remove(key);
            if (old != null) {
                totalCount -= old.length;
                return true;
            }
            return false;
        }
        long[] old = results.put(key, arr);
        totalCount += arr.length - (old == null ? 0 : old.length);
        return true;
    }

    private void publish() {
        long[] out = new long[(int) Math.min(totalCount, Integer.MAX_VALUE)];
        int o = 0;
        for (long[] arr : results.values()) {
            System.arraycopy(arr, 0, out, o, arr.length);
            o += arr.length;
        }
        publication = new Publication(out, seqGenerator.incrementAndGet());
    }

    private static long sectionKey(int cx, int cz, int idx) {
        return (ChunkPos.asLong(cx, cz) << SECTION_INDEX_BITS) | idx;
    }

    /** Global combined-state-id lookup table sized over ALL possible states.
     *  Bug fix: the old code sized it from the default state id only, silently missing
     *  rotated variants whose global ids sat above the default (e.g. sideways logs). */
    private static boolean[] buildStateIdMap(List<Block> targets) {
        if (targets.isEmpty()) return new boolean[0];
        int max = 0;
        for (Block b : targets) {
            for (BlockState s : b.getStateDefinition().getPossibleStates()) {
                int id = Block.getId(s);
                if (id >= max) max = id + 1;
            }
        }
        boolean[] map = new boolean[max];
        for (Block b : targets) {
            for (BlockState s : b.getStateDefinition().getPossibleStates()) {
                map[Block.getId(s)] = true;
            }
        }
        return map;
    }
}
