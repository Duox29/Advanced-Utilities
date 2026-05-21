package com.duox.advancedutilities.gui.widgets;

import com.duox.advancedutilities.system.settings.ButtonSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class ButtonWidget extends SettingWidget {
    private static final int BUTTON_W = 68;

    private final ButtonSetting setting;

    public ButtonWidget(ButtonSetting setting, int x, int y, int width, int height) {
        super(x, y, width, height);
        this.setting = setting;
    }

    @Override
    public void init(Consumer<AbstractWidget> widgetConsumer, Runnable onRefresh) {
        widgetConsumer.accept(new SlimActionButton(
                x + width - BUTTON_W,
                y + 2,
                BUTTON_W,
                height - 4,
                Component.literal(setting.getButtonText()),
                b -> setting.press()
        ));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();

        UiTheme.drawPanel(
                guiGraphics,
                x,
                y,
                width,
                height,
                UiTheme.PANEL_ALT,
                UiTheme.BORDER_SOFT
        );

        guiGraphics.drawString(
                mc.font,
                setting.getName(),
                x + 10,
                y + (height - 8) / 2,
                UiTheme.TEXT_PRIMARY,
                false
        );
    }
}