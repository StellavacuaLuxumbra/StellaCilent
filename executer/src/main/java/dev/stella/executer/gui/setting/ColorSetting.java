package dev.stella.executer.gui.setting;

import dev.stella.executer.gui.render.ColorUtil;

public class ColorSetting extends Setting {
    private int value;
    private int defaultValue;
    private boolean rainbow;

    public ColorSetting(String name, int defaultValue) {
        super(name);
        this.value = defaultValue;
        this.defaultValue = defaultValue;
        this.rainbow = false;
    }

    public int getColor() {
        if (rainbow) {
            return ColorUtil.rainbow(name.hashCode() * 50, 210);
        }
        return value;
    }

    public int getValue() { return value; }
    public void setValue(int v) { this.value = v; }
    public int getDefaultValue() { return defaultValue; }
    public boolean isRainbow() { return rainbow; }
    public void setRainbow(boolean r) { this.rainbow = r; }

    public int getRed() { return (getColor() >> 16) & 0xFF; }
    public int getGreen() { return (getColor() >> 8) & 0xFF; }
    public int getBlue() { return getColor() & 0xFF; }
    public int getAlpha() { return (getColor() >> 24) & 0xFF; }
}
