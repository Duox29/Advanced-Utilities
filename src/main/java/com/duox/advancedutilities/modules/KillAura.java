package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.BooleanSetting;
import com.duox.advancedutilities.system.settings.EntityListSetting;
import com.duox.advancedutilities.system.settings.EnumSetting;
import com.duox.advancedutilities.system.settings.NumberSetting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

public class KillAura extends Module {

    private final NumberSetting range = new NumberSetting("Range", 4.0, 1.0, 6.0, 0.1);
    private final NumberSetting speed = new NumberSetting("Speed", 1.0, 0.0, 10.0, 0.1); // CPS? Or just a speed factor? Usually CPS or tick delay.
                                                                                        // For now assuming 20 = 1 attack per tick, 10 = 1 attack per 2 ticks.
                                                                                        // Wait, "Speed" usually means attack speed.
                                                                                        // Let's interpret as CPS (Clicks Per Second). 20 max.

    // Using tick delay logic: 20 ticks / CPS = ticks per attack.
    // If Speed is 10, then 20/10 = 2 ticks delay.

    private final EnumSetting<Priority> priority = new EnumSetting<>("Priority", Priority.DISTANCE);
    private final NumberSetting degree = new NumberSetting("FOV", 360.0, 10.0, 360.0, 10.0); // Degree for FOV check
    
    private final BooleanSetting hostile = new BooleanSetting("Hostile", true);
    private final BooleanSetting passive = new BooleanSetting("Passive", false);
    private final BooleanSetting players = new BooleanSetting("Players", true);

    private final EntityListSetting customFilter = new EntityListSetting("Custom Filter");

    private int tickCounter = 0;

    public KillAura() {
        super("Kill Aura", "Automatically attacks entities around you.", Category.PLAYER);
        addSetting(range);
        addSetting(speed);
        addSetting(priority);
        addSetting(degree);
        addSetting(hostile);
        addSetting(passive);
        addSetting(players);
        addSetting(customFilter);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;

        if (speed.getValue() == 0) {
            // Check attack cooldown (1.0f means fully charged)
            if (mc.player.getAttackStrengthScale(0.0f) < 1.0f) {
                return;
            }
        } else {
            // Calculate attack delay based on speed (CPS)
            // Minecraft runs at 20 ticks per second.
            int delay = (int) (20.0 / speed.getValue());
            if (delay < 1) delay = 1;

            if (tickCounter < delay) {
                tickCounter++;
                return;
            }
            tickCounter = 0;
        }

        double rangeVal = range.getValue();
        double fovVal = degree.getValue();

        List<Entity> targets = mc.level.getEntitiesOfClass(Entity.class, 
                mc.player.getBoundingBox().inflate(rangeVal), 
                entity -> isValidTarget(entity, rangeVal, fovVal));

        if (targets.isEmpty()) return;

        // Sort by priority
        targets.sort(getComparator());

        Entity target = targets.get(0);
        attack(target);
    }

    private boolean isValidTarget(Entity entity, double rangeVal, double fovVal) {
        if (entity == mc.player) return false;
        if (!(entity instanceof LivingEntity)) return false;
        if (((LivingEntity) entity).isDeadOrDying()) return false;
        if (mc.player.distanceTo(entity) > rangeVal) return false;

        // Check FOV
        if (fovVal < 360) {
            Vec3 lookVec = mc.player.getLookAngle();
            Vec3 toEntityVec = entity.position().subtract(mc.player.position()).normalize();
            double angle = Math.toDegrees(Math.acos(lookVec.dot(toEntityVec)));
            if (angle > fovVal / 2.0) return false;
        }

        // Check types
        boolean isHostile = entity instanceof Monster; // Simple check, might need refinement
        boolean isPassive = entity instanceof Animal; // Simple check
        boolean isPlayer = entity instanceof Player;

        if (customFilter.contains(entity.getType())) {
            // If it's in the custom filter list, and that list entry is true, then it's valid? 
            // Usually custom filter acts as an whitelist or blacklist.
            // Based on EntityListSetting implementation, it stores EntityType -> Boolean.
            // So if it's in the map and true, we target it.
             if (customFilter.contains(entity.getType())) {
                 return true; // Whitelist behavior or Override
             }
        }
        
        // If not in custom filter (or custom filter logic implies valid), check standard filters
        if (isPlayer && players.getValue()) return true;
        if (isHostile && hostile.getValue()) return true;
        return isPassive && passive.getValue();
    }

    private void attack(Entity target) {
        if (mc.gameMode != null) {
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private Comparator<Entity> getComparator() {
        return switch (priority.getValue()) {
            case HEALTH -> Comparator.comparingDouble(e -> ((LivingEntity) e).getHealth());
            case ANGLE -> Comparator.comparingDouble(this::getAngleDifference);
            default -> Comparator.comparingDouble(e -> mc.player.distanceTo(e));
        };
    }
    
    private double getAngleDifference(Entity entity) {
        Vec3 lookVec = mc.player.getLookAngle();
        Vec3 toEntityVec = entity.position().subtract(mc.player.position()).normalize();
        return Math.toDegrees(Math.acos(lookVec.dot(toEntityVec)));
    }

    public enum Priority {
        DISTANCE,
        HEALTH,
        ANGLE
    }
}
