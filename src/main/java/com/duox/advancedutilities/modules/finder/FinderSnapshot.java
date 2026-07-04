package com.duox.advancedutilities.modules.finder;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public record FinderSnapshot(
        long[] blockPositions,
        List<FinderSnapshot.EntityRenderTarget> entityTargets,
        int version
) {
    public static final FinderSnapshot EMPTY = new FinderSnapshot(new long[0], List.of(), 0);

    public boolean isEmpty() {
        return blockPositions.length == 0 && entityTargets.isEmpty();
    }

    public List<AABB> getBlockBoxes() {
        ArrayList<AABB> boxes = new ArrayList<>(blockPositions.length);
        for (long packed : blockPositions) {
            boxes.add(new AABB(
                    BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed),
                    BlockPos.getX(packed) + 1, BlockPos.getY(packed) + 1, BlockPos.getZ(packed) + 1
            ));
        }
        return boxes;
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
