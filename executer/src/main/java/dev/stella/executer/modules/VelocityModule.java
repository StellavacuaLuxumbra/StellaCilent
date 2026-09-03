package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.setting.*;

public class VelocityModule extends ModBase {
    public VelocityModule() {
        super("Velocity", Category.COMBAT, 22);
        add(new SliderSetting("Horizontal", 0.0, 0.0, 100.0, 1.0, "%"));
        add(new SliderSetting("Vertical", 0.0, 0.0, 100.0, 1.0, "%"));
        add(new EnumSetting<>("Mode", Mode.NORMAL));
    }

    public enum Mode { NORMAL, PACKET, BOUNCE }
}
