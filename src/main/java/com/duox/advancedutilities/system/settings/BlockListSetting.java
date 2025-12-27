package com.duox.advancedutilities.system.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashMap;
import java.util.Map;

public class BlockListSetting extends Setting<LinkedHashMap<Block, Boolean>> {
    public BlockListSetting(String name) {
        super(name, new LinkedHashMap<>());
    }

    public void add(Block block) {
        if (!value.containsKey(block)) value.put(block, true);
    }

    public void remove(Block block) {
        value.remove(block);
    }

    public void toggle(Block block) {
        if (value.containsKey(block)) value.put(block, !value.get(block));
    }

    public boolean contains(Block block) {
        return value.getOrDefault(block, false);
    }

    @Override
    public JsonElement save() {
        JsonObject map = new JsonObject();
        this.value.forEach((block, enabled) -> {
            ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
            if (key != null) map.addProperty(key.toString(), enabled);
        });
        return map;
    }

    @Override
    public void load(JsonElement element) {
        if (!element.isJsonObject()) return;

        LinkedHashMap<Block, Boolean> newMap = new LinkedHashMap<>();
        JsonObject obj = element.getAsJsonObject();

        for (String key : obj.keySet()) {
            ResourceLocation rl = ResourceLocation.tryParse(key);
            if (rl != null && ForgeRegistries.BLOCKS.containsKey(rl)) {
                newMap.put(ForgeRegistries.BLOCKS.getValue(rl), obj.get(key).getAsBoolean());
            }
        }
        this.value = newMap;
    }
}