package com.duox.advancedutilities.system;

import com.duox.advancedutilities.system.settings.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    // Sử dụng FMLPaths chuẩn Forge như file cũ của bạn để tương thích tốt hơn
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("advancedutilities.json");

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save(); // Tạo file mặc định
            return;
        }

        try (Reader reader = new FileReader(CONFIG_PATH.toFile())) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) return;

            for (Module module : ModuleManager.INSTANCE.getModules()) {
                if (json.has(module.getName())) {
                    JsonObject moduleJson = json.getAsJsonObject(module.getName());

                    // 1. Load Module Enabled State
                    if (moduleJson.has("enabled")) {
                        module.setEnabled(moduleJson.get("enabled").getAsBoolean());
                    }

                    // 2. Load Settings
                    if (moduleJson.has("settings")) {
                        loadSettings(module, moduleJson.getAsJsonObject("settings"));
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        JsonObject json = new JsonObject();

        for (Module module : ModuleManager.INSTANCE.getModules()) {
            JsonObject moduleJson = new JsonObject();

            // 1. Save Enabled State
            moduleJson.addProperty("enabled", module.isEnabled());

            // 2. Save Settings
            JsonObject settingsJson = new JsonObject();
            for (Setting<?> setting : module.getSettings()) {
                saveSetting(settingsJson, setting);
            }
            moduleJson.add("settings", settingsJson);

            json.add(module.getName(), moduleJson);
        }

        try {
            // Tạo thư mục config nếu chưa có
            if (!Files.exists(FMLPaths.CONFIGDIR.get())) {
                Files.createDirectories(FMLPaths.CONFIGDIR.get());
            }

            try (Writer writer = new FileWriter(CONFIG_PATH.toFile())) {
                GSON.toJson(json, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void saveSetting(JsonObject json, Setting<?> setting) {
        if (setting instanceof BooleanSetting s) {
            json.addProperty(s.getName(), s.getValue());
        } else if (setting instanceof NumberSetting s) {
            json.addProperty(s.getName(), s.getValue());
        } else if (setting instanceof EnumSetting<?> s) {
            json.addProperty(s.getName(), s.getValue().name());
        } else if (setting instanceof BlockListSetting s) {
            List<String> ids = new ArrayList<>();
            for (Block block : s.getValue()) {
                // Lưu ID dạng string (minecraft:dirt) để không bị lỗi khi block ID số thay đổi
                ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
                if (key != null) ids.add(key.toString());
            }
            json.add(s.getName(), GSON.toJsonTree(ids));
        }
    }

    private static void loadSettings(Module module, JsonObject json) {
        for (Setting<?> setting : module.getSettings()) {
            if (!json.has(setting.getName())) continue;
            JsonElement element = json.get(setting.getName());

            try {
                if (setting instanceof BooleanSetting s) {
                    s.setValue(element.getAsBoolean());
                } else if (setting instanceof NumberSetting s) {
                    s.setValue(element.getAsDouble());
                } else if (setting instanceof EnumSetting<?> s) {
                    s.setValueByName(element.getAsString());
                } else if (setting instanceof BlockListSetting s) {
                    List<Block> blocks = new ArrayList<>();
                    for (JsonElement idElement : element.getAsJsonArray()) {
                        ResourceLocation rl = new ResourceLocation(idElement.getAsString());
                        if (ForgeRegistries.BLOCKS.containsKey(rl)) {
                            blocks.add(ForgeRegistries.BLOCKS.getValue(rl));
                        }
                    }
                    s.setValue(blocks);
                }
            } catch (Exception e) {
                System.err.println("Error loading setting " + setting.getName() + ": " + e.getMessage());
            }
        }
    }
}