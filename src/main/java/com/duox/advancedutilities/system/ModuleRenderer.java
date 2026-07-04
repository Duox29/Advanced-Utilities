package com.duox.advancedutilities.system;

import com.duox.advancedutilities.modules.finder.Finder;
import com.duox.advancedutilities.modules.finder.FinderSnapshot;
import com.duox.advancedutilities.system.render.FinderRenderBackend;
import com.duox.advancedutilities.system.render.GlFinderRenderBackend;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;

public class ModuleRenderer {

    private final ModuleManager moduleManager;
    private final FinderRenderBackend finderBackend = new GlFinderRenderBackend();

    public ModuleRenderer(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRenderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Finder finder = moduleManager.getModule(Finder.class);
        if (finder == null || !finder.isEnabled()) return;

        FinderSnapshot snapshot = finder.getSnapshot();
        if (!snapshot.isEmpty()) {
            finderBackend.render(event, snapshot);
        }
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ClientLevel)) return;

        Finder finder = moduleManager.getModule(Finder.class);
        if (finder != null && finder.isEnabled()) {
            finder.onChunkLoad(event.getChunk().getPos());
        }
    }
}
