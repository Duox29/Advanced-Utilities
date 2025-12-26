package com.duox.advancedutilities.system.settings;

import net.minecraft.world.level.block.Block;
import java.util.ArrayList;
import java.util.List;

public class BlockListSetting extends Setting<List<Block>> {
    public BlockListSetting(String name) {
        super(name, new ArrayList<>());
    }

    public void add(Block block) {
        if (!value.contains(block)) {
            value.add(block);
        }
    }

    public void remove(Block block) {
        value.remove(block);
    }

    public boolean contains(Block block) {
        return value.contains(block);
    }
}