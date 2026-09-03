package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.Module;

public abstract class ModBase extends Module {
    public ModBase(String name, Category category, int moduleId) {
        super(name, category, moduleId);
    }
}
