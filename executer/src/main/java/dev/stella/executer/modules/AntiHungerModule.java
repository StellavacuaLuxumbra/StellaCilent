package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.setting.*;

public class AntiHungerModule extends ModBase {
    public AntiHungerModule() {
        super("AntiHunger", Category.PLAYER, 30);
        add(new BooleanSetting("NoExhaust", true));
        add(new BooleanSetting("NoSaturation", false));
    }
}
