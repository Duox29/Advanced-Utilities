package com.duox.advancedutilities.modules.finder;

import com.duox.advancedutilities.system.settings.EntityListSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Periodically scans for target entities around the player.
 *
 * Micro-optimizations vs the old implementation:
 * - Bounded top-K insertion selection instead of a full O(n log n) sort plus a
 *   comparator lambda allocation on every rescan.
 * - Results keep live entity references; rendering interpolates the entity's real
 *   position, eliminating the jitter caused by lerping between stale snapshots.
 */
public class EntityScanner {

    private List<FinderSnapshot.EntityTarget> targets = List.of();
    private int tickCounter = 0;

    public void tick() {
        tickCounter++;
    }

    public boolean isReady(int interval) {
        return tickCounter >= interval;
    }

    /** @return true if the visible result set changed. */
    public boolean rescan(Level level, Entity viewer, EntityListSetting filter,
                          double range, int maxResults) {
        tickCounter = 0;
        if (level == null || viewer == null) return false;

        AABB area = viewer.getBoundingBox().inflate(range);
        List<Entity> candidates = level.getEntities(viewer, area);

        int k = Math.max(1, maxResults);
        Entity[] sel = new Entity[k];
        float[] selDist = new float[k];
        int cnt = 0;

        for (int i = 0, size = candidates.size(); i < size; i++) {
            Entity e = candidates.get(i);
            if (!filter.contains(e.getType())) continue;
            float d = (float) e.distanceToSqr(viewer);
            if (cnt == k && d >= selDist[k - 1]) continue;

            int pos = cnt < k ? cnt++ : k - 1;
            while (pos > 0 && selDist[pos - 1] > d) { // shift-insert into ascending array
                selDist[pos] = selDist[pos - 1];
                sel[pos] = sel[pos - 1];
                pos--;
            }
            selDist[pos] = d;
            sel[pos] = e;
        }

        List<FinderSnapshot.EntityTarget> old = targets;
        boolean changed = old.size() != cnt;
        if (!changed) {
            for (int i = 0; i < cnt; i++) {
                if (old.get(i).entity() != sel[i]) {
                    changed = true;
                    break;
                }
            }
        }
        if (!changed) return false;

        ArrayList<FinderSnapshot.EntityTarget> out = new ArrayList<>(cnt);
        for (int i = 0; i < cnt; i++) out.add(new FinderSnapshot.EntityTarget(sel[i]));
        targets = List.copyOf(out);
        return true;
    }

    public void clear() {
        targets = List.of();
        tickCounter = 0;
    }

    /** @return true if anything was actually cleared. */
    public boolean clearResults() {
        if (targets.isEmpty()) {
            tickCounter = 0;
            return false;
        }
        targets = List.of();
        tickCounter = 0;
        return true;
    }

    public List<FinderSnapshot.EntityTarget> getTargets() {
        return targets;
    }
}
