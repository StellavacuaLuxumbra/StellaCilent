package dev.stella.executer.gui.setting;

public class EnumSetting<T extends Enum<T>> extends Setting {
    private T value;
    private T defaultValue;

    public EnumSetting(String name, T defaultValue) {
        super(name);
        this.value = defaultValue;
        this.defaultValue = defaultValue;
    }

    public T getValue() { return value; }
    public void setValue(T v) { this.value = v; }
    public T getDefaultValue() { return defaultValue; }
    public T[] getValues() { return defaultValue.getDeclaringClass().getEnumConstants(); }
    public int getOrdinal() { return value.ordinal(); }

    public void increment() {
        T[] values = getValues();
        this.value = values[(value.ordinal() + 1) % values.length];
    }

    public void decrement() {
        T[] values = getValues();
        this.value = values[(value.ordinal() - 1 + values.length) % values.length];
    }
}
