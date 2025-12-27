package com.duox.advancedutilities.system.settings;

import net.minecraft.world.entity.EntityType;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public class EntityListSetting extends Setting<LinkedHashMap<EntityType<?>, Boolean>> {
    public EntityListSetting(String name) {
        super(name, new LinkedHashMap<>());
    }

    public void add(EntityType<?> entity) {
        if (!value.containsKey(entity)) {
            value.put(entity, true);
        }
    }

    public void remove(EntityType<?> entity) {
        value.remove(entity);
    }

    public void toggle(EntityType<?> entity) {
        if (value.containsKey(entity)) {
            value.put(entity, !value.get(entity));
        }
    }

    // [FIX 2] Thêm hàm contains
    public boolean contains(EntityType<?> entity) {
        return value.getOrDefault(entity, false);
    }
}