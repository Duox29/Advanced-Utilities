package com.duox.advancedutilities.gui;

import com.duox.advancedutilities.system.BlockSelector;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.gui.widget.ForgeSlider;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Objects;

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
        int widgetWidth = 200;
        int widgetX = center - 100;

        // --- 1. KHỞI TẠO CÁC WIDGET (Button, Slider) ---
        for (Setting<?> setting : module.getSettings()) {

            // A. Xử lý BooleanSetting (Nút Bấm ON/OFF)
            // Đây là phần thiếu trong code cũ của bạn
            if (setting instanceof EnumSetting<?> enumSet) {
                Button btn = Button.builder(
                                Component.literal(enumSet.getName() + ": " + enumSet.getValue().name()),
                                button -> {
                                    enumSet.next(); // Xoay vòng mode
                                    this.rebuildWidgets(); // Cập nhật text trên button
                                })
                        .bounds(widgetX, y, widgetWidth, 20)
                        .build();

                // Đổi màu chữ nếu đang Bật (Xanh lá) hoặc Tắt (Xám)
                if (!enumSet.getValue().name().equalsIgnoreCase("NORMAL")) {
                    btn.setFGColor(0x55FF55);
                }

                this.addRenderableWidget(btn);
                y += 24; // Xuống dòng
            }

            // B. Xử lý NumberSetting (Dùng ForgeSlider chuẩn mượt hơn tự vẽ)
            else if (setting instanceof NumberSetting numSet) {
                ForgeSlider slider = new ForgeSlider(
                        widgetX, y, widgetWidth, 20,
                        Component.literal(numSet.getName() + ": "),
                        Component.empty(), // Suffix
                        numSet.getMin(),
                        numSet.getMax(),
                        numSet.getValue(),
                        numSet.getIncrement(),
                        1, // Độ chính xác số thập phân
                        true // Hiển thị giá trị
                ) {
                    @Override
                    protected void applyValue() {
                        numSet.setValue(this.getValue());
                    }
                };

                this.addRenderableWidget(slider);
                y += 24; // Xuống dòng
            }

            // C. BlockListSetting: Chừa chỗ trống để vẽ thủ công trong hàm render()
            else if (setting instanceof BlockListSetting) {
                y += 30; // Chừa khoảng trống lớn hơn cho list item
            }
        }

        // Tạo nút Back ở cuối cùng
        this.addRenderableWidget(Button.builder(Component.literal("Back"), button -> Objects.requireNonNull(this.minecraft).setScreen(parent)).bounds(center - 100, this.height - 30, 200, 20).build());
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 1. Vẽ nền tối
        this.renderBackground(guiGraphics);

        // 2. Vẽ Tiêu đề
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);

        // 3. Vẽ các Widget đã add (Button, Slider)
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 4. Vẽ thủ công cho BlockListSetting (Vì cái này phức tạp, Widget chuẩn không hỗ trợ tốt)
        int startX = this.width / 2 - 100;
        int y = 40; // Phải khớp với biến y trong init()

        for (Setting<?> setting : module.getSettings()) {

            if (setting instanceof BooleanSetting || setting instanceof NumberSetting) {
                y += 24; // Nhảy qua các dòng đã có Widget đè lên
            }

            else if (setting instanceof BlockListSetting listSet) {
                // Vẽ tên Setting
                guiGraphics.drawString(this.font, listSet.getName(), startX, y + 6, 0xFFFFFF);

                // Vẽ nút ADD (+) giả lập (Vẽ thủ công để giữ style của bạn)
                int btnAddX = startX + 180;
                boolean isHoverAdd = mouseX >= btnAddX && mouseX <= btnAddX + 20 && mouseY >= y && mouseY <= y + 20;

                guiGraphics.fill(btnAddX, y, btnAddX + 20, y + 20, isHoverAdd ? new Color(46, 204, 113).getRGB() : 0xFF555555);
                guiGraphics.drawCenteredString(this.font, "+", btnAddX + 10, y + 6, 0xFFFFFF);

                // Vẽ danh sách Item
                int itemX = startX + 80;
                for (Block b : listSet.getValue()) {
                    ItemStack stack = new ItemStack(b);
                    guiGraphics.renderItem(stack, itemX, y + 2);
                    itemX += 18;
                    if (itemX > startX + 170) break; // Giới hạn hiển thị
                }

                y += 30; // Xuống dòng
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 1. Để super xử lý click cho Button và Slider trước
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        // 2. Xử lý logic riêng cho BlockListSetting (Giữ nguyên logic gốc của bạn)
        int startX = this.width / 2 - 100;
        int y = 40;

        for (Setting<?> setting : module.getSettings()) {
            if (setting instanceof BooleanSetting || setting instanceof NumberSetting) {
                y += 24;
            }
            else if (setting instanceof BlockListSetting listSet) {
                // Check click nút ADD (+)
                int btnAddX = startX + 180;
                if (mouseX >= btnAddX && mouseX <= btnAddX + 20 && mouseY >= y && mouseY <= y + 20) {
                    BlockSelector.INSTANCE.startSelecting(listSet);
                    // Play sound click chuẩn Minecraft
                    return true;
                }

                // Check click vào Item để xóa
                int itemX = startX + 80;
                for (Block b : new ArrayList<>(listSet.getValue())) {
                    if (mouseX >= itemX && mouseX <= itemX + 16 && mouseY >= y && mouseY <= y + 16) {
                        listSet.remove(b);
                        return true;
                    }
                    itemX += 18;
                    if (itemX > startX + 170) break;
                }
                y += 30;
            }
        }
        return false;
    }
}