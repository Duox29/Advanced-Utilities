package com.duox.advancedutilities.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/*
 * Interface này dùng để truy cập vào các field private/protected của class Minecraft
 * mà không cần dùng Reflection (giúp tăng hiệu năng).
 */
@Mixin(Minecraft.class)
public interface IMinecraftAccessor {

    /*\n     * Mapping tên field phải chuẩn xác với version Minecraft (Official Mappings 1.20.1: rightClickDelay)\n     */
    @Accessor("rightClickDelay")
    void setRightClickDelay(int delay);

    @Accessor("rightClickDelay")
    int getRightClickDelay();
}