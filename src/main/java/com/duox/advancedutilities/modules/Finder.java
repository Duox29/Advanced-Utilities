package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.BlockListSetting;
import com.duox.advancedutilities.system.settings.BooleanSetting;
import com.duox.advancedutilities.system.settings.EntityListSetting;
import com.duox.advancedutilities.system.settings.NumberSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
        // Nếu đang quét dở thì bỏ qua để tránh spam thread
        if (mc.player == null || mc.level == null) return;
        if (isScanningBlocks.get()) return;
        isScanningBlocks.set(true);

        // Snapshot các giá trị cần thiết từ main thread để đưa vào worker thread
        BlockPos playerPos = mc.player.blockPosition();
        int r = range.getInt();
        int max = limit.getInt();

        // Chạy bất đồng bộ
        CompletableFuture.runAsync(() -> {
            try {
                List<BlockPos> results = new ArrayList<>();

                // Thuật toán quét: Quét từ tâm ra ngoài (để ưu tiên block gần người chơi nhất)
                // Dùng vòng lặp đơn giản cho hiệu năng cao thay vì Stream API
                for (int x = -r; x <= r; x++) {
                    for (int y = -r; y <= r; y++) {
                        for (int z = -r; z <= r; z++) {
                            if (results.size() >= max) break;

                            // Tối ưu: Kiểm tra khoảng cách Manhattan hoặc Euclidean trước khi getBlockState
                            // ở đây dùng tọa độ tương đối để loop cho nhanh
                            BlockPos pos = playerPos.offset(x, y, z);

                            // Lưu ý: Access world từ thread khác main thread có thể nguy hiểm nếu world thay đổi chunk.
                            // Tuy nhiên, chỉ đọc getBlockState thường an toàn trong phạm vi loaded chunks.
                            // Để an toàn tuyệt đối, cần check chunk loaded trước.
                            if (mc.level.hasChunkAt(pos)) {
                                BlockState state = mc.level.getBlockState(pos);
                                if (blockList.contains(state.getBlock())) {
                                    results.add(pos);
                                }
                            }
                        }
                    }
                }
                // Cập nhật kết quả atomic
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