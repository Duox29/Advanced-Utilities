package com.duox.advancedutilities;

import com.duox.advancedutilities.gui.UtilityGui;
import com.duox.advancedutilities.system.BlockSelector; // [MỚI] Import BlockSelector
import com.duox.advancedutilities.system.Module;      // [MỚI] Import Module
import com.duox.advancedutilities.system.ModuleManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;            // [MỚI] Import TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

@Mod("advancedutilities")
public class AdvancedUtilities {

    public static final KeyMapping OPEN_GUI_KEY = new KeyMapping(
            "Open GUI",
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            "Advanced Utilities"
    );

    public AdvancedUtilities() {
        // Event Bus cho quá trình khởi chạy Mod (Setup, Register Keys)
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::registerKeys);

        // Event Bus cho các sự kiện trong Game (Tick, Input, Render)
        MinecraftForge.EVENT_BUS.register(this);

        // --- [CRITICAL] Đăng ký BlockSelector ---
        // Bắt buộc phải có dòng này để tính năng "Add Block" hoạt động
        BlockSelector.INSTANCE.init();
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        // ModuleManager loading logic (nếu có)
        // ModuleManager.INSTANCE.init(); // Uncomment nếu bạn có hàm init trong Manager
    }

    public void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_GUI_KEY);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        if (OPEN_GUI_KEY.consumeClick()) {
            Minecraft.getInstance().setScreen(new UtilityGui());
        }
    }

    // --- [CRITICAL] Vòng lặp chính của Mod (Heartbeat) ---
    // Hàm này sẽ chạy 20 lần/giây. Nó chịu trách nhiệm gọi onTick() cho các module.
    // Nếu thiếu hàm này, AutoRightClick sẽ đứng im.
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        // Chỉ xử lý ở cuối tick (Phase.END) và khi đã vào game (player != null)
        if (event.phase == TickEvent.Phase.END && Minecraft.getInstance().player != null) {

            // Duyệt qua tất cả module, nếu đang BẬT thì gọi onTick()
            ModuleManager.INSTANCE.getModules().stream()
                    .filter(Module::isEnabled)
                    .forEach(Module::onTick);
        }
    }
}