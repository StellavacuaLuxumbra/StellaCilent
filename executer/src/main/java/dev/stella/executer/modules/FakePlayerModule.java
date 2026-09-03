package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.setting.*;

public class FakePlayerModule extends ModBase {
    public FakePlayerModule() {
        super("FakePlayer", Category.MISC, 27);
        add(new SliderSetting("Health", 20.0, 1.0, 40.0, 0.5, ""));
        add(new BooleanSetting("CopyInventory", false));
    }
}
