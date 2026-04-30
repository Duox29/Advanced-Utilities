package com.duox.advancedutilities.gui.widgets;

import com.duox.advancedutilities.system.ConfigManager;
import com.duox.advancedutilities.system.settings.NumberSetting;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.function.Consumer;

public class NumberWidget extends SettingWidget {
    private final NumberSetting setting;

    public NumberWidget(NumberSetting setting, int x, int y, int width, int height) {
        super(x, y, width, height);
        this.setting = setting;
    }

    @Override
    public void init(Consumer<AbstractWidget> widgetConsumer, Runnable onRefresh) {
        widgetConsumer.accept(new SlimSlider(
                x, y, width, height,
                setting.getName(),
                setting.getValue(),
                setting.getMin(),
                setting.getMax(),
                setting.getIncrement(),
                value -> {
                    setting.setValue(value);
                    ConfigManager.getInstance().save();
                }
        ));
    }
}
