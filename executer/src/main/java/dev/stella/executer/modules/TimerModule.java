package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.setting.SliderSetting;

public class TimerModule extends ModBase {
    public TimerModule() {
        super("Timer", Category.PLAYER, 21);
        add(new SliderSetting("Multiplier", 2.0, 0.1, 10.0, 0.1, "x"));
    }
}
