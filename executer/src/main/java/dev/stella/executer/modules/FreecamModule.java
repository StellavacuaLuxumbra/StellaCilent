package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.setting.*;

public class FreecamModule extends ModBase {
    public FreecamModule() {
        super("Freecam", Category.PLAYER, 29);
        add(new SliderSetting("Speed", 2.0, 0.5, 10.0, 0.1, "x"));
        add(new BooleanSetting("DisableOnDamage", true));
    }
}
