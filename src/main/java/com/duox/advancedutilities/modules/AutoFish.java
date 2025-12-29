package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Constants;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.EnumSetting;
import com.duox.advancedutilities.system.settings.NumberSetting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.phys.Vec3;

/**
 * Automatically fishes using a fishing rod.
 * Supports two modes: XP mode (waits for XP gain) and Normal mode (uses fixed delay).
 */
public class AutoFish extends Module {

    /**
     * Fishing mode options.
     */
    public enum FishMode {
        XP, NORMAL
    }

    private final EnumSetting<FishMode> mode = new EnumSetting<>("Mode", FishMode.XP);
    private final NumberSetting recastDelay = new NumberSetting("Recast Delay", 20, 10, 100, 1);
    private final NumberSetting xpTimeout = new NumberSetting("XP Timeout", 60, 20, 200, 5);

    // State variables
    private boolean isQueuedToRecast = false;
    private int recastTimer = 0;
    private float lastXpProgress = -1;
    private int lastXpLevel = -1;
    private int xpTimeoutCounter = 0;
    private int xpDelayTimer = 0;
    private int idleTicksCounter = 0;

    public AutoFish() {
        super("AutoFish", "Auto fish with toggleable XP Mode.", Category.PLAYER);
        this.addSetting(mode);
        this.addSetting(recastDelay);
        this.addSetting(xpTimeout);
    }

    @Override
    public void onEnable() {
        idleTicksCounter = 0;
        isQueuedToRecast = false;
        recastTimer = 0;
        xpDelayTimer = 0;

        // Snapshot current XP to avoid logic errors when first enabled
        if (mc.player != null) {
            lastXpProgress = mc.player.experienceProgress;
            lastXpLevel = mc.player.experienceLevel;
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;
        var player = mc.player;

        // Watchdog (AFK Protection)
        if (player.fishing == null && !isQueuedToRecast) {
            idleTicksCounter++;
            if (idleTicksCounter >= Constants.AUTOFISH_WATCHDOG_TIMEOUT_TICKS) {
                useRod();
                idleTicksCounter = 0;
                return;
            }
        } else {
            idleTicksCounter = 0;
        }

        // Handle recast logic
        if (isQueuedToRecast) {
            handleRecastLogic();
            return;
        }

        // Detect bite
        if (player.fishing != null) {
            checkForBite();
        }
    }

    private void checkForBite() {
        var bobber = mc.player.fishing;
        if (bobber.tickCount < Constants.AUTOFISH_MIN_BOBBER_AGE) return;

        if (bobber.tickCount >= Constants.AUTOFISH_MAX_WAIT_TICKS) {
            useRod();
            prepareRecast();
            return;
        }

        Vec3 motion = bobber.getDeltaMovement();
        boolean inWater = !mc.level.getFluidState(bobber.blockPosition()).isEmpty();

        // Detect bite (Motion Y < threshold)
        if (motion.y < Constants.AUTOFISH_BITE_MOTION_THRESHOLD && inWater) {
            useRod();
            prepareRecast();

            // Snapshot XP
            lastXpProgress = mc.player.experienceProgress;
            lastXpLevel = mc.player.experienceLevel;
            xpDelayTimer = 0;
        }
    }

    private void prepareRecast() {
        isQueuedToRecast = true;
        recastTimer = recastDelay.getInt();
        xpTimeoutCounter = xpTimeout.getInt();
        idleTicksCounter = 0;
    }

    private void handleRecastLogic() {
        FishMode currentMode = mode.getValue();

        if (currentMode == FishMode.XP) {
            // XP Mode: Wait for XP gain before recasting
            if (xpDelayTimer > 0) {
                xpDelayTimer--;
                if (xpDelayTimer <= 0) {
                    finishRecast();
                }
                return;
            }

            var player = mc.player;
            boolean xpChanged = (player.experienceProgress != lastXpProgress) || (player.experienceLevel != lastXpLevel);

            if (xpChanged) {
                lastXpProgress = player.experienceProgress;
                lastXpLevel = player.experienceLevel;
                xpDelayTimer = Constants.AUTOFISH_XP_DELAY_TICKS;
            } else if (xpTimeoutCounter-- <= 0) {
                finishRecast(); // Timeout -> Force recast
            }
        } else {
            // Normal Mode: Use fixed delay
            if (recastTimer-- <= 0) {
                finishRecast();
            }
        }
    }

    private void finishRecast() {
        useRod();
        isQueuedToRecast = false;
        idleTicksCounter = 0;
    }

    private void useRod() {
        if (mc.gameMode == null || mc.player == null) return;

        InteractionHand hand = null;
        if (mc.player.getMainHandItem().getItem() instanceof FishingRodItem) {
            hand = InteractionHand.MAIN_HAND;
        } else if (mc.player.getOffhandItem().getItem() instanceof FishingRodItem) {
            hand = InteractionHand.OFF_HAND;
        }

        if (hand != null) {
            mc.gameMode.useItem(mc.player, hand);
        }
    }
}