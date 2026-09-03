package dev.stella.executer.gui;

import dev.stella.executer.gui.setting.Setting;

import java.util.ArrayList;
import java.util.List;

public class Module {
    private final String name;
    private final Category category;
    private final int moduleId;
    private boolean enabled;
    private final List<Setting> settings = new ArrayList<>();

    public Module(String name, Category category, int moduleId) {
        this.name = name;
        this.category = category;
        this.moduleId = moduleId;
        this.enabled = false;
    }

    public String getName() { return name; }
    public Category getCategory() { return category; }
    public int getModuleId() { return moduleId; }
    public boolean isEnabled() { return enabled; }
    public List<Setting> getSettings() { return settings; }

    public Module toggle() {
        enabled = !enabled;
        if (enabled) onEnable();
        else onDisable();
        return this;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) onEnable();
        else onDisable();
    }

    public void onEnable() {}
    public void onDisable() {}

    public <T extends Setting> T add(T setting) {
        settings.add(setting);
        return setting;
    }

    public Setting getSetting(String name) {
        for (Setting s : settings) {
            if (s.getName().equalsIgnoreCase(name)) return s;
        }
        return null;
    }
}
