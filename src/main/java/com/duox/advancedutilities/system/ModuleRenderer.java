package com.duox.advancedutilities.system;

import com.duox.advancedutilities.modules.Finder;
import com.duox.advancedutilities.system.Constants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Handles rendering for modules that need world rendering.
 * Currently supports the Finder module for rendering found blocks and entities.
 */
public class ModuleRenderer {

    private final ModuleManager moduleManager;

    public ModuleRenderer(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRenderWorld(RenderLevelStageEvent event) {
        // Render after translucent blocks
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Finder finder = moduleManager.getModule(Finder.class);
        if (finder != null && finder.isEnabled()) {
            renderFinder(event, finder);
        }
    }

    /**
     * Renders the Finder module's found blocks and entities.
     * Uses direct rendering (immediate mode) to have full control over RenderSystem.
     * Ensures NO_DEPTH_TEST is always active for proper rendering.
     */
    private void renderFinder(RenderLevelStageEvent event, Finder finder) {
        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();

        // 1. Chuẩn bị trạng thái RenderSystem (OpenGL)
        // Lưu trạng thái cũ để không làm hỏng game
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest(); // Disable depth test -> see through walls
        RenderSystem.depthMask(false);   // Don't write to depth buffer -> don't occlude other objects
        RenderSystem.disableCull();      // Render both sides of blocks

        // Set shader directly
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // Get Tesselator (direct rendering tool)
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        // Phase 1: Render blocks (quads)
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float rB = 1.0f, gB = 0.8f, bB = 0.2f, aB = Constants.RENDER_BLOCK_ALPHA; // Orange-yellow, transparent
        var blocks = finder.getFoundBlocks(); // Get thread-safe snapshot

        for (BlockPos pos : blocks) {
            addFilledBoxToBuffer(poseStack, buffer, new AABB(pos), rB, gB, bB, aB);
        }

        tesselator.end();

        // Phase 2: Render entities (lines)
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.lineWidth(Constants.RENDER_LINE_WIDTH);

        buffer.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);

        float rE = 1.0f, gE = 0.2f, bE = 0.2f, aE = Constants.RENDER_ENTITY_ALPHA; // Red
        var entities = finder.getFoundEntities();

        for (Entity entity : entities) {
            double x = Mth.lerp(event.getPartialTick(), entity.xo, entity.getX());
            double y = Mth.lerp(event.getPartialTick(), entity.yo, entity.getY());
            double z = Mth.lerp(event.getPartialTick(), entity.zo, entity.getZ());

            AABB box = entity.getType().getDimensions().makeBoundingBox(new Vec3(x, y, z));
            addLineBoxToBuffer(poseStack, buffer, box, rE, gE, bE, aE);
        }

        tesselator.end();

        // Restore state (cleanup)
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(Constants.RENDER_DEFAULT_LINE_WIDTH);

        poseStack.popPose();
    }

    /**
     * Helper method to add a filled box to the vertex buffer.
     */
    private void addFilledBoxToBuffer(PoseStack stack, VertexConsumer buffer, AABB box, float r, float g, float b, float a) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        var matrix = stack.last().pose();

        // Down
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a).endVertex();

        // Up
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a).endVertex();

        // North
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a).endVertex();

        // South
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a).endVertex();

        // West
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a).endVertex();

        // East
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a).endVertex();
    }

    private void addLineBoxToBuffer(PoseStack stack, VertexConsumer buffer, AABB box, float r, float g, float b, float a) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;
        var matrix = stack.last().pose();

        // Bottom
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a).normal(0, 1, 0).endVertex();
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a).normal(0, 1, 0).endVertex();
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a).normal(0, 1, 0).endVertex();
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a).normal(0, 1, 0).endVertex();
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a).normal(0, 1, 0).endVertex();
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a).normal(0, 1, 0).endVertex();
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a).normal(0, 1, 0).endVertex();
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a).normal(0, 1, 0).endVertex();

        // Top
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a).normal(0, 1, 0).endVertex();
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a).normal(0, 1, 0).endVertex();
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a).normal(0, 1, 0).endVertex();
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a).normal(0, 1, 0).endVertex();
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a).normal(0, 1, 0).endVertex();
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a).normal(0, 1, 0).endVertex();
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a).normal(0, 1, 0).endVertex();
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a).normal(0, 1, 0).endVertex();

        // Sides
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a).normal(0, 1, 0).endVertex();
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a).normal(0, 1, 0).endVertex();
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a).normal(0, 1, 0).endVertex();
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a).normal(0, 1, 0).endVertex();
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a).normal(0, 1, 0).endVertex();
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a).normal(0, 1, 0).endVertex();
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a).normal(0, 1, 0).endVertex();
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a).normal(0, 1, 0).endVertex();
    }
}