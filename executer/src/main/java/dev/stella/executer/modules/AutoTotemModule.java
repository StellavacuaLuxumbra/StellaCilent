package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.setting.*;

public class AutoTotemModule extends ModBase {
    public AutoTotemModule() {
        super("AutoTotem", Category.PLAYER, 21);
        add(new SliderSetting("Health", 8.0, 1.0, 20.0, 0.5, ""));
        add(new BooleanSetting("Soft", false));
    }
}
