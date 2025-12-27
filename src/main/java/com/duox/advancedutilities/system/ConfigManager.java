package com.duox.advancedutilities.system;

import com.duox.advancedutilities.system.settings.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

public class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("advancedutilities.json");

    public static void load() {
        System.out.println("[AdvancedUtilities] Loading config from " + CONFIG_PATH);

        if (!Files.exists(CONFIG_PATH)) {
            System.out.println("[AdvancedUtilities] Config file not found, creating default.");
            save();
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
            System.out.println("[AdvancedUtilities] Config loaded successfully.");
        } catch (JsonSyntaxException e) {
            System.err.println("[AdvancedUtilities] Config JSON is malformed! Resetting.");
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        JsonObject json = new JsonObject();

        for (Module module : ModuleManager.INSTANCE.getModules()) {
            JsonObject moduleJson = new JsonObject();
            moduleJson.addProperty("enabled", module.isEnabled());

            JsonObject settingsJson = new JsonObject();
            for (Setting<?> setting : module.getSettings()) {
                saveSetting(settingsJson, setting);
            }
            moduleJson.add("settings", settingsJson);

            json.add(module.getName(), moduleJson);
        }

        try {
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
            JsonObject map = new JsonObject();
            s.getValue().forEach((block, enabled) -> {
                ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
                if (key != null) map.addProperty(key.toString(), enabled);
            });
            json.add(s.getName(), map);
        } else if (setting instanceof EntityListSetting s) {
            JsonObject map = new JsonObject();
            s.getValue().forEach((type, enabled) -> {
                ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
                if (key != null) map.addProperty(key.toString(), enabled);
            });
            json.add(s.getName(), map);
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
                }
                // --- FIX LOGIC LOAD BLOCK LIST ---
                else if (setting instanceof BlockListSetting s) {
                    LinkedHashMap<Block, Boolean> map = new LinkedHashMap<>();
                    if (element.isJsonObject()) {
                        JsonObject obj = element.getAsJsonObject();
                        for (String key : obj.keySet()) {
                            // [FIX] Dùng tryParse thay vì new ResourceLocation để tránh crash và warning
                            ResourceLocation rl = ResourceLocation.tryParse(key);
                            if (rl != null && ForgeRegistries.BLOCKS.containsKey(rl)) {
                                map.put(ForgeRegistries.BLOCKS.getValue(rl), obj.get(key).getAsBoolean());
                            }
                        }
                    }
                    // [QUAN TRỌNG] Dòng này bị thiếu trong code cũ
                    s.setValue(map);
                }
                // --- FIX LOGIC LOAD ENTITY LIST ---
                else if (setting instanceof EntityListSetting s) {
                    LinkedHashMap<EntityType<?>, Boolean> map = new LinkedHashMap<>();
                    if (element.isJsonObject()) {
                        JsonObject obj = element.getAsJsonObject();
                        for (String key : obj.keySet()) {
                            // [FIX] Dùng tryParse
                            ResourceLocation rl = ResourceLocation.tryParse(key);
                            if (rl != null && ForgeRegistries.ENTITY_TYPES.containsKey(rl)) {
                                map.put(ForgeRegistries.ENTITY_TYPES.getValue(rl), obj.get(key).getAsBoolean());
                            }
                        }
                    }
                    s.setValue(map);
                }
            } catch (Exception e) {
                System.err.println("Error loading setting " + setting.getName() + ": " + e.getMessage());
            }
        }
    }
}