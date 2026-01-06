package com.duox.advancedutilities.utils;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.AABB;

public class RenderUtils {

    /**
     * Helper method to add a filled box to the vertex buffer.
     */
    public static void addFilledBoxToBuffer(PoseStack stack, VertexConsumer buffer, AABB box, float r, float g, float b, float a) {
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

    public static void addLineBoxToBuffer(PoseStack stack, VertexConsumer buffer, AABB box, float r, float g, float b, float a) {
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
