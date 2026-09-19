package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.setting.*;

public class NoSlowDownModule extends ModBase {
    public NoSlowDownModule() {
        super("NoSlowDown", Category.MOVEMENT, 23);
        add(new BooleanSetting("Items", true));
        add(new BooleanSetting("Soulsand", true));
        add(new BooleanSetting("Webs", true));
        add(new BooleanSetting("Honey", true));
        add(new BooleanSetting("Shield", true));
    }
}
