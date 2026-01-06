package com.duox.advancedutilities.system;

import com.duox.advancedutilities.modules.Finder;
import com.duox.advancedutilities.utils.RenderUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/*
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
        Frustum frustum = event.getFrustum();

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
            AABB aabb = new AABB(pos);
            // Simple frustum culling
            if (frustum.isVisible(aabb)) {
                RenderUtils.addFilledBoxToBuffer(poseStack, buffer, aabb, rB, gB, bB, aB);
            }
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
            if (frustum.isVisible(box)) {
                RenderUtils.addLineBoxToBuffer(poseStack, buffer, box, rE, gE, bE, aE);
            }
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

    // Removed helper methods as they are now in RenderUtils
}