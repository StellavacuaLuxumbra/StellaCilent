package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.setting.*;

public class AutoArmorModule extends ModBase {
    public AutoArmorModule() {
        super("AutoArmor", Category.PLAYER, 17);
        add(new SliderSetting("Delay", 50.0, 0.0, 200.0, 10.0, "ms"));
    }
}
