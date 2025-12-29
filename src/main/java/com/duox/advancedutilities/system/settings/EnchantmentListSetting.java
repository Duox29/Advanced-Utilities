package com.duox.advancedutilities.system.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashMap;

public class EnchantmentListSetting extends Setting<LinkedHashMap<Enchantment, EnchantmentListSetting.EnchantmentData>> {
    public EnchantmentListSetting(String name) {
        super(name, new LinkedHashMap<>());
    }

    public void add(Enchantment enchantment) {
        if (!value.containsKey(enchantment)) {
            value.put(enchantment, new EnchantmentData(true, 1, 64));
        }
    }

    public void remove(Enchantment enchantment) {
        value.remove(enchantment);
    }

    public void toggle(Enchantment enchantment) {
        if (value.containsKey(enchantment)) {
            EnchantmentData data = value.get(enchantment);
            data.enabled = !data.enabled;
        }
    }
    
    public EnchantmentData getData(Enchantment enchantment) {
        return value.get(enchantment);
    }

    public boolean contains(Enchantment enchantment) {
        return value.containsKey(enchantment) && value.get(enchantment).enabled;
    }

    @Override
    public JsonElement save() {
        JsonObject map = new JsonObject();
        this.value.forEach((ench, data) -> {
            ResourceLocation key = ForgeRegistries.ENCHANTMENTS.getKey(ench);
            if (key != null) {
                JsonObject dataObj = new JsonObject();
                dataObj.addProperty("enabled", data.enabled);
                dataObj.addProperty("minLevel", data.minLevel);
                dataObj.addProperty("maxPrice", data.maxPrice);
                map.add(key.toString(), dataObj);
            }
        });
        return map;
    }

    @Override
    public void load(JsonElement element) {
        if (!element.isJsonObject()) return;

        LinkedHashMap<Enchantment, EnchantmentData> newMap = new LinkedHashMap<>();
        JsonObject obj = element.getAsJsonObject();

        for (String key : obj.keySet()) {
            ResourceLocation rl = ResourceLocation.tryParse(key);
            if (rl != null && ForgeRegistries.ENCHANTMENTS.containsKey(rl)) {
                JsonElement dataElem = obj.get(key);
                if (dataElem.isJsonObject()) {
                    JsonObject dataObj = dataElem.getAsJsonObject();
                    boolean enabled = dataObj.has("enabled") ? dataObj.get("enabled").getAsBoolean() : true;
                    int minLevel = dataObj.has("minLevel") ? dataObj.get("minLevel").getAsInt() : 1;
                    int maxPrice = dataObj.has("maxPrice") ? dataObj.get("maxPrice").getAsInt() : 64;
                    newMap.put(ForgeRegistries.ENCHANTMENTS.getValue(rl), new EnchantmentData(enabled, minLevel, maxPrice));
                } else if (dataElem.isJsonPrimitive() && dataElem.getAsJsonPrimitive().isBoolean()) {
                    // Legacy support for boolean
                    newMap.put(ForgeRegistries.ENCHANTMENTS.getValue(rl), new EnchantmentData(dataElem.getAsBoolean(), 1, 64));
                }
            }
        }
        this.value = newMap;
    }
    
    public static class EnchantmentData {
        public boolean enabled;
        public int minLevel;
        public int maxPrice;
        
        public EnchantmentData(boolean enabled, int minLevel, int maxPrice) {
            this.enabled = enabled;
            this.minLevel = minLevel;
            this.maxPrice = maxPrice;
        }
    }
}
