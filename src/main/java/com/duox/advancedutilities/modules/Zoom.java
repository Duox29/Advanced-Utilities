package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.NumberSetting;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;

public class Zoom extends Module {
    private final NumberSetting zoomMultiplier;

    public Zoom() {
        super("Zoom", "Adjusts the field of view. (Hold Keybind)", Category.RENDER, true);
        
        zoomMultiplier = new NumberSetting("Multiplier", 4.0, 1.0, 50.0, 0.1);
        addSetting(zoomMultiplier);
    }

    @Override
    public void onEnable() {
        NeoForge.EVENT_BUS.register(this);
    }

    @Override
    public void onDisable() {
        NeoForge.EVENT_BUS.unregister(this);
    }

    public double getMultiplier() {
        return zoomMultiplier.getValue();
    }

    @SubscribeEvent
    public void onComputeFovModifier(ComputeFovModifierEvent event) {
        if (isEnabled()) {
            // We will handle FOV in MixinGameRenderer to bypass vanilla clamping
            // But we can keep this for compatibility or partial support if mixin fails?
            // Actually, if we use the Mixin to FORCE the value, this event handler might be redundant or conflicting if we don't coordinate.
            // If we force "Base / Multiplier" in Mixin, we ignore this modifier.
            // So we can remove the logic here or keep it.
            // Ideally, we want to SUPPORT other modifiers (sprinting) but BYPASS the clamp.
            
            // If we use the Mixin approach "cir.setReturnValue(base / zoom)", we lose sprinting effects.
            // That is actually a feature for zoom (steady camera).
            // So let's disable the event logic and rely on the Mixin.
            // OR keep the event logic and rely on Mixin to UNCLAMP.
            
            // If we keep event logic:
            // fov = (base * (mod * 1/zoom)) clamped.
            // If we want to unclamp, we need to know what it was before clamp.
            // Hard to know.
            
            // So the "Force Steady Zoom" approach (Mixin overwrites everything) is safer to bypass limits.
            // It guarantees 50x zoom is 50x zoom regardless of speed potions.
        }
    }
}
