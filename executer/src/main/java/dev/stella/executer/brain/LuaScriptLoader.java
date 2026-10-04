package dev.stella.executer.brain;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Load plain-text Lua scripts from resources.
 * No encryption, no obfuscation - scripts ship as readable .lua assets.
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

    /**
     * Load all Lua scripts and initialize LuaScriptHost
     */
    public static void loadAll() {
        LuaScriptHost.initialize();

        for (String moduleName : LUA_MODULES) {
            String luaCode = loadLua(moduleName);
            if (luaCode != null) {
                LuaScriptHost.loadScript(moduleName, luaCode);
            }
        }
    }

    /**
     * Load a single Lua script from resources
     */
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
