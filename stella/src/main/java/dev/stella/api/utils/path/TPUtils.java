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

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class TPUtils
implements Wrapper {
    public static boolean teleportWithBack(Vec3d newPos, TeleportType type, Runnable runnable) {
        return TPUtils.teleportWithBack(newPos, type, 10.0, runnable);
    }

    public static boolean teleportWithBack(Vec3d newPos, TeleportType type, double step, Runnable runnable) {
        return TPUtils.teleportWithBack(newPos, type, step, true, true, runnable);
    }

    public static boolean teleportWithBack(Vec3d newPos, TeleportType type, double step, boolean stepped, boolean airPath, Runnable runnable) {
        switch (type) {
            case Legacy: {
                return TPUtils.legacyTeleportWithBack(newPos, runnable);
            }
            case New: {
                return TPUtils.newTeleportWithBack(newPos, step, stepped, airPath, runnable);
            }
        }
        return false;
    }

    public static boolean legacyTeleportWithBack(Vec3d newPos, Runnable runnable) {
        List<Vec3> tpPath = PathUtils.computePath(newPos);
        if (tpPath.isEmpty()) {
            return false;
        }
        tpPath.removeFirst();
        if (tpPath.isEmpty()) {
            return false;
        }
        tpPath.forEach(vec3 -> TPUtils.mc.player.networkHandler.sendPacket((Packet)new PlayerMoveC2SPacket.PositionAndOnGround(vec3.x(), vec3.y(), vec3.z(), false)));
        runnable.run();
        tpPath = Lists.reverse(tpPath);
        tpPath.removeFirst();
        tpPath.forEach(vec3 -> TPUtils.mc.player.networkHandler.sendPacket((Packet)new PlayerMoveC2SPacket.PositionAndOnGround(vec3.x(), vec3.y(), vec3.z(), false)));
        TPUtils.mc.player.networkHandler.sendPacket((Packet)new PlayerMoveC2SPacket.PositionAndOnGround(TPUtils.mc.player.getX(), TPUtils.mc.player.getY(), TPUtils.mc.player.getZ(), false));
        return true;
    }

    public static boolean newTeleportWithBack(Vec3d newPos, Runnable runnable) {
        return TPUtils.newTeleportWithBack(newPos, 10.0, runnable);
    }

    public static boolean newTeleportWithBack(Vec3d newPos, double step, Runnable runnable) {
        return TPUtils.newTeleportWithBack(newPos, step, true, true, runnable);
    }

    public static boolean newTeleportWithBack(Vec3d newPos, double step, boolean stepped, boolean airPath, Runnable runnable) {
        Vec3d from = TPUtils.mc.player.getPos();
        if (!TPUtils.sendStepped(from, newPos, step, true, stepped, airPath, false)) {
            return false;
        }
        runnable.run();
        TPUtils.sendStepped(newPos, from, step, false, stepped, airPath, true);
        return true;
    }

    public static boolean newTeleport(Vec3d newPos) {
        return TPUtils.newTeleport(newPos, 10.0);
    }

    public static boolean newTeleport(Vec3d newPos, double step) {
        return TPUtils.newTeleport(newPos, step, true, true);
    }

    public static boolean newTeleport(Vec3d newPos, double step, boolean stepped, boolean airPath) {
        return TPUtils.sendStepped(TPUtils.mc.player.getPos(), newPos, step, true, stepped, airPath, false);
    }

    /**
     * 以 step 为最大步长线性插值发送真实坐标包（末包精确落在 to）。
     * stepped=false 时退化为单包直跳（原版 moved-too-quickly 风险由用户自担）。
     * 中途点碰撞时先做垂直绕行（VClip）；airPath=false 要求中途点必须有地面支撑，否则整次失败。
     * 去程（allowFallback=false）校验失败时不发任何包返回 false；
     * 回程（allowFallback=true）失败时退回单包直跳，保证能回家。
     */
    private static boolean sendStepped(Vec3d from, Vec3d to, double step, boolean lastOnGround, boolean stepped, boolean airPath, boolean allowFallback) {
        if (TPUtils.mc.player == null || TPUtils.mc.world == null) {
            return false;
        }
        if (!stepped) {
            TPUtils.mc.player.networkHandler.sendPacket((Packet)new PlayerMoveC2SPacket.PositionAndOnGround(to.x, to.y, to.z, lastOnGround));
            return true;
        }
        double dist = from.distanceTo(to);
        int packets = Math.max(1, (int)Math.ceil(dist / Math.max(1.0, step)));
        ArrayList<Vec3d> pts = new ArrayList<Vec3d>(packets + 1);
        for (int i = 1; i <= packets; ++i) {
            double t = (double)i / (double)packets;
            pts.add(i == packets ? to : new Vec3d(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t, from.z + (to.z - from.z) * t));
        }
        for (int i = 0; i < pts.size() - 1; ++i) {
            Vec3d p = pts.get(i);
            if (!TpUtil.isBlinkVec(p)) {
                Vec3d detour = detourAround(p);
                if (detour == null) {
                    if (airPath) {
                        continue;
                    }
                    if (allowFallback) {
                        return TPUtils.sendStepped(from, to, step, lastOnGround, false, airPath, false);
                    }
                    return false;
                }
                p = detour;
                pts.set(i, p);
            }
            if (!airPath && !TpUtil.hasSupport(p)) {
                if (allowFallback) {
                    return TPUtils.sendStepped(from, to, step, lastOnGround, false, airPath, false);
                }
                return false;
            }
        }
        for (Vec3d p : pts) {
            boolean last = p == pts.get(pts.size() - 1);
            TPUtils.mc.player.networkHandler.sendPacket((Packet)new PlayerMoveC2SPacket.PositionAndOnGround(p.x, p.y, p.z, last && lastOnGround));
        }
        return true;
    }

    /**
     * 中途点在实体方块内时，向上最多10格、向下最多5格找一个无碰撞点作为绕行。
     */
    private static Vec3d detourAround(Vec3d p) {
        for (int i = 1; i <= 10; ++i) {
            Vec3d cand = new Vec3d(p.x, p.y + (double)i, p.z);
            if (TpUtil.isBlinkVec(cand)) {
                return cand;
            }
        }
        for (int i = 1; i <= 5; ++i) {
            Vec3d cand = new Vec3d(p.x, p.y - (double)i, p.z);
            if (TpUtil.isBlinkVec(cand)) {
                return cand;
            }
        }
        return null;
    }

    public static Vec3d findTpVec(Vec3d target, double reach, double tpRange) {
        return TPUtils.findTpVec(target, reach, tpRange, true);
    }

    /**
     * 优化版落点搜索：从近玩家端起沿朝向逐步回退，先试原方向，
     * 失败再扩 ±15/±30/±45 度侧向候选；每个候选要求
     * 无碰撞、（可选）脚下有支撑、眼点在 reach 内、且到目标无遮挡。
     */
    public static Vec3d findTpVec(Vec3d target, double reach, double tpRange, boolean airTarget) {
        if (TPUtils.mc.player == null || TPUtils.mc.world == null) {
            return null;
        }
        Vec3d from = TPUtils.mc.player.getPos();
        double dx = from.x - target.x;
        double dz = from.z - target.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 1.0E-3) {
            return null;
        }
        double dMax = Math.max(1.0, Math.min(reach - 1.5, horiz - 1.0));
        double baseAngle = Math.atan2(dz, dx);
        double[] angleOffsets = new double[]{0.0, 0.2618, -0.2618, 0.5236, -0.5236, 0.7854, -0.7854};
        double[] yOffsets = new double[]{0.0, -1.0, 1.0, -2.0, 2.0, -3.0, 3.0};
        double eyeHeight = TPUtils.mc.player.getStandingEyeHeight();
        for (double angleOffset : angleOffsets) {
            double angle = baseAngle + angleOffset;
            double ax = Math.cos(angle);
            double az = Math.sin(angle);
            for (double d = dMax; d >= 1.0; d -= 0.5) {
                for (double yOff : yOffsets) {
                    Vec3d cand = new Vec3d(target.x + ax * d, Math.floor(target.y) + yOff, target.z + az * d);
                    if (cand.distanceTo(from) > tpRange || cand.y < (double)TPUtils.mc.world.getBottomY()) {
                        continue;
                    }
                    if (!TpUtil.isBlinkVec(cand)) {
                        continue;
                    }
                    if (!airTarget && !TpUtil.hasSupport(cand)) {
                        continue;
                    }
                    Vec3d eye = new Vec3d(cand.x, cand.y + eyeHeight, cand.z);
                    if (eye.distanceTo(target) > reach + 0.5) {
                        continue;
                    }
                    if (!TPUtils.hasLineOfSight(eye, target)) {
                        continue;
                    }
                    return cand;
                }
            }
        }
        return null;
    }

    private static boolean hasLineOfSight(Vec3d from, Vec3d to) {
        BlockHitResult hit = TPUtils.mc.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, TPUtils.mc.player));
        if (hit.getType() == HitResult.Type.MISS) {
            return true;
        }
        return hit.getPos().distanceTo(to) < 1.0;
    }

    public static enum TeleportType {
        Legacy,
        New;

    }
}
