package com.duox.advancedutilities.gui.widgets;

import com.duox.advancedutilities.system.ConfigManager;
import com.duox.advancedutilities.system.settings.EnumSetting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class EnumWidget extends SettingWidget {
    private final EnumSetting<?> setting;

    public EnumWidget(EnumSetting<?> setting, int x, int y, int width, int height) {
        super(x, y, width, height);
        this.setting = setting;
    }

    @Override
    public void init(Consumer<AbstractWidget> widgetConsumer, Runnable onRefresh) {
        widgetConsumer.accept(new SlimRowButton(
                x, y, width, height,
                () -> Component.literal(setting.getName()),
                () -> Component.literal(setting.getValue().name()),
                () -> UiTheme.TEXT_MUTED,
                button -> {
                    setting.next();
                    ConfigManager.getInstance().save();
                }
        ));
    }
}
