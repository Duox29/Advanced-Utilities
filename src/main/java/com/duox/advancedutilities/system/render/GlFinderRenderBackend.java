package com.duox.advancedutilities.system.render;

import com.duox.advancedutilities.modules.finder.FinderSnapshot;
import com.duox.advancedutilities.system.Constants;
import com.duox.advancedutilities.utils.RenderUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class GlFinderRenderBackend implements FinderRenderBackend {

    private static final int[][] NEIGHBORS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };

    private List<AABB> cachedMergedBoxes;
    private int lastBlockVersion = -1;

    @Override
    public void render(RenderLevelStageEvent event, FinderSnapshot snapshot) {
        if (snapshot.isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        setupRenderState();

        Tesselator tesselator = Tesselator.getInstance();

        if (snapshot.blockPositions().length > 0) {
            if (cachedMergedBoxes == null || snapshot.version() != lastBlockVersion) {
                cachedMergedBoxes = mergeBlocks(snapshot.blockPositions());
                lastBlockVersion = snapshot.version();
            }
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (AABB box : cachedMergedBoxes) {
                RenderUtils.addFilledBoxToBuffer(
                        poseStack, buffer, box,
                        1.0f, 0.8f, 0.2f, Constants.RENDER_BLOCK_ALPHA
                );
            }
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } else {
            cachedMergedBoxes = null;
        }

        if (!snapshot.entityTargets().isEmpty()) {
            RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
            RenderSystem.lineWidth(Constants.RENDER_LINE_WIDTH);
            BufferBuilder lineBuffer = tesselator.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
            for (FinderSnapshot.EntityRenderTarget entity : snapshot.entityTargets()) {
                RenderUtils.addLineBoxToBuffer(
                        poseStack, lineBuffer, entity.lerpedBox(partialTick),
                        1.0f, 0.2f, 0.2f, Constants.RENDER_ENTITY_ALPHA
                );
            }
            BufferUploader.drawWithShader(lineBuffer.buildOrThrow());
        }

        restoreRenderState();
        poseStack.popPose();
    }

    private static List<AABB> mergeBlocks(long[] positions) {
        if (positions.length <= 1) {
            return positions.length == 0
                    ? List.of()
                    : List.of(toAABB(positions[0]));
        }

        HashSet<Long> posSet = new HashSet<>(positions.length);
        for (long p : positions) posSet.add(p);

        HashSet<Long> visited = new HashSet<>(positions.length);
        ArrayList<AABB> result = new ArrayList<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();

        for (long start : positions) {
            if (!visited.add(start)) continue;

            queue.clear();
            queue.addLast(start);

            int minX = BlockPos.getX(start), maxX = minX;
            int minY = BlockPos.getY(start), maxY = minY;
            int minZ = BlockPos.getZ(start), maxZ = minZ;

            while (!queue.isEmpty()) {
                long cur = queue.removeFirst();
                int cx = BlockPos.getX(cur), cy = BlockPos.getY(cur), cz = BlockPos.getZ(cur);

                for (int[] dir : NEIGHBORS) {
                    int nx = cx + dir[0], ny = cy + dir[1], nz = cz + dir[2];
                    long key = BlockPos.asLong(nx, ny, nz);
                    if (posSet.contains(key) && visited.add(key)) {
                        queue.addLast(key);
                        if (nx < minX) minX = nx;
                        if (nx > maxX) maxX = nx;
                        if (ny < minY) minY = ny;
                        if (ny > maxY) maxY = ny;
                        if (nz < minZ) minZ = nz;
                        if (nz > maxZ) maxZ = nz;
                    }
                }
            }

            result.add(new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1));
        }

        return result;
    }

    private static AABB toAABB(long packed) {
        return new AABB(
                BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed),
                BlockPos.getX(packed) + 1, BlockPos.getY(packed) + 1, BlockPos.getZ(packed) + 1
        );
    }

    private void setupRenderState() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
    }

    private void restoreRenderState() {
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(Constants.RENDER_DEFAULT_LINE_WIDTH);
    }
}
