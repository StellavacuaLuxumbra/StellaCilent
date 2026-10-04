package dev.stella.api.management;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;

public class PacketManager {
    public static final PacketManager INSTANCE = new PacketManager();
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private PacketManager() {
    }

    // 发送数据包
    public void sendPacket(Packet<?> packet) {
        if (mc.player != null && mc.player.networkHandler != null) {
            mc.player.networkHandler.sendPacket(packet);
        }
    }

    // 发送数据包（无顺序）
    public void sendPacketNoEvent(Packet<?> packet) {
        if (mc.player != null && mc.player.networkHandler != null) {
            mc.player.networkHandler.sendPacket(packet);
        }
    }
}
