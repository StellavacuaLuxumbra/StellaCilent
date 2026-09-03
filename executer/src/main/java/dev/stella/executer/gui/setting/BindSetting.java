package dev.stella.executer.gui.setting;

import org.lwjgl.glfw.GLFW;

public class BindSetting extends Setting {
    private int value;
    private int defaultValue;
    private boolean holding;

    public BindSetting(String name, int defaultValue) {
        super(name);
        this.value = defaultValue;
        this.defaultValue = defaultValue;
        this.holding = false;
    }

    public int getValue() { return value; }
    public void setValue(int v) { this.value = v; }
    public int getDefaultValue() { return defaultValue; }
    public boolean isHolding() { return holding; }
    public void setHolding(boolean h) { this.holding = h; }

    public String getKeyName() {
        if (value == -1) return "NONE";
        return GLFW.glfwGetKeyName(value, 0);
    }

    public boolean isPressed(int key) {
        return value == key;
    }
}
