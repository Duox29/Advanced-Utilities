package com.duox.advancedutilities.system.settings;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.LinkedHashMap;

public class BlockListSetting extends Setting<LinkedHashMap<Block, Boolean>> {

    /** Lazily rebuilt unmodifiable view of the enabled blocks; avoids per-tick list churn. */
    private java.util.List<Block> enabledView = null;

    public BlockListSetting(String name) {
        super(name, new LinkedHashMap<>());
    }

    public void add(Block block) {
        if (!value.containsKey(block)) value.put(block, true);
        enabledView = null;
    }

    public void remove(Block block) {
        value.remove(block);
        enabledView = null;
    }

    public void toggle(Block block) {
        if (value.containsKey(block)) value.put(block, !value.get(block));
        enabledView = null;
    }

    public boolean contains(Block block) {
        return value.getOrDefault(block, false);
    }

    /** Cached unmodifiable list of currently enabled blocks. The same instance is
     *  returned until the map changes, so callers can compare by identity/equals cheaply. */
    public java.util.List<Block> getBlocksView() {
        if (enabledView == null) {
            java.util.ArrayList<Block> list = new java.util.ArrayList<>();
            for (java.util.Map.Entry<Block, Boolean> e : value.entrySet()) {
                if (e.getValue()) list.add(e.getKey());
            }
            enabledView = java.util.Collections.unmodifiableList(list);
        }
        return enabledView;
    }

    // Add this method to allow the Finder module to retrieve the list of blocks to search for
    public java.util.List<Block> getBlocks() {
        return getBlocksView();
    }

    @Override
    public JsonElement save() {
        JsonObject map = new JsonObject();
        this.value.forEach((block, enabled) -> {
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
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
            if (rl != null && BuiltInRegistries.BLOCK.containsKey(rl)) {
                newMap.put(BuiltInRegistries.BLOCK.get(rl), obj.get(key).getAsBoolean());
            }
        }
        this.value = newMap;
        this.enabledView = null;
    }
}