package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.setting.*;

public class ReachModule extends ModBase {
    public ReachModule() {
        super("Reach", Category.COMBAT, 35);
        add(new SliderSetting("Range", 4.5, 3.0, 6.0, 0.1, ""));
    }
}
