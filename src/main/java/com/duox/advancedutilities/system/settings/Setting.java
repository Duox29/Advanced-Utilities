package com.duox.advancedutilities.system.settings;

import com.google.gson.JsonElement;

/**
 * Refactored to include Serialization logic.
 * Now ConfigManager doesn't need to know the specific type of Setting.
 */
public abstract class Setting<T> {
    private final String name;
    protected T value;

    public Setting(String name, T defaultValue) {
        this.name = name;
        this.value = defaultValue;
    }

    public String getName() { return name; }
    public T getValue() { return value; }
    public void setValue(T value) { this.value = value; }

    // --- Abstract Methods for Clean Architecture ---

    /**
     * Serializes the current value to a JsonElement.
     */
    public abstract JsonElement save();

    /**
     * Loads the value from a JsonElement.
     * Implementation handles validation and try-catch logic.
     */
    public abstract void load(JsonElement element);
}