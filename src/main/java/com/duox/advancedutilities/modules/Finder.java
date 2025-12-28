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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class Finder extends Module {

    // --- Settings ---
    private final BooleanSetting searchBlocks = new BooleanSetting("Search Blocks", true);
    private final BooleanSetting searchEntities = new BooleanSetting("Search Entities", true);

    // Range: Min 4, Max 256 chunks/blocks radius, Step 1
    private final NumberSetting range = new NumberSetting("Range", 64, 16, 256, 16);
    // Limit: Max results to prevent lag rendering
    private final NumberSetting limit = new NumberSetting("Max Results", 1000, 10, 5000, 10);

    private final BlockListSetting blockList = new BlockListSetting("Blocks");
    private final EntityListSetting entityList = new EntityListSetting("Entities");

    // --- State Management ---
    // Dùng List copy để render thread đọc an toàn không bị ConcurrentModificationException
    private volatile List<BlockPos> foundBlocks = new ArrayList<>();
    private volatile List<Entity> foundEntities = new ArrayList<>();

    // Cờ kiểm soát luồng quét block để tránh chạy chồng chéo
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
        tickCounter = 0;
        scanNow(); // Quét ngay khi bật
    }

    @Override
    public void onDisable() {
        foundBlocks.clear();
        foundEntities.clear();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;

        // Giảm tải: Chỉ quét mỗi 20 ticks (1 giây) hoặc khi cần thiết
        if (tickCounter++ > 20) {
            scanNow();
            tickCounter = 0;
        }
    }

    private void scanNow() {
        if (searchEntities.getValue()) {
            scanEntities();
        } else {
            foundEntities = new ArrayList<>();
        }

        if (searchBlocks.getValue()) {
            scanBlocksAsync();
        } else {
            foundBlocks = new ArrayList<>();
        }
    }

    /**
     * Quét Entity trên Main Thread (Entity lookup của Minecraft khá nhanh vì dùng Chunk caching)
     */
    private void scanEntities() {
        if (mc.level == null) return;
        double r = range.getValue();
        AABB area = mc.player.getBoundingBox().inflate(r);

        List<Entity> results = new ArrayList<>();
        int max = limit.getInt();

        // Lấy tất cả entity trong vùng
        List<Entity> allEntities = mc.level.getEntities(mc.player, area);

        for (Entity entity : allEntities) {
            if (results.size() >= max) break;
            if (entityList.contains(entity.getType())) {
                results.add(entity);
            }
        }
        this.foundEntities = results;
    }

    /**
     * Quét Block trên Background Thread để không làm lag game.
     * Đây là kỹ thuật quan trọng nhất để tối ưu hiệu năng.
     */
    private void scanBlocksAsync() {
        if (mc.player == null || mc.level == null) return;
        if (isScanningBlocks.get()) return;
        isScanningBlocks.set(true);

        BlockPos playerPos = mc.player.blockPosition();
        net.minecraft.world.level.ChunkPos playerChunkPos = mc.player.chunkPosition();
        int radiusChunks = range.getInt() / 16;
        int maxResult = limit.getInt();

        // FIX 1: This now works because we added getBlocks() to BlockListSetting
        List<net.minecraft.world.level.block.Block> targetBlocks = new ArrayList<>(blockList.getBlocks());

        if (targetBlocks.isEmpty()) {
            isScanningBlocks.set(false);
            return;
        }

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

                chunksToScan.sort(java.util.Comparator.comparingInt(c ->
                        Math.abs(c.x - playerChunkPos.x) + Math.abs(c.z - playerChunkPos.z)
                ));

                for (net.minecraft.world.level.ChunkPos chunkPos : chunksToScan) {
                    if (results.size() >= maxResult) break;

                    if (mc.level.hasChunkAt(chunkPos.x, chunkPos.z)) {
                        net.minecraft.world.level.chunk.LevelChunk chunk = mc.level.getChunk(chunkPos.x, chunkPos.z);

                        // FIX 2: Loop by index to calculate Y level correctly
                        net.minecraft.world.level.chunk.LevelChunkSection[] sections = chunk.getSections();
                        for (int i = 0; i < sections.length; i++) {
                            net.minecraft.world.level.chunk.LevelChunkSection section = sections[i];
                            if (section == null || section.hasOnlyAir()) continue;

                            // Calculate the bottom Y coordinate of this section
                            // getSectionYFromSectionIndex returns the chunk-y (0, 1, 2...), multiply by 16 to get block-y
                            int bottomY = chunk.getSectionYFromSectionIndex(i) * 16;

                            for (int x = 0; x < 16; x++) {
                                for (int y = 0; y < 16; y++) {
                                    for (int z = 0; z < 16; z++) {
                                        BlockState state = section.getBlockState(x, y, z);

                                        // Performance: Only check if it's a block we want
                                        if (targetBlocks.contains(state.getBlock())) {
                                            int worldX = chunkPos.getMinBlockX() + x;
                                            int worldY = bottomY + y; // Correctly calculated Y
                                            int worldZ = chunkPos.getMinBlockZ() + z;

                                            BlockPos pos = new BlockPos(worldX, worldY, worldZ);
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
                this.foundBlocks = results;
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                isScanningBlocks.set(false);
            }
        });
    }

    // --- Public Getters cho Renderer ---

    public List<BlockPos> getFoundBlocks() {
        return foundBlocks;
    }

    public List<Entity> getFoundEntities() {
        return foundEntities;
    }
}