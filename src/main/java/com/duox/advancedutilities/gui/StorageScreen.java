package com.duox.advancedutilities.gui;

import com.duox.advancedutilities.modules.AutoStash;
import com.duox.advancedutilities.modules.StorageManager;
import com.duox.advancedutilities.system.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

public class StorageScreen extends Screen {
    private final StorageManager storageManager;
    private EditBox searchBox;
    private Button takeButton;
    private Button autoStashButton;

    // Flag để kiểm soát việc tắt module khi đóng GUI
    private boolean keepModuleOn = false;

    // Grid Logic
    private List<ItemEntry> allItems = new ArrayList<>();
    private List<ItemEntry> filteredItems = new ArrayList<>();

    private int scrollOffset = 0;
    private static final int GRID_COLS = 9;
    private static final int SLOT_SIZE = 18;
    private static final int GRID_GAP = 2;
    private static final int GRID_START_X = 20;
    private static final int GRID_START_Y = 40;
    private static final int ROWS_VISIBLE = 8;

    private static class ItemEntry {
        ItemStack stack;
        String id;
        int totalCount;

        ItemEntry(String id, int count) {
            this.id = id;
            this.totalCount = count;
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
            if (item == Items.AIR && !id.equals("minecraft:air")) {
                // Fallback
            }
            this.stack = new ItemStack(item);
        }
    }

    public StorageScreen(StorageManager manager) {
        super(Component.literal("Storage Management"));
        this.storageManager = manager;
    }

    @Override
    protected void init() {
        super.init();

        // Reset flag mỗi khi init lại GUI
        this.keepModuleOn = false;

        int searchWidth = 200;
        this.searchBox = new EditBox(this.font, this.width / 2 - searchWidth / 2, 10, searchWidth, 20, Component.literal("Search"));
        this.searchBox.setMaxLength(50);
        this.searchBox.setResponder(this::onSearchChanged);
        this.addWidget(this.searchBox);

        // Buttons
        this.takeButton = Button.builder(Component.literal("Take Items"), button -> {
            if (!storageManager.isEnabled()) {
                storageManager.setEnabled(true);
            }
            // Logic: Khi bấm Take Items, ta muốn module tiếp tục chạy ngầm để lấy đồ
            storageManager.startRetrieval();

            // Đánh dấu là giữ module bật
            this.keepModuleOn = true;

            // Đóng GUI
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            //this.onClose();
        }).bounds(this.width - 110, this.height - 30, 100, 20).build();
        this.addRenderableWidget(takeButton);

        this.autoStashButton = Button.builder(Component.literal("AutoStash"), button -> {
            AutoStash stash = ModuleManager.INSTANCE.getModule(AutoStash.class);
            if (stash != null) {
                stash.setEnabled(!stash.isEnabled());
            }
        }).bounds(10, this.height - 30, 100, 20).build();
        this.addRenderableWidget(autoStashButton);

        refreshItemList();
    }

    @Override
    public void onClose() {
        // Nếu không có cờ keepModuleOn (nghĩa là người dùng bấm ESC hoặc đóng GUI mà không bấm Take Items)
        // Thì ta phải tắt StorageManager để Mixin không chặn GUI của rương nữa.
        if (!keepModuleOn) {
            storageManager.clearRequestQueue(); // Xóa queue nếu hủy
            storageManager.setEnabled(false);
        }

        super.onClose();
    }

    // --- Các phần code bên dưới giữ nguyên ---

    private void refreshItemList() {
        allItems.clear();
        Map<String, Map<String, Integer>> cache = AutoStash.getChestCache();

        Map<String, Integer> totals = new HashMap<>();

        for (Map<String, Integer> contents : cache.values()) {
            for (Map.Entry<String, Integer> entry : contents.entrySet()) {
                totals.put(entry.getKey(), totals.getOrDefault(entry.getKey(), 0) + entry.getValue());
            }
        }

        for (Map.Entry<String, Integer> entry : totals.entrySet()) {
            allItems.add(new ItemEntry(entry.getKey(), entry.getValue()));
        }

        // Sort by count desc
        allItems.sort((a, b) -> Integer.compare(b.totalCount, a.totalCount));

        filterItems();
    }

    private void filterItems() {
        String query = searchBox.getValue().toLowerCase();
        if (query.isEmpty()) {
            filteredItems = new ArrayList<>(allItems);
        } else {
            filteredItems = allItems.stream()
                    .filter(e -> e.stack.getHoverName().getString().toLowerCase().contains(query) || e.id.contains(query))
                    .collect(Collectors.toList());
        }
        // Clamp scroll
        int maxRow = (int) Math.ceil((double) filteredItems.size() / GRID_COLS);
        int maxScroll = Math.max(0, maxRow - ROWS_VISIBLE);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
    }

    private void onSearchChanged(String text) {
        filterItems();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        // Draw Search Box
        this.searchBox.render(graphics, mouseX, mouseY, partialTick);

        // Draw Grid Background
        int gridWidth = GRID_COLS * (SLOT_SIZE + GRID_GAP);
        int gridHeight = ROWS_VISIBLE * (SLOT_SIZE + GRID_GAP);
        int startX = (this.width - gridWidth) / 2;
        int startY = 40;

        graphics.fill(startX - 2, startY - 2, startX + gridWidth, startY + gridHeight, 0x80000000);

        // Draw Items
        int startIndex = scrollOffset * GRID_COLS;
        int endIndex = Math.min(startIndex + (ROWS_VISIBLE * GRID_COLS), filteredItems.size());

        for (int i = startIndex; i < endIndex; i++) {
            ItemEntry entry = filteredItems.get(i);
            int relIndex = i - startIndex;
            int col = relIndex % GRID_COLS;
            int row = relIndex / GRID_COLS;

            int x = startX + col * (SLOT_SIZE + GRID_GAP);
            int y = startY + row * (SLOT_SIZE + GRID_GAP);

            // Draw Item
            graphics.renderItem(entry.stack, x + 1, y + 1);
            graphics.renderItemDecorations(this.font, entry.stack, x + 1, y + 1, shortenedCount(entry.totalCount));

            // Hover highlight
            if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0x80FFFFFF);

                // Tooltip
                List<Component> tooltip = getTooltipFromItem(this.minecraft, entry.stack);
                tooltip.add(Component.literal("Total: " + entry.totalCount).withStyle(net.minecraft.ChatFormatting.GRAY));

                // Show currently queued amount
                int queued = storageManager.getRequestQueue().getOrDefault(entry.id, 0);
                if (queued > 0) {
                    tooltip.add(Component.literal("Queued: " + queued).withStyle(net.minecraft.ChatFormatting.YELLOW));
                }

                graphics.renderTooltip(this.font, tooltip, entry.stack.getTooltipImage(), mouseX, mouseY);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 5, 0xFFFFFF);
    }

    private String shortenedCount(int count) {
        if (count >= 1000000) return String.format("%.1fM", count / 1000000.0);
        if (count >= 1000) return String.format("%.1fK", count / 1000.0);
        return String.valueOf(count);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        // Grid Click
        int gridWidth = GRID_COLS * (SLOT_SIZE + GRID_GAP);
        int startX = (this.width - gridWidth) / 2;
        int startY = 40;

        int startIndex = scrollOffset * GRID_COLS;
        int endIndex = Math.min(startIndex + (ROWS_VISIBLE * GRID_COLS), filteredItems.size());

        for (int i = startIndex; i < endIndex; i++) {
            int relIndex = i - startIndex;
            int col = relIndex % GRID_COLS;
            int row = relIndex / GRID_COLS;

            int x = startX + col * (SLOT_SIZE + GRID_GAP);
            int y = startY + row * (SLOT_SIZE + GRID_GAP);

            if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                ItemEntry entry = filteredItems.get(i);

                int change = 0;
                if (button == 0) change = 64; // Left
                if (button == 1) change = 1;  // Right

                if (Screen.hasShiftDown()) change = -change;

                int current = storageManager.getRequestQueue().getOrDefault(entry.id, 0);
                int target = current + change;
                if (target < 0) target = 0;
                if (target > entry.totalCount) target = entry.totalCount;

                if (target == 0) {
                    storageManager.getRequestQueue().remove(entry.id);
                } else {
                    storageManager.getRequestQueue().put(entry.id, target);
                }

                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0) {
            scrollOffset--;
        } else if (delta < 0) {
            scrollOffset++;
        }

        int maxRow = (int) Math.ceil((double) filteredItems.size() / GRID_COLS);
        int maxScroll = Math.max(0, maxRow - ROWS_VISIBLE);

        if (scrollOffset < 0) scrollOffset = 0;
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}