package com.duox.advancedutilities.mixin;

import com.duox.advancedutilities.system.ConnectionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState; // NEW IMPORT
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public class MixinConnectScreen {

    @Inject(method = "startConnecting", at = @At("HEAD"))
    private static void onStartConnecting(Screen parent, Minecraft mc, ServerAddress address, ServerData serverData, boolean b, TransferState transferState, CallbackInfo ci) {
        // Debug log
        System.out.println("[AdvancedUtilities] Connecting to: " + (address != null ? address.getHost() : "null"));

        if (serverData != null) {
            ConnectionManager.lastServer = serverData;
        } else if (address != null) {
            // [FIX] Sử dụng constructor cho 1.21
            System.out.println("[AdvancedUtilities] ServerData is null, creating from address...");
            ConnectionManager.lastServer = new ServerData("Last Server", address.getHost() + ":" + address.getPort(), ServerData.Type.OTHER);
        }
    }
}