package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Module;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;

public class NoBreakDelay extends Module {
    private Field delayField;

    public NoBreakDelay() {
        super("NoBreakDelay", "Removes block breaking delay", Category.PLAYER);
        initializeField();
    }

    private void initializeField() {
        try {
            // Cố gắng tìm field theo tên SRG (f_105215_ cho 1.20.1)
            // Đây là biến "destroyDelay" trong MultiPlayerGameMode
            delayField = ObfuscationReflectionHelper.findField(MultiPlayerGameMode.class, "f_105215_");
        } catch (Exception e) {
            try {
                // Nếu lỗi, thử tìm bằng tên thường (dành cho môi trường Dev một số mapping)
                delayField = MultiPlayerGameMode.class.getDeclaredField("destroyDelay");
            } catch (Exception ex) {
                System.err.println("[AdvancedUtilities] CRITICAL: Không tìm thấy field destroyDelay!");
                ex.printStackTrace();
            }
        }

        if (delayField != null) {
            delayField.setAccessible(true);
        }
    }

    @Override
    public void onTick() {
        // Luôn kiểm tra null để tránh crash game
        if (mc.gameMode == null || delayField == null) return;

        try {
            // Lấy giá trị hiện tại
            int currentDelay = delayField.getInt(mc.gameMode);

            // Chỉ set lại nếu nó > 0 để tối ưu hiệu năng
            if (currentDelay > 0) {
                delayField.setInt(mc.gameMode, 0);
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}