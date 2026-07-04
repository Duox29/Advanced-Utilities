package com.duox.advancedutilities.modules.finder;

import com.duox.advancedutilities.system.settings.EntityListSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class EntityScanner {

    private List<FinderSnapshot.EntityRenderTarget> targets = List.of();
    private int tickCounter = 0;
    private boolean dirty = false;

    public void tick() {
        tickCounter++;
    }

    public boolean isReady(int interval) {
        return tickCounter >= interval;
    }

    public void rescan(Level level, Entity filterEntity, EntityListSetting entityList, double range, int maxResults) {
        if (level == null || filterEntity == null) return;

        AABB area = filterEntity.getBoundingBox().inflate(range);
        List<Entity> all = level.getEntities(filterEntity, area);

        all.removeIf(entity -> !entityList.contains(entity.getType()));
        all.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(filterEntity)));

        ArrayList<FinderSnapshot.EntityRenderTarget> results = new ArrayList<>(Math.min(maxResults, all.size()));
        for (Entity entity : all) {
            if (results.size() >= maxResults) break;
            results.add(FinderSnapshot.EntityRenderTarget.from(entity));
        }

        targets = List.copyOf(results);
        tickCounter = 0;
        dirty = true;
    }

    public void clear() {
        targets = List.of();
        tickCounter = 0;
        dirty = false;
    }

    public void clearResults() {
        if (!targets.isEmpty()) {
            targets = List.of();
            dirty = true;
        }
    }

    public List<FinderSnapshot.EntityRenderTarget> getTargets() {
        return targets;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }
}
