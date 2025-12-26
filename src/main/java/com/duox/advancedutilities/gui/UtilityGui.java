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
    private static final int TOP_BAR_HEIGHT = 30; // Chiều cao thanh Category trên cùng
    private static final int SIDEBAR_WIDTH = 120; // Chiều rộng cột danh sách Module (nhỏ)
    private static final int MODULE_BTN_HEIGHT = 22;
    private static final int MODULE_BTN_WIDTH = 100;
    private static final int PADDING = 5;

    // --- State ---
    private int currentTabIndex = 0; // 0 = ACTIVE, 1+ = Categories
    private final List<Category> categories = new ArrayList<>();
    private List<Module> activeModulesSnapshot;
    private Module selectedModule = null;

    // --- Widget Management ---
    private final List<AbstractWidget> dynamicWidgets = new ArrayList<>();
    private final List<SettingWidgetWrapper> customRenderWidgets = new ArrayList<>();

    public UtilityGui() {
        super(Component.literal("Advanced Utilities"));
    }

    @Override
    protected void init() {
        super.init();
        categories.clear();
        Collections.addAll(categories, Category.values());

        // Snapshot active modules
        activeModulesSnapshot = ModuleManager.INSTANCE.getModules().stream()
                .filter(Module::isEnabled)
                .collect(Collectors.toList());

        // Restore settings panel if a module was selected
        if (selectedModule != null) {
            initSettingsPanel(selectedModule);
        }
    }

    /**
     * Khởi tạo panel Settings bên phải cho Module được chọn.
     */
    private void initSettingsPanel(Module module) {
        // Xóa widget cũ
        for (AbstractWidget w : dynamicWidgets) this.removeWidget(w);
        dynamicWidgets.clear();
        customRenderWidgets.clear();

        this.selectedModule = module;
        if (module == null) return;

        // Vị trí bắt đầu của Settings (Bên phải Sidebar)
        int startX = SIDEBAR_WIDTH + 20;
        int startY = TOP_BAR_HEIGHT + 40; // Dưới tiêu đề Module một chút
        int widgetWidth = 200; // Độ rộng chuẩn cho slider/button settings

        for (Setting<?> setting : module.getSettings()) {
            SettingWidgetWrapper widget = null;

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
                widget.init(w -> {
                    this.addRenderableWidget(w);
                    this.dynamicWidgets.add(w);
                }, () -> {});
                this.customRenderWidgets.add(widget);
                startY += widget.getHeight() + PADDING;
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        // ============================
        // 1. BACKGROUNDS & DIVIDERS
        // ============================

        // Sidebar Background (Trái - Đen mờ đậm hơn)
        guiGraphics.fill(0, TOP_BAR_HEIGHT, SIDEBAR_WIDTH, this.height, 0xAA000000);
        // Settings Background (Phải - Đen mờ nhạt hơn)
        guiGraphics.fill(SIDEBAR_WIDTH, TOP_BAR_HEIGHT, this.width, this.height, 0x80000000);

        // Đường kẻ dọc phân chia
        guiGraphics.vLine(SIDEBAR_WIDTH, TOP_BAR_HEIGHT, this.height, 0xFFFFFFFF);
        // Đường kẻ ngang phân chia Top Bar
        guiGraphics.hLine(0, this.width, TOP_BAR_HEIGHT, 0xFFFFFFFF);

        // Title Góc Trái Trên
        guiGraphics.drawString(this.font, "Adv. Utils", 10, 11, 0xFFFFFF, false);

        // ============================
        // 2. CATEGORY TABS (TOP BAR)
        // ============================
        int tabX = 80; // Bắt đầu sau Title
        int tabY = 2;
        int tabHeight = 26;
        int tabWidth = 60;

        // Tab ACTIVE
        boolean isActiveTab = (currentTabIndex == 0);
        drawTabButton(guiGraphics, tabX, tabY, tabWidth, tabHeight, "ACTIVE", isActiveTab, mouseX, mouseY);
        tabX += tabWidth + 5;

        // Tab CATEGORIES
        for (int i = 0; i < categories.size(); i++) {
            boolean isSelected = (currentTabIndex == i + 1);
            drawTabButton(guiGraphics, tabX, tabY, tabWidth, tabHeight, categories.get(i).name(), isSelected, mouseX, mouseY);
            tabX += tabWidth + 5;
        }

        // ============================
        // 3. VERTICAL MODULE LIST (SIDEBAR)
        // ============================
        List<Module> modulesToDisplay;
        if (currentTabIndex == 0) modulesToDisplay = activeModulesSnapshot;
        else modulesToDisplay = ModuleManager.INSTANCE.getModulesByCategory(categories.get(currentTabIndex - 1));

        int btnX = (SIDEBAR_WIDTH - MODULE_BTN_WIDTH) / 2; // Canh giữa cột Sidebar
        int btnY = TOP_BAR_HEIGHT + 10;

        if (modulesToDisplay.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, "Empty", SIDEBAR_WIDTH / 2, btnY, 0xAAAAAA);
        } else {
            for (Module mod : modulesToDisplay) {
                boolean isHovered = isInside(mouseX, mouseY, btnX, btnY, MODULE_BTN_WIDTH, MODULE_BTN_HEIGHT);
                boolean isSelected = (mod == selectedModule);

                // Màu: Xanh (Bật) / Đỏ (Tắt)
                int color = mod.isEnabled() ? new Color(46, 204, 113).getRGB() : new Color(231, 76, 60).getRGB();
                if (isHovered) color = darken(color);

                // Vẽ nút Module
                guiGraphics.fill(btnX, btnY, btnX + MODULE_BTN_WIDTH, btnY + MODULE_BTN_HEIGHT, color);

                // Viền chọn (xanh dương)
                if (isSelected) {
                    guiGraphics.renderOutline(btnX - 1, btnY - 1, MODULE_BTN_WIDTH + 2, MODULE_BTN_HEIGHT + 2, 0xFF3498DB);
                }

                // Tên Module
                guiGraphics.drawCenteredString(this.font, mod.getName(), btnX + MODULE_BTN_WIDTH / 2, btnY + 7, 0xFFFFFF);

                // Tooltip
                if (isHovered) {
                    guiGraphics.renderTooltip(this.font, Component.literal(mod.getDescription()), mouseX, mouseY);
                }

                // Xuống dòng
                btnY += MODULE_BTN_HEIGHT + PADDING;
            }
        }

        // ============================
        // 4. SETTINGS AREA (RIGHT SIDE)
        // ============================
        if (selectedModule != null) {
            // Tiêu đề Settings
            guiGraphics.drawString(this.font, "Settings: " + selectedModule.getName(), SIDEBAR_WIDTH + 20, TOP_BAR_HEIGHT + 15, 0xFFFF00, false);

            // Vẽ các widget custom (MC Widgets tự vẽ bởi super.render)
            for (SettingWidgetWrapper w : customRenderWidgets) {
                w.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        } else {
            guiGraphics.drawCenteredString(this.font, "Select a module to edit settings",
                    SIDEBAR_WIDTH + (this.width - SIDEBAR_WIDTH) / 2, this.height / 2, 0xAAAAAA);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 1. Check click trong vùng Settings (Bên phải)
        if (mouseX > SIDEBAR_WIDTH) {
            // Ưu tiên Custom Widgets (như nút Delete trong BlockList)
            for (SettingWidgetWrapper w : customRenderWidgets) {
                if (w.mouseClicked(mouseX, mouseY, button)) return true;
            }
            // Sau đó đến Standard Widgets (Slider/Button)
            if (super.mouseClicked(mouseX, mouseY, button)) return true;
        }

        // 2. Check click Tabs (Top Bar)
        if (mouseY < TOP_BAR_HEIGHT) {
            int tabX = 80;
            int tabWidth = 60;
            // Active Tab
            if (isInside(mouseX, mouseY, tabX, 2, tabWidth, 26)) {
                currentTabIndex = 0;
                return true;
            }
            tabX += tabWidth + 5;
            // Category Tabs
            for (int i = 0; i < categories.size(); i++) {
                if (isInside(mouseX, mouseY, tabX, 2, tabWidth, 26)) {
                    currentTabIndex = i + 1;
                    return true;
                }
                tabX += tabWidth + 5;
            }
        }

        // 3. Check click Module List (Sidebar)
        if (mouseX <= SIDEBAR_WIDTH && mouseY > TOP_BAR_HEIGHT) {
            List<Module> modulesToDisplay;
            if (currentTabIndex == 0) modulesToDisplay = activeModulesSnapshot;
            else modulesToDisplay = ModuleManager.INSTANCE.getModulesByCategory(categories.get(currentTabIndex - 1));

            int btnX = (SIDEBAR_WIDTH - MODULE_BTN_WIDTH) / 2;
            int btnY = TOP_BAR_HEIGHT + 10;

            for (Module mod : modulesToDisplay) {
                if (isInside(mouseX, mouseY, btnX, btnY, MODULE_BTN_WIDTH, MODULE_BTN_HEIGHT)) {
                    if (button == 0) { // Chuột trái -> Toggle
                        mod.toggle();
                        ConfigManager.save();
                    } else if (button == 1) { // Chuột phải -> Mở Settings
                        initSettingsPanel(mod);
                    }
                    // Nếu click module đang settings -> Reload panel để update trạng thái nếu cần
                    if (mod == selectedModule) {
                        initSettingsPanel(mod);
                    }
                    return true;
                }
                btnY += MODULE_BTN_HEIGHT + PADDING;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    // --- Helpers ---
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
    public boolean isPauseScreen() { return false; }

    // =================================================================================
    // INNER CLASSES (WIDGET WRAPPERS)
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
            super(x, y, width, height); this.setting = setting;
        }
        @Override
        public void init(Consumer<AbstractWidget> widgetConsumer, Runnable onRefresh) {
            Button btn = Button.builder(
                    Component.literal(setting.getName() + ": " + (setting.getValue() ? "ON" : "OFF")),
                    button -> {
                        setting.toggle(); ConfigManager.save();
                        button.setMessage(Component.literal(setting.getName() + ": " + (setting.getValue() ? "ON" : "OFF")));
                        button.setFGColor(setting.getValue() ? 0x55FF55 : 0xAAAAAA);
                    }).bounds(x, y, width, height).build();
            btn.setFGColor(setting.getValue() ? 0x55FF55 : 0xAAAAAA);
            widgetConsumer.accept(btn);
        }
    }

    private static class NumberWidget extends SettingWidgetWrapper {
        private final NumberSetting setting;
        public NumberWidget(NumberSetting setting, int x, int y, int width, int height) {
            super(x, y, width, height); this.setting = setting;
        }
        @Override
        public void init(Consumer<AbstractWidget> widgetConsumer, Runnable onRefresh) {
            ForgeSlider slider = new ForgeSlider(
                    x, y, width, height, Component.literal(setting.getName() + ": "), Component.empty(),
                    setting.getMin(), setting.getMax(), setting.getValue(), setting.getIncrement(), 1, true
            ) {
                @Override protected void applyValue() { setting.setValue(this.getValue()); ConfigManager.save(); }
            };
            widgetConsumer.accept(slider);
        }
    }

    private static class EnumWidget extends SettingWidgetWrapper {
        private final EnumSetting<?> setting;
        public EnumWidget(EnumSetting<?> setting, int x, int y, int width, int height) {
            super(x, y, width, height); this.setting = setting;
        }
        @Override
        public void init(Consumer<AbstractWidget> widgetConsumer, Runnable onRefresh) {
            Button btn = Button.builder(
                    Component.literal(setting.getName() + ": " + setting.getValue().name()),
                    button -> {
                        setting.next(); ConfigManager.save();
                        button.setMessage(Component.literal(setting.getName() + ": " + setting.getValue().name()));
                    }).bounds(x, y, width, height).build();
            widgetConsumer.accept(btn);
        }
    }

    private static class BlockListWidget extends SettingWidgetWrapper {
        private final BlockListSetting setting;
        public BlockListWidget(BlockListSetting setting, int x, int y, int width, int height) {
            super(x, y, width, height); this.setting = setting;
        }
        @Override public void init(Consumer<AbstractWidget> widgetConsumer, Runnable onRefresh) {} // Custom draw only

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            guiGraphics.drawString(mc.font, setting.getName(), x, y + 6, 0xFFFFFF, false);

            // Add Button
            int btnAddX = x + width - 20;
            boolean isHoverAdd = mouseX >= btnAddX && mouseX <= btnAddX + 20 && mouseY >= y && mouseY <= y + 20;
            guiGraphics.fill(btnAddX, y, btnAddX + 20, y + 20, isHoverAdd ? 0xFF2ECC71 : 0xFF555555);
            guiGraphics.drawCenteredString(mc.font, "+", btnAddX + 10, y + 6, 0xFFFFFF);

            // Item List
            int itemX = x + 80;
            int limitX = btnAddX - 5;
            for (Block b : setting.getValue()) {
                if (itemX + 16 > limitX) break;
                guiGraphics.renderItem(new ItemStack(b), itemX, y + 2);
                itemX += 18;
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int btnAddX = x + width - 20;
            if (mouseX >= btnAddX && mouseX <= btnAddX + 20 && mouseY >= y && mouseY <= y + 20) {
                BlockSelector.INSTANCE.startSelecting(setting);
                return true;
            }
            int itemX = x + 80;
            int limitX = btnAddX - 5;
            for (Block b : new ArrayList<>(setting.getValue())) {
                if (itemX + 16 > limitX) break;
                if (mouseX >= itemX && mouseX <= itemX + 16 && mouseY >= y && mouseY <= y + 16) {
                    setting.remove(b); ConfigManager.save(); return true;
                }
                itemX += 18;
            }
            return false;
        }
    }
}