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
import dev.stella.mod.modules.settings.impl.*;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * TpAnchorAura - 瞬移重生锚光环
 * 从简化版反编译代码移植
 */
public class TpAnchorAura extends Module {
    public static TpAnchorAura INSTANCE;

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
    private final SliderSetting scanBorder = this.add(new SliderSetting("ScanBorder", 3, 1, 50));
    private final BooleanSetting allowPlaceProtect = this.add(new BooleanSetting("AllowPlaceProtect", true));
    private final SliderSetting protectStep = this.add(new SliderSetting("ProtectStep", 0.1, 0.01, 1.0, 0.01));

    // 设置分组 - Render
    private final BooleanSetting rotate = this.add(new BooleanSetting("Rotate", true));
    private final BooleanSetting packet = this.add(new BooleanSetting("Packet", true));

    // 运行时变量
    private PlayerEntity target;
    private final Timer placeTimer = new Timer();

    public TpAnchorAura() {
        super("TpAnchorAura", Category.AntiCheatFree);
        this.setChinese("TP锚光环");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        this.placeTimer.reset();
        this.target = null;
    }

    @Override
    public void onDisable() {
        this.target = null;
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

        // 查找所需物品的槽位
        int anchor = this.findItem(Items.RESPAWN_ANCHOR);
        int glowStone = this.findItem(Items.GLOWSTONE);
        int obby = this.findItem(Items.OBSIDIAN);

        // 检查必要物品是否存在
        if (anchor == -1 || glowStone == -1) {
            return;
        }

        int old = mc.player.getInventory().selectedSlot;

        // 检查放置延迟
        if (!this.placeTimer.passed(this.placeDelay.getValueInt())) {
            return;
        }

        Data data = this.getFinalDataFromTarget(this.target);
        if (data == null) {
            return;
        }

        // 执行放置逻辑
        doPlaceAnchor(data.anchorPos, data.protectPos, anchor, glowStone, obby, old);

        this.placeTimer.reset();
    }

    private void doPlaceAnchor(BlockPos anchorPos, BlockPos protectPos, int anchorSlot, int glowStoneSlot, int obbySlot, int oldSlot) {
        // 1. 放置锚点
        if (BlockUtil.canPlace(anchorPos, this.moveDistance.getValue(), true)) {
            this.doSwap(anchorSlot);
            BlockUtil.placeBlock(anchorPos, this.rotate.getValue(), this.packet.getValue());
            if (this.invSwap.getValue()) {
                this.doSwap(anchorSlot);
            } else {
                this.doSwap(oldSlot);
            }
        }

        // 2. 如果需要保护，放置黑曜石
        if (protectPos != null && obbySlot != -1 && BlockUtil.canPlace(protectPos, this.moveDistance.getValue(), true)) {
            this.doSwap(obbySlot);
            BlockUtil.placeBlock(protectPos, this.rotate.getValue(), this.packet.getValue());
            if (this.invSwap.getValue()) {
                this.doSwap(obbySlot);
            } else {
                this.doSwap(oldSlot);
            }
        }

        // 3. 放置荧石（充能锚点）
        this.doSwap(glowStoneSlot);
        
        // 右键锚点进行充能
        BlockHitResult glowstoneResult = new BlockHitResult(
            new Vec3d(anchorPos.getX() + 0.5, anchorPos.getY() + 0.5, anchorPos.getZ() + 0.5),
            Direction.UP,
            anchorPos,
            false
        );
        
        if (this.packet.getValue()) {
            Module.sendSequencedPacket(id -> new net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, glowstoneResult, id));
        } else {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, glowstoneResult);
        }

        if (this.invSwap.getValue()) {
            this.doSwap(glowStoneSlot);
        } else {
            this.doSwap(oldSlot);
        }

        // 4. 右键锚点引爆（使用任意不透明方块）
        // 这里使用空手交互来触发锚点爆炸
        BlockHitResult interactResult = new BlockHitResult(
            new Vec3d(anchorPos.getX() + 0.5, anchorPos.getY() + 0.5, anchorPos.getZ() + 0.5),
            Direction.UP,
            anchorPos,
            false
        );
        
        if (this.packet.getValue()) {
            Module.sendSequencedPacket(id -> new net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, interactResult, id));
        } else {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, interactResult);
        }
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

    private Data getFinalDataFromTarget(PlayerEntity target) {
        if (target == null) {
            return null;
        }

        BlockPos best = null;
        float bestDamage = 0.0f;

        // 遍历所有可能放置锚点的位置
        for (BlockPos anchorPos : getPossibleAnchorPositions(target, this.scanBorder.getValueInt())) {
            float targetDamage = calculateAnchorDamage(anchorPos, target, true);
            if ((double) targetDamage < this.minDamageToTarget.getValue() || targetDamage <= bestDamage) {
                continue;
            }

            // 检查自伤
            float selfDamage = calculateAnchorDamage(anchorPos, mc.player, false);
            if ((double) selfDamage > this.maxDamageToSelf.getValue()) {
                continue;
            }

            best = anchorPos;
            bestDamage = targetDamage;
        }

        if (best == null) {
            return null;
        }

        // 获取传送点位置和自身伤害
        Vec3dAndDamage tpSlotMinDamage = this.getTpSlotMinDamageFromAnchorPos(best, this.scanBorder.getValueInt());
        if (tpSlotMinDamage == null) {
            return null;
        }

        // 如果自身伤害过高且允许放置保护方块
        BlockPos protectPos = null;
        if ((double) tpSlotMinDamage.damage > this.maxDamageToSelf.getValue() && this.allowPlaceProtect.getValue()) {
            protectPos = getProtectPosition(best, tpSlotMinDamage.vec);
        }

        return new Data(best, tpSlotMinDamage.vec, protectPos, tpSlotMinDamage.damage);
    }

    private Vec3dAndDamage getTpSlotMinDamageFromAnchorPos(BlockPos anchorPos, int scanBorder) {
        Vec3d nearestPos = null;
        float minDamage = Float.MAX_VALUE;

        for (BlockPos pos : getSphere(scanBorder, anchorPos)) {
            if (pos.equals(anchorPos)) continue;

            Vec3d vec = new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

            // 检查是否可以传送到该位置
            if (!isBlinkVec(vec)) continue;
            if (!canHit(vec, anchorPos, 6.0)) continue;

            float damageToSelf = calculateAnchorDamage(anchorPos, mc.player, vec);
            if (damageToSelf >= minDamage) continue;

            nearestPos = vec;
            minDamage = damageToSelf;
        }

        if (nearestPos == null) {
            return null;
        }

        return new Vec3dAndDamage(nearestPos, minDamage);
    }

    private List<BlockPos> getPossibleAnchorPositions(PlayerEntity target, int border) {
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

    private List<BlockPos> getSphere(int radius, BlockPos center) {
        List<BlockPos> positions = new ArrayList<>();
        int centerX = center.getX();
        int centerY = center.getY();
        int centerZ = center.getZ();

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

    private BlockPos getProtectPosition(BlockPos anchorPos, Vec3d tpVec) {
        Vec3d anchorCenter = new Vec3d(anchorPos.getX() + 0.5, anchorPos.getY() + 0.5, anchorPos.getZ() + 0.5);
        Vec3d direction = tpVec.subtract(anchorCenter).normalize();
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

    private float calculateAnchorDamage(BlockPos anchorPos, PlayerEntity player, boolean isTarget) {
        Vec3d expVec = new Vec3d(anchorPos.getX() + 0.5, anchorPos.getY() + 0.5, anchorPos.getZ() + 0.5);
        return ExplosionUtil.calculateDamage(expVec, player, mc.player, 6.0f);
    }

    private float calculateAnchorDamage(BlockPos anchorPos, PlayerEntity player, Vec3d playerPos) {
        Vec3d expVec = new Vec3d(anchorPos.getX() + 0.5, anchorPos.getY() + 0.5, anchorPos.getZ() + 0.5);
        return ExplosionUtil.calculateDamage(expVec, player, player, 6.0f);
    }

    private boolean isBlinkVec(Vec3d vec) {
        double dist = mc.player.getPos().distanceTo(vec);
        return dist <= this.moveDistance.getValue() && dist >= 1.0;
    }

    private boolean canHit(Vec3d from, BlockPos toPos, double range) {
        Vec3d to = new Vec3d(toPos.getX() + 0.5, toPos.getY() + 0.5, toPos.getZ() + 0.5);
        if (from.distanceTo(to) > range) return false;
        return true;
    }

    // 数据类
    private static class Data {
        final BlockPos anchorPos;
        final Vec3d tpVec;
        final BlockPos protectPos;
        final float damage;

        Data(BlockPos anchorPos, Vec3d tpVec, BlockPos protectPos, float damage) {
            this.anchorPos = anchorPos;
            this.tpVec = tpVec;
            this.protectPos = protectPos;
            this.damage = damage;
        }
    }

    private static class Vec3dAndDamage {
        final Vec3d vec;
        final float damage;

        Vec3dAndDamage(Vec3d vec, float damage) {
            this.vec = vec;
            this.damage = damage;
        }
    }
}
