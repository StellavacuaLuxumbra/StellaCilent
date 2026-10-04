package dev.stella.mod.modules.settings.impl;

import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * ModuleListSetting - 模块列表多选设置
 * 用于选择多个模块
 */
public class ModuleListSetting extends Setting {
    private final List<Module> defaultValue;
    private List<Module> value;

    public ModuleListSetting(String name, List<Module> defaultValue) {
        super(name);
        this.value = new ArrayList<>(defaultValue);
        this.defaultValue = new ArrayList<>(defaultValue);
    }

    public ModuleListSetting(String name, List<Module> defaultValue, BooleanSupplier visibilityIn) {
        super(name, visibilityIn);
        this.value = new ArrayList<>(defaultValue);
        this.defaultValue = new ArrayList<>(defaultValue);
    }

    public List<Module> getValue() {
        return this.value;
    }

    public void setValue(List<Module> value) {
        this.value = value;
    }

    public List<Module> getDefaultValue() {
        return this.defaultValue;
    }

    public void resetValue() {
        this.value = new ArrayList<>(this.defaultValue);
    }

    /**
     * 检查是否包含指定模块
     */
    public boolean contains(Module module) {
        return this.value.contains(module);
    }

    /**
     * 添加模块
     */
    public void add(Module module) {
        if (!this.value.contains(module)) {
            this.value.add(module);
        }
    }

    /**
     * 移除模块
     */
    public void remove(Module module) {
        this.value.remove(module);
    }

    /**
     * 切换模块选择状态
     */
    public void toggle(Module module) {
        if (this.value.contains(module)) {
            this.value.remove(module);
        } else {
            this.value.add(module);
        }
    }
}
