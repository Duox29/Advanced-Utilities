package com.duox.advancedutilities.system;

import com.duox.advancedutilities.modules.AutoFish;
import com.duox.advancedutilities.modules.AutoRightClick;
import com.duox.advancedutilities.modules.FullBright;
import com.duox.advancedutilities.modules.NoBreakDelay;
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
        // Đăng ký các module
        register(new FullBright());
        register(new NoBreakDelay());
        register(new AutoFish());
        register(new AutoRightClick());
    }

    private void register(Module module) {
        modules.add(module);
    }

    // === THÊM HÀM QUAN TRỌNG NÀY ===
    @SuppressWarnings("unchecked")
    public <T extends Module> T getModule(Class<T> clazz) {
        for (Module module : modules) {
            if (module.getClass() == clazz) {
                return (T) module;
            }
        }
        return null;
    }
    // ===============================

    public List<Module> getModules() {
        return modules;
    }

    public void setModuleState(Module module, boolean state) {
        module.setEnabled(state);
        ConfigUtil.saveConfig();
    }
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

    public void init() {
        MinecraftForge.EVENT_BUS.register(this);
        ConfigUtil.loadConfig();
    }
}