package com.duox.advancedutilities.gui;

import com.duox.advancedutilities.system.BlockSelector;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.gui.widget.ForgeSlider;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class ModuleSettingsScreen extends Screen {

    private final Screen parent;
    private final Module module;
    private final List<SettingWidget> widgets = new ArrayList<>();

    public ModuleSettingsScreen(Screen parent, Module module) {
        super(Component.literal(module.getName() + " Settings"));
        this.parent = parent;
        this.module = module;
    }

    @Override
    protected void init() {
        super.init();
        this.widgets.clear(); // Xóa widget cũ để tránh duplicate khi rebuild

        int y = 40;
        int center = this.width / 2;
        int widgetWidth = 200;
        int widgetX = center - 100;

        // --- FACTORY PATTERN: Tự động chọn Widget dựa trên loại Setting ---
        for (Setting<?> setting : module.getSettings()) {
            SettingWidget widget = null;

            if (setting instanceof BooleanSetting boolSet) {
                widget = new BooleanWidget(boolSet, widgetX, y, widgetWidth, 20);
            } else if (setting instanceof NumberSetting numSet) {
                widget = new NumberWidget(numSet, widgetX, y, widgetWidth, 20);
            } else if (setting instanceof EnumSetting<?> enumSet) {
                widget = new EnumWidget(enumSet, widgetX, y, widgetWidth, 20);
            } else if (setting instanceof BlockListSetting listSet) {
                widget = new BlockListWidget(listSet, widgetX, y, widgetWidth, 30);
            }

            // Nếu tạo thành công, thêm vào danh sách quản lý
            if (widget != null) {
                this.widgets.add(widget);
                // Đăng ký các thành phần con (Button, Slider) vào Screen chính
                widget.init(this::addRenderableWidget, this::rebuildWidgets);

                // Cập nhật toạ độ Y cho widget tiếp theo
                y += widget.getHeight() + 4; // +4 padding
            }
        }

        // Tạo nút Back ở cuối cùng
        this.addRenderableWidget(Button.builder(Component.literal("Back"),
                        button -> Objects.requireNonNull(this.minecraft).setScreen(parent))
                .bounds(center - 100, this.height - 30, 200, 20)
                .build());
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);

        // Vẽ các widget chuẩn (Button, Slider)
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Vẽ thêm các thành phần custom (như BlockList)
        for (SettingWidget widget : widgets) {
            widget.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Để super xử lý click cho Button/Slider chuẩn trước
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        // Xử lý click custom (ví dụ: xoá item trong list)
        for (SettingWidget widget : widgets) {
            if (widget.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return false;
    }

    // =================================================================================
    // ABSTRACT WIDGET & IMPLEMENTATIONS
    // (Trong dự án thực tế, bạn nên tách các class này ra file riêng)
    // =================================================================================

    /**
     * Lớp cha trừu tượng cho mọi Widget Setting
     */
    private abstract static class SettingWidget {
        protected final int x, y, width, height;

        public SettingWidget(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public int getHeight() { return height; }

        // Khởi tạo các component (Button, Slider) và add vào Screen cha
        public abstract void init(Consumer<AbstractWidget> widgetConsumer, Runnable onRefresh);

        // Vẽ custom (nếu cần)
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {}

        // Xử lý click custom (nếu cần)
        public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
    }

    // --- 1. Boolean Widget ---
    private static class BooleanWidget extends SettingWidget {
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
                                onRefresh.run(); // Refresh lại text
                            })
                    .bounds(x, y, width, height)
                    .build();

            btn.setFGColor(setting.getValue() ? 0x55FF55 : 0xAAAAAA);
            widgetConsumer.accept(btn);
        }
    }

    // --- 2. Enum Widget (Cho Mode Selection) ---
    private static class EnumWidget extends SettingWidget {
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
                                onRefresh.run();
                            })
                    .bounds(x, y, width, height)
                    .build();

            // Highlight xanh nếu không phải chế độ NORMAL
            if (!setting.getValue().name().equalsIgnoreCase("NORMAL")) {
                btn.setFGColor(0x55FF55);
            }
            widgetConsumer.accept(btn);
        }
    }

    // --- 3. Number Widget ---
    private static class NumberWidget extends SettingWidget {
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
                }
            };
            widgetConsumer.accept(slider);
        }
    }

    // --- 4. Block List Widget (Logic phức tạp nhất được tách biệt) ---
    private static class BlockListWidget extends SettingWidget {
        private final BlockListSetting setting;

        public BlockListWidget(BlockListSetting setting, int x, int y, int width, int height) {
            super(x, y, width, height); // Height ở đây nên lớn hơn (ví dụ 30)
            this.setting = setting;
        }

        @Override
        public void init(Consumer<AbstractWidget> widgetConsumer, Runnable onRefresh) {
            // Widget này tự vẽ, không dùng Button/Slider chuẩn của MC
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            // Vẽ tên Setting
            guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font,
                    setting.getName(), x, y + 6, 0xFFFFFF);

            // Vẽ nút ADD (+)
            int btnAddX = x + width - 20; // Căn phải
            boolean isHoverAdd = mouseX >= btnAddX && mouseX <= btnAddX + 20 && mouseY >= y && mouseY <= y + 20;

            guiGraphics.fill(btnAddX, y, btnAddX + 20, y + 20, isHoverAdd ? new Color(46, 204, 113).getRGB() : 0xFF555555);
            guiGraphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font,
                    "+", btnAddX + 10, y + 6, 0xFFFFFF);

            // Vẽ danh sách Item
            int itemX = x + 80; // Dịch sang phải để tránh tên setting
            int limitX = btnAddX - 5; // Giới hạn không vẽ đè lên nút Add

            for (Block b : setting.getValue()) {
                ItemStack stack = new ItemStack(b);
                guiGraphics.renderItem(stack, itemX, y + 2);
                itemX += 18;
                if (itemX > limitX) break;
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // Check click nút ADD (+)
            int btnAddX = x + width - 20;
            if (mouseX >= btnAddX && mouseX <= btnAddX + 20 && mouseY >= y && mouseY <= y + 20) {
                BlockSelector.INSTANCE.startSelecting(setting);
                // Có thể thêm playSound ở đây
                return true;
            }

            // Check click vào Item để xóa
            int itemX = x + 80;
            int limitX = btnAddX - 5;

            // Dùng bản sao để tránh ConcurrentModificationException khi xóa
            for (Block b : new ArrayList<>(setting.getValue())) {
                if (mouseX >= itemX && mouseX <= itemX + 16 && mouseY >= y && mouseY <= y + 16) {
                    setting.remove(b);
                    return true;
                }
                itemX += 18;
                if (itemX > limitX) break;
            }
            return false;
        }
    }
}