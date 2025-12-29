package com.duox.advancedutilities.mixin;

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
            // We apply the modifier directly here to ensure it goes below vanilla limits if any
            // Note: ComputeFovModifierEvent is usually sufficient, but if something clamps it later,
            // we might need to be aggressive or check where the clamping happens.
            // Vanilla GameRenderer.getFov usually returns the final FOV.
            // If the user says it stops at 10x, it implies a lower bound clamp.
            // Let's print debug info or just force it.
            
            // Actually, wait. ComputeFovModifierEvent is fired inside getFov.
            // If we are here (RETURN of getFov), the event has already fired.
            // If the value is still clamped, we might be able to override it here.
            // However, looking at vanilla code (which we can't see but can infer),
            // getFov result is used for projection matrix.
            
            // If I look at 1.20.1 source (via knowledge base/inference):
            // The method calls getFov which calls the event.
            // Then it might clamp.
            // Let's try to override the return value directly.
            
            // BUT, the zoom module logic was: event.setNewFovModifier(event.getNewFovModifier() / multiplier);
            // This modifies the *modifier*, not the base FOV.
            // Base FOV is usually 70-110.
            // Modifier starts at 1.0.
            // If multiplier is 50, modifier is 0.02.
            // Final FOV = Base * Modifier. 70 * 0.02 = 1.4 degrees.
            
            // If vanilla clamps the *result* of getFov, we can overwrite it here.
            
            // cir.setReturnValue(originalFov); // This would just be identity.
            
            // Wait, if the user says "stops at 10x", maybe there is a MINIMUM FOV clamp in the camera or projection?
            // Usually GameRenderer.getFov returns the FOV in degrees.
            
            // Let's try to force the FOV to be what we want if it didn't go low enough.
            // We can recalculate what it SHOULD be.
            // But we don't have easy access to the "unclamped" value if it was clamped inside.
            // However, we can just apply our zoom again if we think it wasn't applied?
            // No, that would double apply.
            
            // Let's assume the event worked but something clamped it.
            // If we inject at RETURN, we see the clamped value.
            // If we want to force it lower, we can just set it.
            
            // But wait, the event modification is clean.
            // If we use Mixin, we need to know the base FOV to apply math?
            // Or we can just trust the Zoom module's setting.
            
            // Let's try this:
            // If Zoom is enabled, we ensure the FOV allows very small values.
            // Actually, vanilla might have `Mth.clamp(fov, 1.0, 180.0)` or similar.
            // If so, cir.getReturnValue() would be 1.0 if we tried to go lower.
            
            // If we want to go lower than 1.0 (which is extreme zoom), we need to override here.
            
            // Let's try to set the return value to exactly what we expect if possible,
            // OR just unclamp it if it seems clamped.
            
            // But a safer bet is:
            // Calculate expected FOV = (Base FOV * Modifier).
            // We don't have Base FOV easily here without re-reading settings or fields.
            
            // Alternative: Just remove the clamp via Redirect?
            // Redirecting Math.clamp/Mth.clamp in getFov is cleaner if we can find it.
            // But we don't have the method body to know for sure.
            
            // Use a simpler approach:
            // Inject at RETURN.
            // Calculate target FOV based on settings and known state? No too complex.
            
            // Let's just looking at the value.
            // If zoom is 50x, and base is 90. Target is 1.8.
            // If vanilla clamps at 10.0 or 5.0, we will see 5.0 here.
            // We can just set it to `originalFov` ? No.
            
            // Let's try to re-apply the logic on the final value if it seems too high?
            // No, that's messy.
            
            // Let's try to allow the FOV to go lower.
            // If I look at similar mods, they often mixin to `getFov` and just set the value.
            
            // I'll try to just bypass the clamp by setting the return value.
            // But I need to know what the value *should* be.
            
            // Let's assume the event handler IS working, but the result is clamped.
            // The event modifies the *modifier*.
            // The code is likely:
            // double fov = options.fov().get();
            // fov *= modifier;
            // fov = Mth.clamp(fov, MIN, MAX);
            // return fov;
            
            // If I inject at RETURN, I can just set `cir.setReturnValue(unclampedFov)`.
            // How do I get unclampedFov?
            // I can roughly reconstruct it:
            // We know the modifier was supposed to be (1 / zoom).
            // But we don't know the base fov easily (it's in options).
            
            // Actually, we can get options via Minecraft.getInstance().options.fov().get().
            
             double baseFov = Minecraft.getInstance().options.fov().get();
             // We also need to account for other modifiers (sprinting, flying, etc.)
             // The event system handles that.
             
             // If we just want to force zoom:
             // double targetFov = baseFov / zoomModule.getMultiplier();
             // This ignores other modifiers (swiftness, bows). Maybe that's acceptable for a "Zoom" feature?
             // Usually Zoom should stack with those?
             // If we want to stack, we need the "original modifier" before zoom.
             
             // If we stick to the event, we are good citizens.
             // If the event result is clamped, we are stuck.
             
             // Let's try to use `@Redirect` on the clamping method if possible.
             // But I don't know the exact line or method call without reading source.
             
             // Strategy:
             // 1. Inject at RETURN.
             // 2. Check if Zoom is active.
             // 3. Re-calculate the desired FOV purely based on (Current Return Value * something)? No.
             // 4. Re-calculate based on (Base FOV / Zoom Multiplier).
             //    This ignores other modifiers which might be clamped out anyway.
             //    This effectively says "When Zoom is on, FOV is exactly Base/Zoom".
             //    This disables dynamic FOV (sprinting etc) while zoomed, which is often DESIRED for a steady zoom (like OptiFine).
             
             double target = baseFov / zoomModule.getMultiplier();
             cir.setReturnValue(target);
        }
    }
}
