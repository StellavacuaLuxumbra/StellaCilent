package dev.stella.executer.brain;

import dev.stella.executer.gui.ClickGuiScreen;
import dev.stella.executer.gui.Module;
import dev.stella.executer.gui.ModuleManager;
import dev.stella.executer.gui.setting.BooleanSetting;
import dev.stella.executer.gui.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Java replacement for C++ InstructionEncoder.
 * Reads 指令表 from Lua and applies instructions to Java modules + game.
 */
public final class InstructionEncoder {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private InstructionEncoder() {}

    public static void execute(LuaTable 指令表) {
        if (mc.player == null || mc.world == null) return;

        LuaValue[] keys = 指令表.keys();
        for (LuaValue key : keys) {
            LuaValue value = 指令表.get(key);
            if (value.istable()) {
                LuaTable tbl = value.checktable();
                String moduleName = key.tojstring();
                processModule(moduleName, tbl);
            }
        }
    }

    private static void processModule(String moduleName, LuaTable tbl) {
        switch (moduleName) {
            case "ClickGUI" -> {
                if (tbl.get("open").optboolean(false)) {
                    mc.setScreen(ClickGuiScreen.getInstance());
                    tbl.set("open", LuaValue.valueOf(false));
                }
            }
            default -> applyLuaConfigToModule(moduleName, tbl);
        }
    }

    private static void applyLuaConfigToModule(String moduleName, LuaTable tbl) {
        Module m = ModuleManager.getInstance().getModule(moduleName);
        if (m == null) return;

        LuaValue enabledVal = tbl.get("enabled");
        if (!enabledVal.isnil()) {
            boolean enabled = enabledVal.toboolean();
            if (m.isEnabled() != enabled) {
                m.setEnabled(enabled);
            }
        }

        LuaValue[] keys = tbl.keys();
        for (LuaValue k : keys) {
            String key = k.tojstring();
            if (key.equals("enabled")) continue;

            LuaValue v = tbl.get(k);
            var setting = m.getSetting(key);
            if (setting == null) continue;

            if (setting instanceof SliderSetting slider) {
                if (v.isnumber()) slider.setValue(v.todouble());
            } else if (setting instanceof BooleanSetting bool) {
                if (v.isboolean()) bool.setValue(v.toboolean());
            }
        }
    }
}
