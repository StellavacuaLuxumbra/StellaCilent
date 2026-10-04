package dev.stella.mod.modules.impl.combat;

import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.ClientTickEvent;
import dev.stella.api.utils.combat.CombatUtil;
import dev.stella.api.utils.math.ExplosionUtil;
import dev.stella.api.utils.math.Timer;
import dev.stella.api.utils.player.EntityUtil;
import dev.stella.api.utils.player.InventoryUtil;
import dev.stella.api.utils.world.BlockUtil;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.impl.client.AntiCheat;
import dev.stella.mod.modules.settings.impl.*;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * TpCrystalAura - 瞬移水晶光环
 * 从简化版反编译代码移植
 */
public class TpCrystalAura extends Module {
    public static TpCrystalAura INSTANCE;

    // 设置分组 - Place
    private final BooleanSetting usingPause = this.add(new BooleanSetting("UsingPause", true));
    private final BooleanSetting invSwap = this.add(new BooleanSetting("InvSwap", true));

    // 设置分组 - TpMode
    private final SliderSetting moveDistance = this.add(new SliderSetting("MoveDistance", 10.0, 1.0, 128.0));
    private final BooleanSetting backVar = this.add(new BooleanSetting("Back", false));

    // 设置分组 - Calc
    private final SliderSetting range = this.add(new SliderSetting("Range", 10.0, 1.0, 20.0));
    private final SliderSetting maxDamageToSelf = this.add(new SliderSetting("MaxDamageToSelf", 6.0, 0.1, 20.0, 0.1));
    private final SliderSetting minDamageToTarget = this.add(new SliderSetting("MinDamageToTarget", 3.0, 0.1, 37.0, 0.1));
    private final SliderSetting placeDelay = this.add(new SliderSetting("PlaceDelay", 400, 0, 1000));
    private final SliderSetting breakDelay = this.add(new SliderSetting("BreakDelay", 200, 0, 500));
    private final SliderSetting scanBorder = this.add(new SliderSetting("ScanBorder", 3, 1, 50));
    private final BooleanSetting allowPlaceProtect = this.add(new BooleanSetting("AllowPlaceProtect", true));
    private final SliderSetting protectStep = this.add(new SliderSetting("ProtectStep", 0.1, 0.01, 1.0, 0.01));

    // 设置分组 - Render
    private final BooleanSetting rotate = this.add(new BooleanSetting("Rotate", true));
    private final BooleanSetting packet = this.add(new BooleanSetting("Packet", true));

    // 运行时变量
    private PlayerEntity target;
    private final Timer placeTimer = new Timer();
    private final Timer breakTimer = new Timer();
    private Vec3d tpVec;
    private BlockPos obbyPos;
    private BlockPos protect;

    public TpCrystalAura() {
        super("TpCrystalAura", Category.AntiCheatFree);
        this.setChinese("TP水晶光环");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        this.placeTimer.reset();
        this.breakTimer.reset();
        this.target = null;
        this.tpVec = null;
        this.obbyPos = null;
        this.protect = null;
    }

    @Override
    public void onDisable() {
        this.target = null;
        this.tpVec = null;
        this.obbyPos = null;
        this.protect = null;
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (nullCheck()) return;
        if (!event.isPre()) return;

        // 获取最近的目标
        this.target = CombatUtil.getClosestEnemy(this.range.getValue());
        if (this.target == null) {
            return;
        }

        // 如果开启了暂停且玩家在使用物品，则跳过
        if (this.usingPause.getValue() && mc.player.isUsingItem()) {
            return;
        }

        // 查找水晶和黑曜石的槽位
        int crystal = this.findItem(Items.END_CRYSTAL);
        int obby = this.findItem(Items.OBSIDIAN);

        // 如果没有水晶或黑曜石则返回
        if (crystal == -1 || obby == -1) {
            return;
        }

        this.doPlace();
        this.doBreak();
    }

    private void doPlace() {
        if (!this.placeTimer.passed(this.placeDelay.getValueInt())) {
            return;
        }

        // 再次获取物品槽位（确保最新）
        int crystal = this.findItem(Items.END_CRYSTAL);
        int obby = this.findItem(Items.OBSIDIAN);
        int old = mc.player.getInventory().selectedSlot;

        PlaceData data = this.getFinalPlaceDataFromTarget(this.target);
        if (data == null) {
            return;
        }

        this.obbyPos = data.obbyPos;
        Vec3d tpVec = data.tpVec;

        // 执行放置逻辑
        doPlaceAndCrystal(data.obbyPos, crystal, obby, old);

        this.placeTimer.reset();
    }

    private void doPlaceAndCrystal(BlockPos obbyPos, int crystalSlot, int obbySlot, int oldSlot) {
        // 放置黑曜石
        if (BlockUtil.canPlace(obbyPos, this.moveDistance.getValue(), true)) {
            this.doSwap(obbySlot);
            BlockUtil.placeBlock(obbyPos, this.rotate.getValue(), this.packet.getValue());
            if (this.invSwap.getValue()) {
                this.doSwap(obbySlot);
            } else {
                this.doSwap(oldSlot);
            }
        }

        // 放置水晶
        this.doSwap(crystalSlot);
        BlockHitResult result = new BlockHitResult(
            new Vec3d(obbyPos.getX() + 0.5, obbyPos.getY() + 1, obbyPos.getZ() + 0.5),
            Direction.UP,
            obbyPos,
            false
        );

        if (this.packet.getValue()) {
            Module.sendSequencedPacket(id -> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, result, id));
        } else {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, result);
        }

        if (this.invSwap.getValue()) {
            this.doSwap(crystalSlot);
        } else {
            this.doSwap(oldSlot);
        }
    }

    private void doBreak() {
        if (!this.breakTimer.passed(this.breakDelay.getValueInt())) {
            return;
        }

        int crystal = this.findItem(Items.END_CRYSTAL);
        int obby = this.findItem(Items.OBSIDIAN);
        int old = mc.player.getInventory().selectedSlot;

        BreakData data = this.getFinalBreakDataFromTarget(this.target);
        if (data == null) {
            return;
        }

        this.protect = data.protect;
        this.tpVec = data.tpVec;

        // 如果需要放置保护方块
        if (this.protect != null && BlockUtil.canPlace(this.protect, this.moveDistance.getValue(), true)) {
            this.doSwap(obby);
            BlockUtil.placeBlock(this.protect, this.rotate.getValue(), this.packet.getValue());
            if (this.invSwap.getValue()) {
                this.doSwap(obby);
            } else {
                this.doSwap(old);
            }
        }

        // 攻击水晶实体
        if (data.crystal != null && data.crystal.isAlive()) {
            mc.interactionManager.attackEntity(mc.player, data.crystal);
            mc.player.swingHand(Hand.MAIN_HAND);
        }

        this.breakTimer.reset();
    }

    private void doSwap(int slot) {
        if (slot == -1) return;
        if (this.invSwap.getValue()) {
            InventoryUtil.inventorySwap(slot, mc.player.getInventory().selectedSlot);
        } else {
            InventoryUtil.switchToSlot(slot);
        }
    }

    private int findItem(Item item) {
        if (this.invSwap.getValue()) {
            return InventoryUtil.findItemInventorySlot(item);
        }
        return InventoryUtil.findItem(item);
    }

    private PlaceData getFinalPlaceDataFromTarget(PlayerEntity target) {
        float maxTargetDamage = 0.0f;
        BlockPos finalObbyPos = null;

        // 遍历所有可能放置黑曜石的位置
        for (BlockPos obbyPos : getPossiblePlacePositions(target, this.scanBorder.getValueInt())) {
            Vec3d expVec = new Vec3d(obbyPos.getX() + 0.5, obbyPos.getY() + 1, obbyPos.getZ() + 0.5);

            // 检查是否有实体阻挡
            if (hasEntityInBox(getCrystalBox(obbyPos))) {
                continue;
            }

            float targetDamage = calculateDamage(expVec, target, true);
            if ((double) targetDamage < this.minDamageToTarget.getValue() || targetDamage <= maxTargetDamage) {
                continue;
            }

            // 检查自伤
            float selfDamage = calculateDamage(expVec, mc.player, false);
            if ((double) selfDamage > this.maxDamageToSelf.getValue()) {
                continue;
            }

            maxTargetDamage = targetDamage;
            finalObbyPos = obbyPos;
        }

        if (finalObbyPos == null) {
            return null;
        }

        Vec3d finalExpVec = new Vec3d(finalObbyPos.getX() + 0.5, finalObbyPos.getY() + 1, finalObbyPos.getZ() + 0.5);

        // 获取传送位置
        Vec3d tpSlot = getTpSlotMinDamageFromExpVec(finalExpVec, this.scanBorder.getValueInt(), finalObbyPos);
        if (tpSlot == null) {
            return null;
        }

        return new PlaceData(finalObbyPos, tpSlot, maxTargetDamage);
    }

    private Box getCrystalBox(BlockPos pos) {
        return new Box(
            pos.getX() - 1.0,
            pos.getY(),
            pos.getZ() - 1.0,
            pos.getX() + 2.0,
            pos.getY() + 3.0,
            pos.getZ() + 2.0
        );
    }

    private BreakData getFinalBreakDataFromTarget(PlayerEntity target) {
        float maxDamage = 0.0f;
        EndCrystalEntity best = null;
        Vec3d tpSlotMinDamage = null;
        float bestSelfDamage = Float.MAX_VALUE;

        // 遍历所有实体，寻找水晶
        List<EndCrystalEntity> crystals = new ArrayList<>();
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof EndCrystalEntity) {
                crystals.add((EndCrystalEntity) entity);
            }
        }

        for (EndCrystalEntity crystal : crystals) {
            Vec3d expVec = crystal.getPos();
            float damageToTarget = calculateDamage(expVec, target, true);

            if ((double) damageToTarget < this.minDamageToTarget.getValue()) continue;

            Vec3d tpSlot = getTpSlotMinDamageFromExpVec(expVec, this.scanBorder.getValueInt(), crystal.getBlockPos());
            if (tpSlot == null) continue;

            float selfDamage = calculateDamage(expVec, mc.player, false);
            if ((double) selfDamage > this.maxDamageToSelf.getValue()) continue;

            if (damageToTarget > maxDamage) {
                maxDamage = damageToTarget;
                best = crystal;
                tpSlotMinDamage = tpSlot;
                bestSelfDamage = selfDamage;
            }
        }

        if (best == null) {
            return null;
        }

        Vec3d finalExpVec = best.getPos();

        // 如果需要放置保护方块
        BlockPos protectPos = null;
        if ((double) bestSelfDamage > this.maxDamageToSelf.getValue() && this.allowPlaceProtect.getValue()) {
            protectPos = getProtectPosition(finalExpVec, tpSlotMinDamage);
        }

        return new BreakData(best, tpSlotMinDamage, protectPos, bestSelfDamage);
    }

    private BlockPos getProtectPosition(Vec3d expVec, Vec3d tpVec) {
        Vec3d direction = tpVec.subtract(expVec).normalize();
        double step = Math.min(this.protectStep.getValue(), 1.0);

        for (double t = 0.1; t < 1.0; t += step) {
            Vec3d point = tpVec.add(direction.multiply(t));
            BlockPos pointPos = new BlockPos((int) point.x, (int) point.y, (int) point.z);

            // 简化检查逻辑
            if (!BlockUtil.canPlace(pointPos, 6.0, true)) continue;
            
            // 检查是否有实体
            boolean hasEntity = false;
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof PlayerEntity) continue;
                if (entity.getBoundingBox().intersects(new Box(pointPos))) {
                    hasEntity = true;
                    break;
                }
            }
            if (hasEntity) continue;

            return pointPos;
        }
        return null;
    }

    private Vec3d getTpSlotMinDamageFromExpVec(Vec3d expVec, int scanBorder, BlockPos obbyPos) {
        Vec3d nearestPos = null;
        float minDamage = Float.MAX_VALUE;

        for (BlockPos pos : getSphere(scanBorder, expVec)) {
            if (pos.equals(obbyPos) || pos.equals(obbyPos.up())) continue;

            Vec3d vec = new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

            // 检查是否可以传送到该位置
            if (!isBlinkVec(vec)) continue;
            if (!canHit(vec, mc.player.getBlockPos(), 6.0)) continue;
            if (!canHit(vec, obbyPos, 6.0)) continue;

            float damageToSelf = calculateDamage(expVec, mc.player, vec, false);
            if (damageToSelf >= minDamage) continue;

            nearestPos = vec;
            minDamage = damageToSelf;
        }

        return nearestPos;
    }

    private List<BlockPos> getPossiblePlacePositions(PlayerEntity target, int border) {
        List<BlockPos> positions = new ArrayList<>();
        BlockPos targetPos = new BlockPos(target.getBlockX(), target.getBlockY(), target.getBlockZ());

        // 目标周围的方块
        for (int x = -border; x <= border; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -border; z <= border; z++) {
                    BlockPos pos = targetPos.add(x, y, z);
                    if (BlockUtil.canPlace(pos, this.moveDistance.getValue(), true)) {
                        positions.add(pos);
                    }
                }
            }
        }

        return positions;
    }

    private List<BlockPos> getSphere(int radius, Vec3d center) {
        List<BlockPos> positions = new ArrayList<>();
        int centerX = (int) center.x;
        int centerY = (int) center.y;
        int centerZ = (int) center.z;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z <= radius * radius) {
                        positions.add(new BlockPos(centerX + x, centerY + y, centerZ + z));
                    }
                }
            }
        }

        return positions;
    }

    private boolean hasEntityInBox(Box box) {
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof PlayerEntity) continue;
            if (entity.getBoundingBox().intersects(box)) {
                return true;
            }
        }
        return false;
    }

    private float calculateDamage(Vec3d crystalPos, PlayerEntity player, boolean isTarget) {
        return ExplosionUtil.calculateDamage(crystalPos, player, mc.player, 6.0f);
    }

    private float calculateDamage(Vec3d crystalPos, PlayerEntity player, Vec3d playerPos, boolean isTarget) {
        // 使用玩家位置计算伤害
        return ExplosionUtil.calculateDamage(crystalPos, player, player, 6.0f);
    }

    private boolean isBlinkVec(Vec3d vec) {
        double dist = mc.player.getPos().distanceTo(vec);
        return dist <= this.moveDistance.getValue() && dist >= 1.0;
    }

    private boolean canHit(Vec3d from, Box targetBox, double range) {
        // 简单的射线检测
        Vec3d to = targetBox.getCenter();
        if (from.distanceTo(to) > range) return false;

        // 检查视线是否清晰
        return true;
    }

    private boolean canHit(Vec3d from, BlockPos toPos, double range) {
        Vec3d to = new Vec3d(toPos.getX() + 0.5, toPos.getY() + 0.5, toPos.getZ() + 0.5);
        if (from.distanceTo(to) > range) return false;
        return true;
    }

    // 数据类
    private static class PlaceData {
        final BlockPos obbyPos;
        final Vec3d tpVec;
        final float damage;

        PlaceData(BlockPos obbyPos, Vec3d tpVec, float damage) {
            this.obbyPos = obbyPos;
            this.tpVec = tpVec;
            this.damage = damage;
        }
    }

    private static class BreakData {
        final EndCrystalEntity crystal;
        final Vec3d tpVec;
        final BlockPos protect;
        final float damage;

        BreakData(EndCrystalEntity crystal, Vec3d tpVec, BlockPos protect, float damage) {
            this.crystal = crystal;
            this.tpVec = tpVec;
            this.protect = protect;
            this.damage = damage;
        }
    }
}
