package com.duox.advancedutilities.modules.finder;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Immutable view of everything the Finder currently renders.
 *
 * blockPositions is the exact array instance published by the scanner worker and is
 * never mutated afterwards; the renderer exploits that reference-stability as its
 * mesh cache key (rebuild only when the instance identity changes).
 */
public record FinderSnapshot(
        long[] blockPositions,
        List<EntityTarget> entityTargets,
        int version
) {
    public static final FinderSnapshot EMPTY = new FinderSnapshot(new long[0], List.of(), 0);

    public boolean isEmpty() {
        return blockPositions.length == 0 && entityTargets.isEmpty();
    }

    /** Live entity reference wrapper: lerps against the entity's actual current position
     *  at render time instead of stale scan-time coordinates, so boxes track entities
     *  smoothly between rescans and disappear the moment the entity is removed. */
    public static final class EntityTarget {
        private final Entity entity;
        private final float halfWidth;
        private final float height;

        public EntityTarget(Entity entity) {
            this.entity = entity;
            this.halfWidth = entity.getBbWidth() * 0.5F;
            this.height = entity.getBbHeight();
        }

        public Entity entity() {
            return entity;
        }

        /** @return the interpolated box, or null if the entity is gone and should no
         *          longer be rendered. */
        public AABB lerpedBox(float partialTick) {
            if (entity.isRemoved()) return null;
            double x = Mth.lerp(partialTick, entity.xo, entity.getX());
            double y = Mth.lerp(partialTick, entity.yo, entity.getY());
            double z = Mth.lerp(partialTick, entity.zo, entity.getZ());
            return new AABB(x - halfWidth, y, z - halfWidth, x + halfWidth, y + height, z + halfWidth);
        }
    }
}
