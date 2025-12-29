package com.duox.advancedutilities.mixin;
/*
 * Mixin to hook into GameRenderer to apply Zoom
 */
import com.duox.advancedutilities.modules.Zoom;
import com.duox.advancedutilities.system.ModuleManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void onGetFov(Camera camera, float partialTicks, boolean useFOVSetting, CallbackInfoReturnable<Double> cir) {
        Zoom zoomModule = ModuleManager.INSTANCE.getModule(Zoom.class);
        if (zoomModule != null && zoomModule.isEnabled()) {
            double originalFov = cir.getReturnValue();

             double baseFov = Minecraft.getInstance().options.fov().get();
             
             double target = baseFov / zoomModule.getMultiplier();
             cir.setReturnValue(target);
        }
    }
}
