package com.duox.advancedutilities.gui.widgets;
/*
 * Widget for managing an EnchantmentListSetting.
 * Supports adding enchantments with min level and max price constraints.
 */
import com.duox.advancedutilities.system.ConfigManager;
import com.duox.advancedutilities.system.settings.EnchantmentListSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.duox.advancedutilities.system.settings.EnchantmentListSetting.EnchantmentData;

// ...

public class EnchantmentListWidget extends SettingWidget {
    private final EnchantmentListSetting setting;
    private EditBox idInput;
    private EditBox levelInput;
    private EditBox priceInput;
    private Runnable onRefreshCallback;
    private static final int ITEM_SIZE = 18;
    private static final int INPUT_AREA_HEIGHT = 55; // Increased height for more inputs

    public EnchantmentListWidget(EnchantmentListSetting setting, int x, int y, int width, int height) {
        super(x, y, width, height);
        this.setting = setting;
    }

    private int calculateContentHeight() {
        int count = setting.getValue().size();
        int itemsPerRow = (width - 4) / ITEM_SIZE;
        if (itemsPerRow < 1) itemsPerRow = 1;
        int rows = (int) Math.ceil((double) count / itemsPerRow);
        int neededHeight = INPUT_AREA_HEIGHT + (rows * ITEM_SIZE) + 4;
        return Math.max(height, neededHeight);
    }

    @Override
    public int getHeight() {
        return calculateContentHeight();
    }

    @Override
    public void init(Consumer<AbstractWidget> widgetConsumer, Runnable onRefresh) {
        this.onRefreshCallback = onRefresh;
        Minecraft mc = Minecraft.getInstance();

        // ID Input
        idInput = new EditBox(mc.font, x, y + 12, width - 45, 18, Component.literal("Enchantment ID"));
        idInput.setMaxLength(256);
        widgetConsumer.accept(idInput);

        // Level & Price Input
        levelInput = new EditBox(mc.font, x, y + 32, (width - 45) / 2 - 2, 18, Component.literal("Min Level"));
        levelInput.setValue("1");
        widgetConsumer.accept(levelInput);

        priceInput = new EditBox(mc.font, x + (width - 45) / 2 + 2, y + 32, (width - 45) / 2 - 2, 18, Component.literal("Max Price"));
        priceInput.setValue("64");
        widgetConsumer.accept(priceInput);

        Button btnAddId = Button.builder(Component.literal("Add"), b -> {
            String val = idInput.getValue();
            if (val != null && !val.isEmpty()) {
                try {
                    ResourceLocation rl = ResourceLocation.tryParse(val.contains(":") ? val : "minecraft:" + val);
                    if (rl != null && ForgeRegistries.ENCHANTMENTS.containsKey(rl)) {
                        int lvl = 1;
                        int price = 64;
                        try { lvl = Integer.parseInt(levelInput.getValue()); } catch (Exception e) {}
                        try { price = Integer.parseInt(priceInput.getValue()); } catch (Exception e) {}
                        
                        setting.add(ForgeRegistries.ENCHANTMENTS.getValue(rl));
                        // Update data
                        EnchantmentData data = setting.getData(ForgeRegistries.ENCHANTMENTS.getValue(rl));
                        if (data != null) {
                            data.minLevel = lvl;
                            data.maxPrice = price;
                        }
                        
                        ConfigManager.getInstance().save();
                        idInput.setValue("");
                        if (onRefreshCallback != null) onRefreshCallback.run();
                    }
                } catch (Exception ignored) {}
            }
        }).bounds(x + width - 40, y + 12, 40, 38).build();
        widgetConsumer.accept(btnAddId);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        guiGraphics.drawString(mc.font, setting.getName(), x, y + 2, 0xFFFFFF, false);
        
        // Labels for inputs
        // guiGraphics.drawString(mc.font, "Lvl", x, y + 36, 0xAAAAAA, false);
        // guiGraphics.drawString(mc.font, "Price", x + (width - 45) / 2 + 2, y + 36, 0xAAAAAA, false);

        int startX = x + 2;
        int startY = y + INPUT_AREA_HEIGHT;
        int currentX = startX;
        int currentY = startY;
        int limitX = x + width - ITEM_SIZE;

        for (Map.Entry<Enchantment, EnchantmentData> entry : setting.getValue().entrySet()) {
            if (currentX > limitX) {
                currentX = startX;
                currentY += ITEM_SIZE;
            }

            Enchantment enchant = entry.getKey();
            EnchantmentData data = entry.getValue();
            boolean enabled = data.enabled;

            int bgColor = enabled ? 0x8000FF00 : 0x80FF0000;
            guiGraphics.fill(currentX, currentY, currentX + 16, currentY + 16, bgColor);

            if (mouseX >= currentX && mouseX <= currentX + 16 && mouseY >= currentY && mouseY <= currentY + 16) {
                guiGraphics.renderOutline(currentX, currentY, 16, 16, 0xFFFFFFFF);
                
                String tooltip = enchant.getFullname(data.minLevel).getString() + 
                        (enabled ? " [ON]" : " [OFF]") +
                        "\nMin Lvl: " + data.minLevel + 
                        "\nMax Price: " + data.maxPrice;
                        
                List<Component> tooltips = new ArrayList<>();
                for (String line : tooltip.split("\n")) tooltips.add(Component.literal(line));
                
                guiGraphics.renderComponentTooltip(mc.font, tooltips, mouseX, mouseY);
            }

            ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchant, data.minLevel));
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

        List<Enchantment> keys = new ArrayList<>(setting.getValue().keySet());
        for (Enchantment enchant : keys) {
            if (currentX > limitX) {
                currentX = startX;
                currentY += ITEM_SIZE;
            }

            if (mouseX >= currentX && mouseX <= currentX + 16 && mouseY >= currentY && mouseY <= currentY + 16) {
                if (button == 0) {
                    setting.toggle(enchant);
                } else if (button == 1) {
                    setting.remove(enchant);
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
