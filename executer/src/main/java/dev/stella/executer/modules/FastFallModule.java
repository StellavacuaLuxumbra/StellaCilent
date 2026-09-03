package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.setting.SliderSetting;

public class FastFallModule extends ModBase {
    public FastFallModule() {
        super("FastFall", Category.PLAYER, 21);
        add(new SliderSetting("Delay", 0.0, 0.0, 4.0, 1.0, "ticks"));
    }
}
