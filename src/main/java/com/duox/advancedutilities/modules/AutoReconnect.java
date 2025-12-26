package com.duox.advancedutilities.modules;

import com.duox.advancedutilities.system.Category;
import com.duox.advancedutilities.system.Module;
import com.duox.advancedutilities.system.settings.NumberSetting;

public class AutoReconnect extends Module {

    // Default 5 seconds, range 0-60
    public final NumberSetting waitTime = new NumberSetting("Wait Time", 5, 0, 60, 0.5);

    public AutoReconnect() {
        super("AutoReconnect", "Automatically reconnects after being kicked.", Category.MISC);
        addSetting(waitTime);
    }

    // Returns the wait time in ticks (20 ticks = 1 second)
    public int getWaitTicks() {
        return (int) (waitTime.getValue() * 20);
    }

    // This module doesn't use onTick() because it operates on the DisconnectedScreen
}