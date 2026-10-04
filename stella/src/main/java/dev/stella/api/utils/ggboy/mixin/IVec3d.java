package dev.stella.api.utils.ggboy.mixin;

import net.minecraft.util.math.Vec3i;
import org.joml.Vector3d;

/**
 * IVec3d 接口
 * 功能：提供对 Vec3d 的扩展方法
 * 注意：实际的 Mixin 实现在 dev.stella.asm.accessors.IVec3d
 */
public interface IVec3d {
    
    /**
     * 直接设置向量的 x, y, z 坐标
     */
    void set(double x, double y, double z);

    /**
     * 从整数向量设置坐标
     */
    default void set(Vec3i vec) {
        this.set(vec.getX(), vec.getY(), vec.getZ());
    }

    /**
     * 从 JOML 向量设置坐标
     */
    default void set(Vector3d vec) {
        this.set(vec.x, vec.y, vec.z);
    }

    /**
     * 单独设置 X 和 Z 坐标（Y 坐标不变）
     * 在 FlyOnFirstTime.onMove() 中用于归零水平速度
     */
    void setXZ(double x, double z);

    /**
     * 单独设置 Y 坐标（垂直速度）
     * 在 FlyOnFirstTime.onMove() 中用于归零垂直速度
     */
    void setY(double y);
}
