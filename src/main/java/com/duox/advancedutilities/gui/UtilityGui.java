package com.duox.advancedutilities.gui;

import com.duox.advancedutilities.gui.widgets.*; // Import tất cả Widget
import com.duox.advancedutilities.system.*;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class UtilityGui extends Screen {

    // --- Layout Constants ---
    private static final int TOP_BAR_HEIGHT = 30;
    private static final int SIDEBAR_WIDTH = 120;
    private static final int MODULE_BTN_HEIGHT = 22;
    private static final int MODULE_BTN_WIDTH = 100;
    private static final int PADDING = 5;

    // --- State ---
    private int currentTabIndex = 0;
    private final List<Category> categories = new ArrayList<>();
    private List<Module> activeModulesSnapshot;
    private Module selectedModule = null;

    // --- Widget Management ---
    private final List<AbstractWidget> dynamicWidgets = new ArrayList<>();
    private final List<SettingWidget> customRenderWidgets = new ArrayList<>();

    public UtilityGui() {
        super(Component.literal("Advanced Utilities"));
    }

    @Override
    protected void init() {
        super.init();
        categories.clear();
        Collections.addAll(categories, Category.values());

        activeModulesSnapshot = ModuleManager.INSTANCE.getModules().stream()
                .filter(Module::isEnabled)
                .collect(Collectors.toList());

        if (selectedModule != null) {
            initSettingsPanel(selectedModule);
        }
    }

    private void initSettingsPanel(Module module) {
        // Clear old widgets
        for (AbstractWidget w : dynamicWidgets) this.removeWidget(w);
        dynamicWidgets.clear();
        customRenderWidgets.clear();

        this.selectedModule = module;
        if (module == null) return;

        int startX = SIDEBAR_WIDTH + 20;
        int startY = TOP_BAR_HEIGHT + 40;
        int widgetWidth = 200;

        // --- REFACTOR START ---
        // Use WidgetFactory instead of manual instanceof checks
        for (Setting<?> setting : module.getSettings()) {
            // Determine height based on type (Lists need more space)
            int height = (setting instanceof com.duox.advancedutilities.system.settings.BlockListSetting
                    || setting instanceof com.duox.advancedutilities.system.settings.EntityListSetting) ? 55 : 20;

            // Use the Factory
            SettingWidget widget = com.duox.advancedutilities.gui.factory.WidgetFactory.create(setting, startX, startY, widgetWidth, height);

            if (widget != null) {
                widget.init(w -> {
                    this.addRenderableWidget(w);
                    this.dynamicWidgets.add(w);
                }, () -> {});

                this.customRenderWidgets.add(widget);
                startY += widget.getHeight() + PADDING;
            }
        }
        // --- REFACTOR END ---
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        // 1. Vẽ khung nền
        guiGraphics.fill(0, TOP_BAR_HEIGHT, SIDEBAR_WIDTH, this.height, 0xAA000000);
        guiGraphics.fill(SIDEBAR_WIDTH, TOP_BAR_HEIGHT, this.width, this.height, 0x80000000);
        guiGraphics.vLine(SIDEBAR_WIDTH, TOP_BAR_HEIGHT, this.height, 0xFFFFFFFF);
        guiGraphics.hLine(0, this.width, TOP_BAR_HEIGHT, 0xFFFFFFFF);
        guiGraphics.drawString(this.font, "Adv. Utils", 10, 11, 0xFFFFFF, false);

        // 2. Vẽ Tabs (Top Bar)
        int tabX = 80;
        boolean isActiveTab = (currentTabIndex == 0);
        drawTabButton(guiGraphics, tabX, 2, 60, 26, "ACTIVE", isActiveTab, mouseX, mouseY);
        tabX += 65;
        for (int i = 0; i < categories.size(); i++) {
            boolean isSelected = (currentTabIndex == i + 1);
            drawTabButton(guiGraphics, tabX, 2, 60, 26, categories.get(i).name(), isSelected, mouseX, mouseY);
            tabX += 65;
        }

        // 3. Vẽ danh sách Module (Sidebar)
        renderModuleList(guiGraphics, mouseX, mouseY);

        // 4. Vẽ tiêu đề Settings & Custom Widgets
        if (selectedModule != null) {
            guiGraphics.drawString(this.font, "Settings: " + selectedModule.getName(), SIDEBAR_WIDTH + 20, TOP_BAR_HEIGHT + 15, 0xFFFF00, false);
            // Gọi hàm render của các custom widget (ví dụ BlockListWidget cần vẽ item)
            for (SettingWidget w : customRenderWidgets) {
                w.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        } else {
            guiGraphics.drawCenteredString(this.font, "Select a module to edit settings",
                    SIDEBAR_WIDTH + (this.width - SIDEBAR_WIDTH) / 2, this.height / 2, 0xAAAAAA);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderModuleList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        List<Module> modulesToDisplay = (currentTabIndex == 0) ? activeModulesSnapshot : ModuleManager.INSTANCE.getModulesByCategory(categories.get(currentTabIndex - 1));
        int btnX = (SIDEBAR_WIDTH - MODULE_BTN_WIDTH) / 2;
        int btnY = TOP_BAR_HEIGHT + 10;

        if (modulesToDisplay.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, "Empty", SIDEBAR_WIDTH / 2, btnY, 0xAAAAAA);
            return;
        }

        for (Module mod : modulesToDisplay) {
            boolean isHovered = isInside(mouseX, mouseY, btnX, btnY, MODULE_BTN_WIDTH, MODULE_BTN_HEIGHT);
            boolean isSelected = (mod == selectedModule);
            int color = mod.isEnabled() ? 0xFF2ECC71 : 0xFFE74C3C;
            if (isHovered) color = darken(color);

            guiGraphics.fill(btnX, btnY, btnX + MODULE_BTN_WIDTH, btnY + MODULE_BTN_HEIGHT, color);
            if (isSelected) guiGraphics.renderOutline(btnX - 1, btnY - 1, MODULE_BTN_WIDTH + 2, MODULE_BTN_HEIGHT + 2, 0xFF3498DB);
            guiGraphics.drawCenteredString(this.font, mod.getName(), btnX + MODULE_BTN_WIDTH / 2, btnY + 7, 0xFFFFFF);
            if (isHovered) guiGraphics.renderTooltip(this.font, Component.literal(mod.getDescription()), mouseX, mouseY);
            btnY += MODULE_BTN_HEIGHT + PADDING;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Ưu tiên 1: Click vào Settings (Custom Widget)
        if (mouseX > SIDEBAR_WIDTH) {
            for (SettingWidget w : customRenderWidgets) {
                if (w.mouseClicked(mouseX, mouseY, button)) return true;
            }
            if (super.mouseClicked(mouseX, mouseY, button)) return true;
        }

        // Ưu tiên 2: Click vào Top Bar (Tabs)
        if (mouseY < TOP_BAR_HEIGHT) {
            int tabX = 80;
            if (isInside(mouseX, mouseY, tabX, 2, 60, 26)) { currentTabIndex = 0; return true; }
            tabX += 65;
            for (int i = 0; i < categories.size(); i++) {
                if (isInside(mouseX, mouseY, tabX, 2, 60, 26)) { currentTabIndex = i + 1; return true; }
                tabX += 65;
            }
        }
        // Ưu tiên 3: Click vào Sidebar (Module List)
        else if (mouseX <= SIDEBAR_WIDTH) {
            List<Module> modulesToDisplay = (currentTabIndex == 0) ? activeModulesSnapshot : ModuleManager.INSTANCE.getModulesByCategory(categories.get(currentTabIndex - 1));
            int btnX = (SIDEBAR_WIDTH - MODULE_BTN_WIDTH) / 2;
            int btnY = TOP_BAR_HEIGHT + 10;
            for (Module mod : modulesToDisplay) {
                if (isInside(mouseX, mouseY, btnX, btnY, MODULE_BTN_WIDTH, MODULE_BTN_HEIGHT)) {
                    if (button == 0) { mod.toggle(); ConfigManager.save(); }
                    else if (button == 1 || mod == selectedModule) { initSettingsPanel(mod); }
                    return true;
                }
                btnY += MODULE_BTN_HEIGHT + PADDING;
            }
        }
        return false;
    }

    // Helpers
    private boolean isInside(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx <= x + w && my >= y && my <= y + h; }
    private int darken(int color) { Color c = new Color(color); return new Color((int)(c.getRed() * 0.7), (int)(c.getGreen() * 0.7), (int)(c.getBlue() * 0.7)).getRGB(); }
    private void drawTabButton(GuiGraphics g, int x, int y, int w, int h, String t, boolean s, int mx, int my) {
        int c = s ? 0xFF3498DB : 0xFF2C3E50;
        if (isInside(mx, my, x, y, w, h) && !s) c = 0xFF34495E;
        g.fill(x, y, x + w, y + h, c);
        g.drawCenteredString(this.font, t, x + w / 2, y + 8, s ? 0xFFFF00 : 0xAAAAAA);
    }
}