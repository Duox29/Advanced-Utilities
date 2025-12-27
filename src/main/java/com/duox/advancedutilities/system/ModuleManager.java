package com.duox.advancedutilities.system;

import com.duox.advancedutilities.modules.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ModuleManager {
    public static final ModuleManager INSTANCE = new ModuleManager();
    private final List<Module> modules = new ArrayList<>();

    private ModuleManager() {
        register(new FullBright());
        register(new NoBreakDelay());
        register(new AutoFish());
        register(new AutoRightClick());
        register(new AutoReconnect());
        register(new FastClick());
        register(new Finder());
    }

    private void register(Module module) {
        modules.add(module);
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T getModule(Class<T> clazz) {
        for (Module module : modules) {
            if (module.getClass() == clazz) {
                return (T) module;
            }
        }
        return null;
    }

    public List<Module> getModules() {
        return modules;
    }

    // --- CẬP NHẬT PHẦN NÀY ---
    public void setModuleState(Module module, boolean state) {
        module.setEnabled(state);
        ConfigManager.save(); // Gọi Manager mới để lưu toàn bộ config (bao gồm settings)
    }
    // --------------------------

    public List<Module> getModulesByCategory(Category category) {
        return modules.stream()
                .filter(module -> module.getCategory() == category)
                .collect(Collectors.toList());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            modules.stream().filter(Module::isEnabled).forEach(Module::onTick);
        }
    }

    // --- CẬP NHẬT PHẦN NÀY ---
    public void init() {
        MinecraftForge.EVENT_BUS.register(this);
        ConfigManager.load(); // Gọi Manager mới để load tất cả
    }
    // --------------------------
}