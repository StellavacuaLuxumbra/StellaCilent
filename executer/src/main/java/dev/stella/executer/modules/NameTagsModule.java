package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.setting.*;

public class NameTagsModule extends ModBase {
    public NameTagsModule() {
        super("NameTags", Category.RENDER, 24);
        add(new SliderSetting("Scale", 1.5, 0.5, 5.0, 0.1, "x"));
        add(new BooleanSetting("Health", true));
        add(new BooleanSetting("Armor", true));
        add(new BooleanSetting("Distance", false));
        add(new BooleanSetting("Background", true));
    }
}
