package com.duox.advancedutilities.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * Shared theme + slim custom widgets for the modernized config UI.
 */
public final class UiTheme {
    public static final int SCREEN_DIM = 0x88000000;
    public static final int PANEL = 0xF6171B22;
    public static final int PANEL_ALT = 0xF91C222B;
    public static final int PANEL_SOFT = 0xF613171D;
    public static final int PANEL_HOVER = 0xFA202733;
    public static final int PANEL_ACTIVE = 0xFA243042;
    public static final int BORDER = 0xFF2B3442;
    public static final int BORDER_SOFT = 0xCC2B3644;
    public static final int ACCENT = 0xFF6EA8FE;
    public static final int ACCENT_SOFT = 0x553E7BDA;
    public static final int SUCCESS = 0xFF58D68D;
    public static final int DANGER = 0xFFF07178;
    public static final int TEXT_PRIMARY = 0xFFF4F8FF;
    public static final int TEXT_MUTED = 0xFFC1CBD9;
    public static final int TEXT_FAINT = 0xFF9AA7BA;

    private UiTheme() {}

    public static void drawPanel(GuiGraphics g, int x, int y, int w, int h, int bg, int border) {
        g.fill(x, y, x + w, y + h, bg);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w - 1, y, x + w, y + h, border);
    }

    public static void drawInset(GuiGraphics g, int x, int y, int w, int h) {
        drawPanel(g, x, y, w, h, PANEL_SOFT, BORDER_SOFT);
    }

    public static void drawPill(GuiGraphics g, int x, int y, int w, int h, int bg, int textColor, Font font, String text) {
        drawPanel(g, x, y, w, h, bg, withAlpha(textColor, 90));
        g.drawCenteredString(font, text, x + w / 2, y + (h - 8) / 2, textColor);
    }

    public static void drawSectionLabel(GuiGraphics g, Font font, String text, int x, int y) {
        g.drawString(font, text, x, y, TEXT_FAINT, false);
    }

    public static boolean isInside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    public static int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    public static void styleEditBox(EditBox editBox) {
        editBox.setBordered(false);
        editBox.setTextColor(TEXT_PRIMARY);
        editBox.setTextColorUneditable(TEXT_MUTED);
    }
}

final class SlimActionButton extends Button {
    public SlimActionButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int bg = this.active
                ? (this.isHoveredOrFocused() ? UiTheme.PANEL_HOVER : UiTheme.PANEL_ALT)
                : UiTheme.PANEL_SOFT;
        UiTheme.drawPanel(guiGraphics, getX(), getY(), width, height, bg, UiTheme.BORDER_SOFT);
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2,
                this.active ? UiTheme.TEXT_PRIMARY : UiTheme.TEXT_FAINT);
    }
}

final class SlimRowButton extends Button {
    private final Supplier<Component> leftSupplier;
    private final Supplier<Component> rightSupplier;
    private final Supplier<Integer> rightColorSupplier;

    public SlimRowButton(int x, int y, int width, int height,
                         Supplier<Component> leftSupplier,
                         Supplier<Component> rightSupplier,
                         Supplier<Integer> rightColorSupplier,
                         OnPress onPress) {
        super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
        this.leftSupplier = leftSupplier;
        this.rightSupplier = rightSupplier;
        this.rightColorSupplier = rightColorSupplier;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int bg = this.isHoveredOrFocused() ? UiTheme.PANEL_HOVER : UiTheme.PANEL_ALT;
        UiTheme.drawPanel(guiGraphics, getX(), getY(), width, height, bg, UiTheme.BORDER_SOFT);

        Font font = Minecraft.getInstance().font;
        Component left = leftSupplier.get();
        Component right = rightSupplier.get();
        int rx = getX() + width - 10 - font.width(right);

        guiGraphics.drawString(font, left, getX() + 10, getY() + (height - 8) / 2, UiTheme.TEXT_PRIMARY, false);
        guiGraphics.drawString(font, right, rx, getY() + (height - 8) / 2, rightColorSupplier.get(), false);
    }
}

final class SlimSlider extends AbstractSliderButton {
    private final String label;
    private final double min;
    private final double max;
    private final double increment;
    private final java.util.function.DoubleConsumer onValueChanged;

    public SlimSlider(int x, int y, int width, int height,
                      String label,
                      double currentValue,
                      double min,
                      double max,
                      double increment,
                      java.util.function.DoubleConsumer onValueChanged) {
        super(x, y, width, height, Component.empty(), normalize(currentValue, min, max));
        this.label = label;
        this.min = min;
        this.max = max;
        this.increment = increment;
        this.onValueChanged = onValueChanged;
        updateMessage();
    }

    private static double normalize(double value, double min, double max) {
        if (max <= min) return 0.0D;
        return (value - min) / (max - min);
    }

    private double actualValue() {
        double raw = this.value * (max - min) + min;
        if (increment > 0.0D) {
            raw = Math.round(raw / increment) * increment;
        }
        return Math.max(min, Math.min(max, raw));
    }

    @Override
    protected void updateMessage() {
        this.setMessage(Component.literal(label + ": " + String.format("%.2f", actualValue())));
    }

    @Override
    protected void applyValue() {
        onValueChanged.accept(actualValue());
        updateMessage();
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();

        UiTheme.drawPanel(guiGraphics, x, y, width, height, this.isHoveredOrFocused() ? UiTheme.PANEL_HOVER : UiTheme.PANEL_ALT, UiTheme.BORDER_SOFT);

        guiGraphics.drawString(font, label, x + 10, y + 6, UiTheme.TEXT_PRIMARY, false);
        String valueText = String.format("%.2f", actualValue());
        guiGraphics.drawString(font, valueText, x + width - 10 - font.width(valueText), y + 6, UiTheme.TEXT_MUTED, false);

        int trackX = x + 11;
        int trackY = y + height - 6;
        int trackW = width - 20;
        guiGraphics.fill(trackX, trackY, trackX + trackW, trackY + 2, UiTheme.BORDER);

        int fillW = (int) Math.round(trackW * this.value);
        guiGraphics.fill(trackX, trackY, trackX + fillW, trackY + 2, UiTheme.ACCENT);

        int knobX = trackX + fillW - 2;
        guiGraphics.fill(knobX, trackY - 3, knobX + 4, trackY + 5, UiTheme.ACCENT);
    }
}
