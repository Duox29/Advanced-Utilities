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
    private final NumberSetting speed = new NumberSetting("Speed", 1.0, 0.0, 10.0, 0.1);

    private final EnumSetting<Priority> priority = new EnumSetting<>("Priority", Priority.DISTANCE);
    private final NumberSetting degree = new NumberSetting("FOV", 360.0, 10.0, 360.0, 10.0);

    private final BooleanSetting hostile = new BooleanSetting("Hostile", true);
    private final BooleanSetting passive = new BooleanSetting("Passive", false);
    private final BooleanSetting players = new BooleanSetting("Players", true);
    private final BooleanSetting allLivingExceptPlayer = new BooleanSetting("All Living (Except Player)", false); // OPTION MỚI

    private final EntityListSetting customFilter = new EntityListSetting("Custom Filter");

    private int tickCounter = 0;

    public KillAura() {
        super("Kill Aura", "Automatically attacks entities around you.", Category.COMBAT);
        addSetting(range);
        addSetting(speed);
        addSetting(priority);
        addSetting(degree);
        addSetting(hostile);
        addSetting(passive);
        addSetting(players);
        addSetting(allLivingExceptPlayer); // THÊM SETTING
        addSetting(customFilter);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;

        if (speed.getValue() == 0) {
            if (mc.player.getAttackStrengthScale(0.0f) < 1.0f) {
                return;
            }
        } else {
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

        // === OPTION MỚI: TẤN CÔNG MỌI SINH VẬT NGOẠI TRỪ NGƯỜI CHƠI ===
        if (allLivingExceptPlayer.getValue() && !(entity instanceof Player)) {
            return true;
        }

        // Custom filter (whitelist)
        if (customFilter.contains(entity.getType())) {
            return true;
        }

        // Standard filters
        boolean isHostile = entity instanceof Monster;
        boolean isPassive = entity instanceof Animal;
        boolean isPlayer = entity instanceof Player;

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