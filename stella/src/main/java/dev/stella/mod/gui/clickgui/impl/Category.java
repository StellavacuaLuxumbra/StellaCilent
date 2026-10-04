package dev.stella.mod.gui.clickgui.impl;

enum ScrollMode {
    OLD,
    NEW
}

class ClickGuiConfig {
    private static ClickGuiConfig instance;
    private ScrollMode scrollMode = ScrollMode.NEW;
    private float catHeight = 200f;
    private float moduleHeight = 18f;
    private float moduleWidth = 100f;

    public static ClickGuiConfig getInstance() {
        if (instance == null) {
            instance = new ClickGuiConfig();
        }
        return instance;
    }

    public ScrollMode getScrollMode() { return scrollMode; }
    public void setScrollMode(ScrollMode mode) { this.scrollMode = mode; }

    public float getCatHeight() { return catHeight; }
    public void setCatHeight(float height) { this.catHeight = height; }

    public float getModuleHeight() { return moduleHeight; }
    public void setModuleHeight(float height) { this.moduleHeight = height; }

    public float getModuleWidth() { return moduleWidth; }
    public void setModuleWidth(float width) { this.moduleWidth = width; }
}