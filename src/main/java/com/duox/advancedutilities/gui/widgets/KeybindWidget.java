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
    private static final int PURGE_W = 58;
    private static final int PURGE_GAP = 6;

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

    private void purgeKeybind() {
        keyMapping.setKey(InputConstants.UNKNOWN);
        Minecraft.getInstance().options.save();
        KeyMapping.resetMapping();
        listening = false;
        updateMessage();
    }

    private void updateMessage() {
        if (listening) {
            this.setMessage(Component.literal("Press keyboard or mouse input"));
        } else if (InputConstants.UNKNOWN.equals(keyMapping.getKey())) {
            this.setMessage(Component.literal("Unbound"));
        } else {
            this.setMessage(Component.literal(keyMapping.getKey().getDisplayName().getString()));
        }
    }

    private int getPurgeX() {
        return getX() + width - PURGE_W - 4;
    }

    private int getPurgeY() {
        return getY() + 2;
    }

    private int getPurgeH() {
        return height - 4;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;

        int bg = this.isHoveredOrFocused() ? UiTheme.PANEL_HOVER : UiTheme.PANEL_ALT;
        UiTheme.drawPanel(guiGraphics, getX(), getY(), width, height, bg, listening ? UiTheme.ACCENT : UiTheme.BORDER_SOFT);

        int purgeX = getPurgeX();
        int purgeY = getPurgeY();
        int purgeH = getPurgeH();
        boolean purgeHovered = UiTheme.isInside(mouseX, mouseY, purgeX, purgeY, PURGE_W, purgeH);

        UiTheme.drawPill(guiGraphics, purgeX, purgeY, PURGE_W, purgeH,
                purgeHovered ? UiTheme.PANEL_HOVER : UiTheme.PANEL_SOFT,
                UiTheme.DANGER,
                font,
                "Purge");

        guiGraphics.drawString(font, "Keybind", getX() + 10, getY() + (height - 8) / 2, UiTheme.TEXT_PRIMARY, false);

        int valueRight = purgeX - PURGE_GAP;
        int valueX = Math.max(getX() + 70, valueRight - font.width(getMessage()));
        guiGraphics.drawString(font, getMessage(), valueX, getY() + (height - 8) / 2,
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
        if (!this.active || !this.visible) {
            return false;
        }

        if (UiTheme.isInside(mouseX, mouseY, getPurgeX(), getPurgeY(), PURGE_W, getPurgeH())) {
            purgeKeybind();
            return true;
        }

        if (listening) {
            InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(button);
            keyMapping.setKey(key);
            Minecraft.getInstance().options.save();
            KeyMapping.resetMapping();
            listening = false;
            updateMessage();
            return true;
        }

        if (this.clicked(mouseX, mouseY)) {
            this.onPress();
            return true;
        }

        return false;
    }
}