package com.duox.advancedutilities.system;

import com.duox.advancedutilities.modules.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ModuleManager {
    public static final ModuleManager INSTANCE = new ModuleManager();

    // Use a Map for O(1) lookup by class instead of looping
    private final Map<Class<? extends Module>, Module> moduleMap = new LinkedHashMap<>();

    private ModuleManager() {
        // Constructor is now empty or minimal
    }

    /**
     * Registers a module instance.
     * Call this from your Main class registration phase.
     */
    public void register(Module module) {
        moduleMap.put(module.getClass(), module);
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T getModule(Class<T> clazz) {
        return (T) moduleMap.get(clazz);
    }

    public List<Module> getModules() {
        return new ArrayList<>(moduleMap.values());
    }

    public void setModuleState(Module module, boolean state) {
        module.setEnabled(state);
        ConfigManager.save();
    }

    public List<Module> getModulesByCategory(Category category) {
        return moduleMap.values().stream()
                .filter(module -> module.getCategory() == category)
                .collect(Collectors.toList());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            moduleMap.values().stream()
                    .filter(Module::isEnabled)
                    .forEach(Module::onTick);
        }
    }

    public void init() {
        MinecraftForge.EVENT_BUS.register(this);
        // Note: Load config AFTER modules are registered externally
        ConfigManager.load();
    }
}