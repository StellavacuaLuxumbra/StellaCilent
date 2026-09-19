package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.setting.*;

public class ScaffoldModule extends ModBase {
    public ScaffoldModule() {
        super("Scaffold", Category.MOVEMENT, 36);
        add(new BooleanSetting("Rotate", true));
        add(new BooleanSetting("Swing", true));
        add(new BooleanSetting("Tower", false));
    }
}
