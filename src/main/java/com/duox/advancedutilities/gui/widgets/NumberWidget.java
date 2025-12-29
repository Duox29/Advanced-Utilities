package com.duox.advancedutilities.gui.widgets;
/*
 * Widget for configuring numeric settings.
 * Renders as a slider.
 */
import com.duox.advancedutilities.system.ConfigManager;
import com.duox.advancedutilities.system.settings.NumberSetting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.widget.ForgeSlider;

import java.util.function.Consumer;

public class NumberWidget extends SettingWidget {
    private final NumberSetting setting;

    public NumberWidget(NumberSetting setting, int x, int y, int width, int height) {
        super(x, y, width, height);
        this.setting = setting;
    }

    @Override
    public void init(Consumer<AbstractWidget> widgetConsumer, Runnable onRefresh) {
        // ForgeSlider tự động xử lý việc kéo thả
        ForgeSlider slider = new ForgeSlider(
                x, y, width, height,
                Component.literal(setting.getName() + ": "),
                Component.empty(),
                setting.getMin(),
                setting.getMax(),
                setting.getValue(),
                setting.getIncrement(),
                1,
                true
        ) {
            @Override
            protected void applyValue() {
                setting.setValue(this.getValue());
                ConfigManager.getInstance().save();
            }
        };
        widgetConsumer.accept(slider);
    }
}