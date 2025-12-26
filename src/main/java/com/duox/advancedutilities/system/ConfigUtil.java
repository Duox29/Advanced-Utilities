package com.duox.advancedutilities.system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ConfigUtil {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    // File sẽ nằm ở: .minecraft/config/advancedutilities.json
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("advancedutilities.json");

    // Lưu danh sách Module đang bật
    public static void saveConfig() {
        Map<String, Boolean> moduleStates = new HashMap<>();

        for (Module module : ModuleManager.INSTANCE.getModules()) {
            moduleStates.put(module.getName(), module.isEnabled());
        }

        try {
            // Tạo thư mục nếu chưa tồn tại
            if (!Files.exists(FMLPaths.CONFIGDIR.get())) {
                Files.createDirectories(FMLPaths.CONFIGDIR.get());
            }

            try (Writer writer = new FileWriter(CONFIG_PATH.toFile())) {
                GSON.toJson(moduleStates, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Đọc file config và áp dụng vào các module
    public static void loadConfig() {
        if (!Files.exists(CONFIG_PATH)) return; // Nếu chưa có file thì bỏ qua

        try (Reader reader = new FileReader(CONFIG_PATH.toFile())) {
            Map<String, Boolean> moduleStates = GSON.fromJson(reader, new TypeToken<Map<String, Boolean>>(){}.getType());

            if (moduleStates != null) {
                for (Module module : ModuleManager.INSTANCE.getModules()) {
                    if (moduleStates.containsKey(module.getName())) {
                        boolean enabled = moduleStates.get(module.getName());
                        module.setEnabled(enabled); // Set trạng thái đã lưu
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}