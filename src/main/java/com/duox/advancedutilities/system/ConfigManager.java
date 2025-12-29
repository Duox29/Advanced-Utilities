package com.duox.advancedutilities.system;

import com.duox.advancedutilities.system.settings.Setting;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/*
 * Manages configuration loading and saving for the Advanced Utilities mod.
 * Uses a singleton pattern to ensure consistent access throughout the mod.
 */
public class ConfigManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final ConfigManager INSTANCE = new ConfigManager();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path configPath = FMLPaths.CONFIGDIR.get().resolve("advancedutilities.json");

    private ConfigManager() {
        // Private constructor for singleton pattern
    }

    /**
     * Gets the singleton instance of ConfigManager.
     *
     * @return The ConfigManager instance
     */
    public static ConfigManager getInstance() {
        return INSTANCE;
    }

    /**
     * Loads configuration from disk.
     * If the config file doesn't exist, creates a new one with default values.
     */
    public void load() {
        if (!Files.exists(configPath)) {
            LOGGER.info("Config file not found, creating default configuration");
            save();
            return;
        }

        try (Reader reader = new FileReader(configPath.toFile())) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            if (json == null) {
                LOGGER.warn("Config file is empty or invalid, using defaults");
                return;
            }

            int loadedModules = 0;
            for (Module module : ModuleManager.INSTANCE.getModules()) {
                if (json.has(module.getName())) {
                    JsonObject moduleJson = json.getAsJsonObject(module.getName());

                    if (moduleJson.has("enabled")) {
                        module.setEnabled(moduleJson.get("enabled").getAsBoolean());
                    }

                    if (moduleJson.has("settings")) {
                        JsonObject settingsJson = moduleJson.getAsJsonObject("settings");
                        int loadedSettings = 0;
                        for (Setting<?> setting : module.getSettings()) {
                            if (settingsJson.has(setting.getName())) {
                                try {
                                    setting.load(settingsJson.get(setting.getName()));
                                    loadedSettings++;
                                } catch (Exception e) {
                                    LOGGER.error("Failed to load setting '{}' for module '{}': {}", 
                                            setting.getName(), module.getName(), e.getMessage());
                                }
                            }
                        }
                        if (loadedSettings > 0) {
                            LOGGER.debug("Loaded {} settings for module '{}'", loadedSettings, module.getName());
                        }
                    }
                    loadedModules++;
                }
            }
            LOGGER.info("Successfully loaded configuration for {} modules", loadedModules);
        } catch (IOException e) {
            LOGGER.error("Failed to load configuration file: {}", e.getMessage(), e);
        }
    }

    /**
     * Saves the current configuration to disk.
     * Creates the config directory if it doesn't exist.
     */
    public void save() {
        JsonObject json = new JsonObject();

        for (Module module : ModuleManager.INSTANCE.getModules()) {
            JsonObject moduleJson = new JsonObject();
            moduleJson.addProperty("enabled", module.isEnabled());

            JsonObject settingsJson = new JsonObject();
            for (Setting<?> setting : module.getSettings()) {
                settingsJson.add(setting.getName(), setting.save());
            }
            moduleJson.add("settings", settingsJson);

            json.add(module.getName(), moduleJson);
        }

        try {
            if (!Files.exists(FMLPaths.CONFIGDIR.get())) {
                Files.createDirectories(FMLPaths.CONFIGDIR.get());
            }
            try (Writer writer = new FileWriter(configPath.toFile())) {
                gson.toJson(json, writer);
            }
            LOGGER.debug("Configuration saved successfully");
        } catch (IOException e) {
            LOGGER.error("Failed to save configuration file: {}", e.getMessage(), e);
        }
    }
}