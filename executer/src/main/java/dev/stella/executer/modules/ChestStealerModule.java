package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.setting.*;

public class ChestStealerModule extends ModBase {
    public ChestStealerModule() {
        super("ChestStealer", Category.PLAYER, 22);
        add(new SliderSetting("Delay", 100.0, 0.0, 500.0, 10.0, "ms"));
        add(new BooleanSetting("CloseAfter", true));
    }
}
