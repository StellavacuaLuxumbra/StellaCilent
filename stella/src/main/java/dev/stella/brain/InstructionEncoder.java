package dev.stella.brain;

import dev.stella.stella;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.Setting;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import dev.stella.mod.modules.settings.impl.EnumSetting;
import dev.stella.mod.modules.settings.impl.SliderSetting;
import dev.stella.mod.modules.settings.impl.StringSetting;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * 把 Lua 的 指令表 应用到 SunCat 模块系统（Stella 魔改核心桥接）。
 *
 * 指令表 格式:
 *   指令表["Aura"] = { enabled = true, range = 4.0, mode = "Single" }
 */
public final class InstructionEncoder {

    private InstructionEncoder() {}

    public static void execute(LuaTable commands) {
        if (commands == null || stella.MODULE == null) return;

        for (LuaValue key : commands.keys()) {
            LuaValue value = commands.get(key);
            if (!value.istable()) continue;

            Module module = findModule(key.tojstring());
            if (module == null) continue;

            apply(module, value.checktable());
        }
    }

    private static Module findModule(String name) {
        for (Module m : stella.MODULE.getModules()) {
            if (m.getName().equalsIgnoreCase(name)) return m;
        }
        return null;
    }

    private static void apply(Module module, LuaTable tbl) {
        LuaValue enabled = tbl.get("enabled");
        if (!enabled.isnil()) {
            boolean want = enabled.toboolean();
            if (want && module.isOff()) module.enable();
            else if (!want && module.isOn()) module.disable();
        }

        for (LuaValue key : tbl.keys()) {
            String settingName = key.tojstring();
            if (settingName.equals("enabled")) continue;

            Setting setting = findSetting(module, settingName);
            if (setting == null) continue;

            LuaValue v = tbl.get(key);
            try {
                if (setting instanceof SliderSetting slider && v.isnumber()) {
                    slider.setValue(v.todouble());
                } else if (setting instanceof BooleanSetting bool && v.isboolean()) {
                    bool.setValue(v.toboolean());
                } else if (setting instanceof StringSetting str && v.isstring()) {
                    str.setValue(v.tojstring());
                } else if (setting instanceof EnumSetting<?> en && v.isstring()) {
                    en.setEnumValue(v.tojstring());
                }
            } catch (Exception ignored) {}
        }
    }

    private static Setting findSetting(Module module, String name) {
        for (Setting s : module.getSettings()) {
            if (s.getName().equalsIgnoreCase(name)) return s;
        }
        return null;
    }
}
