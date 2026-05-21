package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.BooleanSetting;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

public class TrueSight extends Module {

    private final BooleanSetting noFogSetting = new BooleanSetting("No Fog", true);
    private final BooleanSetting noDarknessSetting = new BooleanSetting("No Darkness", true);

    public TrueSight() {
        super("True Sight", "Removes fog, blindness, and darkness effects to clear your vision.", Category.RENDER);
        addSetting(noFogSetting);
        addSetting(noDarknessSetting);
    }

    @Override
    public void onEnable() {
        NeoForge.EVENT_BUS.register(this);
    }

    @Override
    public void onDisable() {
        NeoForge.EVENT_BUS.unregister(this);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        if (noDarknessSetting.getValue()) {
            if (mc.player.hasEffect(MobEffects.BLINDNESS)) {
                mc.player.removeEffect(MobEffects.BLINDNESS);
            }
            if (mc.player.hasEffect(MobEffects.DARKNESS)) {
                mc.player.removeEffect(MobEffects.DARKNESS);
            }
        }
    }

    @SubscribeEvent
    public void onRenderFog(ViewportEvent.RenderFog event) {
        if (noFogSetting.getValue()) {
            if (event.getMode() == FogRenderer.FogMode.FOG_SKY) return;
            event.setNearPlaneDistance(10000.0F);
            event.setFarPlaneDistance(20000.0F);
            event.setCanceled(true);
        }
    }
}