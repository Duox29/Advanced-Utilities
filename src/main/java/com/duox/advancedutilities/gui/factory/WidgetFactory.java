package com.duox.advancedutilities.gui.factory;

import com.duox.advancedutilities.gui.widgets.*;
import com.duox.advancedutilities.system.settings.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Central registry for mapping Data (Settings) to Views (Widgets).
 * This eliminates 'instanceof' chains in your Screens.
 */
public class WidgetFactory {
    // Functional interface to create a widget
    @FunctionalInterface
    public interface WidgetProvider<T extends Setting<?>> {
        SettingWidget create(T setting, int x, int y, int w, int h);
    }

    // The Registry Map
    private static final Map<Class<? extends Setting>, WidgetProvider> providers = new HashMap<>();

    // Static Block to register your defaults
    static {
        register(BooleanSetting.class, (s, x, y, w, h) ->
                new BooleanWidget(s, x, y, w, h));

        register(NumberSetting.class, (s, x, y, w, h) ->
                new NumberWidget(s, x, y, w, h));

        register(EnumSetting.class, (s, x, y, w, h) ->
                new EnumWidget(s, x, y, w, h));

        // Note: BlockListWidget usually needs more height, handled in the create logic or passed in
        register(BlockListSetting.class, (s, x, y, w, h) ->
                new BlockListWidget(s, x, y, w, 55)); // Hardcoded height specific to this widget

        register(EntityListSetting.class, (s, x, y, w, h) ->
                new EntityListWidget(s, x, y, w, 55));
    }

    /**
     * Registers a new Setting-to-Widget mapping.
     * Call this from your Mod setup if you add custom settings from other packages.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Setting<?>> void register(Class<T> settingClass, WidgetProvider<T> provider) {
        providers.put(settingClass, provider);
    }

    /**
     * Factory method to generate the correct widget.
     */
    @SuppressWarnings("unchecked")
    public static SettingWidget create(Setting<?> setting, int x, int y, int w, int hDefault) {
        WidgetProvider provider = providers.get(setting.getClass());
        if (provider == null) {
            // Fallback or Error logging
            System.err.println("No widget provider found for setting: " + setting.getClass().getName());
            return null;
        }
        return provider.create(setting, x, y, w, hDefault);
    }
}