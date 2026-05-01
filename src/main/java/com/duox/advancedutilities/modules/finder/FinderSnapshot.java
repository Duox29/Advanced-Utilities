package com.duox.advancedutilities.modules.finder;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.List;

public record FinderSnapshot(
        List<AABB> blockBoxes,
        List<EntityRenderTarget> entityTargets
) {
    public static final FinderSnapshot EMPTY = new FinderSnapshot(List.of(), List.of());

    public boolean isEmpty() {
        return blockBoxes.isEmpty() && entityTargets.isEmpty();
    }

    public record EntityRenderTarget(
            double prevX, double prevY, double prevZ,
            double x, double y, double z,
            float width, float height
    ) {
        public static EntityRenderTarget from(Entity entity) {
            return new EntityRenderTarget(
                    entity.xo, entity.yo, entity.zo,
                    entity.getX(), entity.getY(), entity.getZ(),
                    entity.getBbWidth(), entity.getBbHeight()
            );
        }

        public AABB lerpedBox(float partialTick) {
            double lx = Mth.lerp(partialTick, prevX, x);
            double ly = Mth.lerp(partialTick, prevY, y);
            double lz = Mth.lerp(partialTick, prevZ, z);

            double hw = width * 0.5D;
            return new AABB(
                    lx - hw, ly, lz - hw,
                    lx + hw, ly + height, lz + hw
            );
        }
    }
}