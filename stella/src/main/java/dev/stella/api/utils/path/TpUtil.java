package dev.stella.api.utils.path;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;

/**
 * TpUtil - 瞬移工具类
 * 从 Sent 客户端移植，实现严格的传送逻辑
 */
public class TpUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    // 记录已放置的位置，防止重复放置
    public static Set<BlockPos> placedPos = new HashSet<>();

    /**
     * 执行传送移动（Sent 版本）
     * 使用 VClip 垂直穿越逻辑，绕过障碍
     */
    public static void doTpMove(Vec3d fromVec, Vec3d toVec, double moveDistance, boolean back, Runnable action) {
        if (mc.player == null || mc.world == null) return;
        if (!isBlinkVec(fromVec) || !isBlinkVec(toVec)) return;

        // 查找垂直移动向量（向上/向下穿越）
        Vec3d a1 = findVClipVecToMove(fromVec, toVec);
        Vec3d b1 = findVClipVecToMove(toVec, fromVec);

        // 计算需要的数据包数量
        double distA = fromVec.distanceTo(a1);
        double distB = fromVec.distanceTo(b1);
        double distC = fromVec.distanceTo(toVec);

        int a = (int) Math.ceil(distA / moveDistance);
        int b = (int) Math.ceil(distB / moveDistance);
        int c = (int) Math.ceil(distC / moveDistance);
        int p = Math.max(a, Math.max(b, c)) - 1 + 4;

        // 包数量限制（防止被反作弊检测）
        if (p > 20) {
            return;
        }

        // 发送移动包
        for (int i = 1; i < p; i++) {
            sendMovePacket(fromVec.x, fromVec.y, fromVec.z, false);
        }
        sendMovePacket(a1.x, a1.y, a1.z, false);
        sendMovePacket(toVec.x, toVec.y, toVec.z, false);

        // 执行动作（放置/攻击等）
        action.run();

        // 返回原位置
        if (back) {
            sendMovePacket(b1.x, b1.y, b1.z, false);
            sendMovePacket(fromVec.x, fromVec.y, fromVec.z, false);
        } else {
            mc.player.setPosition(toVec.x, toVec.y, toVec.z);
        }
    }

    /**
     * 查找垂直穿越向量（尝试向上或向下移动来绕过障碍）
     */
    private static Vec3d findVClipVecToMove(Vec3d from, Vec3d to) {
        // 尝试向上移动
        for (int i = 1; i <= 10; i++) {
            Vec3d testVec = new Vec3d(from.x, from.y + i, from.z);
            if (isBlinkVec(testVec)) {
                return testVec;
            }
        }
        // 尝试向下移动
        for (int i = 1; i >= -5; i--) {
            Vec3d testVec = new Vec3d(from.x, from.y + i, from.z);
            if (isBlinkVec(testVec)) {
                return testVec;
            }
        }
        return from;
    }

    /**
     * 发送移动数据包
     */
    private static void sendMovePacket(double x, double y, double z, boolean onGround) {
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround));
    }

    /**
     * 检查位置是否可以传送（Sent 严格版本）
     * 检查脚部和头部4个角点的碰撞
     */
    public static boolean isBlinkVec(Vec3d vec) {
        if (mc.world == null || mc.player == null) return false;

        // 检查是否在虚空以下
        if (vec.y < mc.world.getBottomY()) return false;

        Box box = mc.player.getBoundingBox();
        double halfX = box.getLengthX() / 2.0;
        double height = box.getLengthY();
        double halfZ = box.getLengthZ() / 2.0;

        // 检查玩家边界框的4个底部角点
        double[][] corners = {
            { halfX, 0.0, halfZ },
            { halfX, 0.0, -halfZ },
            { -halfX, 0.0, halfZ },
            { -halfX, 0.0, -halfZ }
        };

        for (double[] corner : corners) {
            BlockPos feet = new BlockPos(
                (int) Math.floor(vec.x + corner[0]),
                (int) Math.floor(vec.y),
                (int) Math.floor(vec.z + corner[2])
            );

            // 检查脚部和上方是否有碰撞方块
            if (isCollisionBlock(feet)) return false;
            if (isCollisionBlock(feet.up())) return false;

            // 检查头部
            BlockPos head = new BlockPos(
                (int) Math.floor(vec.x + corner[0]),
                (int) Math.floor(vec.y + height),
                (int) Math.floor(vec.z + corner[2])
            );

            if (isCollisionBlock(head)) return false;
        }

        return true;
    }

    /**
     * 检查方块是否有碰撞
     */
    private static boolean isCollisionBlock(BlockPos pos) {
        if (mc.world == null) return false;
        BlockState state = mc.world.getBlockState(pos);
        return state.getBlock() != Blocks.AIR && state.isSolid();
    }

    /**
     * 检查是否可以放置方块（TP 版本）
     */
    public static boolean canTpPlaceAt(BlockPos pos, boolean ignoreEntities) {
        return clientCanPlace(pos, ignoreEntities) && !placedPos.contains(pos);
    }

    /**
     * 客户端放置检查
     */
    private static boolean clientCanPlace(BlockPos pos, boolean ignoreEntities) {
        if (mc.world == null) return false;
        if (pos.getY() < mc.world.getBottomY()) return false;
        if (!canReplace(pos)) return false;
        return ignoreEntities || !hasEntity(pos, true);
    }

    /**
     * 检查方块是否可以替换
     */
    private static boolean canReplace(BlockPos pos) {
        if (mc.world == null) return false;
        BlockState state = mc.world.getBlockState(pos);
        return state.isReplaceable();
    }

    /**
     * 检查位置是否有实体
     */
    private static boolean hasEntity(BlockPos pos, boolean ignoreCrystal) {
        if (mc.world == null) return false;
        Box box = new Box(pos);
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof net.minecraft.entity.player.PlayerEntity) continue;
            if (e.getBoundingBox().intersects(box)) return true;
        }
        return false;
    }

    /**
     * 在位置放置方块（带潜行处理）
     */
    public static void doPlaceAt(BlockPos pos, net.minecraft.util.Hand hand, Vec3d playerVec) {
        // 检查是否需要潜行
        boolean needSneak = needSneak(pos);
        if (needSneak) {
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));
        }

        placedPos.add(pos);

        // 获取放置侧面
        net.minecraft.util.math.Direction side = getTpPlaceSide(pos, playerVec);
        if (side == null) {
            side = net.minecraft.util.math.Direction.DOWN;
        }

        BlockPos neighbour = pos.offset(side);
        Vec3d hitPos = new Vec3d(
            pos.getX() + 0.5 + side.getVector().getX() * 0.5,
            pos.getY() + 0.5 + side.getVector().getY() * 0.5,
            pos.getZ() + 0.5 + side.getVector().getZ() * 0.5
        );

        net.minecraft.util.hit.BlockHitResult bhr = new net.minecraft.util.hit.BlockHitResult(hitPos, side.getOpposite(), neighbour, false);

        // 发送放置包（使用序列ID）
        mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket(hand, bhr, mc.player.currentScreenHandler.getRevision()));
        mc.player.swingHand(hand);

        if (needSneak) {
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
        }
    }

    /**
     * 获取 TP 放置的最佳侧面
     */
    private static net.minecraft.util.math.Direction getTpPlaceSide(BlockPos pos, Vec3d playerVec) {
        net.minecraft.util.math.Direction bestSide = null;
        double bestDist = Double.MAX_VALUE;

        for (net.minecraft.util.math.Direction side : net.minecraft.util.math.Direction.values()) {
            BlockPos neighbour = pos.offset(side);
            if (mc.world.getBlockState(neighbour).isSolid()) {
                Vec3d sideVec = new Vec3d(
                    pos.getX() + 0.5 + side.getVector().getX() * 0.5,
                    pos.getY() + 0.5 + side.getVector().getY() * 0.5,
                    pos.getZ() + 0.5 + side.getVector().getZ() * 0.5
                );
                double dist = playerVec.distanceTo(sideVec);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestSide = side;
                }
            }
        }

        return bestSide;
    }

    /**
     * 检查是否需要潜行
     */
    private static boolean needSneak(BlockPos pos) {
        if (mc.world == null) return false;
        BlockState state = mc.world.getBlockState(pos);
        return !state.isReplaceable() && !mc.player.isSneaking();
    }

    /**
     * 攻击实体
     */
    public static void doAttackEntity(net.minecraft.entity.Entity entity) {
        if (entity == null || !entity.isAlive()) return;
        mc.interactionManager.attackEntity(mc.player, entity);
        mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
    }

    /**
     * 点击方块（精确版本）
     */
    public static void clickAt(BlockPos pos, net.minecraft.util.Hand hand, Vec3d playerVec) {
        net.minecraft.util.math.Direction side = getTpPlaceSide(pos, playerVec);
        if (side == null) {
            side = net.minecraft.util.math.Direction.DOWN;
        }

        BlockPos neighbour = pos.offset(side);
        Vec3d hitPos = new Vec3d(
            pos.getX() + 0.5 + side.getVector().getX() * 0.5,
            pos.getY() + 0.5 + side.getVector().getY() * 0.5,
            pos.getZ() + 0.5 + side.getVector().getZ() * 0.5
        );

        net.minecraft.util.hit.BlockHitResult bhr = new net.minecraft.util.hit.BlockHitResult(hitPos, side.getOpposite(), neighbour, false);
        mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket(hand, bhr, mc.player.currentScreenHandler.getRevision()));
        mc.player.swingHand(hand);
    }

    /**
     * 检查是否可以从 fromVec 击中 pos（使用眼睛高度）
     */
    public static boolean canHit(Vec3d fromVec, BlockPos pos, double hitRange) {
        if (mc.player == null) return false;

        // 获取眼睛高度
        double eyeHeight = mc.player.getStandingEyeHeight();
        Vec3d fromWithEye = new Vec3d(fromVec.x, fromVec.y + eyeHeight, fromVec.z);
        Vec3d toVec = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        double distanceSq = fromWithEye.squaredDistanceTo(toVec);
        return distanceSq <= hitRange * hitRange;
    }

    /**
     * 检查是否可以从 fromVec 击中 box（使用眼睛高度）
     */
    public static boolean canHit(Vec3d fromVec, net.minecraft.util.math.Box box, double hitRange) {
        if (mc.player == null) return false;

        // 获取眼睛高度
        double eyeHeight = mc.player.getStandingEyeHeight();
        Vec3d fromWithEye = new Vec3d(fromVec.x, fromVec.y + eyeHeight, fromVec.z);
        Vec3d closest = getClosestPointToBox(fromWithEye, box);

        double distanceSq = fromWithEye.squaredDistanceTo(closest);
        return distanceSq <= hitRange * hitRange;
    }

    /**
     * 获取点到 Box 的最近点
     */
    private static Vec3d getClosestPointToBox(Vec3d point, net.minecraft.util.math.Box box) {
        double x = Math.max(box.minX, Math.min(point.x, box.maxX));
        double y = Math.max(box.minY, Math.min(point.y, box.maxY));
        double z = Math.max(box.minZ, Math.min(point.z, box.maxZ));
        return new Vec3d(x, y, z);
    }
}
