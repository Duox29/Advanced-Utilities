package com.duox.advancedutilities.mixin;

import com.duox.advancedutilities.modules.Zoom;
import com.duox.advancedutilities.system.ModuleManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MouseHandler.class)
public class MixinMouseHandler {

    @WrapOperation(
            method = "turnPlayer",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/MouseHandler;accumulatedDX:D", opcode = org.objectweb.asm.Opcodes.GETFIELD)
    )
    private double wrapAccumulatedDX(MouseHandler instance, Operation<Double> original) {
        double rawDX = original.call(instance);
        Zoom zoom = ModuleManager.INSTANCE.getModule(Zoom.class);
        if (zoom != null && zoom.isEnabled()) {
            return rawDX * zoom.getSensitivityModifier();
        }
        return rawDX;
    }

    @WrapOperation(
            method = "turnPlayer",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/MouseHandler;accumulatedDY:D", opcode = org.objectweb.asm.Opcodes.GETFIELD)
    )
    private double wrapAccumulatedDY(MouseHandler instance, Operation<Double> original) {
        double rawDY = original.call(instance);
        Zoom zoom = ModuleManager.INSTANCE.getModule(Zoom.class);
        if (zoom != null && zoom.isEnabled()) {
            return rawDY * zoom.getSensitivityModifier();
        }
        return rawDY;
    }
}