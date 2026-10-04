package dev.stella.mod.modules.impl.client;

import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.TickEvent;
import dev.stella.brain.LuaScriptHost;
import dev.stella.brain.LuaScriptLoader;
import dev.stella.ipc.IpcHost;
import dev.stella.ipc.InstructionDecoder;
import dev.stella.ipc.Protocol;
import dev.stella.ipc.WorldSnapshot;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import net.minecraft.client.MinecraftClient;

/**
 * StellaBridge - 本客户端区别于原版 SunCat 的核心特性：
 *  1. Lua 脚本运行时（assets/stella/lua/*.lua 逐 tick 执行）
 *  2. 与 C++ brain (cilent.exe) 的共享内存双向 IPC 通道
 *
 *  Lua 默认开启；IpcBridge 设置项控制是否连接外部 C++ brain。
 */
public class StellaBridge extends Module {
    public static StellaBridge INSTANCE;

    private final BooleanSetting ipcBridge = this.add(new BooleanSetting("IpcBridge", false));
    private IpcHost ipc;

    public StellaBridge() {
        super("StellaBridge", "Lua script runtime + shared-memory IPC to the C++ brain (Stella features)",
                Module.Category.Client);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        try {
            LuaScriptLoader.loadAll();
            System.out.println("[Stella] Lua runtime loaded");
        } catch (Exception e) {
            System.err.println("[Stella] Lua load failed: " + e.getMessage());
        }
        if (ipcBridge.getValue()) {
            startIpc();
        }
    }

    @Override
    public void onDisable() {
        stopIpc();
        LuaScriptHost.unloadAll();
    }

    @EventListener
    public void onTick(TickEvent event) {
        if (event.isPost()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        if (LuaScriptHost.isInitialized()) {
            LuaScriptHost.tick(mc);
        }

        IpcHost host = ipc;
        if (host != null) {
            byte[] snapshot = WorldSnapshot.serialize(mc);
            if (snapshot.length > 0) {
                host.send(Protocol.EV_WORLD_SNAPSHOT, snapshot);
            }
            byte[] tickPayload = new byte[8];
            long time = mc.world.getTime();
            for (int i = 0; i < 8; i++) {
                tickPayload[i] = (byte) (time >>> (8 * i));
            }
            host.send(Protocol.EV_TICK, tickPayload);
        }
    }

    private void startIpc() {
        if (ipc != null) return;
        try {
            ipc = new IpcHost();
            ipc.start(this::dispatch);
            System.out.println("[Stella] IPC bridge started");
        } catch (Exception e) {
            ipc = null;
            System.err.println("[Stella] IPC start failed: " + e.getMessage());
        }
    }

    private void stopIpc() {
        if (ipc != null) {
            ipc.stop();
            ipc = null;
        }
    }

    private void dispatch(int opcode, byte[] payload) {
        try {
            switch (opcode) {
                case Protocol.OP_CONNECT -> {
                    IpcHost host = ipc;
                    if (host != null) {
                        host.resetTx();
                        host.send(Protocol.EV_CONNECTED, new byte[0]);
                        System.out.println("[Stella] C++ brain connected");
                    }
                }
                case Protocol.CMD_INSTRUCTION_SEQ -> InstructionDecoder.execute(payload);
                default -> { }
            }
        } catch (Exception e) {
            System.err.println("[Stella] IPC dispatch error: " + e.getMessage());
        }
    }
}
