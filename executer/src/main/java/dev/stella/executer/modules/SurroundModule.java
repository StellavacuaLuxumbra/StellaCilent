package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.setting.*;

public class SurroundModule extends ModBase {
    public SurroundModule() {
        super("Surround", Category.COMBAT, 34);
        add(new SliderSetting("Delay", 50.0, 0.0, 200.0, 10.0, "ms"));
        add(new EnumSetting<>("Mode", Mode.OBSIDIAN));
        add(new BooleanSetting("Expand", true));
        add(new BooleanSetting("Swing", true));
        add(new BooleanSetting("Dynamic", false));
    }

    public enum Mode { OBSIDIAN, END_STONE, BOTH }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}
}
