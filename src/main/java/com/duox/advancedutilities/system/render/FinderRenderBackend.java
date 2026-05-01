package com.duox.advancedutilities.system.render;

import com.duox.advancedutilities.modules.finder.FinderSnapshot;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public interface FinderRenderBackend {
    void render(RenderLevelStageEvent event, FinderSnapshot snapshot);
}