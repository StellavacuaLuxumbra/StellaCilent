package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.setting.*;

public class FastBreakModule extends ModBase {
    public FastBreakModule() {
        super("FastBreak", Category.PLAYER, 18);
        add(new SliderSetting("Multiplier", 3.5, 1.0, 10.0, 0.5, "x"));
    }
}
