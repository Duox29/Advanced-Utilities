package com.duox.advancedutilities.gui.widgets;

import com.duox.advancedutilities.system.ConfigManager;
import com.duox.advancedutilities.system.settings.ItemListSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ItemListWidget extends SettingWidget {
    private static final int ITEM_SIZE = 18;
    private static final int INPUT_AREA_HEIGHT = 44;

    private final ItemListSetting setting;
    private EditBox idInput;
    private Runnable onRefreshCallback;

    public ItemListWidget(ItemListSetting setting, int x, int y, int width, int height) {
        super(x, y, width, height);
        this.setting = setting;
    }

    private int calculateContentHeight() {
        int count = setting.getValue().size();
        int itemsPerRow = Math.max(1, (width - 16) / ITEM_SIZE);
        int rows = (int) Math.ceil((double) count / itemsPerRow);
        return Math.max(height, INPUT_AREA_HEIGHT + rows * ITEM_SIZE + 12);
    }

    @Override
    public int getHeight() {
        return calculateContentHeight();
    }

    @Override
    public void init(Consumer<AbstractWidget> widgetConsumer, Runnable onRefresh) {
        this.onRefreshCallback = onRefresh;
        Minecraft mc = Minecraft.getInstance();

        idInput = new EditBox(mc.font, x + 1, y + 23, width - 68, 18, Component.literal("minecraft:bread"));
        idInput.setMaxLength(256);
        UiTheme.styleEditBox(idInput);
        widgetConsumer.accept(idInput);

        widgetConsumer.accept(new SlimActionButton(x + width - 68, y + 18, 68, 20, Component.literal("Add"), b -> {
            String val = idInput.getValue();
            if (val == null || val.isEmpty()) return;
            ResourceLocation rl = ResourceLocation.tryParse(val.contains(":") ? val : "minecraft:" + val);
            if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
                setting.add(BuiltInRegistries.ITEM.get(rl));
                ConfigManager.getInstance().save();
                idInput.setValue("");
                if (onRefreshCallback != null) onRefreshCallback.run();
            }
        }));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        guiGraphics.drawString(mc.font, setting.getName(), x, y + 4, UiTheme.TEXT_PRIMARY, false);
        UiTheme.drawInset(guiGraphics, x, y + 18, width, 20);

        int startX = x;
        int startY = y + INPUT_AREA_HEIGHT;
        int currentX = startX;
        int currentY = startY;
        int limitX = x + width - ITEM_SIZE;

        for (Map.Entry<Item, Boolean> entry : setting.getValue().entrySet()) {
            if (currentX > limitX) {
                currentX = startX;
                currentY += ITEM_SIZE;
            }

            Item item = entry.getKey();
            boolean enabled = entry.getValue();
            UiTheme.drawPanel(guiGraphics, currentX, currentY, 16, 16,
                    enabled ? UiTheme.withAlpha(UiTheme.ACCENT, 48) : UiTheme.PANEL_SOFT,
                    enabled ? UiTheme.ACCENT : UiTheme.BORDER_SOFT);

            if (UiTheme.isInside(mouseX, mouseY, currentX, currentY, 16, 16)) {
                guiGraphics.renderTooltip(mc.font,
                        Component.literal(item.getDescription().getString() + (enabled ? " [enabled]" : " [disabled]")),
                        mouseX, mouseY);
            }

            guiGraphics.renderItem(new ItemStack(item), currentX, currentY);
            currentX += ITEM_SIZE;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int startX = x;
        int startY = y + INPUT_AREA_HEIGHT;
        int currentX = startX;
        int currentY = startY;
        int limitX = x + width - ITEM_SIZE;
        List<Item> keys = new ArrayList<>(setting.getValue().keySet());

        for (Item item : keys) {
            if (currentX > limitX) {
                currentX = startX;
                currentY += ITEM_SIZE;
            }

            if (UiTheme.isInside(mouseX, mouseY, currentX, currentY, 16, 16)) {
                if (button == 0) {
                    setting.toggle(item);
                } else if (button == 1) {
                    setting.remove(item);
                    if (onRefreshCallback != null) onRefreshCallback.run();
                }
                ConfigManager.getInstance().save();
                return true;
            }
            currentX += ITEM_SIZE;
        }
        return false;
    }
}
