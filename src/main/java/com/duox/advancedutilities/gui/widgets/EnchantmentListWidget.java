package com.duox.advancedutilities.gui.widgets;

import com.duox.advancedutilities.system.ConfigManager;
import com.duox.advancedutilities.system.settings.EnchantmentListSetting;
import com.duox.advancedutilities.system.settings.EnchantmentListSetting.EnchantmentData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class EnchantmentListWidget extends SettingWidget {
    private static final int ITEM_SIZE = 18;
    private static final int INPUT_AREA_HEIGHT = 64;

    private final EnchantmentListSetting setting;
    private EditBox idInput;
    private EditBox levelInput;
    private EditBox priceInput;
    private Runnable onRefreshCallback;

    public EnchantmentListWidget(EnchantmentListSetting setting, int x, int y, int width, int height) {
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

        idInput = new EditBox(mc.font, x + 1, y + 20, width - 68, 18, Component.literal("minecraft:sharpness"));
        levelInput = new EditBox(mc.font, x + 1, y + 42, 76, 18, Component.literal("Level"));
        priceInput = new EditBox(mc.font, x + 81, y + 42, 76, 18, Component.literal("Price"));
        levelInput.setValue("1");
        priceInput.setValue("64");
        UiTheme.styleEditBox(idInput);
        UiTheme.styleEditBox(levelInput);
        UiTheme.styleEditBox(priceInput);
        widgetConsumer.accept(idInput);
        widgetConsumer.accept(levelInput);
        widgetConsumer.accept(priceInput);

        widgetConsumer.accept(new SlimActionButton(x + width - 68, y + 20, 68, 40, Component.literal("Add"), b -> {
            String val = idInput.getValue();
            if (val == null || val.isEmpty()) return;

            try {
                String id = val.contains(":") ? val : "minecraft:" + val;
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl == null || mc.level == null) return;

                Optional<Holder.Reference<Enchantment>> optionalEnch = mc.level.registryAccess()
                        .registryOrThrow(Registries.ENCHANTMENT)
                        .getHolder(rl);
                if (optionalEnch.isEmpty()) return;

                int level = parseInt(levelInput.getValue(), 1);
                int price = parseInt(priceInput.getValue(), 64);
                setting.add(id);
                EnchantmentData data = setting.getData(id);
                if (data != null) {
                    data.minLevel = level;
                    data.maxPrice = price;
                }

                ConfigManager.getInstance().save();
                idInput.setValue("");
                if (onRefreshCallback != null) onRefreshCallback.run();
            } catch (Exception ignored) {
            }
        }));
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        guiGraphics.drawString(mc.font, setting.getName(), x, y + 4, UiTheme.TEXT_PRIMARY, false);
        //guiGraphics.drawString(mc.font, "LMB toggle  •  RMB remove", x + width - 100, y + 4, UiTheme.TEXT_FAINT, false);

        UiTheme.drawInset(guiGraphics, x, y + 18, width - 68, 20);
        UiTheme.drawInset(guiGraphics, x, y + 40, 76, 20);
        UiTheme.drawInset(guiGraphics, x + 80, y + 40, 76, 20);

        int startX = x + 2;
        int startY = y + INPUT_AREA_HEIGHT;
        int currentX = startX;
        int currentY = startY;
        int limitX = x + width - ITEM_SIZE;

        if (mc.level == null) return;

        for (Map.Entry<String, EnchantmentData> entry : setting.getValue().entrySet()) {
            if (currentX > limitX) {
                currentX = startX;
                currentY += ITEM_SIZE;
            }

            String enchantId = entry.getKey();
            EnchantmentData data = entry.getValue();
            ResourceLocation rl = ResourceLocation.tryParse(enchantId);
            if (rl == null) continue;

            Optional<Holder.Reference<Enchantment>> optionalEnch = mc.level.registryAccess()
                    .registryOrThrow(Registries.ENCHANTMENT)
                    .getHolder(rl);
            if (optionalEnch.isEmpty()) continue;

            Holder<Enchantment> enchantHolder = optionalEnch.get();
            UiTheme.drawPanel(guiGraphics, currentX, currentY, 16, 16,
                    data.enabled ? UiTheme.withAlpha(UiTheme.ACCENT, 48) : UiTheme.PANEL_SOFT,
                    data.enabled ? UiTheme.ACCENT : UiTheme.BORDER_SOFT);

            if (UiTheme.isInside(mouseX, mouseY, currentX, currentY, 16, 16)) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Enchantment.getFullname(enchantHolder, data.minLevel));
                tooltip.add(Component.literal("Enabled: " + data.enabled));
                tooltip.add(Component.literal("Min Level: " + data.minLevel));
                tooltip.add(Component.literal("Max Price: " + data.maxPrice));
                guiGraphics.renderComponentTooltip(mc.font, tooltip, mouseX, mouseY);
            }

            ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantHolder, data.minLevel));
            guiGraphics.renderItem(book, currentX, currentY);
            currentX += ITEM_SIZE;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int startX = x + 2;
        int startY = y + INPUT_AREA_HEIGHT;
        int currentX = startX;
        int currentY = startY;
        int limitX = x + width - ITEM_SIZE;
        List<String> keys = new ArrayList<>(setting.getValue().keySet());

        for (String enchantId : keys) {
            if (currentX > limitX) {
                currentX = startX;
                currentY += ITEM_SIZE;
            }

            if (UiTheme.isInside(mouseX, mouseY, currentX, currentY, 16, 16)) {
                if (button == 0) {
                    setting.toggle(enchantId);
                } else if (button == 1) {
                    setting.remove(enchantId);
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
