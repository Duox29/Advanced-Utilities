package com.duox.advancedutilities.gui;

import com.duox.advancedutilities.gui.factory.WidgetFactory;
import com.duox.advancedutilities.gui.widgets.KeybindWidget;
import com.duox.advancedutilities.gui.widgets.SettingWidget;
import com.duox.advancedutilities.gui.widgets.UiTheme;
import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.ConfigManager;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.ModuleManager;
import com.duox.advancedutilities.system.settings.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private static final int SEARCH_HEIGHT = 22;
    private static final int SEARCH_GAP = 10;
    private static final int SETTINGS_HEADER_SPACE = 46;
    private static final int SCROLL_STEP = 18;

    private int currentTabIndex = 0;
    private final List<Category> categories = new ArrayList<>();
    private Module selectedModule;

    private final List<AbstractWidget> dynamicWidgets = new ArrayList<>();
    private final Map<AbstractWidget, Integer> dynamicWidgetBaseY = new HashMap<>();
    private final List<SettingWidget> customRenderWidgets = new ArrayList<>();

    private EditBox moduleSearchBox;

    private int moduleScrollOffset;
    private int settingsScrollOffset;
    private int settingsContentHeight;

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

        initSearchBox();

        if (selectedModule == null || !getModulesToDisplay().contains(selectedModule)) {
            List<Module> modules = getModulesToDisplay();
            selectedModule = modules.isEmpty() ? null : modules.get(0);
        }

        clampModuleScroll();
        initSettingsPanel(selectedModule);
        updateSearchBoxState();
    }

    private void initSearchBox() {
        int searchX = panelX + 22;
        int searchY = panelY + HEADER_HEIGHT + 29;
        int searchW = SIDEBAR_WIDTH - 44;
        int searchH = SEARCH_HEIGHT - 8;
        String currentValue = moduleSearchBox == null ? "" : moduleSearchBox.getValue();

        moduleSearchBox = new EditBox(this.font, searchX, searchY, searchW, searchH, Component.literal("Search modules"));
        moduleSearchBox.setValue(currentValue);
        moduleSearchBox.setMaxLength(64);
        UiTheme.styleEditBox(moduleSearchBox);
        moduleSearchBox.setResponder(value -> {
            moduleScrollOffset = 0;
            ensureSelectedModuleVisible();
        });
        this.addRenderableWidget(moduleSearchBox);
    }

    private List<Module> getModulesToDisplay() {
        List<Module> base = currentTabIndex == 0
                ? ModuleManager.INSTANCE.getModules()
                : ModuleManager.INSTANCE.getModulesByCategory(categories.get(currentTabIndex - 1));

        String query = getSearchQuery();
        if (currentTabIndex != 0 || query.isEmpty()) {
            return base;
        }

        List<Module> filtered = new ArrayList<>();
        for (Module module : base) {
            String name = module.getName() == null ? "" : module.getName().toLowerCase(Locale.ROOT);
            String description = module.getDescription() == null ? "" : module.getDescription().toLowerCase(Locale.ROOT);
            if (name.contains(query) || description.contains(query)) {
                filtered.add(module);
            }
        }
        return filtered;
    }

    private String getSearchQuery() {
        return moduleSearchBox == null ? "" : moduleSearchBox.getValue().trim().toLowerCase(Locale.ROOT);
    }

    private void ensureSelectedModuleVisible() {
        List<Module> modules = getModulesToDisplay();
        Module newSelection = modules.contains(selectedModule)
                ? selectedModule
                : (modules.isEmpty() ? null : modules.get(0));

        if (newSelection != selectedModule) {
            initSettingsPanel(newSelection);
        } else {
            clampModuleScroll();
        }
    }

    private void initSettingsPanel(Module module) {
        for (AbstractWidget widget : dynamicWidgets) {
            this.removeWidget(widget);
        }
        dynamicWidgets.clear();
        dynamicWidgetBaseY.clear();
        customRenderWidgets.clear();

        this.selectedModule = module;
        this.settingsScrollOffset = 0;
        this.settingsContentHeight = 0;

        if (module == null) {
            return;
        }

        int startX = contentX + INNER_PAD;
        int startY = getSettingsViewportTop();
        int widgetWidth = contentW - (INNER_PAD * 2);

        KeybindWidget keybindWidget = new KeybindWidget(startX, startY, widgetWidth, 24, module.getKeyMapping());
        addDynamicWidget(keybindWidget);
        startY += 24 + SETTING_GAP;

        for (Setting<?> setting : module.getSettings()) {
            int defaultHeight = (setting instanceof BlockListSetting
                    || setting instanceof EntityListSetting
                    || setting instanceof ItemListSetting
                    || setting instanceof EnchantmentListSetting)
                    ? 74
                    : 24;

            SettingWidget widget = WidgetFactory.create(setting, startX, startY, widgetWidth, defaultHeight);
            if (widget == null) {
                continue;
            }

            widget.init(this::addDynamicWidget, () -> this.initSettingsPanel(this.selectedModule));
            this.customRenderWidgets.add(widget);
            startY += widget.getHeight() + SETTING_GAP;
        }

        this.settingsContentHeight = Math.max(0, startY - getSettingsViewportTop() - SETTING_GAP);
        clampSettingsScroll();
        updateDynamicWidgetPositions();
    }

    private void addDynamicWidget(AbstractWidget widget) {
        this.addRenderableWidget(widget);
        this.dynamicWidgets.add(widget);
        this.dynamicWidgetBaseY.put(widget, widget.getY());
    }

    private void updateSearchBoxState() {
        if (moduleSearchBox == null) {
            return;
        }
        boolean visible = currentTabIndex == 0;
        moduleSearchBox.visible = visible;
        moduleSearchBox.active = visible;
    }

    private void updateDynamicWidgetPositions() {
        int viewportTop = getSettingsViewportTop();
        int viewportBottom = getSettingsViewportBottom();

        for (AbstractWidget widget : dynamicWidgets) {
            Integer baseY = dynamicWidgetBaseY.get(widget);
            if (baseY == null) {
                continue;
            }

            int newY = baseY - settingsScrollOffset;
            widget.setY(newY);

            boolean visible = newY + widget.getHeight() > viewportTop && newY < viewportBottom;
            widget.visible = visible;
            widget.active = visible;
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, UiTheme.SCREEN_DIM);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        updateSearchBoxState();
        updateDynamicWidgetPositions();

        drawFrame(guiGraphics, mouseX, mouseY);
        drawTabs(guiGraphics, mouseX, mouseY);
        renderSearchBox(guiGraphics, mouseX, mouseY, partialTick);
        renderModuleList(guiGraphics, mouseX, mouseY);
        renderSettingsPane(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawFrame(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        UiTheme.drawPanel(guiGraphics, panelX, panelY, panelW, panelH, UiTheme.PANEL, UiTheme.BORDER);
        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + HEADER_HEIGHT, UiTheme.PANEL_ALT);
        guiGraphics.fill(panelX + SIDEBAR_WIDTH, panelY + HEADER_HEIGHT, panelX + SIDEBAR_WIDTH + 1, panelY + panelH, UiTheme.BORDER_SOFT);
        guiGraphics.fill(panelX, panelY + HEADER_HEIGHT, panelX + panelW, panelY + HEADER_HEIGHT + 1, UiTheme.BORDER_SOFT);

        guiGraphics.drawString(this.font, "Advanced Utilities", panelX + 14, panelY + 9, UiTheme.TEXT_PRIMARY, false);
        guiGraphics.drawString(this.font, "Slim config panel", panelX + 14, panelY + 20, UiTheme.TEXT_MUTED, false);

        int rightHintWidth = this.font.width("ESC close");
        guiGraphics.drawString(this.font, "ESC close", panelX + panelW - 14 - rightHintWidth, panelY + 14, UiTheme.TEXT_MUTED, false);

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

    private void renderSearchBox(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (moduleSearchBox == null || !moduleSearchBox.visible) {
            return;
        }

        int boxX = panelX + 12;
        int boxY = panelY + HEADER_HEIGHT + 24;
        int boxW = SIDEBAR_WIDTH - 24;
        boolean hovered = UiTheme.isInside(mouseX, mouseY, boxX, boxY, boxW, SEARCH_HEIGHT);
        int bg = hovered || moduleSearchBox.isFocused() ? UiTheme.PANEL_HOVER : UiTheme.PANEL_SOFT;

        UiTheme.drawPanel(guiGraphics, boxX, boxY, boxW, SEARCH_HEIGHT, bg, moduleSearchBox.isFocused() ? UiTheme.ACCENT : UiTheme.BORDER_SOFT);
        guiGraphics.drawString(this.font, "Search", boxX + 8, boxY + 7, UiTheme.TEXT_MUTED, false);
        moduleSearchBox.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderModuleList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int x = panelX + 12;
        int yStart = getModuleListStartY();
        int width = SIDEBAR_WIDTH - 24;
        int yBottom = panelY + panelH - 12;

        List<Module> modulesToDisplay = getModulesToDisplay();
        if (modulesToDisplay.isEmpty()) {
            String emptyText = currentTabIndex == 0 && !getSearchQuery().isEmpty()
                    ? "No modules match your search"
                    : "No modules in this category";
            guiGraphics.drawString(this.font, emptyText, x, yStart + 4, UiTheme.TEXT_MUTED, false);
            return;
        }

        guiGraphics.enableScissor(x, yStart, x + width, yBottom);

        int y = yStart - moduleScrollOffset;
        for (Module mod : modulesToDisplay) {
            if (y + MODULE_ROW_HEIGHT <= yStart || y >= yBottom) {
                y += MODULE_ROW_HEIGHT + MODULE_GAP;
                continue;
            }

            boolean hovered = UiTheme.isInside(mouseX, mouseY, x, y, width, MODULE_ROW_HEIGHT);
            boolean selected = mod == selectedModule;

            int bg = selected ? UiTheme.PANEL_ACTIVE : hovered ? UiTheme.PANEL_HOVER : UiTheme.PANEL_SOFT;
            UiTheme.drawPanel(guiGraphics, x, y, width, MODULE_ROW_HEIGHT, bg, selected ? UiTheme.ACCENT : UiTheme.BORDER_SOFT);

            int dotColor = mod.isEnabled() ? UiTheme.SUCCESS : UiTheme.TEXT_MUTED;
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

        guiGraphics.disableScissor();
    }

    private void renderSettingsPane(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int paneX = getSettingsPaneX();
        int paneY = getSettingsPaneY();
        int paneH = getSettingsPaneHeight();

        UiTheme.drawPanel(guiGraphics, paneX, paneY, contentW, paneH, UiTheme.PANEL_SOFT, UiTheme.BORDER_SOFT);

        if (selectedModule == null) {
            guiGraphics.drawCenteredString(this.font, "Select a module", paneX + contentW / 2, paneY + paneH / 2 - 5, UiTheme.TEXT_MUTED);
            return;
        }

        guiGraphics.drawString(this.font, selectedModule.getName(), paneX + INNER_PAD, paneY + 12, UiTheme.TEXT_PRIMARY, false);
        guiGraphics.drawString(this.font, selectedModule.getDescription(), paneX + INNER_PAD, paneY + 24, UiTheme.TEXT_MUTED, false);

        UiTheme.drawPill(guiGraphics,
                paneX + contentW - 76,
                paneY + 10,
                56,
                18,
                selectedModule.isEnabled() ? UiTheme.withAlpha(UiTheme.SUCCESS, 36) : UiTheme.PANEL_ALT,
                selectedModule.isEnabled() ? UiTheme.SUCCESS : UiTheme.TEXT_MUTED,
                this.font,
                selectedModule.isEnabled() ? "ACTIVE" : "DISABLED");

        guiGraphics.fill(paneX + INNER_PAD, getSettingsViewportTop() - 8, paneX + contentW - INNER_PAD, getSettingsViewportTop() - 7, UiTheme.BORDER_SOFT);

        guiGraphics.enableScissor(paneX + 1, getSettingsViewportTop(), paneX + contentW - 1, getSettingsViewportBottom());
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, -settingsScrollOffset, 0.0F);
        for (SettingWidget widget : customRenderWidgets) {
            widget.render(guiGraphics, mouseX, mouseY + settingsScrollOffset, partialTick);
        }
        guiGraphics.pose().popPose();

        for (AbstractWidget widget : dynamicWidgets) {
            if (widget.visible) {
                widget.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }
        guiGraphics.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (moduleSearchBox != null && moduleSearchBox.visible && moduleSearchBox.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (isInsideSettingsViewport(mouseX, mouseY)) {
            for (SettingWidget widget : customRenderWidgets) {
                if (widget.mouseClicked(mouseX, mouseY + settingsScrollOffset, button)) {
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
                moduleScrollOffset = 0;
                updateSearchBoxState();
                ensureSelectedModuleVisible();
                return true;
            }
            x += tabWidth + 6;

            for (int i = 0; i < categories.size(); i++) {
                if (UiTheme.isInside(mouseX, mouseY, x, y, tabWidth, TAB_HEIGHT)) {
                    currentTabIndex = i + 1;
                    moduleScrollOffset = 0;
                    updateSearchBoxState();
                    ensureSelectedModuleVisible();
                    return true;
                }
                x += tabWidth + 6;
            }
        }

        int rowX = panelX + 12;
        int rowY = getModuleListStartY() - moduleScrollOffset;
        int rowW = SIDEBAR_WIDTH - 24;
        int rowBottom = panelY + panelH - 12;
        for (Module mod : getModulesToDisplay()) {
            if (rowY + MODULE_ROW_HEIGHT > getModuleListStartY() && rowY < rowBottom && UiTheme.isInside(mouseX, mouseY, rowX, rowY, rowW, MODULE_ROW_HEIGHT)) {
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isInsideSettingsViewport(mouseX, mouseY)) {
            if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }

            int maxScroll = getMaxSettingsScroll();
            if (maxScroll > 0) {
                settingsScrollOffset = clamp(settingsScrollOffset - (int) Math.round(scrollY * SCROLL_STEP), 0, maxScroll);
                updateDynamicWidgetPositions();
                return true;
            }
        }

        if (isInsideModuleList(mouseX, mouseY)) {
            int maxScroll = getMaxModuleScroll();
            if (maxScroll > 0) {
                moduleScrollOffset = clamp(moduleScrollOffset - (int) Math.round(scrollY * SCROLL_STEP), 0, maxScroll);
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int getModuleListStartY() {
        int baseY = panelY + HEADER_HEIGHT + 24;
        return currentTabIndex == 0 ? baseY + SEARCH_HEIGHT + SEARCH_GAP : baseY;
    }

    private int getModuleListViewportHeight() {
        return Math.max(0, (panelY + panelH - 12) - getModuleListStartY());
    }

    private int getMaxModuleScroll() {
        int rowCount = getModulesToDisplay().size();
        int contentHeight = rowCount <= 0 ? 0 : (rowCount * (MODULE_ROW_HEIGHT + MODULE_GAP)) - MODULE_GAP;
        return Math.max(0, contentHeight - getModuleListViewportHeight());
    }

    private void clampModuleScroll() {
        moduleScrollOffset = clamp(moduleScrollOffset, 0, getMaxModuleScroll());
    }

    private int getSettingsPaneX() {
        return contentX;
    }

    private int getSettingsPaneY() {
        return panelY + HEADER_HEIGHT + 10;
    }

    private int getSettingsPaneHeight() {
        return panelH - HEADER_HEIGHT - 20;
    }

    private int getSettingsViewportTop() {
        return contentY + SETTINGS_HEADER_SPACE;
    }

    private int getSettingsViewportBottom() {
        return getSettingsPaneY() + getSettingsPaneHeight() - INNER_PAD;
    }

    private int getSettingsViewportHeight() {
        return Math.max(0, getSettingsViewportBottom() - getSettingsViewportTop());
    }

    private int getMaxSettingsScroll() {
        return Math.max(0, settingsContentHeight - getSettingsViewportHeight());
    }

    private void clampSettingsScroll() {
        settingsScrollOffset = clamp(settingsScrollOffset, 0, getMaxSettingsScroll());
    }

    private boolean isInsideModuleList(double mouseX, double mouseY) {
        return UiTheme.isInside(mouseX, mouseY,
                panelX + 12,
                getModuleListStartY(),
                SIDEBAR_WIDTH - 24,
                getModuleListViewportHeight());
    }

    private boolean isInsideSettingsViewport(double mouseX, double mouseY) {
        return UiTheme.isInside(mouseX, mouseY,
                getSettingsPaneX() + 1,
                getSettingsViewportTop(),
                contentW - 2,
                getSettingsViewportHeight());
    }

    private int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }
}
