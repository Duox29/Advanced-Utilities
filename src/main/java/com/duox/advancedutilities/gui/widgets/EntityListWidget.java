package com.duox.advancedutilities.gui.widgets;

import com.duox.advancedutilities.system.ConfigManager;
import com.duox.advancedutilities.system.settings.EntityListSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class EntityListWidget extends SettingWidget {
    private final EntityListSetting setting;
    private EditBox idInput;

    public EntityListWidget(EntityListSetting setting, int x, int y, int width, int height) {
        super(x, y, width, height);
        this.setting = setting;
    }

    @Override
    public void init(Consumer<AbstractWidget> widgetConsumer, Runnable onRefresh) {
        Minecraft mc = Minecraft.getInstance();
        idInput = new EditBox(mc.font, x, y + 32, width - 45, 18, Component.literal("Entity ID"));
        idInput.setMaxLength(256);
        widgetConsumer.accept(idInput);

        Button btnAddId = Button.builder(Component.literal("Add"), b -> {
            String val = idInput.getValue();
            if (val != null && !val.isEmpty()) {
                try {
                    ResourceLocation rl = ResourceLocation.tryParse(val.contains(":") ? val : "minecraft:" + val);
                    if (rl != null && ForgeRegistries.ENTITY_TYPES.containsKey(rl)) {
                        setting.add(ForgeRegistries.ENTITY_TYPES.getValue(rl));
                        ConfigManager.save();
                        idInput.setValue("");
                    }
                } catch (Exception ignored) {}
            }
        }).bounds(x + width - 40, y + 32, 40, 18).build();
        widgetConsumer.accept(btnAddId);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        guiGraphics.drawString(mc.font, setting.getName(), x, y + 6, 0xFFFFFF, false);

        int itemX = x + 80;
        int itemY = y + 2;
        int limitX = x + width - 5;

        for (Map.Entry<EntityType<?>, Boolean> entry : setting.getValue().entrySet()) {
            if (itemX + 16 > limitX) break;

            EntityType<?> type = entry.getKey();
            boolean enabled = entry.getValue();

            int bgColor = enabled ? 0x8000FF00 : 0x80FF0000;
            guiGraphics.fill(itemX, itemY, itemX + 16, itemY + 16, bgColor);

            if (mouseX >= itemX && mouseX <= itemX + 16 && mouseY >= itemY && mouseY <= itemY + 16) {
                guiGraphics.renderOutline(itemX, itemY, 16, 16, 0xFFFFFFFF);
                guiGraphics.renderTooltip(mc.font, Component.literal(type.getDescription().getString() + (enabled ? " [ON]" : " [OFF]")), mouseX, mouseY);
            }

            // FIX: Lỗi ItemStack crash
            SpawnEggItem eggItem = ForgeSpawnEggItem.fromEntityType(type);
            if (eggItem != null) {
                guiGraphics.renderItem(eggItem.getDefaultInstance(), itemX, itemY);
            } else {
                guiGraphics.drawString(mc.font, "?", itemX + 4, itemY + 4, 0xAAAAAA);
            }
            itemX += 18;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int itemX = x + 80;
        int itemY = y + 2;
        int limitX = x + width - 5;

        List<EntityType<?>> keys = new ArrayList<>(setting.getValue().keySet());
        for (EntityType<?> type : keys) {
            if (itemX + 16 > limitX) break;
            if (mouseX >= itemX && mouseX <= itemX + 16 && mouseY >= itemY && mouseY <= itemY + 16) {
                if (button == 0) setting.toggle(type);
                else if (button == 1) setting.remove(type);
                ConfigManager.save();
                return true;
            }
            itemX += 18;
        }
        return false;
    }
}