package com.duox.advancedutilities.gui;

import com.duox.advancedutilities.gui.factory.WidgetFactory;
import com.duox.advancedutilities.gui.widgets.KeybindWidget;
import com.duox.advancedutilities.gui.widgets.SettingWidget;
import com.duox.advancedutilities.gui.widgets.UiTheme;
import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.ConfigManager;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.ModuleManager;
import com.duox.advancedutilities.system.settings.EnchantmentListSetting;
import com.duox.advancedutilities.system.settings.Setting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Redesigned utility GUI using a centered two-pane layout,
 * compact module cards, and a softer modern visual hierarchy.
 */
public class UtilityGui extends Screen {

    private static final int OUTER_MARGIN = 18;
    private static final int HEADER_HEIGHT = 38;
    private static final int SIDEBAR_WIDTH = 228;
    private static final int PANEL_GAP = 14;
    private static final int INNER_PAD = 16;
    private static final int MODULE_ROW_HEIGHT = 28;
    private static final int MODULE_GAP = 6;
    private static final int SETTING_GAP = 8;
    private static final int TAB_HEIGHT = 22;

    private int currentTabIndex = 0;
    private final List<Category> categories = new ArrayList<>();
    private Module selectedModule;

    private final List<AbstractWidget> dynamicWidgets = new ArrayList<>();
    private final List<SettingWidget> customRenderWidgets = new ArrayList<>();

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int contentX;
    private int contentY;
    private int contentW;

    public UtilityGui() {
        super(Component.literal("Advanced Utilities"));
    }

    @Override
    protected void init() {
        super.init();
        categories.clear();
        categories.addAll(Arrays.asList(Category.values()));

        panelW = Math.min(920, this.width - (OUTER_MARGIN * 2));
        panelH = Math.min(620, this.height - (OUTER_MARGIN * 2));
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        contentX = panelX + SIDEBAR_WIDTH + PANEL_GAP;
        contentY = panelY + HEADER_HEIGHT + INNER_PAD;
        contentW = panelW - SIDEBAR_WIDTH - PANEL_GAP - (INNER_PAD * 2);

        if (selectedModule == null) {
            List<Module> modules = getModulesToDisplay();
            if (!modules.isEmpty()) {
                selectedModule = modules.get(0);
            }
        }
        initSettingsPanel(selectedModule);
    }

    private List<Module> getModulesToDisplay() {
        return currentTabIndex == 0
                ? ModuleManager.INSTANCE.getModules()
                : ModuleManager.INSTANCE.getModulesByCategory(categories.get(currentTabIndex - 1));
    }

    private void initSettingsPanel(Module module) {
        for (AbstractWidget widget : dynamicWidgets) {
            this.removeWidget(widget);
        }
        dynamicWidgets.clear();
        customRenderWidgets.clear();

        this.selectedModule = module;
        if (module == null) return;

        int startX = contentX + INNER_PAD;
        int startY = contentY + 40;
        int widgetWidth = contentW - (INNER_PAD * 2);

        KeybindWidget keybindWidget = new KeybindWidget(startX, startY, widgetWidth, 24, module.getKeyMapping());
        this.addRenderableWidget(keybindWidget);
        this.dynamicWidgets.add(keybindWidget);
        startY += 24 + SETTING_GAP;

        for (Setting<?> setting : module.getSettings()) {
            int defaultHeight = (setting instanceof com.duox.advancedutilities.system.settings.BlockListSetting
                    || setting instanceof com.duox.advancedutilities.system.settings.EntityListSetting
                    || setting instanceof com.duox.advancedutilities.system.settings.ItemListSetting
                    || setting instanceof EnchantmentListSetting)
                    ? 74
                    : 24;

            SettingWidget widget = WidgetFactory.create(setting, startX, startY, widgetWidth, defaultHeight);
            if (widget == null) continue;

            widget.init(w -> {
                this.addRenderableWidget(w);
                this.dynamicWidgets.add(w);
            }, () -> this.initSettingsPanel(this.selectedModule));

            this.customRenderWidgets.add(widget);
            startY += widget.getHeight() + SETTING_GAP;
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, UiTheme.SCREEN_DIM);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        drawFrame(guiGraphics, mouseX, mouseY);
        drawTabs(guiGraphics, mouseX, mouseY);
        renderModuleList(guiGraphics, mouseX, mouseY);
        renderSettingsPane(guiGraphics, mouseX, mouseY, partialTick);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawFrame(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        UiTheme.drawPanel(guiGraphics, panelX, panelY, panelW, panelH, UiTheme.PANEL, UiTheme.BORDER);
        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + HEADER_HEIGHT, UiTheme.PANEL_ALT);
        guiGraphics.fill(panelX + SIDEBAR_WIDTH, panelY + HEADER_HEIGHT, panelX + SIDEBAR_WIDTH + 1, panelY + panelH, UiTheme.BORDER_SOFT);
        guiGraphics.fill(panelX, panelY + HEADER_HEIGHT, panelX + panelW, panelY + HEADER_HEIGHT + 1, UiTheme.BORDER_SOFT);

        guiGraphics.drawString(this.font, "Advanced Utilities", panelX + 14, panelY + 9, UiTheme.TEXT_PRIMARY, false);
        guiGraphics.drawString(this.font, "Slim config panel", panelX + 14, panelY + 20, UiTheme.TEXT_FAINT, false);

        int rightHintWidth = this.font.width("ESC close");
        guiGraphics.drawString(this.font, "ESC close", panelX + panelW - 14 - rightHintWidth, panelY + 14, UiTheme.TEXT_FAINT, false);

        UiTheme.drawSectionLabel(guiGraphics, this.font, "MODULES", panelX + 14, panelY + HEADER_HEIGHT + 10);
    }

    private void drawTabs(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int tabCount = categories.size() + 1;
        int tabStartX = panelX + 160;
        int availableW = panelW - 180;
        int tabWidth = Math.max(48, Math.min(78, (availableW - (tabCount - 1) * 6) / Math.max(tabCount, 1)));
        int x = tabStartX;
        int y = panelY + 8;

        drawTab(guiGraphics, x, y, tabWidth, TAB_HEIGHT, "ALL", currentTabIndex == 0, mouseX, mouseY);
        x += tabWidth + 6;

        for (int i = 0; i < categories.size(); i++) {
            drawTab(guiGraphics, x, y, tabWidth, TAB_HEIGHT, categories.get(i).name(), currentTabIndex == i + 1, mouseX, mouseY);
            x += tabWidth + 6;
        }
    }

    private void drawTab(GuiGraphics guiGraphics, int x, int y, int width, int height, String label, boolean selected, int mouseX, int mouseY) {
        boolean hovered = UiTheme.isInside(mouseX, mouseY, x, y, width, height);
        int bg = selected ? UiTheme.ACCENT_SOFT : hovered ? UiTheme.PANEL_HOVER : UiTheme.PANEL_SOFT;
        UiTheme.drawPanel(guiGraphics, x, y, width, height, bg, selected ? UiTheme.ACCENT : UiTheme.BORDER_SOFT);
        guiGraphics.drawCenteredString(this.font, label, x + width / 2, y + 7, selected ? UiTheme.TEXT_PRIMARY : UiTheme.TEXT_MUTED);
    }

    private void renderModuleList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int x = panelX + 12;
        int y = panelY + HEADER_HEIGHT + 24;
        int width = SIDEBAR_WIDTH - 24;

        List<Module> modulesToDisplay = getModulesToDisplay();
        if (modulesToDisplay.isEmpty()) {
            guiGraphics.drawString(this.font, "No modules in this category", x, y + 4, UiTheme.TEXT_FAINT, false);
            return;
        }

        for (Module mod : modulesToDisplay) {
            boolean hovered = UiTheme.isInside(mouseX, mouseY, x, y, width, MODULE_ROW_HEIGHT);
            boolean selected = mod == selectedModule;

            int bg = selected ? UiTheme.PANEL_ACTIVE : hovered ? UiTheme.PANEL_HOVER : UiTheme.PANEL_SOFT;
            UiTheme.drawPanel(guiGraphics, x, y, width, MODULE_ROW_HEIGHT, bg, selected ? UiTheme.ACCENT : UiTheme.BORDER_SOFT);

            int dotColor = mod.isEnabled() ? UiTheme.SUCCESS : UiTheme.TEXT_FAINT;
            guiGraphics.fill(x + 8, y + 11, x + 14, y + 17, dotColor);
            guiGraphics.drawString(this.font, mod.getName(), x + 20, y + 10, UiTheme.TEXT_PRIMARY, false);

            int pillW = 36;
            int pillX = x + width - pillW - 8;
            UiTheme.drawPill(guiGraphics, pillX, y + 6, pillW, 16,
                    mod.isEnabled() ? UiTheme.withAlpha(UiTheme.SUCCESS, 40) : UiTheme.PANEL_ALT,
                    mod.isEnabled() ? UiTheme.SUCCESS : UiTheme.TEXT_MUTED,
                    this.font,
                    mod.isEnabled() ? "ON" : "OFF");

            if (hovered) {
                guiGraphics.renderTooltip(this.font, Component.literal(mod.getDescription()), mouseX, mouseY);
            }
            y += MODULE_ROW_HEIGHT + MODULE_GAP;
        }
    }

    private void renderSettingsPane(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int paneX = contentX;
        int paneY = panelY + HEADER_HEIGHT + 10;
        int paneH = panelH - HEADER_HEIGHT - 20;

        UiTheme.drawPanel(guiGraphics, paneX, paneY, contentW, paneH, UiTheme.PANEL_SOFT, UiTheme.BORDER_SOFT);

        if (selectedModule == null) {
            guiGraphics.drawCenteredString(this.font, "Select a module", paneX + contentW / 2, paneY + paneH / 2 - 5, UiTheme.TEXT_MUTED);
            return;
        }

        guiGraphics.drawString(this.font, selectedModule.getName(), paneX + INNER_PAD, paneY + 12, UiTheme.TEXT_PRIMARY, false);
        guiGraphics.drawString(this.font, selectedModule.getDescription(), paneX + INNER_PAD, paneY + 24, UiTheme.TEXT_FAINT, false);

        UiTheme.drawPill(guiGraphics,
                paneX + contentW - 76,
                paneY + 10,
                56,
                18,
                selectedModule.isEnabled() ? UiTheme.withAlpha(UiTheme.SUCCESS, 36) : UiTheme.PANEL_ALT,
                selectedModule.isEnabled() ? UiTheme.SUCCESS : UiTheme.TEXT_MUTED,
                this.font,
                selectedModule.isEnabled() ? "ACTIVE" : "DISABLED");

        for (SettingWidget widget : customRenderWidgets) {
            widget.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseY >= panelY + HEADER_HEIGHT && mouseX > panelX + SIDEBAR_WIDTH + PANEL_GAP) {
            for (SettingWidget widget : customRenderWidgets) {
                if (widget.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
            if (super.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        if (mouseY >= panelY && mouseY <= panelY + HEADER_HEIGHT) {
            int tabCount = categories.size() + 1;
            int tabStartX = panelX + 160;
            int availableW = panelW - 180;
            int tabWidth = Math.max(48, Math.min(78, (availableW - (tabCount - 1) * 6) / Math.max(tabCount, 1)));
            int x = tabStartX;
            int y = panelY + 8;

            if (UiTheme.isInside(mouseX, mouseY, x, y, tabWidth, TAB_HEIGHT)) {
                currentTabIndex = 0;
                if (!getModulesToDisplay().contains(selectedModule)) {
                    selectedModule = getModulesToDisplay().isEmpty() ? null : getModulesToDisplay().get(0);
                }
                initSettingsPanel(selectedModule);
                return true;
            }
            x += tabWidth + 6;

            for (int i = 0; i < categories.size(); i++) {
                if (UiTheme.isInside(mouseX, mouseY, x, y, tabWidth, TAB_HEIGHT)) {
                    currentTabIndex = i + 1;
                    if (!getModulesToDisplay().contains(selectedModule)) {
                        selectedModule = getModulesToDisplay().isEmpty() ? null : getModulesToDisplay().get(0);
                    }
                    initSettingsPanel(selectedModule);
                    return true;
                }
                x += tabWidth + 6;
            }
        }

        int rowX = panelX + 12;
        int rowY = panelY + HEADER_HEIGHT + 24;
        int rowW = SIDEBAR_WIDTH - 24;
        for (Module mod : getModulesToDisplay()) {
            if (UiTheme.isInside(mouseX, mouseY, rowX, rowY, rowW, MODULE_ROW_HEIGHT)) {
                int pillW = 36;
                int pillX = rowX + rowW - pillW - 8;
                if (UiTheme.isInside(mouseX, mouseY, pillX, rowY + 6, pillW, 16)) {
                    mod.toggle();
                    ConfigManager.getInstance().save();
                } else {
                    initSettingsPanel(mod);
                }
                return true;
            }
            rowY += MODULE_ROW_HEIGHT + MODULE_GAP;
        }

        return false;
    }
}
