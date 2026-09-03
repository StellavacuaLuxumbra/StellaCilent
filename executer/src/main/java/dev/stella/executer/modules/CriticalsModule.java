package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.setting.*;

public class CriticalsModule extends ModBase {
    public CriticalsModule() {
        super("Criticals", Category.COMBAT, 23);
        add(new EnumSetting<>("Mode", Mode.MINI_JUMP));
    }

    public enum Mode { MINI_JUMP, PACKET, JUMP }
}
