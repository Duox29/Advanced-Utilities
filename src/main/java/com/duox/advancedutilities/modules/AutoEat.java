package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.BooleanSetting;
import com.duox.advancedutilities.system.settings.NumberSetting;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class AutoEat extends Module {
    private final NumberSetting minHealth = new NumberSetting("Min Health", 10.0, 1.0, 20.0, 1.0);
    private final NumberSetting minHunger = new NumberSetting("Min Hunger", 14.0, 1.0, 20.0, 1.0);
    private final BooleanSetting overflow = new BooleanSetting("Overflow", false);
    
    private int oldSlot = -1;
    private boolean eating = false;
    private long lastEatTime = 0;

    public AutoEat() {
        super("Auto Eat", "Automatically eats food when hungry or low health", Category.PLAYER);
        addSetting(minHealth);
        addSetting(minHunger);
        addSetting(overflow);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;
        
        // Cooldown 500ms
        if (System.currentTimeMillis() - lastEatTime < 500) return;
        
        if (eating) {
            if (!mc.player.isUsingItem()) {
                stopEating();
            }
            return;
        }

        float health = mc.player.getHealth();
        float hunger = mc.player.getFoodData().getFoodLevel();

        boolean needsHealth = health <= minHealth.getValue();
        boolean needsFood = hunger <= minHunger.getValue();

        if (needsHealth || needsFood) {
            int bestSlot = findBestFood(needsHealth);
            if (bestSlot != -1) {
                startEating(bestSlot);
            }
        }
    }

    private int findBestFood(boolean urgentHealth) {
        int bestSlot = -1;
        float bestValue = -1;
        
        int currentFood = mc.player.getFoodData().getFoodLevel();

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEdible()) continue;
            
            FoodProperties food = stack.getFoodProperties(mc.player);
            if (food == null) continue;

            boolean isGap = stack.getItem() == Items.GOLDEN_APPLE || stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE;
            float value = 0;

            if (isGap) {
                // Golden Apple Logic:
                // 1. Must need health
                // 2. No Regeneration (don't overlap healing)
                // 3. No Absorption (don't stack absorption/waste apples)
                if (urgentHealth 
                    && !mc.player.hasEffect(MobEffects.REGENERATION) 
                    && !mc.player.hasEffect(MobEffects.ABSORPTION)) {
                    value = 1000; 
                } else {
                    continue;
                }
            } else {
                // Normal Food Logic
                int nutrition = food.getNutrition();
                
                // Only eat if it doesn't overflow hunger bar
                // (User requested: replace saturation check with hunger check)
                if (currentFood + nutrition > 20) {
                     continue;
                }

                // Score is just nutrition value
                value = nutrition;
            }

            if (value > bestValue) {
                bestValue = value;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private void startEating(int slot) {
        oldSlot = mc.player.getInventory().selected;
        mc.player.getInventory().selected = slot;
        mc.options.keyUse.setDown(true);
        eating = true;
    }

    private void stopEating() {
        mc.options.keyUse.setDown(false);
        if (oldSlot != -1) {
            mc.player.getInventory().selected = oldSlot;
            oldSlot = -1;
        }
        eating = false;
        lastEatTime = System.currentTimeMillis();
    }
    
    @Override
    public void onDisable() {
        if (eating) stopEating();
    }
}
