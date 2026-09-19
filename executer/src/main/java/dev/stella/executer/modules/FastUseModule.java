package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.setting.*;

public class FastUseModule extends ModBase {
    public FastUseModule() {
        super("FastUse", Category.PLAYER, 31);
        add(new SliderSetting("Multiplier", 2.0, 1.0, 10.0, 0.5, "x"));
        add(new BooleanSetting("Potions", true));
    }
}
