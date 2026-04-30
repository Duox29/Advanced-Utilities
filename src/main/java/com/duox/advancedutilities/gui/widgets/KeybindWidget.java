package com.duox.advancedutilities.gui.widgets;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class KeybindWidget extends Button {
    private final KeyMapping keyMapping;
    private boolean listening = false;

    public KeybindWidget(int x, int y, int width, int height, KeyMapping keyMapping) {
        super(x, y, width, height, Component.empty(), b -> {}, DEFAULT_NARRATION);
        this.keyMapping = keyMapping;
        this.updateMessage();
    }

    @Override
    public void onPress() {
        this.listening = !this.listening;
        this.updateMessage();
    }

    private void updateMessage() {
        if (listening) {
            this.setMessage(Component.literal("Press keyboard or mouse input"));
        } else {
            this.setMessage(Component.literal(keyMapping.getKey().getDisplayName().getString()));
        }
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int bg = this.isHoveredOrFocused() ? UiTheme.PANEL_HOVER : UiTheme.PANEL_ALT;
        UiTheme.drawPanel(guiGraphics, getX(), getY(), width, height, bg, listening ? UiTheme.ACCENT : UiTheme.BORDER_SOFT);

        guiGraphics.drawString(font, "Keybind", getX() + 10, getY() + (height - 8) / 2, UiTheme.TEXT_PRIMARY, false);
        guiGraphics.drawString(font, getMessage(), getX() + width - 10 - font.width(getMessage()), getY() + (height - 8) / 2,
                listening ? UiTheme.ACCENT : UiTheme.TEXT_MUTED, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listening) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                listening = false;
            } else {
                InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
                keyMapping.setKey(key);
                Minecraft.getInstance().options.save();
                KeyMapping.resetMapping();
                listening = false;
            }
            updateMessage();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (listening) {
            InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(button);
            keyMapping.setKey(key);
            Minecraft.getInstance().options.save();
            KeyMapping.resetMapping();
            listening = false;
            updateMessage();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
