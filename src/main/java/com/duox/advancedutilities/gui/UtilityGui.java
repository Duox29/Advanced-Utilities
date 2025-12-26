package com.duox.advancedutilities.gui;

import com.duox.advancedutilities.system.BlockSelector;
import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.ConfigManager;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.ModuleManager;
import com.duox.advancedutilities.system.settings.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.gui.widget.ForgeSlider;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class UtilityGui extends Screen {

    // --- Layout Constants ---
    private static final int TAB_HEIGHT = 25;
    private static final int PANEL_WIDTH = 200; // Fixed width for settings panel
    private static final int PADDING = 5;

    // --- State ---
    private int currentTabIndex = 0; // 0 = ACTIVE, 1+ = Categories
    private final List<Category> categories = new ArrayList<>();
    private List<Module> activeModulesSnapshot;
    private Module selectedModule = null; // The module currently being edited

    // --- Widget Management ---
    // We keep track of "dynamic" widgets (settings sliders/buttons) so we can remove them when switching modules
    private final List<AbstractWidget> dynamicWidgets = new ArrayList<>();
    private final List<SettingWidgetWrapper> customRenderWidgets = new ArrayList<>();

    public UtilityGui() {
        super(Component.literal("Advanced Utilities"));
    }

    @Override
    protected void init() {
        super.init();

        // 1. Initialize Categories
        categories.clear();
        Collections.addAll(categories, Category.values());

        // 2. Snapshot active modules
        activeModulesSnapshot = ModuleManager.INSTANCE.getModules().stream()
                .filter(Module::isEnabled)
                .collect(Collectors.toList());

        // 3. If a module was selected before resize/init, attempt to restore its settings panel
        if (selectedModule != null) {
            initSettingsPanel(selectedModule);
        }
    }

    /**
     * Initializes the settings panel on the right side for the specific module.
     * Clears old widgets and creates new ones using the Factory pattern.
     */
    private void initSettingsPanel(Module module) {
        // Clear previous dynamic widgets from the Screen's render list
        for (AbstractWidget w : dynamicWidgets) {
            this.removeWidget(w);
        }
        dynamicWidgets.clear();
        customRenderWidgets.clear();

        this.selectedModule = module;
        if (module == null) return;

        int startX = this.width - PANEL_WIDTH + 10;
        int startY = 40;
        int widgetWidth = PANEL_WIDTH - 20;

        for (Setting<?> setting : module.getSettings()) {
            SettingWidgetWrapper widget = null;

            // Factory Logic
            if (setting instanceof BooleanSetting s) {
                widget = new BooleanWidget(s, startX, startY, widgetWidth, 20);
            } else if (setting instanceof NumberSetting s) {
                widget = new NumberWidget(s, startX, startY, widgetWidth, 20);
            } else if (setting instanceof EnumSetting<?> s) {
                widget = new EnumWidget(s, startX, startY, widgetWidth, 20);
            } else if (setting instanceof BlockListSetting s) {
                widget = new BlockListWidget(s, startX, startY, widgetWidth, 30);
            }

            if (widget != null) {
                // Register standard MC widgets (Buttons, Sliders)
                widget.init(w -> {
                    this.addRenderableWidget(w);
                    this.dynamicWidgets.add(w);
                }, () -> { /* onRefresh callback if needed */ });

                // Register wrapper for custom rendering/clicking
                this.customRenderWidgets.add(widget);

                startY += widget.getHeight() + PADDING;
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        // --- Layout Calculation ---
        int mainAreaWidth = this.width - PANEL_WIDTH;
        int startX = 20;
        int startY = 25;

        // --- 1. Draw Title ---
        guiGraphics.drawString(this.font, "Advanced Utilities", 20, 10, 0xFFFFFF, false);

        // --- 2. Draw Settings Panel Background (Right Side) ---
        // Darker background for the panel
        guiGraphics.fill(mainAreaWidth, 0, this.width, this.height, 0x80000000);
        guiGraphics.vLine(mainAreaWidth, 0, this.height, 0xFFFFFFFF); // Separator line

        if (selectedModule != null) {
            guiGraphics.drawCenteredString(this.font, selectedModule.getName() + " Settings",
                    mainAreaWidth + (PANEL_WIDTH / 2), 15, 0xFFFFFF);
        } else {
            guiGraphics.drawCenteredString(this.font, "Select a Module",
                    mainAreaWidth + (PANEL_WIDTH / 2), this.height / 2, 0xAAAAAA);
        }

        // --- 3. Render Tabs (Top Bar) ---
        int tabX = startX;
        int tabWidth = 60;

        // Draw Active Tab
        boolean isActiveTabSelected = (currentTabIndex == 0);
        drawTabButton(guiGraphics, tabX, startY, tabWidth, TAB_HEIGHT, "ACTIVE", isActiveTabSelected, mouseX, mouseY);
        tabX += tabWidth + 5;

        // Draw Category Tabs
        for (int i = 0; i < categories.size(); i++) {
            boolean isSelected = (currentTabIndex == i + 1);
            Category cat = categories.get(i);
            drawTabButton(guiGraphics, tabX, startY, tabWidth, TAB_HEIGHT, cat.name(), isSelected, mouseX, mouseY);
            tabX += tabWidth + 5;
        }

        // --- 4. Render Modules Grid ---
        List<Module> modulesToDisplay;
        if (currentTabIndex == 0) modulesToDisplay = activeModulesSnapshot;
        else modulesToDisplay = ModuleManager.INSTANCE.getModulesByCategory(categories.get(currentTabIndex - 1));

        int gridX = startX;
        int gridY = startY + TAB_HEIGHT + 10;
        int btnWidth = 100;
        int btnHeight = 20;

        if (modulesToDisplay.isEmpty()) {
            guiGraphics.drawString(this.font, "No modules here...", gridX, gridY, 0xAAAAAA, false);
        } else {
            for (Module mod : modulesToDisplay) {
                boolean isHovered = isInside(mouseX, mouseY, gridX, gridY, btnWidth, btnHeight);
                boolean isSelected = (mod == selectedModule);

                // Color Logic: Green (Enabled), Red (Disabled), Blue Border (Selected)
                int color = mod.isEnabled() ? new Color(46, 204, 113).getRGB() : new Color(231, 76, 60).getRGB();
                if (isHovered) color = darken(color);

                guiGraphics.fill(gridX, gridY, gridX + btnWidth, gridY + btnHeight, color);

                // Draw Selection Border
                if (isSelected) {
                    guiGraphics.renderOutline(gridX - 1, gridY - 1, btnWidth + 2, btnHeight + 2, 0xFF3498DB);
                }

                guiGraphics.drawCenteredString(this.font, mod.getName(), gridX + btnWidth / 2, gridY + 6, 0xFFFFFF);

                if (isHovered) {
                    guiGraphics.renderTooltip(this.font, Component.literal(mod.getDescription()), mouseX, mouseY);
                }

                // Grid Flow Logic
                gridX += btnWidth + PADDING;
                // Wrap if we hit the settings panel
                if (gridX + btnWidth > mainAreaWidth) {
                    gridX = startX;
                    gridY += btnHeight + PADDING;
                }
            }
        }

        // --- 5. Render Custom Widget Elements (Block Lists, etc.) ---
        // Standard widgets (buttons/sliders) are rendered by super.render()
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Custom rendering for things not covered by standard widgets
        for (SettingWidgetWrapper w : customRenderWidgets) {
            w.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 1. Handle Settings Panel Interactions first
        if (selectedModule != null && mouseX > (this.width - PANEL_WIDTH)) {
            // Pass click to custom widgets (like BlockList delete buttons)
            for (SettingWidgetWrapper w : customRenderWidgets) {
                if (w.mouseClicked(mouseX, mouseY, button)) return true;
            }
            // Pass click to standard widgets (Buttons, Sliders)
            if (super.mouseClicked(mouseX, mouseY, button)) return true;
        }

        // 2. Handle Tab Interactions
        int startX = 20;
        int startY = 25;
        int tabWidth = 60;

        if (isInside(mouseX, mouseY, startX, startY, tabWidth, TAB_HEIGHT)) {
            currentTabIndex = 0;
            return true;
        }
        startX += tabWidth + 5;

        for (int i = 0; i < categories.size(); i++) {
            if (isInside(mouseX, mouseY, startX, startY, tabWidth, TAB_HEIGHT)) {
                currentTabIndex = i + 1;
                return true;
            }
            startX += tabWidth + 5;
        }

        // 3. Handle Module Interactions
        // Re-calculate grid positions to find which module was clicked
        List<Module> modulesToDisplay;
        if (currentTabIndex == 0) modulesToDisplay = activeModulesSnapshot;
        else modulesToDisplay = ModuleManager.INSTANCE.getModulesByCategory(categories.get(currentTabIndex - 1));

        int mainAreaWidth = this.width - PANEL_WIDTH;
        int gridX = 20;
        int gridY = startY + TAB_HEIGHT + 10;
        int btnWidth = 100;
        int btnHeight = 20;

        for (Module mod : modulesToDisplay) {
            if (isInside(mouseX, mouseY, gridX, gridY, btnWidth, btnHeight)) {
                if (button == 0) { // Left Click -> Toggle
                    mod.toggle();
                    ConfigManager.save();
                } else if (button == 1) { // Right Click -> Select Settings
                    initSettingsPanel(mod);
                }
                return true;
            }

            gridX += btnWidth + PADDING;
            if (gridX + btnWidth > mainAreaWidth) {
                gridX = 20;
                gridY += btnHeight + PADDING;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    // --- Helper Methods ---

    private boolean isInside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private int darken(int color) {
        Color c = new Color(color);
        return new Color((int)(c.getRed() * 0.7), (int)(c.getGreen() * 0.7), (int)(c.getBlue() * 0.7)).getRGB();
    }

    private void drawTabButton(GuiGraphics guiGraphics, int x, int y, int w, int h, String text, boolean selected, int mx, int my) {
        int color = selected ? new Color(52, 152, 219).getRGB() : new Color(44, 62, 80).getRGB();
        boolean hovered = isInside(mx, my, x, y, w, h);
        if (hovered && !selected) color = new Color(52, 73, 94).getRGB();

        guiGraphics.fill(x, y, x + w, y + h, color);
        guiGraphics.drawCenteredString(this.font, text, x + w / 2, y + 8, selected ? 0xFFFF00 : 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // =================================================================================
    // INNER CLASSES FOR SETTINGS WIDGETS
    // =================================================================================

    private abstract static class SettingWidgetWrapper {
        protected final int x, y, width, height;
        public SettingWidgetWrapper(int x, int y, int width, int height) {
            this.x = x; this.y = y; this.width = width; this.height = height;
        }
        public int getHeight() { return height; }
        public abstract void init(Consumer<AbstractWidget> widgetConsumer, Runnable onRefresh);
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {}
        public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
    }

    private static class BooleanWidget extends SettingWidgetWrapper {
        private final BooleanSetting setting;
        public BooleanWidget(BooleanSetting setting, int x, int y, int width, int height) {
            super(x, y, width, height);
            this.setting = setting;
        }
        @Override
        public void init(Consumer<AbstractWidget> widgetConsumer, Runnable onRefresh) {
            Button btn = Button.builder(
                            Component.literal(setting.getName() + ": " + (setting.getValue() ? "ON" : "OFF")),
                            button -> {
                                setting.toggle();
                                ConfigManager.save();
                                button.setMessage(Component.literal(setting.getName() + ": " + (setting.getValue() ? "ON" : "OFF")));
                                button.setFGColor(setting.getValue() ? 0x55FF55 : 0xAAAAAA);
                            })
                    .bounds(x, y, width, height)
                    .build();
            btn.setFGColor(setting.getValue() ? 0x55FF55 : 0xAAAAAA);
            widgetConsumer.accept(btn);
        }
    }

    private static class NumberWidget extends SettingWidgetWrapper {
        private final NumberSetting setting;
        public NumberWidget(NumberSetting setting, int x, int y, int width, int height) {
            super(x, y, width, height);
            this.setting = setting;
        }
        @Override
        public void init(Consumer<AbstractWidget> widgetConsumer, Runnable onRefresh) {
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
                    ConfigManager.save();
                }
            };
            widgetConsumer.accept(slider);
        }
    }

    private static class EnumWidget extends SettingWidgetWrapper {
        private final EnumSetting<?> setting;
        public EnumWidget(EnumSetting<?> setting, int x, int y, int width, int height) {
            super(x, y, width, height);
            this.setting = setting;
        }
        @Override
        public void init(Consumer<AbstractWidget> widgetConsumer, Runnable onRefresh) {
            Button btn = Button.builder(
                            Component.literal(setting.getName() + ": " + setting.getValue().name()),
                            button -> {
                                setting.next();
                                ConfigManager.save();
                                button.setMessage(Component.literal(setting.getName() + ": " + setting.getValue().name()));
                            })
                    .bounds(x, y, width, height)
                    .build();
            widgetConsumer.accept(btn);
        }
    }

    private static class BlockListWidget extends SettingWidgetWrapper {
        private final BlockListSetting setting;
        public BlockListWidget(BlockListSetting setting, int x, int y, int width, int height) {
            super(x, y, width, height);
            this.setting = setting;
        }

        @Override
        public void init(Consumer<AbstractWidget> widgetConsumer, Runnable onRefresh) {
            // Custom drawn, no standard widgets
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            // Name
            guiGraphics.drawString(mc.font, setting.getName(), x, y + 6, 0xFFFFFF, false);

            // Add Button (Visual)
            int btnAddX = x + width - 20;
            boolean isHoverAdd = mouseX >= btnAddX && mouseX <= btnAddX + 20 && mouseY >= y && mouseY <= y + 20;
            guiGraphics.fill(btnAddX, y, btnAddX + 20, y + 20, isHoverAdd ? 0xFF2ECC71 : 0xFF555555);
            guiGraphics.drawCenteredString(mc.font, "+", btnAddX + 10, y + 6, 0xFFFFFF);

            // Item List
            int itemX = x + 80;
            int limitX = btnAddX - 5;
            for (Block b : setting.getValue()) {
                if (itemX + 16 > limitX) break; // Clip logic
                guiGraphics.renderItem(new ItemStack(b), itemX, y + 2);
                itemX += 18;
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int btnAddX = x + width - 20;

            // Click Add
            if (mouseX >= btnAddX && mouseX <= btnAddX + 20 && mouseY >= y && mouseY <= y + 20) {
                BlockSelector.INSTANCE.startSelecting(setting);
                return true;
            }

            // Click Item to Remove
            int itemX = x + 80;
            int limitX = btnAddX - 5;
            for (Block b : new ArrayList<>(setting.getValue())) {
                if (itemX + 16 > limitX) break;
                if (mouseX >= itemX && mouseX <= itemX + 16 && mouseY >= y && mouseY <= y + 16) {
                    setting.remove(b);
                    ConfigManager.save();
                    return true;
                }
                itemX += 18;
            }
            return false;
        }
    }
}