package com.duox.advancedutilities.gui.widgets;

import com.duox.advancedutilities.system.BlockSelector;
import com.duox.advancedutilities.system.ConfigManager;
import com.duox.advancedutilities.system.settings.BlockListSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class BlockListWidget extends SettingWidget {
    private final BlockListSetting setting;
    private EditBox idInput;

    public BlockListWidget(BlockListSetting setting, int x, int y, int width, int height) {
        super(x, y, width, height);
        this.setting = setting;
    }

    @Override
    public void init(Consumer<AbstractWidget> widgetConsumer, Runnable onRefresh) {
        Minecraft mc = Minecraft.getInstance();
        idInput = new EditBox(mc.font, x, y + 32, width - 45, 18, Component.literal("Block ID"));
        idInput.setMaxLength(256);
        widgetConsumer.accept(idInput);

        Button btnAddId = Button.builder(Component.literal("Add"), b -> {
            String val = idInput.getValue();
            if (val != null && !val.isEmpty()) {
                // FIX: Dùng tryParse thay vì constructor (Deprecated 1.20.6)
                ResourceLocation rl = ResourceLocation.tryParse(val.contains(":") ? val : "minecraft:" + val);
                if (rl != null && ForgeRegistries.BLOCKS.containsKey(rl)) {
                    setting.add(ForgeRegistries.BLOCKS.getValue(rl));
                    ConfigManager.save();
                    idInput.setValue("");
                }
            }
        }).bounds(x + width - 40, y + 32, 40, 18).build();
        widgetConsumer.accept(btnAddId);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        guiGraphics.drawString(mc.font, setting.getName(), x, y + 6, 0xFFFFFF, false);

        int btnAddX = x + width - 20;
        boolean isHoverAdd = mouseX >= btnAddX && mouseX <= btnAddX + 20 && mouseY >= y && mouseY <= y + 20;
        guiGraphics.fill(btnAddX, y, btnAddX + 20, y + 20, isHoverAdd ? 0xFF2ECC71 : 0xFF555555);
        guiGraphics.drawCenteredString(mc.font, "+", btnAddX + 10, y + 6, 0xFFFFFF);

        int itemX = x + 80;
        int itemY = y + 2;
        int limitX = btnAddX - 5;

        // FIX: Duyệt Map bằng entrySet
        for (Map.Entry<Block, Boolean> entry : setting.getValue().entrySet()) {
            if (itemX + 16 > limitX) break;

            Block block = entry.getKey();
            boolean enabled = entry.getValue();

            int bgColor = enabled ? 0x8000FF00 : 0x80FF0000;
            guiGraphics.fill(itemX, itemY, itemX + 16, itemY + 16, bgColor);

            if (mouseX >= itemX && mouseX <= itemX + 16 && mouseY >= itemY && mouseY <= itemY + 16) {
                guiGraphics.renderOutline(itemX, itemY, 16, 16, 0xFFFFFFFF);
                guiGraphics.renderTooltip(mc.font, Component.literal(block.getName().getString() + (enabled ? " [ON]" : " [OFF]")), mouseX, mouseY);
            }

            guiGraphics.renderItem(new ItemStack(block), itemX, itemY);
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
        int itemY = y + 2;
        int limitX = btnAddX - 5;

        // FIX: Chuyển KeySet sang ArrayList để tránh lỗi ConcurrentModification khi xóa
        List<Block> keys = new ArrayList<>(setting.getValue().keySet());
        for (Block block : keys) {
            if (itemX + 16 > limitX) break;
            if (mouseX >= itemX && mouseX <= itemX + 16 && mouseY >= itemY && mouseY <= itemY + 16) {
                if (button == 0) setting.toggle(block);
                else if (button == 1) setting.remove(block);
                ConfigManager.save();
                return true;
            }
            itemX += 18;
        }
        return false;
    }
}