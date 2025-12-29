package com.duox.advancedutilities.modules;

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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/*
 * Searches for specific blocks and entities in the world and renders them.
 * Uses async scanning for blocks to avoid blocking the main thread.
 */
public class Finder extends Module {

    private static final Logger LOGGER = LoggerFactory.getLogger(Finder.class);

    private final BooleanSetting searchBlocks = new BooleanSetting("Search Blocks", true);
    private final BooleanSetting searchEntities = new BooleanSetting("Search Entities", true);
    private final NumberSetting range = new NumberSetting("Range", Constants.FINDER_DEFAULT_RANGE,
            Constants.FINDER_MIN_RANGE, Constants.FINDER_MAX_RANGE, 16);
    private final NumberSetting limit = new NumberSetting("Max Results", Constants.FINDER_DEFAULT_LIMIT,
            Constants.FINDER_MIN_LIMIT, Constants.FINDER_MAX_LIMIT, 10);
    
    // New Settings
    private final NumberSetting chunksPerScan = new NumberSetting("Chunks Per Scan", 8, 1, 32, 1);
    private final NumberSetting movementThreshold = new NumberSetting("Movement Threshold", 8, 0, 64, 1);

    private final BlockListSetting blockList = new BlockListSetting("Blocks");
    private final EntityListSetting entityList = new EntityListSetting("Entities");

    // State management
    private volatile List<BlockPos> foundBlocks = Collections.emptyList();
    private volatile List<Entity> foundEntities = Collections.emptyList();

    // Internal state for incremental scanning
    private final List<BlockPos> accumulatedBlocks = new ArrayList<>();
    private final List<ChunkPos> pendingChunks = new ArrayList<>();
    private BlockPos lastScanPos = null;
    
    private final AtomicBoolean isScanningBlocks = new AtomicBoolean(false);
    private int tickCounter = 0;

    public Finder() {
        super("Finder", "Searches for specific blocks and entities globally.", Category.RENDER);
        this.addSetting(searchBlocks);
        this.addSetting(blockList);
        this.addSetting(searchEntities);
        this.addSetting(entityList);
        this.addSetting(range);
        this.addSetting(limit);
        this.addSetting(chunksPerScan);
        this.addSetting(movementThreshold);
    }

    @Override
    public void onEnable() {
        resetScanState();
        tickCounter = Constants.FINDER_SCAN_INTERVAL_TICKS; // Force initial check
    }

    @Override
    public void onDisable() {
        foundBlocks = Collections.emptyList();
        foundEntities = Collections.emptyList();
        resetScanState();
    }

    private void resetScanState() {
        isScanningBlocks.set(false);
        pendingChunks.clear();
        accumulatedBlocks.clear();
        lastScanPos = null;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;

        // Entity scanning (fast, keep on main thread but maybe check interval)
        if (tickCounter++ >= Constants.FINDER_SCAN_INTERVAL_TICKS) {
            if (searchEntities.getValue()) {
                scanEntities();
            } else {
                foundEntities = Collections.emptyList();
            }
            tickCounter = 0;
        }

        // Block scanning logic
        if (searchBlocks.getValue()) {
            handleBlockScanning();
        } else {
            if (!foundBlocks.isEmpty()) {
                foundBlocks = Collections.emptyList();
                resetScanState();
            }
        }
    }

    private void handleBlockScanning() {
        BlockPos playerPos = mc.player.blockPosition();

        // Check for movement to restart scan
        boolean shouldRestart = false;
        if (lastScanPos == null) {
            shouldRestart = true;
        } else {
            double distSq = lastScanPos.distSqr(playerPos);
            double threshold = movementThreshold.getInt();
            if (distSq > threshold * threshold) {
                shouldRestart = true;
            }
        }

        if (shouldRestart) {
            // Cancel current scan logic if possible (we just clear pending)
            pendingChunks.clear();
            accumulatedBlocks.clear();
            
            // Populate pending chunks
            int r = range.getInt();
            int radiusChunks = r / 16;
            ChunkPos playerChunkPos = mc.player.chunkPosition();
            int minX = playerChunkPos.x - radiusChunks;
            int maxX = playerChunkPos.x + radiusChunks;
            int minZ = playerChunkPos.z - radiusChunks;
            int maxZ = playerChunkPos.z + radiusChunks;

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pendingChunks.add(new ChunkPos(x, z));
                }
            }

            // Sort by distance from player
            pendingChunks.sort(Comparator.comparingInt(c ->
                    Math.abs(c.x - playerChunkPos.x) + Math.abs(c.z - playerChunkPos.z)
            ));

            lastScanPos = playerPos;
            // Clear current results as we start over
            foundBlocks = Collections.emptyList();
        }

        // Process batch if we have chunks and not currently scanning
        if (!pendingChunks.isEmpty() && !isScanningBlocks.get()) {
            dispatchBatchScan();
        }
    }

    private void dispatchBatchScan() {
        if (mc.player == null || mc.level == null) return;
        
        // Capture the scan session ID (using lastScanPos as the token)
        BlockPos scanSessionToken = this.lastScanPos;
        
        isScanningBlocks.set(true);
        
        // Take a batch of chunks
        int batchSize = chunksPerScan.getInt();
        List<ChunkPos> batch = new ArrayList<>();
        while (batch.size() < batchSize && !pendingChunks.isEmpty()) {
            batch.add(pendingChunks.remove(0));
        }

        // Snapshot data needed for async
        BlockPos playerPos = mc.player.blockPosition();
        Level level = mc.level; // Capture level
        
        int rangeVal = range.getInt();
        long rangeSq = (long) rangeVal * rangeVal;
        int maxResult = limit.getInt();
        
        // Use Set for O(1) lookup
        Set<Block> targetBlocks = new HashSet<>(blockList.getBlocks());
        
        // Check current count safely
        int currentCount;
        synchronized (accumulatedBlocks) {
            currentCount = accumulatedBlocks.size();
        }
        
        if (targetBlocks.isEmpty() || currentCount >= maxResult) {
            isScanningBlocks.set(false);
            return;
        }

        CompletableFuture.runAsync(() -> {
            List<BlockPos> batchResults = new ArrayList<>();
            try {
                for (ChunkPos chunkPos : batch) {
                    if (currentCount + batchResults.size() >= maxResult) break;

                    if (level.hasChunkAt(chunkPos.x, chunkPos.z)) {
                        LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
                        
                        LevelChunkSection[] sections = chunk.getSections();
                        for (int i = 0; i < sections.length; i++) {
                            LevelChunkSection section = sections[i];
                            if (section == null || section.hasOnlyAir()) continue;

                            int bottomY = chunk.getSectionYFromSectionIndex(i) * 16;
                            
                            for (int x = 0; x < 16; x++) {
                                for (int y = 0; y < 16; y++) {
                                    for (int z = 0; z < 16; z++) {
                                        BlockState state = section.getBlockState(x, y, z);
                                        if (targetBlocks.contains(state.getBlock())) {
                                            int worldX = chunkPos.getMinBlockX() + x;
                                            int worldY = bottomY + y;
                                            int worldZ = chunkPos.getMinBlockZ() + z;
                                            
                                            BlockPos pos = new BlockPos(worldX, worldY, worldZ);
                                            if (playerPos.distSqr(pos) <= rangeSq) {
                                                batchResults.add(pos);
                                                if (currentCount + batchResults.size() >= maxResult) return; 
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Update results in thread-safe way
                synchronized (accumulatedBlocks) {
                    // Check if scan session is still valid
                    if (Objects.equals(scanSessionToken, Finder.this.lastScanPos)) {
                        accumulatedBlocks.addAll(batchResults);
                        foundBlocks = new ArrayList<>(accumulatedBlocks);
                    }
                }
                
            } catch (Exception e) {
                LOGGER.error("Error in Finder async scan", e);
            } finally {
                isScanningBlocks.set(false);
            }
        });
    }

    private void scanEntities() {
        if (mc.level == null) return;
        
        double r = range.getValue();
        AABB area = mc.player.getBoundingBox().inflate(r);

        List<Entity> results = new ArrayList<>();
        int max = limit.getInt();

        List<Entity> allEntities = mc.level.getEntities(mc.player, area);

        // Sort by distance
        allEntities.sort(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player)));

        for (Entity entity : allEntities) {
            if (results.size() >= max) break;
            if (entityList.contains(entity.getType())) {
                results.add(entity);
            }
        }
        this.foundEntities = results;
    }

    public List<BlockPos> getFoundBlocks() {
        return foundBlocks;
    }

    public List<Entity> getFoundEntities() {
        return foundEntities;
    }
}
