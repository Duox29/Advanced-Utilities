package com.duox.advancedutilities.system.render;

import com.duox.advancedutilities.modules.finder.FinderSnapshot;
import com.duox.advancedutilities.system.Constants;
import com.duox.advancedutilities.utils.RenderUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public class GlFinderRenderBackend implements FinderRenderBackend {

    @Override
    public void render(RenderLevelStageEvent event, FinderSnapshot snapshot) {
        if (snapshot.isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        Tesselator tesselator = Tesselator.getInstance();

        if (!snapshot.blockBoxes().isEmpty()) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

            for (AABB box : snapshot.blockBoxes()) {
                RenderUtils.addFilledBoxToBuffer(
                        poseStack, buffer, box,
                        1.0f, 0.8f, 0.2f, Constants.RENDER_BLOCK_ALPHA
                );
            }

            BufferUploader.drawWithShader(buffer.buildOrThrow());
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

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(Constants.RENDER_DEFAULT_LINE_WIDTH);

        poseStack.popPose();
    }
}