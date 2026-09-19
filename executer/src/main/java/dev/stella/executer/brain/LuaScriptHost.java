package dev.stella.executer.brain;

import org.luaj.vm2.*;
import org.luaj.vm2.lib.*;
import org.luaj.vm2.lib.jse.JsePlatform;
import dev.stella.executer.gui.Module;
import dev.stella.executer.gui.ModuleManager;
import dev.stella.executer.modules.ModuleTicker;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LuaJ VM management - one global table for world state, per-tick execution.
 * Replaces C++ brain/lua_host.cpp
 */
public final class LuaScriptHost {
    private static LuaTable worldTable;
    private static final Map<String, LuaValue> moduleStates = new ConcurrentHashMap<>();
    private static final Map<String, LuaTable> moduleTables = new ConcurrentHashMap<>();
    private static boolean initialized = false;
    private static Globals globals;

    private LuaScriptHost() {}

    public static void initialize() {
        if (initialized) return;
        
        globals = JsePlatform.standardGlobals();
        
        worldTable = new LuaTable();
        globals.set("world", worldTable);
        
        globals.set("指令表", new LuaTable());
        
        globals.set("dump_table", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue table, LuaValue indent) {
                StringBuilder sb = new StringBuilder();
                dumpTable(table.checktable(), sb, indent.isnil() ? "" : indent.tojstring());
                return LuaValue.valueOf(sb.toString());
            }
        });
        
        initialized = true;
    }

    private static void dumpTable(LuaTable table, StringBuilder sb, String indent) {
        sb.append("{\n");
        LuaValue[] keys = table.keys();
        for (LuaValue key : keys) {
            LuaValue value = table.get(key);
            sb.append(indent).append("  [").append(key).append("] = ");
            if (value.istable()) {
                dumpTable(value.checktable(), sb, indent + "  ");
            } else {
                sb.append(value).append(",\n");
            }
        }
        sb.append(indent).append("}\n");
    }

    public static void loadScript(String moduleName, String luaCode) {
        if (!initialized) initialize();
        
        try {
            LuaValue chunk = globals.load(luaCode, moduleName);
            chunk.call();
            
            LuaValue moduleTable = globals.get(moduleName);
            if (moduleTable != null && moduleTable.istable()) {
                moduleTables.put(moduleName, moduleTable.checktable());
                moduleStates.put(moduleName, moduleTable);
            }
        } catch (Exception e) {
            System.err.println("[LuaScriptHost] Failed to load " + moduleName + ": " + e.getMessage());
        }
    }

    public static void updateWorldTable(int worldTime, float playerX, float playerY, float playerZ,
                                         float playerMotionX, float playerMotionY, float playerMotionZ,
                                         boolean isSwimming, boolean isSprinting, boolean isFalling,
                                         float health, float hunger, float airSupply, float maxAirSupply,
                                         int dimension, float temperature, float humidity,
                                         float blockLight, float skyLight, float moonPhase,
                                         float lightLevel, int biomeId, float timeOfDay,
                                         boolean isSubmerged, boolean isBurn, boolean isFrozen) {
        if (!initialized || worldTable == null) return;
        
        worldTable.set("worldTime", LuaValue.valueOf(worldTime));
        worldTable.set("playerX", LuaValue.valueOf(playerX));
        worldTable.set("playerY", LuaValue.valueOf(playerY));
        worldTable.set("playerZ", LuaValue.valueOf(playerZ));
        worldTable.set("playerMotionX", LuaValue.valueOf(playerMotionX));
        worldTable.set("playerMotionY", LuaValue.valueOf(playerMotionY));
        worldTable.set("playerMotionZ", LuaValue.valueOf(playerMotionZ));
        worldTable.set("isSwimming", LuaValue.valueOf(isSwimming));
        worldTable.set("isSprinting", LuaValue.valueOf(isSprinting));
        worldTable.set("isFalling", LuaValue.valueOf(isFalling));
        worldTable.set("health", LuaValue.valueOf(health));
        worldTable.set("hunger", LuaValue.valueOf(hunger));
        worldTable.set("airSupply", LuaValue.valueOf(airSupply));
        worldTable.set("maxAirSupply", LuaValue.valueOf(maxAirSupply));
        worldTable.set("dimension", LuaValue.valueOf(dimension));
        worldTable.set("temperature", LuaValue.valueOf(temperature));
        worldTable.set("humidity", LuaValue.valueOf(humidity));
        worldTable.set("blockLight", LuaValue.valueOf(blockLight));
        worldTable.set("skyLight", LuaValue.valueOf(skyLight));
        worldTable.set("moonPhase", LuaValue.valueOf(moonPhase));
        worldTable.set("lightLevel", LuaValue.valueOf(lightLevel));
        worldTable.set("biomeId", LuaValue.valueOf(biomeId));
        worldTable.set("timeOfDay", LuaValue.valueOf(timeOfDay));
        worldTable.set("isSubmerged", LuaValue.valueOf(isSubmerged));
        worldTable.set("isBurn", LuaValue.valueOf(isBurn));
        worldTable.set("isFrozen", LuaValue.valueOf(isFrozen));
    }

    public static void updateNearbyBlocks(net.minecraft.client.MinecraftClient mc) {
        if (!initialized || mc.world == null || mc.player == null) return;
        
        LuaTable nearbyBlocks = new LuaTable();
        int px = (int) Math.floor(mc.player.getX());
        int py = (int) Math.floor(mc.player.getY());
        int pz = (int) Math.floor(mc.player.getZ());
        
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = px + dx;
                    int y = py + dy;
                    int z = pz + dz;
                    
                    net.minecraft.block.BlockState state = mc.world.getBlockState(
                        new net.minecraft.util.math.BlockPos(x, y, z));
                    
                    LuaTable blockInfo = new LuaTable();
                    blockInfo.set("x", LuaValue.valueOf(x));
                    blockInfo.set("y", LuaValue.valueOf(y));
                    blockInfo.set("z", LuaValue.valueOf(z));
                    blockInfo.set("isAir", LuaValue.valueOf(state.isAir()));
                    blockInfo.set("hardness", LuaValue.valueOf(state.getHardness(mc.world, new net.minecraft.util.math.BlockPos(x, y, z))));
                    blockInfo.set("isLiquid", LuaValue.valueOf(!state.getFluidState().isEmpty()));
                    
                    nearbyBlocks.set(LuaValue.valueOf(dx + 1 + (dy + 1) * 3 + (dz + 1) * 9), blockInfo);
                }
            }
        }
        
        worldTable.set("nearbyBlocks", nearbyBlocks);
    }

    public static void tick(net.minecraft.client.MinecraftClient mc) {
        if (!initialized) return;
        
        ModuleTicker.updateWorldTableFromPlayer(mc);
        
        updateNearbyBlocks(mc);
        
        LuaValue onTick = globals.get("onTick");
        if (onTick.isfunction()) {
            try {
                onTick.call();
            } catch (Exception e) {
                System.err.println("[LuaScriptHost] onTick error: " + e.getMessage());
            }
        }
        
        LuaTable 指令表 = globals.get("指令表").checktable();
        
        Set<String> enabledModules = new HashSet<>();
        Map<String, Map<String, Object>> moduleConfigs = new HashMap<>();
        
        LuaValue[] keys = 指令表.keys();
        for (LuaValue key : keys) {
            LuaValue value = 指令表.get(key);
            if (value.istable()) {
                LuaTable tbl = value.checktable();
                String moduleName = key.tojstring();
                enabledModules.add(moduleName);
                
                Map<String, Object> config = new HashMap<>();
                LuaValue[] tblKeys = tbl.keys();
                for (LuaValue k : tblKeys) {
                    LuaValue v = tbl.get(k);
                    if (v.isboolean()) config.put(k.tojstring(), v.toboolean());
                    else if (v.isnumber()) config.put(k.tojstring(), v.todouble());
                    else if (v.isstring()) config.put(k.tojstring(), v.tojstring());
                }
                moduleConfigs.put(moduleName, config);
            }
        }
        
        ModuleTicker.applyLuaModules(enabledModules, moduleConfigs);
    }

    public static void unloadAll() {
        moduleTables.clear();
        moduleStates.clear();
        if (globals != null) {
            globals.set("world", LuaValue.NIL);
            globals.set("指令表", LuaValue.NIL);
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static Globals getGlobals() {
        return globals;
    }
}
