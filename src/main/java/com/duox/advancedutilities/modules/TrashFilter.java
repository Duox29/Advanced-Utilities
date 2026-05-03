package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.BooleanSetting;
import com.duox.advancedutilities.system.settings.ItemListSetting;
import com.duox.advancedutilities.system.settings.NumberSetting;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

public class TrashFilter extends Module {

    private final ItemListSetting trashItems = new ItemListSetting("Trash Items");

    private final BooleanSetting dropFromMainInventory = new BooleanSetting("Drop Main Inventory", true);
    private final BooleanSetting dropFromHotbar = new BooleanSetting("Drop Hotbar", false);
    private final BooleanSetting dropFromOffhand = new BooleanSetting("Drop Offhand", false);

    private final BooleanSetting wholeStack = new BooleanSetting("Drop Whole Stack", true);
    private final BooleanSetting onlyWhenInventoryClosed = new BooleanSetting("Only When Inventory Closed", false);

    private final NumberSetting delayTicks = new NumberSetting("Delay Ticks", 2.0, 0.0, 20.0, 1.0);

    private int tickCounter = 0;

    public TrashFilter() {
        super("Trash Filter", "Automatically drops filtered trash items when they enter your inventory.", Category.PLAYER);

        addSetting(trashItems);
        addSetting(dropFromMainInventory);
        addSetting(dropFromHotbar);
        addSetting(dropFromOffhand);
        addSetting(wholeStack);
        addSetting(onlyWhenInventoryClosed);
        addSetting(delayTicks);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        if (onlyWhenInventoryClosed.getValue() && mc.screen != null) {
            return;
        }

        int delay = (int) Math.max(0, delayTicks.getValue());
        if (tickCounter < delay) {
            tickCounter++;
            return;
        }
        tickCounter = 0;

        // Player inventory menu slot layout (vanilla):
        // 0      = crafting result
        // 1..4   = crafting input
        // 5..8   = armor
        // 9..35  = main inventory
        // 36..44 = hotbar
        // 45     = offhand

        if (dropFromMainInventory.getValue()) {
            if (dropFirstTrashInRange(9, 35)) return;
        }

        if (dropFromHotbar.getValue()) {
            if (dropFirstTrashInRange(36, 44)) return;
        }

        if (dropFromOffhand.getValue()) {
            dropTrashSlot(45);
        }
    }

    private boolean dropFirstTrashInRange(int startSlot, int endSlot) {
        for (int slot = startSlot; slot <= endSlot; slot++) {
            if (dropTrashSlot(slot)) {
                return true;
            }
        }
        return false;
    }

    private boolean dropTrashSlot(int slot) {
        ItemStack stack = mc.player.inventoryMenu.getSlot(slot).getItem();
        if (stack.isEmpty()) return false;
        if (!isTrash(stack)) return false;

        mc.gameMode.handleInventoryMouseClick(
                mc.player.inventoryMenu.containerId,
                slot,
                wholeStack.getValue() ? 1 : 0,
                ClickType.THROW,
                mc.player
        );

        return true;
    }

    private boolean isTrash(ItemStack stack) {
        return trashItems.contains(stack.getItem());
    }
}