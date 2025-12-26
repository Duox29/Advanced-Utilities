package com.duox.advancedutilities.gui;

import com.duox.advancedutilities.system.BlockSelector;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.BlockListSetting;
import com.duox.advancedutilities.system.settings.BooleanSetting;
import com.duox.advancedutilities.system.settings.NumberSetting;
import com.duox.advancedutilities.system.settings.Setting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.awt.Color;
import java.util.ArrayList;

public class ModuleSettingsScreen extends Screen {

    private final Screen parent;
    private final Module module;

    public ModuleSettingsScreen(Screen parent, Module module) {
        super(Component.literal(module.getName() + " Settings"));
        this.parent = parent;
        this.module = module;
    }

    @Override
    protected void init() {
        super.init();
        int y = 40;
        int center = this.width / 2;

        // Tạo nút Back
        this.addRenderableWidget(Button.builder(Component.literal("Back"), button -> {
            this.minecraft.setScreen(parent);
        }).bounds(center - 100, this.height - 30, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, module.getName() + " Settings", this.width / 2, 15, 0xFFFFFF);

        int startX = this.width / 2 - 100;
        int y = 50;

        // Render từng Setting
        for (Setting<?> setting : module.getSettings()) {

            // Vẽ tên Setting
            guiGraphics.drawString(this.font, setting.getName(), startX, y, 0xFFFFFF);

            // --- XỬ LÝ NUMBER SETTING (SLIDER GIẢ LẬP) ---
            if (setting instanceof NumberSetting) {
                NumberSetting numSet = (NumberSetting) setting;
                String valStr = String.format("%.1f", numSet.getValue());

                // Vẽ thanh slider background
                guiGraphics.fill(startX + 100, y - 2, startX + 200, y + 8, 0xFF555555);

                // Tính độ dài thanh value
                double percent = (numSet.getValue() - numSet.getMin()) / (numSet.getMax() - numSet.getMin());
                int barWidth = (int) (100 * percent);
                guiGraphics.fill(startX + 100, y - 2, startX + 100 + barWidth, y + 8, new Color(46, 204, 113).getRGB());

                // Vẽ giá trị số
                guiGraphics.drawString(this.font, valStr, startX + 205, y, 0xAAAAAA);
            }

            // --- XỬ LÝ BOOLEAN SETTING (CHECKBOX GIẢ LẬP) ---
            else if (setting instanceof BlockListSetting) {
                BlockListSetting listSet = (BlockListSetting) setting;

                // 1. Vẽ nút ADD (+)
                int btnAddX = startX + 180;
                boolean isHoverAdd = mouseX >= btnAddX && mouseX <= btnAddX + 20 && mouseY >= y - 2 && mouseY <= y + 18;
                guiGraphics.fill(btnAddX, y - 2, btnAddX + 20, y + 18, isHoverAdd ? new Color(46, 204, 113).getRGB() : 0xFF555555);
                guiGraphics.drawCenteredString(this.font, "+", btnAddX + 10, y + 2, 0xFFFFFF);

                // 2. Vẽ danh sách các Block đã thêm (Dạng icon nhỏ)
                int itemX = startX + 80;
                for (Block b : listSet.getValue()) {
                    ItemStack stack = new ItemStack(b);
                    guiGraphics.renderItem(stack, itemX, y);
                    itemX += 18; // Dịch sang phải cho item tiếp theo

                    // Giới hạn hiển thị (nếu quá dài thì thôi, hoặc xuống dòng - ở đây làm đơn giản)
                    if (itemX > startX + 170) break;
                }

                guiGraphics.drawString(this.font, "Blocks:", startX, y + 4, 0xAAAAAA);
            }

            y += 20; // Xuống dòng
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Xử lý click vào Slider hoặc Checkbox
        int startX = this.width / 2 - 100;
        int y = 50;

        for (Setting<?> setting : module.getSettings()) {

            // Logic Click NumberSetting (Slider)
            if (setting instanceof NumberSetting) {
                if (mouseX >= startX + 100 && mouseX <= startX + 200 && mouseY >= y - 2 && mouseY <= y + 8) {
                    NumberSetting numSet = (NumberSetting) setting;
                    // Tính giá trị dựa trên vị trí click chuột
                    double percent = (mouseX - (startX + 100)) / 100.0;
                    double val = numSet.getMin() + (percent * (numSet.getMax() - numSet.getMin()));
                    numSet.setValue(val);
                    return true;
                }
            }

            // Logic Click BooleanSetting (Toggle)
            else if (setting instanceof BlockListSetting) {
                BlockListSetting listSet = (BlockListSetting) setting;

                // Logic Click nút ADD
                int btnAddX = startX + 180;
                if (mouseX >= btnAddX && mouseX <= btnAddX + 20 && mouseY >= y - 2 && mouseY <= y + 18) {
                    // Kích hoạt chế độ chọn Block
                    BlockSelector.INSTANCE.startSelecting(listSet);
                    return true;
                }

                // Logic Click vào Item để xóa (Remove)
                int itemX = startX + 80;
                for (Block b : new ArrayList<>(listSet.getValue())) { // Dùng copy list để tránh lỗi ConcurrentModification
                    if (mouseX >= itemX && mouseX <= itemX + 16 && mouseY >= y && mouseY <= y + 16) {
                        listSet.remove(b); // Xóa block khi click vào icon
                        // Play sound click
                        return true;
                    }
                    itemX += 18;
                    if (itemX > startX + 170) break;
                }
            }
            y += 20;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    // Hỗ trợ kéo chuột cho Slider
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return this.mouseClicked(mouseX, mouseY, button);
    }
}