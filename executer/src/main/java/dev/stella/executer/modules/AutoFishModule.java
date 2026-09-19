package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.setting.*;

public class AutoFishModule extends ModBase {
    public AutoFishModule() {
        super("AutoFish", Category.PLAYER, 32);
        add(new SliderSetting("Delay", 100.0, 0.0, 500.0, 10.0, "ms"));
        add(new BooleanSetting("AutoCast", true));
        add(new BooleanSetting("RodSwitch", true));
    }
}
