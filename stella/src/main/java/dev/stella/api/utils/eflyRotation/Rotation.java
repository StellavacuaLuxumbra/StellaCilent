package dev.stella.api.utils.eflyRotation;

/**
 * 这是一个简单的旋转信息包装类
 * 用于存储 Yaw, Pitch 以及该旋转请求的优先级
 */
public class Rotation {
    private float yaw;
    private float pitch;
    private final int priority;
    private final boolean snap;

    public Rotation(int priority, float yaw, float pitch) {
        this(priority, yaw, pitch, false);
    }

    public Rotation(int priority, float yaw, float pitch, boolean snap) {
        this.priority = priority;
        this.yaw = yaw;
        this.pitch = pitch;
        this.snap = snap;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isSnap() {
        return snap;
    }

    /**
     * 规范化角度，确保角度在合法范围内
     */
    public Rotation fix() {
        // 将 yaw 限制在 -180 到 180 之间 (Minecraft 标准)
        while (this.yaw > 180.0f) this.yaw -= 360.0f;
        while (this.yaw <= -180.0f) this.yaw += 360.0f;
        
        // 将 pitch 限制在 -90 到 90 之间
        if (this.pitch > 90.0f) this.pitch = 90.0f;
        if (this.pitch < -90.0f) this.pitch = -90.0f;
        
        return this;
    }
}