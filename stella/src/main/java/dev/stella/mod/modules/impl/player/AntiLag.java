package dev.stella.mod.modules.impl.player;

import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.PacketEvent;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.impl.combat.SpearGod;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import dev.stella.mod.modules.settings.impl.SliderSetting;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;

public class AntiLag extends Module {
    public static AntiLag INSTANCE;
    
    private final BooleanSetting debug = this.add(new BooleanSetting("Debug", false));
    private final SliderSetting range = this.add(new SliderSetting("Range", 200.0, 0.0, 256.0));
    private final SliderSetting moveD = this.add(new SliderSetting("MoveDistance", 10.0, 0.0, 128.0));

    public AntiLag() {
        super("AntiLag", Module.Category.Player);
        this.setChinese("防卡顿");
        INSTANCE = this;
    }

    @EventListener
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof PlayerPositionLookS2CPacket p) {
            // 如果 SpearGod 正在工作，不拦截传送包
            if (SpearGod.INSTANCE != null && SpearGod.INSTANCE.isOn()) {
                return;
            }
            
            if (mc.player == null) return;

            double x = p.getX();
            double y = p.getY();
            double z = p.getZ();
            Vec3d packetPos = new Vec3d(x, y, z);
            Vec3d playerPos = mc.player.getPos();

            double dis = packetPos.distanceTo(playerPos);

            if (dis >= range.getValue()) {
                return;
            }

            event.setCancelled(true);
            mc.getNetworkHandler().sendPacket(new TeleportConfirmC2SPacket(p.getTeleportId()));

            if (debug.getValue()) {
                this.sendMessage("§7[AntiLag] §f拦截服务器传送包 - 距离: §7" + String.format("%.2f", dis));
            }

            // 平滑传送到服务器位置
            doTeleport(playerPos, packetPos, moveD.getValue(), mc.player.isOnGround());
        }
    }
    
    private void doTeleport(Vec3d from, Vec3d to, double maxDist, boolean onGround) {
        double dist = from.distanceTo(to);
        int packets = (int) Math.ceil(dist / maxDist);
        
        for (int i = 0; i < packets; i++) {
            double progress = (double) (i + 1) / packets;
            Vec3d intermediate = from.lerp(to, progress);
            
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                intermediate.x,
                intermediate.y,
                intermediate.z,
                onGround
            ));
        }
    }

    @Override
    public String getInfo() {
        return String.format("%.1f", range.getValue());
    }
}
