package com.duox.advancedutilities.gui.widgets;

import com.duox.advancedutilities.system.ConfigManager;
import com.duox.advancedutilities.system.settings.BooleanSetting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class BooleanWidget extends SettingWidget {
    private final BooleanSetting setting;

    public BooleanWidget(BooleanSetting setting, int x, int y, int width, int height) {
        super(x, y, width, height);
        this.setting = setting;
    }

    @Override
    public void init(Consumer<AbstractWidget> widgetConsumer, Runnable onRefresh) {
        widgetConsumer.accept(new SlimRowButton(
                x, y, width, height,
                () -> Component.literal(setting.getName()),
                () -> Component.literal(setting.getValue() ? "ON" : "OFF"),
                () -> setting.getValue() ? UiTheme.SUCCESS : UiTheme.TEXT_MUTED,
                button -> {
                    setting.toggle();
                    ConfigManager.getInstance().save();
                }
        ));
    }
}
