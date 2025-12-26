package com.duox.advancedutilities.mixin;

import com.duox.advancedutilities.modules.NoBreakDelay;
import com.duox.advancedutilities.system.ModuleManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MultiPlayerGameMode.class)
public class MixinMultiPlayerGameMode {

    // Kỹ thuật: WrapOperation (Đặc sản của MixinExtras)
    // SỬA: Đổi f_105215_ thành destroyDelay vì đang dùng Official Mappings
    @WrapOperation(
            method = "continueDestroyBlock",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;destroyDelay:I", // Sửa tại đây
                    opcode = 0xB4 // Opcode.GETFIELD
            )
    )
    private int modifyDestroyDelay(MultiPlayerGameMode instance, Operation<Integer> original) {
        NoBreakDelay module = (NoBreakDelay) ModuleManager.INSTANCE.getModule(NoBreakDelay.class);

        if (module != null && module.isEnabled()) {
            return 0;
        }

        return original.call(instance);
    }
}