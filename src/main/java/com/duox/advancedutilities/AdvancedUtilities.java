package com.duox.advancedutilities;

import com.duox.advancedutilities.gui.UtilityGui;
import com.duox.advancedutilities.system.BlockSelector;
import com.duox.advancedutilities.system.ConfigManager;
import com.duox.advancedutilities.system.ModuleManager;
import com.duox.advancedutilities.system.Module;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
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

    // [FIX] Constructor Injection: The modern way to get the Event Bus.
    // This replaces the deprecated FMLJavaModLoadingContext.get()
    public AdvancedUtilities(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        // Register Mod Lifecycle Events
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::registerKeys);

        // Register Game Events (Tick, Input, etc.)
        MinecraftForge.EVENT_BUS.register(this);

        // Initialize Systems
        BlockSelector.INSTANCE.init();
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        ModuleManager.INSTANCE.init();
        ConfigManager.load();
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

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && Minecraft.getInstance().player != null) {
            ModuleManager.INSTANCE.getModules().stream()
                    .filter(Module::isEnabled)
                    .forEach(Module::onTick);
        }
    }
}