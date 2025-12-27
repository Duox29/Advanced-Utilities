package com.duox.advancedutilities.system.settings;

import net.minecraft.world.level.block.Block;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BlockListSetting extends Setting<LinkedHashMap<Block, Boolean>> {
    public BlockListSetting(String name) {
        super(name, new LinkedHashMap<>());
    }

    public void add(Block block) {
        if (!value.containsKey(block)) {
            value.put(block, true); // Default ON
        }
    }

    public void remove(Block block) {
        value.remove(block);
    }

    public void toggle(Block block) {
        if (value.containsKey(block)) {
            value.put(block, !value.get(block));
        }
    }

    // [FIX 1] Thêm hàm này để AutoRightClick và Finder không bị lỗi
    // Trả về true NẾU block có trong danh sách VÀ đang được BẬT (true)
    public boolean contains(Block block) {
        return value.getOrDefault(block, false);
    }
}