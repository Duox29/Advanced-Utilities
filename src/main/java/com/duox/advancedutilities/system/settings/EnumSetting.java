package com.duox.advancedutilities.system.settings;

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
}