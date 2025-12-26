package com.duox.advancedutilities.gui;


import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.ConfigUtil;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.ModuleManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class UtilityGui extends Screen {
    private Category currentCategory = Category.RENDER;
    private EditBox searchBox;
    private final int TAB_WIDTH = 80;

    public UtilityGui() {
        super(Component.literal("Utility GUI"));
    }

    @Override
    protected void init() {
        super.init();

        // 1. Tạo Search Box
        this.searchBox = new EditBox(this.font, this.width / 2 - 100, 10, 200, 20, Component.literal("Search"));
        this.addRenderableWidget(searchBox);

        // 2. Tạo Buttons cho Module (dựa trên Category hiện tại và Search)
        refreshModuleButtons();
    }

    private void refreshModuleButtons() {
        // Xóa các button cũ (trừ search box)
        this.clearWidgets();
        this.addRenderableWidget(searchBox);

        // Tạo Tab Categories (Bên trái)
        int y = 40;
        for (Category cat : Category.values()) {
            Button catBtn = Button.builder(Component.literal(cat.name()), b -> {
                this.currentCategory = cat;
                refreshModuleButtons(); // Load lại list module
            }).bounds(10, y, 70, 20).build();

            // Highlight category đang chọn (Optional: Custom render)
            this.addRenderableWidget(catBtn);
            y += 25;
        }

        // Tạo Module Buttons (Lưới bên phải)
        String searchText = searchBox.getValue().toLowerCase();
        List<Module> modulesToShow = ModuleManager.INSTANCE.getModules().stream()
                .filter(m -> {
                    // Logic Search: Nếu có text search thì tìm all category, nếu không thì theo category tab
                    boolean matchSearch = m.getName().toLowerCase().contains(searchText);
                    boolean matchCat = !searchText.isEmpty() || m.getCategory() == currentCategory;
                    return matchSearch && matchCat;
                })
                .toList();

        int col = 0;
        int row = 0;
        int startX = TAB_WIDTH + 20;
        int startY = 40;

        for (Module mod : modulesToShow) {
            String status = mod.isEnabled() ? "[ON]" : "[OFF]";
            int color = mod.isEnabled() ? 0xFF00FF00 : 0xFFFF0000; // Xanh hoặc Đỏ

            Button modBtn = Button.builder(Component.literal(mod.getName() + " " + status), b -> {
                        mod.toggle();
                        ConfigUtil.saveConfig();
                        refreshModuleButtons(); // Update text ON/OFF
                    })
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(mod.getDescription())))
                    .bounds(startX + (col * 110), startY + (row * 25), 100, 20)
                    .build();

            this.addRenderableWidget(modBtn);

            col++;
            if (col > 2) { // 3 cột
                col = 0;
                row++;
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics); // Làm tối background
        super.render(graphics, mouseX, mouseY, partialTick);

        // Render tiêu đề
        graphics.drawCenteredString(this.font, "Utilities Menu", this.width / 2, 5, 0xFFFFFF);
    }

    // Logic cho Search Box: Update real-time khi gõ
    @Override
    public boolean charTyped(char pCodePoint, int pModifiers) {
        boolean result = super.charTyped(pCodePoint, pModifiers);
        if (searchBox.isFocused()) {
            refreshModuleButtons();
        }
        return result;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox.isFocused()) {
            super.keyPressed(keyCode, scanCode, modifiers);
            refreshModuleButtons();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}