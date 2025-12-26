package com.duox.advancedutilities.system;

import com.duox.advancedutilities.system.settings.Setting;
import net.minecraft.client.Minecraft;
import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    protected final Minecraft mc = Minecraft.getInstance();
    private final String name;
    private final String description;
    private final Category category;
    private boolean enabled = false;

    // --- THÊM PHẦN NÀY ---
    private final List<Setting<?>> settings = new ArrayList<>();

    public Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    // Đăng ký Setting vào Module
    protected void addSetting(Setting<?> setting) {
        this.settings.add(setting);
    }

    public List<Setting<?>> getSettings() {
        return settings;
    }
    // ---------------------

    public void toggle() {
        this.enabled = !this.enabled;
        if (this.enabled) onEnable();
        else onDisable();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) onEnable();
        else onDisable();
    }

    public boolean isEnabled() { return enabled; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }

    public void onEnable() {}
    public void onDisable() {}
    public void onTick() {}
}