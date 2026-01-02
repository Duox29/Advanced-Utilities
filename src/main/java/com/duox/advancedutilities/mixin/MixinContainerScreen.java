package com.duox.advancedutilities.mixin;

import com.duox.advancedutilities.gui.StoragePanel;
import com.duox.advancedutilities.modules.StorageManager;
import com.duox.advancedutilities.system.ModuleManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class MixinContainerScreen extends Screen {

    // Lưu vị trí panel static để nhớ vị trí khi mở lại inventory
    @Unique
    private static int lastPanelX = -1;
    @Unique
    private static int lastPanelY = -1;

    @Unique
    private StoragePanel storagePanel;

    protected MixinContainerScreen(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        StorageManager sm = ModuleManager.INSTANCE.getModule(StorageManager.class);
        if (sm != null && sm.isEnabled()) {

            // Tính toán vị trí mặc định nếu chưa có
            if (lastPanelX == -1) {
                lastPanelX = (this.width / 2) + 90;
                lastPanelY = (this.height - 200) / 2;
            }

            // Tạo Panel mới tại vị trí cũ
            storagePanel = new StoragePanel(sm, lastPanelX, lastPanelY);

            // QUAN TRỌNG: Thêm Panel như một Widget chính thống
            // Việc này tự động kích hoạt render, click, scroll, tooltip cho Panel
            this.addRenderableWidget(storagePanel);
        }
    }

    // Lưu lại vị trí khi đóng screen để lần sau mở ra nó ở chỗ cũ
    @Override
    public void removed() {
        if (storagePanel != null) {
            lastPanelX = storagePanel.x;
            lastPanelY = storagePanel.y;
        }
        super.removed();
    }
}