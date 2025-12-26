package com.duox.advancedutilities.system;

import com.duox.advancedutilities.modules.FullBright; // Ví dụ import
import com.duox.advancedutilities.modules.NoBreakDelay; // Ví dụ import
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    public static final ModuleManager INSTANCE = new ModuleManager();
    private final List<Module> modules = new ArrayList<>();

    private ModuleManager() {
        // Đăng ký module
        register(new FullBright());
        register(new NoBreakDelay());
    }

    private void register(Module module) {
        modules.add(module);
    }

    public List<Module> getModules() {
        return modules;
    }

    // === METHOD MỚI: Gọi hàm này khi toggle module ===
    public void setModuleState(Module module, boolean state) {
        module.setEnabled(state);
        ConfigUtil.saveConfig(); // Lưu ngay khi thay đổi
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            modules.stream().filter(Module::isEnabled).forEach(Module::onTick);
        }
    }

    public void init() {
        MinecraftForge.EVENT_BUS.register(this);
        ConfigUtil.loadConfig(); // Load config khi khởi động
    }
}