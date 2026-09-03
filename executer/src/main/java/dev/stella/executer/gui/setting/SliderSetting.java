package dev.stella.executer.gui.setting;

public class SliderSetting extends Setting {
    private double value;
    private double defaultValue;
    private double minValue;
    private double maxValue;
    private double increment;
    private String suffix;

    public SliderSetting(String name, double value, double min, double max, double increment, String suffix) {
        super(name);
        this.value = value;
        this.defaultValue = value;
        this.minValue = min;
        this.maxValue = max;
        this.increment = increment;
        this.suffix = suffix;
    }

    public double getValue() { return value; }
    public void setValue(double v) { this.value = Math.round(v / increment) * increment; }
    public double getMinValue() { return minValue; }
    public double getMaxValue() { return maxValue; }
    public double getIncrement() { return increment; }
    public double getDefaultValue() { return defaultValue; }
    public String getSuffix() { return suffix; }

    public String getDisplayValue() {
        if (increment >= 1) return String.format("%.0f%s", value, suffix);
        if (increment >= 0.1) return String.format("%.1f%s", value, suffix);
        return String.format("%.2f%s", value, suffix);
    }

    public float getProgress() {
        return (float) ((value - minValue) / (maxValue - minValue));
    }
}
