package dev.stella.brain;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * LuaJ 脚本运行时 (Stella 特性，移植自旧 executer 架构)。
 *
 * 与旧版区别：
 *  - world 表结构化为 world.player / world.entities / world.time
 *  - 提供真实游戏动作绑定 (attack / look_at / velocity / ...)，脚本可直接驱动游戏
 *  - 缓存所有带 tick() 的模块表，逐 tick 调用
 *  - 指令表 透传给 InstructionEncoder，驱动 SunCat 模块
 */
public final class LuaScriptHost {
    private static final int MAX_TRACKED_ENTITIES = 64;

    private static LuaTable worldTable;
    private static LuaTable commandTable;
    private static final Map<String, LuaValue> moduleTables = new ConcurrentHashMap<>();
    private static boolean initialized = false;
    private static GlobalsHolder globals;

    private static final class GlobalsHolder {
        final org.luaj.vm2.Globals g;
        GlobalsHolder(org.luaj.vm2.Globals g) { this.g = g; }
    }

    private LuaScriptHost() {}

    public static void initialize() {
        if (initialized) return;

        org.luaj.vm2.Globals g = JsePlatform.standardGlobals();
        globals = new GlobalsHolder(g);

        worldTable = new LuaTable();
        g.set("world", worldTable);

        commandTable = new LuaTable();
        g.set("指令表", commandTable);

        g.set("dump_table", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue table, LuaValue indent) {
                StringBuilder sb = new StringBuilder();
                dumpTable(table.checktable(), sb, indent.isnil() ? "" : indent.tojstring());
                return LuaValue.valueOf(sb.toString());
            }
        });

        registerGameBindings(g);
        initialized = true;
    }

    // ==================== 游戏动作绑定 ====================

    private static void registerGameBindings(org.luaj.vm2.Globals g) {
        g.set("attack", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player == null || mc.world == null || mc.interactionManager == null) return LuaValue.NIL;
                Entity target = mc.world.getEntityById(arg.checkint());
                if (target != null) {
                    mc.interactionManager.attackEntity(mc.player, target);
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
                return LuaValue.NIL;
            }
        });

        g.set("swing_hand", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player == null) return LuaValue.NIL;
                int h = arg.optint(0);
                mc.player.swingHand(h == 1 ? Hand.OFF_HAND : Hand.MAIN_HAND);
                return LuaValue.NIL;
            }
        });

        g.set("look_at", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player == null) return LuaValue.NIL;
                double x = arg.checkdouble(1);
                double y = arg.checkdouble(2);
                double z = arg.checkdouble(3);
                double dx = x - mc.player.getX();
                double dy = y - (mc.player.getY() + mc.player.getStandingEyeHeight());
                double dz = z - mc.player.getZ();
                double horiz = Math.sqrt(dx * dx + dz * dz);
                float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));
                mc.player.setYaw(yaw);
                mc.player.setPitch(Math.max(-90.0f, Math.min(90.0f, pitch)));
                return LuaValue.NIL;
            }
        });

        g.set("velocity", new ThreeArgFunctionCompat());
        g.set("chat", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.getNetworkHandler() == null) return LuaValue.NIL;
                mc.getNetworkHandler().sendChatMessage(arg.checkjstring());
                return LuaValue.NIL;
            }
        });

        g.set("set_slot", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player == null) return LuaValue.NIL;
                int slot = arg.checkint();
                if (slot >= 0 && slot <= 8) mc.player.getInventory().selectedSlot = slot;
                return LuaValue.NIL;
            }
        });

        g.set("sneak", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player != null) mc.player.setSneaking(arg.toboolean());
                return LuaValue.NIL;
            }
        });

        g.set("sprint", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player != null) mc.player.setSprinting(arg.toboolean());
                return LuaValue.NIL;
            }
        });

        g.set("use_item", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player == null || mc.world == null || mc.interactionManager == null) return LuaValue.NIL;
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                return LuaValue.NIL;
            }
        });
    }

    /** velocity(x, y, z) */
    private static final class ThreeArgFunctionCompat extends org.luaj.vm2.lib.VarArgFunction {
        @Override
        public LuaValue invoke(org.luaj.vm2.Varargs args) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || args.narg() < 3) return LuaValue.NIL;
            mc.player.setVelocity(args.checkdouble(1), args.checkdouble(2), args.checkdouble(3));
            return LuaValue.NIL;
        }
    }

    private static void dumpTable(LuaTable table, StringBuilder sb, String indent) {
        sb.append("{\n");
        for (LuaValue key : table.keys()) {
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

    // ==================== 脚本加载 ====================

    public static void loadScript(String moduleName, String luaCode) {
        if (!initialized) initialize();
        try {
            globals.g.load(luaCode, moduleName).call();
            rescanModuleTables();
        } catch (Exception e) {
            System.err.println("[LuaScriptHost] Failed to load " + moduleName + ": " + e.getMessage());
        }
    }

    /** 扫描全局环境，缓存所有含 tick 函数的表 */
    private static void rescanModuleTables() {
        moduleTables.clear();
        for (LuaValue key : globals.g.keys()) {
            LuaValue value = globals.g.get(key);
            if (value.istable() && value.get("tick").isfunction()) {
                moduleTables.put(key.tojstring(), value);
            }
        }
    }

    // ==================== world 表 ====================

    private static void updateWorld(MinecraftClient mc) {
        if (worldTable == null || mc.player == null || mc.world == null) return;
        try {
            LuaTable player = new LuaTable();
            player.set("x", LuaValue.valueOf(mc.player.getX()));
            player.set("y", LuaValue.valueOf(mc.player.getY()));
            player.set("z", LuaValue.valueOf(mc.player.getZ()));
            player.set("velX", LuaValue.valueOf(mc.player.getVelocity().x));
            player.set("velY", LuaValue.valueOf(mc.player.getVelocity().y));
            player.set("velZ", LuaValue.valueOf(mc.player.getVelocity().z));
            player.set("yaw", LuaValue.valueOf(mc.player.getYaw()));
            player.set("pitch", LuaValue.valueOf(mc.player.getPitch()));
            player.set("health", LuaValue.valueOf(mc.player.getHealth()));
            player.set("armor", LuaValue.valueOf(mc.player.getArmor()));
            player.set("hunger", LuaValue.valueOf(mc.player.getHungerManager().getFoodLevel()));
            player.set("onGround", LuaValue.valueOf(mc.player.isOnGround()));
            player.set("inWater", LuaValue.valueOf(mc.player.isInsideWaterOrBubbleColumn()));
            player.set("sneaking", LuaValue.valueOf(mc.player.isSneaking()));
            worldTable.set("player", player);

            worldTable.set("time", LuaValue.valueOf(mc.world.getTime()));
            worldTable.set("worldTime", LuaValue.valueOf(mc.world.getTimeOfDay()));
            worldTable.set("dimension", LuaValue.valueOf(mc.world.getRegistryKey().getValue().toString()));

            List<LuaValue> entities = new ArrayList<>();
            int count = 0;
            for (Entity e : mc.world.getEntities()) {
                if (count >= MAX_TRACKED_ENTITIES) break;
                if (e == mc.player) continue;
                LuaTable t = new LuaTable();
                t.set("id", LuaValue.valueOf(e.getId()));
                t.set("isPlayer", LuaValue.valueOf(e instanceof PlayerEntity));
                t.set("x", LuaValue.valueOf(e.getX()));
                t.set("y", LuaValue.valueOf(e.getY()));
                t.set("z", LuaValue.valueOf(e.getZ()));
                t.set("name", LuaValue.valueOf(e.getName().getString()));
                if (e instanceof LivingEntity living) {
                    t.set("health", LuaValue.valueOf(living.getHealth()));
                    t.set("maxHealth", LuaValue.valueOf(living.getMaxHealth()));
                } else {
                    t.set("health", LuaValue.valueOf(-1.0f));
                }
                entities.add(t);
                count++;
            }
            LuaTable arr = new LuaTable();
            for (int i = 0; i < entities.size(); i++) {
                arr.set(i + 1, entities.get(i));
            }
            worldTable.set("entities", arr);
        } catch (Exception ignored) {}
    }

    // ==================== tick ====================

    public static void tick(MinecraftClient mc) {
        if (!initialized) return;
        updateWorld(mc);

        try {
            LuaValue onTick = globals.g.get("onTick");
            if (onTick.isfunction()) {
                onTick.call();
            }
        } catch (Exception e) {
            System.err.println("[LuaScriptHost] onTick error: " + e.getMessage());
        }

        for (LuaValue moduleTable : moduleTables.values()) {
            LuaValue tick = moduleTable.get("tick");
            if (tick.isfunction()) {
                try {
                    tick.call();
                } catch (Exception e) {
                    System.err.println("[LuaScriptHost] module tick error: " + e.getMessage());
                }
            }
        }

        try {
            LuaValue cmd = globals.g.get("指令表");
            if (cmd.istable()) {
                InstructionEncoder.execute(cmd.checktable());
            }
        } catch (Exception e) {
            System.err.println("[LuaScriptHost] 指令表 dispatch error: " + e.getMessage());
        }
    }

    public static void unloadAll() {
        moduleTables.clear();
        if (initialized && globals != null) {
            globals.g.set("world", LuaValue.NIL);
            globals.g.set("指令表", LuaValue.NIL);
        }
        worldTable = null;
        commandTable = null;
        initialized = false;
        globals = null;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static org.luaj.vm2.Globals getGlobals() {
        return globals == null ? null : globals.g;
    }
}
