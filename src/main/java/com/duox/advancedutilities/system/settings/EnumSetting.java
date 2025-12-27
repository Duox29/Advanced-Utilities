package com.duox.advancedutilities.system.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class EnumSetting<T extends Enum<T>> extends Setting<T> {
    private final T[] modes;

    public EnumSetting(String name, T defaultValue) {
        super(name, defaultValue);
        this.modes = defaultValue.getDeclaringClass().getEnumConstants();
    }

    public void next() {
        int nextIndex = (value.ordinal() + 1) % modes.length;
        value = modes[nextIndex];
    }

    public void setValueByName(String name) {
        for (T constant : modes) {
            if (constant.name().equalsIgnoreCase(name)) {
                this.value = constant;
                return;
            }
        }
    }

    // --- Polymorphic Serialization ---

    @Override
    public JsonElement save() {
        return new JsonPrimitive(this.value.name());
    }

    @Override
    public void load(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            setValueByName(element.getAsString());
        }
    }
}