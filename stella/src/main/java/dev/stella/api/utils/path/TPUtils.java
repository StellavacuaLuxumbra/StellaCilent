/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.Lists
 *  net.minecraft.network.packet.Packet
 *  net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket$OnGroundOnly
 *  net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket$PositionAndOnGround
 *  net.minecraft.util.math.Vec3d
 */
package dev.stella.api.utils.path;

import com.google.common.collect.Lists;
import dev.stella.api.utils.Wrapper;

import java.util.List;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

public class TPUtils
implements Wrapper {
    public static void teleportWithBack(Vec3d newPos, TeleportType type, Runnable runnable) {
        TPUtils.teleportWithBack(newPos, type, 10.0, runnable);
    }

    public static void teleportWithBack(Vec3d newPos, TeleportType type, double step, Runnable runnable) {
        switch (type) {
            case Legacy: {
                TPUtils.legacyTeleportWithBack(newPos, runnable);
                break;
            }
            case New: {
                TPUtils.newTeleportWithBack(newPos, step, runnable);
            }
        }
    }

    public static void legacyTeleportWithBack(Vec3d newPos, Runnable runnable) {
        List<Vec3> tpPath = PathUtils.computePath(newPos);
        if (tpPath.isEmpty()) {
            return;
        }
        tpPath.removeFirst();
        if (tpPath.isEmpty()) {
            return;
        }
        tpPath.forEach(vec3 -> TPUtils.mc.player.networkHandler.sendPacket((Packet)new PlayerMoveC2SPacket.PositionAndOnGround(vec3.x(), vec3.y(), vec3.z(), false)));
        runnable.run();
        tpPath = Lists.reverse(tpPath);
        tpPath.removeFirst();
        tpPath.forEach(vec3 -> TPUtils.mc.player.networkHandler.sendPacket((Packet)new PlayerMoveC2SPacket.PositionAndOnGround(vec3.x(), vec3.y(), vec3.z(), false)));
        TPUtils.mc.player.networkHandler.sendPacket((Packet)new PlayerMoveC2SPacket.PositionAndOnGround(TPUtils.mc.player.getX(), TPUtils.mc.player.getY(), TPUtils.mc.player.getZ(), false));
    }

    public static void newTeleportWithBack(Vec3d newPos, Runnable runnable) {
        TPUtils.newTeleportWithBack(newPos, 10.0, runnable);
    }

    public static void newTeleportWithBack(Vec3d newPos, double step, Runnable runnable) {
        Vec3d from = TPUtils.mc.player.getPos();
        TPUtils.sendStepped(from, newPos, step, true);
        runnable.run();
        TPUtils.sendStepped(newPos, from, step, false);
    }

    public static void newTeleport(Vec3d newPos) {
        TPUtils.newTeleport(newPos, 10.0);
    }

    public static void newTeleport(Vec3d newPos, double step) {
        TPUtils.sendStepped(TPUtils.mc.player.getPos(), newPos, step, true);
    }

    /**
     * 以 step 为最大步长线性插值发送真实坐标包（末包精确落在 to），
     * 替代旧的 OnGroundOnly 填充单包大跳，避免 moved too quickly。
     */
    private static void sendStepped(Vec3d from, Vec3d to, double step, boolean lastOnGround) {
        double dist = from.distanceTo(to);
        int packets = Math.max(1, (int)Math.ceil(dist / Math.max(1.0, step)));
        for (int i = 1; i <= packets; ++i) {
            double t = (double)i / (double)packets;
            double x = from.x + (to.x - from.x) * t;
            double y = from.y + (to.y - from.y) * t;
            double z = from.z + (to.z - from.z) * t;
            boolean last = i == packets;
            TPUtils.mc.player.networkHandler.sendPacket((Packet)new PlayerMoveC2SPacket.PositionAndOnGround(last ? to.x : x, last ? to.y : y, last ? to.z : z, last && lastOnGround));
        }
    }

    /**
     * 在目标附近、朝玩家方向、reach 距离内找一个可落脚的 TP 点。
     * 返回 null 表示不可达（或水平距离过近无法定向）。
     */
    public static Vec3d findTpVec(Vec3d target, double reach, double tpRange) {
        Vec3d from = TPUtils.mc.player.getPos();
        double dx = from.x - target.x;
        double dz = from.z - target.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 1.0E-3) {
            return null;
        }
        double d = Math.max(1.0, Math.min(reach - 1.5, horiz - 1.0));
        double nx = dx / horiz;
        double nz = dz / horiz;
        for (double yOff : new double[]{0.0, -1.0, 1.0, -2.0, 2.0}) {
            Vec3d cand = new Vec3d(target.x + nx * d, Math.floor(target.y) + yOff, target.z + nz * d);
            if (cand.distanceTo(from) <= tpRange && TpUtil.isBlinkVec(cand)) {
                return cand;
            }
        }
        return null;
    }

    public static enum TeleportType {
        Legacy,
        New;

    }
}

