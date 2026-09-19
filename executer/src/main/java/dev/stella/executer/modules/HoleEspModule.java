package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.setting.*;

public class HoleEspModule extends ModBase {
    public HoleEspModule() {
        super("HoleESP", Category.RENDER, 26);
        add(new SliderSetting("Range", 16.0, 1.0, 64.0, 1.0, "blocks"));
        add(new ColorSetting("SafeColor", 0x00FF00));
        add(new ColorSetting("UnsafeColor", 0xFFFF00));
        add(new ColorSetting("MixedColor", 0xFF8800));
        add(new BooleanSetting("ShowUnbreakable", true));
    }
}
