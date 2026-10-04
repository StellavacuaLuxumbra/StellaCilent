package dev.stella.brain;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Loads plain-text Lua scripts from resources (assets/stella/lua/).
 * 不加密、不混淆 —— 脚本以可读源码随 jar 发布。
 */
public final class LuaScriptLoader {
    private static final String LUA_DIR = "/assets/stella/lua/";

    private static final String[] LUA_MODULES = {
        "kill_aura",
        "crystal_aura",
        "fly",
        "hole_esp",
        "selftrap",
        "speed",
        "surround"
    };

    private LuaScriptLoader() {}

    public static void loadAll() {
        LuaScriptHost.initialize();
        for (String moduleName : LUA_MODULES) {
            String luaCode = loadLua(moduleName);
            if (luaCode != null) {
                LuaScriptHost.loadScript(moduleName, luaCode);
            }
        }
    }

    public static String loadLua(String moduleName) {
        try (InputStream is = LuaScriptLoader.class.getResourceAsStream(LUA_DIR + moduleName + ".lua")) {
            if (is == null) {
                System.err.println("[LuaScriptLoader] Script not found: " + moduleName);
                return null;
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[LuaScriptLoader] Failed to load " + moduleName + ": " + e.getMessage());
            return null;
        }
    }
}
