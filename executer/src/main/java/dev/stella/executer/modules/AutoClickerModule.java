package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.setting.*;

public class AutoClickerModule extends ModBase {
    public AutoClickerModule() {
        super("AutoClicker", Category.COMBAT, 33);
        add(new SliderSetting("MinCPS", 10.0, 1.0, 20.0, 1.0, ""));
        add(new SliderSetting("MaxCPS", 14.0, 1.0, 20.0, 1.0, ""));
        add(new BooleanSetting("Players", true));
        add(new BooleanSetting("Mobs", true));
        add(new BooleanSetting("Animals", false));
    }
}
