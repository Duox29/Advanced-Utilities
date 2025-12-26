package com.duox.advancedutilities;

import com.duox.advancedutilities.gui.UtilityGui;
import com.duox.advancedutilities.system.ModuleManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

@Mod("advancedutilities") // ID của Mod (phải trùng với mods.toml)
public class AdvancedUtilities {

    // Keybind mở Menu
    public static final KeyMapping OPEN_GUI_KEY = new KeyMapping(
            "Open GUI", // Tên key trong file lang (hoặc để raw text)
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            "Advanced Utilities" // Category trong Keybind settings
    );

    public AdvancedUtilities() {
        // Đăng ký Event Bus cho Mod (Setup, Register Keys)
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::registerKeys);

        // Đăng ký Event Bus cho Forge (Tick, Input, Game Events)
        MinecraftForge.EVENT_BUS.register(this);
    }

    // Khởi tạo Client
    private void clientSetup(final FMLClientSetupEvent event) {
        // Khởi tạo ModuleManager và Config
        ModuleManager.INSTANCE.init();
    }

    // Đăng ký Keybind vào Game
    public void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_GUI_KEY);
    }

    // Lắng nghe phím bấm (Runtime)
    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        // Kiểm tra Key bấm và đảm bảo không null
        if (OPEN_GUI_KEY.consumeClick()) {
            Minecraft.getInstance().setScreen(new UtilityGui());
        }
    }
}