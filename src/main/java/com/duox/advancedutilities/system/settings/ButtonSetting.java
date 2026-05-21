package com.duox.advancedutilities.system.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

public class ButtonSetting extends Setting<Runnable> {

    private final String buttonText;

    public ButtonSetting(String name, String buttonText, Runnable action) {
        super(name, action);
        this.buttonText = buttonText;
    }

    public String getButtonText() {
        return buttonText;
    }

    public void press() {
        if (value != null) {
            value.run();
        }
    }

    @Override
    public JsonElement save() {
        return JsonNull.INSTANCE;
    }

    @Override
    public void load(JsonElement element) {
        // Button không cần load config
    }
}