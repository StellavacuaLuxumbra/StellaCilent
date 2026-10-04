package dev.stella.api.utils.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.GameRules;
import net.minecraft.world.explosion.Explosion;

/**
 * ExplosionUtilPlus - 从 Zenith 移植的伤害计算工具类
 * 用于水晶爆炸伤害预测
 */
public class ExplosionUtilPlus {

    /**
     * 计算爆炸伤害
     * @param x 爆炸X坐标
     * @param y 爆炸Y坐标
     * @param z 爆炸Z坐标
     * @param entity 目标实体
     * @param power 爆炸威力
     * @return 预估伤害
     */
    public static float calculateDamage(double x, double y, double z, Entity entity, float power) {
        if (entity == null) return 0;
        if (entity instanceof PlayerEntity player) {
            return calculateDamage(x, y, z, player, player, power);
        }
        return calculateDamage(x, y, z, (LivingEntity) entity, power);
    }

    /**
     * 计算爆炸伤害（带预测）
     * @param x 爆炸X坐标
     * @param y 爆炸Y坐标
     * @param z 爆炸Z坐标
     * @param player 实际玩家
     * @param predict 预测玩家位置
     * @param power 爆炸威力
     * @return 预估伤害
     */
    public static float calculateDamage(double x, double y, double z, PlayerEntity player, PlayerEntity predict, float power) {
        if (player == null || predict == null) return 0;

        double dist = player.getPos().distanceTo(new Vec3d(x, y, z));
        if (dist > 12.0) return 0;

        // 计算曝光度
        double exposure = getExposure(new Vec3d(x, y, z), player);
        double impact = (1.0 - (dist / 12.0)) * exposure;
        double damage = ((impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0);

        // 应用难度乘数
        damage *= getDifficultyMultiplier();
        damage = getDamageAfterAbsorb(damage, player);

        return (float) damage;
    }

    /**
     * 计算爆炸伤害（LivingEntity版本）
     */
    public static float calculateDamage(double x, double y, double z, LivingEntity entity, float power) {
        if (entity == null) return 0;

        double dist = entity.getPos().distanceTo(new Vec3d(x, y, z));
        if (dist > 12.0) return 0;

        double exposure = getExposure(new Vec3d(x, y, z), entity);
        double impact = (1.0 - (dist / 12.0)) * exposure;
        double damage = ((impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0);

        damage *= getDifficultyMultiplier();
        damage = getDamageAfterAbsorb(damage, entity);

        return (float) damage;
    }

    /**
     * 获取曝光度（实体暴露在爆炸中的比例）
     */
    private static double getExposure(Vec3d source, Entity entity) {
        Box box = entity.getBoundingBox();
        double d = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
        double e = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
        double f = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
        double g = (1.0 - Math.floor(1.0 / d) * d) / 2.0;
        double h = (1.0 - Math.floor(1.0 / f) * f) / 2.0;

        if (!(d < 0.0) && !(e < 0.0) && !(f < 0.0)) {
            int i = 0;
            int j = 0;

            for (double k = 0.0; k <= 1.0; k += d) {
                for (double l = 0.0; l <= 1.0; l += e) {
                    for (double m = 0.0; m <= 1.0; m += f) {
                        double n = box.minX + (box.maxX - box.minX) * k;
                        double o = box.minY + (box.maxY - box.minY) * l;
                        double p = box.minZ + (box.maxZ - box.minZ) * m;
                        Vec3d vec3d = new Vec3d(n + g, o, p + h);
                        if (canSee(source, vec3d, entity.getWorld())) {
                            ++i;
                        }
                        ++j;
                    }
                }
            }

            return (double) i / (double) j;
        }
        return 0.0;
    }

    /**
     * 检查两个点之间是否可见
     */
    private static boolean canSee(Vec3d from, Vec3d to, BlockView world) {
        return world.raycast(new net.minecraft.world.RaycastContext(
                from, to,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,
                mc.player
        )) == null;
    }

    /**
     * 获取难度乘数
     */
    private static double getDifficultyMultiplier() {
        if (mc.world == null) return 1.0;
        return switch (mc.world.getDifficulty()) {
            case PEACEFUL -> 0.0;
            case EASY -> 1.0;
            case NORMAL -> 2.0;
            case HARD -> 3.0;
        };
    }

    /**
     * 计算吸收后的伤害
     */
    private static double getDamageAfterAbsorb(double damage, LivingEntity entity) {
        if (entity instanceof PlayerEntity player) {
            // 应用保护附魔减伤
            int epf = getEnchantmentProtectionFactor(player);
            if (epf > 0) {
                damage *= (1.0 - (epf * 0.04));
            }
        }
        return Math.max(damage, 0);
    }

    /**
     * 获取附魔保护系数
     */
    private static int getEnchantmentProtectionFactor(PlayerEntity player) {
        int totalEPF = 0;
        for (var stack : player.getArmorItems()) {
            var enchantments = stack.getEnchantments();
            for (var entry : enchantments.getEnchantmentEntries()) {
                if (entry.getKey().matchesKey(RegistryKey.of(RegistryKeys.ENCHANTMENT, net.minecraft.util.Identifier.ofVanilla("protection")))) {
                    int level = entry.getIntValue();
                    totalEPF += level;
                }
            }
        }
        return Math.min(totalEPF, 20);
    }

    private static net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
}
