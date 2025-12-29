package com.duox.advancedutilities.mixin;
/*
 * Mixin to hook into DisconnectedScreen to add Reconnect buttons
 */
import com.duox.advancedutilities.modules.AutoReconnect;
import com.duox.advancedutilities.system.ConnectionManager;
import com.duox.advancedutilities.system.ModuleManager;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisconnectedScreen.class)
public abstract class MixinDisconnectedScreen extends Screen {

    @Unique private Button autoReconnectBtn;
    @Unique private Button reconnectBtn;
    @Unique private int timer;

    protected MixinDisconnectedScreen(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        System.out.println("[AdvancedUtilities] DisconnectedScreen init.");

        int buttonWidth = 200;
        int buttonHeight = 20;

        // Đặt nút ở vị trí thấp hẳn xuống dưới (chiều cao màn hình - 60)
        int yBase = this.height - 60;

        // 1. Nút Reconnect
        this.reconnectBtn = this.addRenderableWidget(Button.builder(Component.literal("Reconnect"), button -> {
            this.reconnect();
        }).bounds(this.width / 2 - 100, yBase, buttonWidth, buttonHeight).build());

        // 2. Nút Auto Reconnect
        this.autoReconnectBtn = this.addRenderableWidget(Button.builder(Component.literal("Auto Reconnect"), button -> {
            AutoReconnect mod = ModuleManager.INSTANCE.getModule(AutoReconnect.class);
            if (mod != null) {
                mod.toggle();
                this.updateButtonText(mod);
                if (mod.isEnabled()) {
                    this.timer = mod.getWaitTicks();
                }
            }
        }).bounds(this.width / 2 - 100, yBase + 24, buttonWidth, buttonHeight).build());

        // Logic check server
        if (ConnectionManager.lastServer == null) {
            this.reconnectBtn.active = false;
            this.reconnectBtn.setMessage(Component.literal("Unknown Last Server"));
            this.autoReconnectBtn.active = false;
        } else {
            AutoReconnect mod = ModuleManager.INSTANCE.getModule(AutoReconnect.class);
            if (mod != null) {
                if (mod.isEnabled()) {
                    this.timer = mod.getWaitTicks();
                }
                this.updateButtonText(mod);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (ConnectionManager.lastServer == null || this.autoReconnectBtn == null) return;

        AutoReconnect mod = ModuleManager.INSTANCE.getModule(AutoReconnect.class);
        if (mod != null && mod.isEnabled()) {
            if (this.timer > 0) {
                this.timer--;
                this.updateButtonText(mod);
            } else {
                this.reconnect();
            }
        }
    }

    @Unique
    private void updateButtonText(AutoReconnect mod) {
        if (this.autoReconnectBtn == null) return;
        if (mod.isEnabled()) {
            double secondsLeft = Math.ceil(this.timer / 20.0);
            this.autoReconnectBtn.setMessage(Component.literal("Auto Reconnect (" + (int)secondsLeft + "s)"));
        } else {
            this.autoReconnectBtn.setMessage(Component.literal("Auto Reconnect (Off)"));
        }
    }

    @Unique
    private void reconnect() {
        ServerData server = ConnectionManager.lastServer;
        if (server != null) {
            ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), this.minecraft, ServerAddress.parseString(server.ip), server, false);
        }
    }
}