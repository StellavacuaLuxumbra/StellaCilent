package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.setting.*;

public class AutoLogModule extends ModBase {
    public AutoLogModule() {
        super("AutoLog", Category.PLAYER, 19);
        add(new SliderSetting("Health", 4.0, 1.0, 20.0, 0.5, ""));
    }
}
