package dev.stella.executer.gui.setting;

import java.util.function.BooleanSupplier;

public abstract class Setting {
    protected String name;
    protected BooleanSupplier visibility;

    public Setting(String name) {
        this.name = name;
        this.visibility = null;
    }

    public String getName() { return name; }

    public boolean isVisible() {
        return visibility == null || visibility.getAsBoolean();
    }

    public Setting setVisibility(BooleanSupplier v) {
        this.visibility = v;
        return this;
    }
}
