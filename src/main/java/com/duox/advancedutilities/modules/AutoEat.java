package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.BooleanSetting;
import com.duox.advancedutilities.system.settings.NumberSetting;
import net.minecraft.core.component.DataComponents;
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
    private int cooldownTicks = 0;

    public AutoEat() {
        super("Auto Eat", "Automatically eats food when hungry or low health", Category.PLAYER);
        addSetting(minHealth);
        addSetting(minHunger);
        addSetting(overflow);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;

        if (cooldownTicks > 0) {
            cooldownTicks--;
        }

        if (eating) {
            if (!shouldKeepEating()) {
                stopEating();
            }
            return;
        }

        if (cooldownTicks > 0) return;

        boolean needsHealth = mc.player.getHealth() <= minHealth.getValue();
        boolean needsFood = mc.player.getFoodData().getFoodLevel() <= minHunger.getValue();

        if (!needsHealth && !needsFood) return;

        int bestSlot = findBestFood(needsHealth, needsFood);
        if (bestSlot != -1) {
            startEating(bestSlot);
        }
    }

    private boolean shouldKeepEating() {
        if (mc.player == null) return false;
        if (!mc.player.isUsingItem()) return false;

        ItemStack using = mc.player.getUseItem();
        FoodProperties food = using.get(DataComponents.FOOD);
        if (food == null) return false;

        boolean needsHealth = mc.player.getHealth() <= minHealth.getValue();
        boolean needsFood = mc.player.getFoodData().getFoodLevel() <= minHunger.getValue();

        if (isGoldenApple(using)) {
            return needsHealth;
        }

        return needsFood || needsHealth;
    }

    private int findBestFood(boolean urgentHealth, boolean needsFood) {
        int bestSlot = -1;
        double bestScore = Double.NEGATIVE_INFINITY;

        int currentFood = mc.player.getFoodData().getFoodLevel();
        float currentSat = mc.player.getFoodData().getSaturationLevel();

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food == null) continue;

            double score = scoreFood(stack, food, currentFood, currentSat, urgentHealth, needsFood);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    private double scoreFood(ItemStack stack, FoodProperties food, int currentFood, float currentSat,
                             boolean urgentHealth, boolean needsFood) {

        if (isGoldenApple(stack)) {
            if (!urgentHealth) return Double.NEGATIVE_INFINITY;

            // Optional conservative behavior:
            // if already under regen + absorption, do not waste a gap
            if (mc.player.hasEffect(MobEffects.REGENERATION) && mc.player.hasEffect(MobEffects.ABSORPTION)) {
                return Double.NEGATIVE_INFINITY;
            }

            return stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE ? 5000.0 : 3000.0;
        }

        int missingFood = 20 - currentFood;
        if (missingFood <= 0 && !food.canAlwaysEat()) {
            return Double.NEGATIVE_INFINITY;
        }

        int nutrition = food.nutrition();
        float saturation = food.saturation();

        // If overflow is disabled, reject food that restores more hunger than needed.
        if (!overflow.getValue() && nutrition > missingFood) {
            return Double.NEGATIVE_INFINITY;
        }

        int effectiveFoodGain = Math.min(nutrition, missingFood);
        int foodAfterEat = Math.min(20, currentFood + nutrition);

        // Saturation after eating cannot exceed the new food level.
        float satRoom = Math.max(0.0F, foodAfterEat - currentSat);
        float effectiveSatGain = Math.min(saturation, satRoom);

        int wastedFood = Math.max(0, nutrition - effectiveFoodGain);
        float wastedSat = Math.max(0.0F, saturation - effectiveSatGain);

        double score = 0.0;

        // Base hunger value
        score += effectiveFoodGain * (needsFood ? 100.0 : 55.0);

        // Saturation matters too, especially for efficiency
        score += effectiveSatGain * 22.0;

        // If low HP, favor food that helps sustain regen longer
        if (urgentHealth) {
            score += effectiveSatGain * 10.0;

            // Bonus if this food helps push toward natural regen threshold
            if (currentFood < 18) {
                int towardRegen = Math.min(effectiveFoodGain, 18 - currentFood);
                score += towardRegen * 35.0;
            }
        }

        // Waste penalties
        score -= wastedFood * 40.0;
        score -= wastedSat * 8.0;

        // Prefer tighter fit when several foods are similar
        score -= Math.abs(missingFood - nutrition) * 3.0;
        if (nutrition == missingFood) {
            score += 20.0;
        }

        return score;
    }

    private boolean isGoldenApple(ItemStack stack) {
        return stack.getItem() == Items.GOLDEN_APPLE || stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE;
    }

    private void startEating(int slot) {
        if (mc.player == null) return;

        oldSlot = mc.player.getInventory().selected;
        mc.player.getInventory().selected = slot;
        mc.options.keyUse.setDown(true);
        eating = true;
    }

    private void stopEating() {
        mc.options.keyUse.setDown(false);

        if (mc.player != null && oldSlot != -1) {
            mc.player.getInventory().selected = oldSlot;
        }

        oldSlot = -1;
        eating = false;
        cooldownTicks = 10; // 10 ticks = 0.5s
    }

    @Override
    public void onDisable() {
        if (eating) {
            stopEating();
        }
    }
}