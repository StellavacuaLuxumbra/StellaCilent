package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.setting.*;

public class SelfTrapModule extends ModBase {
    public SelfTrapModule() {
        super("SelfTrap", Category.COMBAT, 15);
        add(new SliderSetting("Delay", 50.0, 0.0, 200.0, 10.0, "ms"));
        add(new EnumSetting<>("Mode", Mode.HEAD));
        add(new BooleanSetting("AntiOverlap", true));
        add(new BooleanSetting("Swing", true));
    }

    public enum Mode { HEAD, FULL }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}
}
