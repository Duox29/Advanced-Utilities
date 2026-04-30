package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.NumberSetting;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;

public class Zoom extends Module {
    private final NumberSetting zoomMultiplier;
    private double currentScrollMultiplier = 1.0;

    public Zoom() {
        super("Zoom", "Adjusts the field of view. (Hold Keybind)", Category.RENDER, true);

        zoomMultiplier = new NumberSetting("Multiplier", 4.0, 1.0, 50.0, 0.1);
        addSetting(zoomMultiplier);
    }

    @Override
    public void onEnable() {
        currentScrollMultiplier = 1.0;
        NeoForge.EVENT_BUS.register(this);
    }

    @Override
    public void onDisable() {
        currentScrollMultiplier = 1.0;
        NeoForge.EVENT_BUS.unregister(this);
    }

    public void onMouseScroll(double amount) {
        if (!isEnabled()) return;

        if (amount > 0) {
            currentScrollMultiplier *= 1.1;
        } else if (amount < 0) {
            currentScrollMultiplier /= 1.1;
        }
        double totalMultiplier = zoomMultiplier.getValue() * currentScrollMultiplier;
        if (totalMultiplier < 1.0) {
            currentScrollMultiplier = 1.0 / zoomMultiplier.getValue();
        }
        if (totalMultiplier > 100.0) {
            currentScrollMultiplier = 100.0 / zoomMultiplier.getValue();
        }
    }
    public double getMultiplier() {
        return zoomMultiplier.getValue() * currentScrollMultiplier;
    }

    @SubscribeEvent
    public void onComputeFovModifier(ComputeFovModifierEvent event) {
    }
    public double getSensitivityModifier() {
        if (!isEnabled()) return 1.0;
        return 1.0 / getMultiplier();
    }
}