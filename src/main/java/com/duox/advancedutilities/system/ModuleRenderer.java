package com.duox.advancedutilities.system;
import net.minecraft.util.Mth;
import com.duox.advancedutilities.modules.Finder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.awt.Color;

public class ModuleRenderer {

    private final ModuleManager moduleManager;

    public ModuleRenderer(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRenderWorld(RenderLevelStageEvent event) {
        // Chỉ render sau khi các block mờ đã render xong (để ESP hiển thị xuyên tường)
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Finder finder = (Finder) moduleManager.getModule(Finder.class);
        if (finder != null && finder.isEnabled()) {
            renderFinder(event, finder);
        }
    }

    private void renderFinder(RenderLevelStageEvent event, Finder finder) {
        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();

        // Setup buffer vẽ lines
        var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer builder = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();
        // Dịch chuyển về tọa độ camera âm để vẽ đúng vị trí thế giới
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        // Render Blocks
        // Màu vàng cho block (RGBA)
        float rB = 1.0f, gB = 1.0f, bB = 0.0f, aB = 1.0f;
        for (BlockPos pos : finder.getFoundBlocks()) {
            AABB box = new AABB(pos);
            LevelRenderer.renderLineBox(poseStack, builder, box, rB, gB, bB, aB);
        }

        // Render Entities
        // Màu đỏ cho entity
        float rE = 1.0f, gE = 0.0f, bE = 0.0f, aE = 1.0f;
        for (Entity entity : finder.getFoundEntities()) {
            // Lấy bounding box nội suy theo partial ticks để mượt mà
            double x = Mth.lerp(event.getPartialTick(), entity.xo, entity.getX());
            double y = Mth.lerp(event.getPartialTick(), entity.yo, entity.getY());
            double z = Mth.lerp(event.getPartialTick(), entity.zo, entity.getZ());

            // Vì chúng ta đã translate cả poseStack, ta cần vẽ box tại vị trí thực
            // bounding box của entity là dynamic
            AABB box = entity.getType().getDimensions().makeBoundingBox(new Vec3(x, y, z));
            LevelRenderer.renderLineBox(poseStack, builder, box, rE, gE, bE, aE);
        }

        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines()); // Force draw
    }
}