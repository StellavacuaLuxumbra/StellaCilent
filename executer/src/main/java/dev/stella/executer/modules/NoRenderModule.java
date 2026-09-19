package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.setting.*;

public class NoRenderModule extends ModBase {
    public NoRenderModule() {
        super("NoRender", Category.RENDER, 27);
        add(new BooleanSetting("Pumpkins", true));
        add(new BooleanSetting("Fire", true));
        add(new BooleanSetting("Fog", true));
        add(new BooleanSetting("Totems", false));
        add(new BooleanSetting("BossBar", false));
        add(new BooleanSetting("Scoreboard", false));
        add(new BooleanSetting("Armour", false));
    }
}
