package com.duox.advancedutilities.system.render;

import com.duox.advancedutilities.modules.finder.FinderSnapshot;
import com.duox.advancedutilities.system.Constants;
import com.duox.advancedutilities.utils.RenderUtils;
import com.mojang.blaze3d.systems.RenderSystem;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders Finder snapshots.
 *
 * Performance model:
 * - The block mesh is uploaded once into a STATIC_DRAW {@link VertexBuffer} and only
 *   rebuilt when the scanner publishes a different result array (identity-keyed).
 *   Steady-state rendering cost is one draw call of an existing buffer - no CPU
 *   tessellation, no re-upload, zero garbage per frame.
 * - Only exposed cube faces are emitted (neighbor test against the result set), so
 *   ore veins collapse to their shell instead of drawing every interior face.
 * - Camera translation is applied through the model-view matrix, which is what makes
 *   the world-space mesh cacheable across frames at all.
 */
public class GlFinderRenderBackend implements FinderRenderBackend {

    private static final float BLOCK_R = 1.0F, BLOCK_G = 0.8F, BLOCK_B = 0.2F;

    /** Corner offsets (4 x xyz) for each cube face, CCW seen from outside. */
    private static final int[][] FACE_CORNERS = {
            {0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0}, // up    (+Y)
            {0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1}, // down  (-Y)
            {0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0}, // north (-Z)
            {1, 0, 1, 1, 1, 1, 0, 1, 1, 0, 0, 1}, // south (+Z)
            {0, 0, 1, 0, 1, 1, 0, 1, 0, 0, 0, 0}, // west  (-X)
            {1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1}, // east  (+X)
    };
    /** Neighbor lookup deltas matching FACE_CORNERS order. */
    private static final int[][] FACE_DELTAS = {
            {0, 1, 0}, {0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0},
    };

    private VertexBuffer blockBuffer;
    private long[] meshKey;   // identity of the published positions array
    private final List<AABB> entityBoxScratch = new ArrayList<>(64);

    @Override
    public void render(RenderLevelStageEvent event, FinderSnapshot snapshot) {
        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        setupRenderState();
        try {
            renderBlocks(poseStack, snapshot);
            renderEntities(poseStack, snapshot, partialTick);
        } finally {
            restoreRenderState();
            poseStack.popPose();
        }
    }

    private void renderBlocks(PoseStack poseStack, FinderSnapshot snapshot) {
        long[] positions = snapshot.blockPositions();
        if (positions.length == 0) {
            releaseBlockBuffer();
            return;
        }
        if (positions != meshKey) {
            rebuildBlockMesh(positions);
        }
        if (blockBuffer == null) return;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var shader = GameRenderer.getPositionColorShader();
        if (shader == null) return;
        blockBuffer.bind();
        blockBuffer.drawWithShader(poseStack.last().pose(), RenderSystem.getProjectionMatrix(), shader);
        VertexBuffer.unbind();
    }

    private void rebuildBlockMesh(long[] positions) {
        releaseBlockBuffer();

        LongOpenHashSet set = new LongOpenHashSet(positions.length * 2);
        for (long p : positions) set.add(p);

        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean anyFace = false;
        for (long p : positions) {
            int x = BlockPos.getX(p);
            int y = BlockPos.getY(p);
            int z = BlockPos.getZ(p);
            for (int f = 0; f < 6; f++) {
                int[] d = FACE_DELTAS[f];
                if (set.contains(BlockPos.asLong(x + d[0], y + d[1], z + d[2]))) continue;
                emitFace(buffer, x, y, z, f);
                anyFace = true;
            }
        }
        if (!anyFace) {
            meshKey = positions;
            return; // fully-enclosed results; nothing visible
        }

        MeshData data = buffer.buildOrThrow();
        VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vbo.bind();
        vbo.upload(data); // takes ownership of the MeshData
        VertexBuffer.unbind();
        blockBuffer = vbo;
        meshKey = positions;
    }

    private void emitFace(BufferBuilder buffer, int x, int y, int z, int face) {
        int[] c = FACE_CORNERS[face];
        for (int i = 0; i < 12; i += 3) {
            buffer.addVertex(x + c[i], y + c[i + 1], z + c[i + 2])
                    .setColor(BLOCK_R, BLOCK_G, BLOCK_B, Constants.RENDER_BLOCK_ALPHA);
        }
    }

    private void renderEntities(PoseStack poseStack, FinderSnapshot snapshot, float partialTick) {
        List<FinderSnapshot.EntityTarget> targets = snapshot.entityTargets();
        if (targets.isEmpty()) return;

        entityBoxScratch.clear();
        for (int i = 0, size = targets.size(); i < size; i++) {
            AABB box = targets.get(i).lerpedBox(partialTick);
            if (box != null) entityBoxScratch.add(box);
        }
        if (entityBoxScratch.isEmpty()) return;

        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.lineWidth(Constants.RENDER_LINE_WIDTH);
        BufferBuilder lineBuffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        for (int i = 0, size = entityBoxScratch.size(); i < size; i++) {
            RenderUtils.addLineBoxToBuffer(poseStack, lineBuffer, entityBoxScratch.get(i),
                    1.0F, 0.2F, 0.2F, Constants.RENDER_ENTITY_ALPHA);
        }
        BufferUploader.drawWithShader(lineBuffer.buildOrThrow());
    }

    private void releaseBlockBuffer() {
        if (blockBuffer != null) {
            blockBuffer.close();
            blockBuffer = null;
        }
        meshKey = null;
    }

    @Override
    public void close() {
        releaseBlockBuffer();
    }

    private void setupRenderState() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableCull(); // back-face culling halves fill cost; faces are wound correctly
    }

    private void restoreRenderState() {
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(Constants.RENDER_DEFAULT_LINE_WIDTH);
    }
}
