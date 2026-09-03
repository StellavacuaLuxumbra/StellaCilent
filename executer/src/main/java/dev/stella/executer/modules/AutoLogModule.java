package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.setting.*;

public class AutoLogModule extends ModBase {
    public AutoLogModule() {
        super("AutoLog", Category.MISC, 28);
        add(new SliderSetting("Health", 4.0, 1.0, 20.0, 0.5, ""));
        add(new BooleanSetting("Totems", true));
        add(new SliderSetting("MinTotems", 1.0, 0.0, 10.0, 1.0, ""));
    }
}
