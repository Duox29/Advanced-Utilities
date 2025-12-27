package com.duox.advancedutilities.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MultiPlayerGameMode.class)
public interface IMultiPlayerGameModeAccessor {
    @Accessor("destroyDelay")
    void setDestroyDelay(int delay);
    @Accessor("destroyDelay")
    int getDestroyDelay();
}