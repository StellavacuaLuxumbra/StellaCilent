package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.setting.*;

public class CrosshairModule extends ModBase {
    public CrosshairModule() {
        super("Crosshair", Category.RENDER, 28);
        add(new ColorSetting("Color", 0xFFFFFF));
        add(new SliderSetting("Size", 4.0, 1.0, 10.0, 0.5, ""));
        add(new SliderSetting("Gap", 2.0, 0.0, 5.0, 0.5, ""));
        add(new SliderSetting("Thickness", 1.0, 0.5, 3.0, 0.5, ""));
        add(new BooleanSetting("Dynamic", true));
    }
}
