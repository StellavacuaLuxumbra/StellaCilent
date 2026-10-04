package dev.stella.api.utils.eflyRotation;

import dev.stella.api.utils.Wrapper;
import dev.stella.core.impl.RotationManager;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public class RotationUtils implements Wrapper {

    /**
     * 获取当前实际发给服务器的 Yaw
     */
    public static float getActualYaw() {
        if (RotationManager.fixYaw != 0) {
            return RotationManager.fixYaw;
        }
        return mc.player != null ? mc.player.getYaw() : 0.0f;
    }

    /**
     * 获取当前实际发给服务器的 Pitch
     */
    public static float getActualPitch() {
        if (RotationManager.fixPitch != 0) {
            return RotationManager.fixPitch;
        }
        return mc.player != null ? mc.player.getPitch() : 0.0f;
    }

    /**
     * 发送静默转向包 (调用你的 RotationManager)
     */
    public static void doSilentRotate(float yaw, float pitch) {
        RotationManager.INSTANCE.silentRotate(yaw, pitch);
    }

    /**
     * 强行同步发包
     */
    public static void packetRotate(float yaw, float pitch) {
        if (mc.player != null && mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, mc.player.isOnGround()));
        }
    }

    /**
     * 设置旋转 (适配 Kami 风格接口)
     * @param yaw Yaw 角度
     * @param pitch Pitch 角度
     * @param priority 优先级
     */
    public static void setRotation(float yaw, float pitch, int priority) {
        Rotation rotation = new Rotation(priority, yaw, pitch);
        dev.stella.core.impl.RotationManager.INSTANCE.rotateTo(rotation);
    }

    /**
     * 设置旋转 (简化版本，默认优先级 99)
     */
    public static void setRotation(float[] angles, int priority) {
        if (angles.length >= 2) {
            setRotation(angles[0], angles[1], priority);
        }
    }
}