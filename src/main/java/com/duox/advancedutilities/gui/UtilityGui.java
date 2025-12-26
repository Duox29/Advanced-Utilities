package com.duox.advancedutilities.gui;

import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.ConfigUtil;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.ModuleManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class UtilityGui extends Screen {

    private int currentTabIndex = 0; // 0 = ACTIVE, 1+ = Categories
    private final List<Category> categories = new ArrayList<>();

    // Snapshot danh sách module đang bật khi mở GUI
    private List<Module> activeModulesSnapshot;

    public UtilityGui() {
        super(Component.literal("Advanced Utilities"));
    }

    @Override
    protected void init() {
        super.init();

        // 1. Tạo danh sách Category (ACTIVE là logic riêng, không nằm trong Enum)
        categories.clear();
        for (Category c : Category.values()) {
            categories.add(c);
        }

        // 2. Chụp lại danh sách các module đang ON ngay lúc mở GUI
        // Điều này đảm bảo khi tắt module trong tab Active, nó không biến mất ngay lập tức
        activeModulesSnapshot = ModuleManager.INSTANCE.getModules().stream()
                .filter(Module::isEnabled)
                .collect(Collectors.toList());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        // --- CONSTANTS ---
        int tabHeight = 25;
        int sidebarWidth = 80; // Dùng sidebar bên trái cho Tab nhìn sẽ gọn hơn là Top bar nếu nhiều Category
        int startX = 20;
        int startY = 40;

        // --- TITLE ---
        guiGraphics.drawCenteredString(this.font, "Advanced Utilities", this.width / 2, 15, 0xFFFFFF);

        // --- RENDER TABS (Top Bar style cho gọn) ---
        int tabX = startX;
        int tabY = 25;
        int tabWidth = 60;

        // Vẽ Tab ACTIVE (Index 0)
        boolean isActiveTabSelected = (currentTabIndex == 0);
        drawTabButton(guiGraphics, tabX, tabY, tabWidth, tabHeight, "ACTIVE", isActiveTabSelected, mouseX, mouseY);
        tabX += tabWidth + 5;

        // Vẽ các Tab Category (Index 1 -> n)
        for (int i = 0; i < categories.size(); i++) {
            boolean isSelected = (currentTabIndex == i + 1);
            Category cat = categories.get(i);
            drawTabButton(guiGraphics, tabX, tabY, tabWidth, tabHeight, cat.name(), isSelected, mouseX, mouseY);
            tabX += tabWidth + 5;
        }

        // --- RENDER MODULES ---
        List<Module> modulesToDisplay;

        if (currentTabIndex == 0) {
            // Tab ACTIVE: Hiển thị list đã chụp (snapshot)
            modulesToDisplay = activeModulesSnapshot;
        } else {
            // Tab Category: Lấy list từ Manager
            modulesToDisplay = ModuleManager.INSTANCE.getModulesByCategory(categories.get(currentTabIndex - 1));
        }

        // Grid Layout cho modules
        int gridX = startX;
        int gridY = tabY + tabHeight + 10;
        int btnWidth = 100;
        int btnHeight = 20;
        int padding = 5;

        if (modulesToDisplay.isEmpty()) {
            guiGraphics.drawString(this.font, "No modules here...", gridX, gridY, 0xAAAAAA, false);
        } else {
            for (Module mod : modulesToDisplay) {
                // Check hover
                boolean isHovered = (mouseX >= gridX && mouseX <= gridX + btnWidth && mouseY >= gridY && mouseY <= gridY + btnHeight);

                // Màu nút: Xanh (Bật) / Đỏ (Tắt) / Xám (Disable logic)
                int color = mod.isEnabled() ? new Color(46, 204, 113).getRGB() : new Color(231, 76, 60).getRGB();

                // Vẽ box module
                guiGraphics.fill(gridX, gridY, gridX + btnWidth, gridY + btnHeight, isHovered ? color : darken(color));
                guiGraphics.drawCenteredString(this.font, mod.getName(), gridX + btnWidth / 2, gridY + 6, 0xFFFFFF);

                // Tooltip (optional)
                if (isHovered) {
                    guiGraphics.renderTooltip(this.font, Component.literal(mod.getDescription()), mouseX, mouseY);
                }

                // Xuống dòng hoặc sang cột (Simple column layout)
                gridY += btnHeight + padding;
                if (gridY > this.height - 30) { // Nếu dài quá thì sang cột mới
                    gridY = tabY + tabHeight + 10;
                    gridX += btnWidth + padding;
                }
            }
        }
    }

    // Helper: Làm tối màu khi không hover
    private int darken(int color) {
        Color c = new Color(color);
        return new Color((int)(c.getRed() * 0.7), (int)(c.getGreen() * 0.7), (int)(c.getBlue() * 0.7)).getRGB();
    }

    private void drawTabButton(GuiGraphics guiGraphics, int x, int y, int w, int h, String text, boolean selected, int mx, int my) {
        int color = selected ? new Color(52, 152, 219).getRGB() : new Color(44, 62, 80).getRGB();
        boolean hovered = (mx >= x && mx <= x + w && my >= y && my <= y + h);
        if (hovered && !selected) color = new Color(52, 73, 94).getRGB();

        guiGraphics.fill(x, y, x + w, y + h, color);
        guiGraphics.drawCenteredString(this.font, text, x + w / 2, y + 8, selected ? 0xFFFF00 : 0xAAAAAA);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int tabX = 20;
        int tabY = 25;
        int tabWidth = 60;
        int tabHeight = 25;

        // 1. Check click TABS
        // Active Tab
        if (isInside(mouseX, mouseY, tabX, tabY, tabWidth, tabHeight)) {
            currentTabIndex = 0;
            return true;
        }
        tabX += tabWidth + 5;

        // Category Tabs
        for (int i = 0; i < categories.size(); i++) {
            if (isInside(mouseX, mouseY, tabX, tabY, tabWidth, tabHeight)) {
                currentTabIndex = i + 1;
                return true;
            }
            tabX += tabWidth + 5;
        }

        // 2. Check click MODULES
        List<Module> modulesToDisplay;
        if (currentTabIndex == 0) modulesToDisplay = activeModulesSnapshot;
        else modulesToDisplay = ModuleManager.INSTANCE.getModulesByCategory(categories.get(currentTabIndex - 1));

        int gridX = 20;
        int gridY = tabY + tabHeight + 10;
        int btnWidth = 100;
        int btnHeight = 20;
        int padding = 5;

        for (Module mod : modulesToDisplay) {
            if (isInside(mouseX, mouseY, gridX, gridY, btnWidth, btnHeight)) {
                // Toggle Module
                mod.toggle();

                // Lưu Config ngay khi bấm
                ConfigUtil.saveConfig();

                // Lưu ý: Không xóa khỏi activeModulesSnapshot ở đây
                // để giữ nút hiển thị cho đến khi thoát GUI.
                return true;
            }

            gridY += btnHeight + padding;
            if (gridY > this.height - 30) {
                gridY = tabY + tabHeight + 10;
                gridX += btnWidth + padding;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isInside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}