package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.setting.*;

public class AimAssistModule extends ModBase {
    public AimAssistModule() {
        super("AimAssist", Category.COMBAT, 34);
        add(new SliderSetting("FOV", 90.0, 30.0, 180.0, 5.0, ""));
        add(new SliderSetting("Speed", 5.0, 1.0, 15.0, 0.5, ""));
        add(new BooleanSetting("Players", true));
        add(new BooleanSetting("Mobs", false));
        add(new BooleanSetting("Smooth", true));
    }
}
