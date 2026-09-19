package dev.stella.executer.brain;

import dev.stella.executer.protection.RaspProtection;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;

/**
 * Load Lua scripts from resources, support XOR encryption for anti-RE.
 * Scripts are encrypted at build time, decrypted at runtime.
 * RASP protection integrated.
 */
public final class LuaScriptLoader {
    private static final String LUA_DIR = "/assets/stella/lua/";
    private static final byte[] XOR_KEY = {(byte)0x53, (byte)0x74, (byte)0x65, (byte)0x6C, (byte)0x6C, (byte)0x61, (byte)0x43, (byte)0x6C};
    
    private static final String[] LUA_MODULES = {
        "KillAura",
        "CrystalAura",
        "Fullbright",
        "Scaffold",
        "AutoTotem",
        "ChestStealer",
        "ElytraFly"
    };
    
    private LuaScriptLoader() {}
    
    /**
     * Load all Lua scripts and initialize LuaScriptHost
     */
    public static void loadAll() {
        // RASP保护：环境检测
        RaspProtection.initialize();
        if (RaspProtection.isTampered()) {
            System.err.println("[LuaScriptLoader] RASP: Environment tampered, aborting");
            return;
        }
        
        LuaScriptHost.initialize();
        
        for (String moduleName : LUA_MODULES) {
            String luaCode = loadLua(moduleName);
            if (luaCode != null) {
                LuaScriptHost.loadScript(moduleName, luaCode);
            }
        }
    }
    
    /**
     * Load a single Lua module from resources
     */
    public static String loadLua(String moduleName) {
        try {
            // RASP检查
            if (RaspProtection.isTampered()) {
                return null;
            }
            
            InputStream is = LuaScriptLoader.class.getResourceAsStream(LUA_DIR + moduleName + ".luenc");
            if (is != null) {
                byte[] encrypted = is.readAllBytes();
                is.close();
                return xorDecrypt(encrypted);
            }
            
            is = LuaScriptLoader.class.getResourceAsStream(LUA_DIR + moduleName + ".lua");
            if (is != null) {
                String code = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                is.close();
                return code;
            }
            
            System.err.println("[LuaScriptLoader] Script not found: " + moduleName);
            return null;
        } catch (Exception e) {
            System.err.println("[LuaScriptLoader] Failed to load " + moduleName + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * XOR decrypt (same as C++ shared/encrypt.cpp)
     */
    public static String xorDecrypt(byte[] data) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ XOR_KEY[i % XOR_KEY.length]);
        }
        return new String(result, StandardCharsets.UTF_8);
    }
    
    /**
     * XOR encrypt for build-time use
     */
    public static byte[] xorEncrypt(String plaintext) {
        byte[] data = plaintext.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ XOR_KEY[i % XOR_KEY.length]);
        }
        return result;
    }
    
    /**
     * Main method for building encrypted Lua scripts
     * Usage: java -cp ... dev.stella.executer.brain.LuaScriptLoader <input_dir> <output_dir>
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: LuaScriptLoader <input_lua_dir> <output_luenc_dir>");
            System.exit(1);
        }
        
        Path inputDir = Path.of(args[0]);
        Path outputDir = Path.of(args[1]);
        
        Files.createDirectories(outputDir);
        
        for (String moduleName : LUA_MODULES) {
            Path inputFile = inputDir.resolve(moduleName + ".lua");
            Path outputFile = outputDir.resolve(moduleName + ".luenc");
            
            if (Files.exists(inputFile)) {
                String luaCode = Files.readString(inputFile);
                byte[] encrypted = xorEncrypt(luaCode);
                Files.write(outputFile, encrypted);
                System.out.println("Encrypted: " + moduleName + ".lua -> " + moduleName + ".luenc");
            } else {
                System.err.println("Warning: " + inputFile + " not found, skipping");
            }
        }
    }
}
