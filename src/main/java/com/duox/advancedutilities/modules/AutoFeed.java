package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.BooleanSetting;
import com.duox.advancedutilities.system.settings.NumberSetting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AutoFeed extends Module {

    private final NumberSetting radius = new NumberSetting("Radius", 4.5, 1.0, 6.0, 0.5);
    private final NumberSetting delay = new NumberSetting("Delay", 8.0, 1.0, 40.0, 1.0);
    private final NumberSetting feedCooldown = new NumberSetting("Feed Cooldown", 600.0, 20.0, 2400.0, 20.0);
    private final BooleanSetting feedBabies = new BooleanSetting("Feed Babies", false);

    private final Map<UUID, Long> fedAnimals = new HashMap<>();

    private int tickCounter = 0;

    public AutoFeed() {
        super("Auto Feed", "Automatically feeds nearby breedable animals.", Category.WORLD);
        addSetting(radius);
        addSetting(delay);
        addSetting(feedCooldown);
        addSetting(feedBabies);
    }

    @Override
    public void onEnable() {
        tickCounter = 0;
        fedAnimals.clear();

        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.literal("§a[AutoFeed] Enabled"),
                    true
            );
        }
    }

    @Override
    public void onDisable() {
        tickCounter = 0;
        fedAnimals.clear();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        tickCounter++;

        int delayTicks = delay.getValue().intValue();
        if (tickCounter < delayTicks) return;
        tickCounter = 0;

        long gameTime = mc.level.getGameTime();
        cleanupFedCache(gameTime);

        double r = radius.getValue();

        AABB box = mc.player.getBoundingBox().inflate(r);

        List<Animal> animals = mc.level.getEntitiesOfClass(
                Animal.class,
                box,
                animal ->
                        animal.isAlive()
                                && mc.player.distanceTo(animal) <= r
                                && shouldFeed(animal, gameTime)
        );

        if (animals.isEmpty()) return;

        animals.sort(Comparator.comparingDouble(a -> mc.player.distanceToSqr(a)));

        for (Animal animal : animals) {
            int foodSlot = findFoodSlotFor(animal);

            if (foodSlot == -1) {
                continue;
            }

            int oldSlot = mc.player.getInventory().selected;
            mc.player.getInventory().selected = foodSlot;

            mc.gameMode.interact(mc.player, animal, InteractionHand.MAIN_HAND);
            mc.player.swing(InteractionHand.MAIN_HAND);

            mc.player.getInventory().selected = oldSlot;

            fedAnimals.put(animal.getUUID(), gameTime);

            return;
        }
    }

    private boolean shouldFeed(Animal animal, long gameTime) {
        if (animal.isBaby() && !feedBabies.getValue()) {
            return false;
        }

        if (!animal.isBaby() && !animal.canFallInLove()) {
            return false;
        }

        Long lastFedTime = fedAnimals.get(animal.getUUID());
        if (lastFedTime == null) {
            return true;
        }

        return gameTime - lastFedTime >= feedCooldown.getValue().longValue();
    }

    private void cleanupFedCache(long gameTime) {
        long cooldown = feedCooldown.getValue().longValue();

        Iterator<Map.Entry<UUID, Long>> iterator = fedAnimals.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();

            if (gameTime - entry.getValue() >= cooldown) {
                iterator.remove();
            }
        }
    }

    private int findFoodSlotFor(Animal animal) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);

            if (stack.isEmpty()) continue;

            if (animal.isFood(stack)) {
                return i;
            }
        }

        return -1;
    }
}