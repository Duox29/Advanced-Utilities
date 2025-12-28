package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.BlockListSetting;
import com.duox.advancedutilities.system.settings.BooleanSetting;
import com.duox.advancedutilities.system.settings.EntityListSetting;
import com.duox.advancedutilities.system.settings.NumberSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class Finder extends Module {

    // --- Settings ---
    private final BooleanSetting searchBlocks = new BooleanSetting("Search Blocks", true);
    private final BooleanSetting searchEntities = new BooleanSetting("Search Entities", true);

    // Range: 64 blocks radius is sufficient usually.
    private final NumberSetting range = new NumberSetting("Range", 64, 16, 256, 16);
    private final NumberSetting limit = new NumberSetting("Max Results", 1000, 10, 5000, 10);

    private final BlockListSetting blockList = new BlockListSetting("Blocks");
    private final EntityListSetting entityList = new EntityListSetting("Entities");

    // --- State Management ---
    // Sử dụng volatile để đảm bảo Visibility giữa các luồng.
    // Chúng ta sẽ thay thế hoàn toàn List này mỗi lần scan xong (Immutable pattern cho render thread).
    private volatile List<BlockPos> foundBlocks = Collections.emptyList();
    private volatile List<Entity> foundEntities = Collections.emptyList();

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
    }

    @Override
    public void onEnable() {
        tickCounter = 100; // Force scan immediately on enable
    }

    @Override
    public void onDisable() {
        foundBlocks = Collections.emptyList();
        foundEntities = Collections.emptyList();
        isScanningBlocks.set(false);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;

        // OPTIMIZATION: Chỉ quét mỗi 5 giây (20 ticks * 5 = 100)
        if (tickCounter++ >= 100) {
            scanNow();
            tickCounter = 0;
        }
    }

    private void scanNow() {
        // Entity scan is fast enough to run on main thread periodically
        if (searchEntities.getValue()) {
            scanEntities();
        } else {
            foundEntities = Collections.emptyList();
        }

        // Block scan is heavy, run async
        if (searchBlocks.getValue()) {
            scanBlocksAsync();
        } else {
            foundBlocks = Collections.emptyList();
        }
    }

    private void scanEntities() {
        if (mc.level == null) return;
        double r = range.getValue();
        AABB area = mc.player.getBoundingBox().inflate(r);

        List<Entity> results = new ArrayList<>();
        int max = limit.getInt();

        // Lấy danh sách entities an toàn trên main thread
        List<Entity> allEntities = mc.level.getEntities(mc.player, area);

        for (Entity entity : allEntities) {
            if (results.size() >= max) break;
            if (entityList.contains(entity.getType())) {
                results.add(entity);
            }
        }
        // Atomic swap
        this.foundEntities = results;
    }

    private void scanBlocksAsync() {
        if (mc.player == null || mc.level == null) return;
        // Nếu đang scan dở thì bỏ qua, đợi lần sau (tránh spam thread pool)
        if (isScanningBlocks.get()) return;

        // Snapshot data cần thiết từ Main Thread để mang sang Async Thread
        BlockPos playerPos = mc.player.blockPosition();
        net.minecraft.world.level.ChunkPos playerChunkPos = mc.player.chunkPosition();
        int radiusChunks = range.getInt() / 16;
        int maxResult = limit.getInt();

        List<net.minecraft.world.level.block.Block> targetBlocks = new ArrayList<>(blockList.getBlocks());

        if (targetBlocks.isEmpty()) {
            foundBlocks = Collections.emptyList();
            return;
        }

        isScanningBlocks.set(true);

        CompletableFuture.runAsync(() -> {
            try {
                List<BlockPos> results = new ArrayList<>();
                int minX = playerChunkPos.x - radiusChunks;
                int maxX = playerChunkPos.x + radiusChunks;
                int minZ = playerChunkPos.z - radiusChunks;
                int maxZ = playerChunkPos.z + radiusChunks;

                List<net.minecraft.world.level.ChunkPos> chunksToScan = new ArrayList<>();
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        chunksToScan.add(new net.minecraft.world.level.ChunkPos(x, z));
                    }
                }

                // Sorting chunks by distance helps finding closest blocks first
                chunksToScan.sort(Comparator.comparingInt(c ->
                        Math.abs(c.x - playerChunkPos.x) + Math.abs(c.z - playerChunkPos.z)
                ));

                for (net.minecraft.world.level.ChunkPos chunkPos : chunksToScan) {
                    if (results.size() >= maxResult) break;

                    // Note: Accessing chunks async needs care.
                    // In simple mods, reading getChunk usually works if chunk is loaded,
                    // but deep engine access might require synchronized checks.
                    // Assuming mc.level.getChunk is safe enough for read-only block state access here.
                    if (mc.level.hasChunkAt(chunkPos.x, chunkPos.z)) {
                        net.minecraft.world.level.chunk.LevelChunk chunk = mc.level.getChunk(chunkPos.x, chunkPos.z);

                        net.minecraft.world.level.chunk.LevelChunkSection[] sections = chunk.getSections();
                        for (int i = 0; i < sections.length; i++) {
                            net.minecraft.world.level.chunk.LevelChunkSection section = sections[i];
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
                                            // Quick distance check
                                            if (playerPos.distSqr(pos) <= range.getInt() * range.getInt()) {
                                                results.add(pos);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // Atomic swap: Thread-safe update without locking render thread
                this.foundBlocks = results;
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                isScanningBlocks.set(false);
            }
        });
    }

    public List<BlockPos> getFoundBlocks() {
        return foundBlocks;
    }

    public List<Entity> getFoundEntities() {
        return foundEntities;
    }
}