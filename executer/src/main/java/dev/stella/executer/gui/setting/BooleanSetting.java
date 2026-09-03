package dev.stella.executer.gui.setting;

public class BooleanSetting extends Setting {
    private boolean value;
    private boolean defaultValue;
    private BooleanSetting parent;
    private boolean open;

    public BooleanSetting(String name, boolean defaultValue) {
        super(name);
        this.value = defaultValue;
        this.defaultValue = defaultValue;
    }

    public boolean getValue() { return value; }
    public void setValue(boolean v) { this.value = v; }
    public void toggle() { this.value = !this.value; }
    public boolean getDefaultValue() { return defaultValue; }

    public BooleanSetting setParent(BooleanSetting parent) {
        this.parent = parent;
        return this;
    }

    public BooleanSetting getParent() { return parent; }
    public boolean hasParent() { return parent != null; }
    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }
}
