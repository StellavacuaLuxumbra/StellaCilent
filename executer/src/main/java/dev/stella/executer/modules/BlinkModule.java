package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.setting.*;

public class BlinkModule extends ModBase {
    public BlinkModule() {
        super("Blink", Category.PLAYER, 29);
        add(new SliderSetting("PulseDelay", 500.0, 50.0, 5000.0, 50.0, "ms"));
        add(new BooleanSetting("RenderTrails", true));
    }
}
