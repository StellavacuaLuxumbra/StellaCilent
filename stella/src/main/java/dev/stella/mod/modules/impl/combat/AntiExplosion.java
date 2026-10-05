package dev.stella.mod.modules.impl.combat;

import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.PacketEvent;
import dev.stella.api.utils.combat.CombatUtil;
import dev.stella.api.utils.math.ExplosionUtil;
import dev.stella.api.utils.player.InventoryUtil;
import dev.stella.api.utils.world.BlockUtil;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.impl.*;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * AntiExplosion - 反爆炸模块
 * 防止水晶爆炸和锚点爆炸伤害
 * 从简化版反编译代码移植
 */
public class AntiExplosion extends Module {
    public static AntiExplosion INSTANCE;

    // 功能开关
    private final BooleanSetting antiCrystal = this.add(new BooleanSetting("Anti-Crystal", true));
    private final BooleanSetting antiAnchor = this.add(new BooleanSetting("Anti-Anchor", true));

    // 范围设置
    private final SliderSetting range = this.add(new SliderSetting("Range", 100.0, 1.0, 200.0));
    private final SliderSetting maxDamageToSelf = this.add(new SliderSetting("MaxDamageToSelf", 3.0, 0.1, 37.0, 0.1));
    private final SliderSetting scanBorder = this.add(new SliderSetting("ScanBorder", 6, 1, 50));

    // 保护方块设置
    private final BooleanSetting allowPlaceProtect = this.add(new BooleanSetting("AllowPlaceProtect", true));
    private final SliderSetting protectStep = this.add(new SliderSetting("ProtectStep", 0.1, 0.01, 1.0, 0.01));

    // 传送设置
    private final SliderSetting moveDistance = this.add(new SliderSetting("MoveDistance", 10.0, 1.0, 128.0));
    private final BooleanSetting allowIntoVoid = this.add(new BooleanSetting("AllowIntoVoid", false));
    private final BooleanSetting backVar = this.add(new BooleanSetting("Back", false));
    private final BooleanSetting invSwap = this.add(new BooleanSetting("InvSwap", true));

    // 运行时变量
    private Vec3d safeVec = null;
    private BlockPos protectPos = null;

    public AntiExplosion() {
        super("AntiExplosion", Category.AntiCheatFree);
        this.setChinese("反爆炸");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        this.safeVec = null;
        this.protectPos = null;
    }

    @Override
    public void onDisable() {
        this.safeVec = null;
        this.protectPos = null;
    }

    @EventListener(priority = 200)
    private void onPacketReceive(PacketEvent.Receive event) {
        if (nullCheck()) return;

        // 处理锚点充能数据包
        if (event.getPacket() instanceof BlockUpdateS2CPacket && this.antiAnchor.getValue()) {
            BlockUpdateS2CPacket anchorPacket = (BlockUpdateS2CPacket) event.getPacket();
            if (anchorPacket.getState().getBlock() == Blocks.RESPAWN_ANCHOR) {
                this.doAntiAnchor(anchorPacket.getPos());
            }
        }

        // 处理实体生成数据包 - 水晶生成
        if (event.getPacket() instanceof EntitySpawnS2CPacket && this.antiCrystal.getValue()) {
            EntitySpawnS2CPacket spawnPacket = (EntitySpawnS2CPacket) event.getPacket();
            if (spawnPacket.getEntityType() == net.minecraft.entity.EntityType.END_CRYSTAL) {
                Vec3d crystalVec = new Vec3d(spawnPacket.getX(), spawnPacket.getY(), spawnPacket.getZ());
                this.doAntiCrystal(crystalVec);
            }
        }
    }

    private void doAntiAnchor(BlockPos anchorPos) {
        Vec3d anchorVec = new Vec3d(anchorPos.getX() + 0.5, anchorPos.getY() + 0.5, anchorPos.getZ() + 0.5);
        Vec3d playerVec = mc.player.getPos();

        // 距离检查
        if (playerVec.distanceTo(anchorVec) > this.range.getValue()) return;

        // 状态检查
        if (mc.player.isDead()) return;
        if (mc.isPaused()) return;

        int glowStone = this.findItem(Items.GLOWSTONE);
        int unBlock = this.findUnBlock();

        if (glowStone == -1 || unBlock == -1) return;

        AnchorData anchorData = this.getSafePosToIgniteAnchor(anchorPos, this.scanBorder.getValueInt());
        if (anchorData == null) return;

        this.safeVec = anchorData.tpVec;

        // 如果需要放置保护方块
        if (anchorData.protectPos != null && BlockUtil.canPlace(anchorData.protectPos, this.moveDistance.getValue(), true)) {
            this.protectPos = anchorData.protectPos;
            int slot = this.findItem(Items.OBSIDIAN);
            if (slot != -1) {
                this.doSwap(slot);
                BlockUtil.placeBlock(anchorData.protectPos, true, true);
                this.doSwap(slot);
            }
        }

        // 放置荧石充能
        this.doSwap(glowStone);
        BlockHitResult glowstoneResult = new BlockHitResult(anchorVec, Direction.UP, anchorPos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, glowstoneResult);
        this.doSwap(glowStone);

        // 使用不透明方块右键引爆
        this.doSwap(unBlock);
        BlockHitResult interactResult = new BlockHitResult(anchorVec, Direction.UP, anchorPos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, interactResult);
    }

    private void doAntiCrystal(Vec3d crystalVec) {
        if (mc.player.getPos().distanceTo(crystalVec) > this.range.getValue()) return;
        if (mc.player.isDead()) return;

        CrystalData data = this.getSafePosToAttackCrystal(crystalVec, this.scanBorder.getValueInt());
        if (data == null) return;

        this.safeVec = data.tpVec;

        // 放置保护方块（黑曜石）
        if (data.protectPos != null && BlockUtil.canPlace(data.protectPos, this.moveDistance.getValue(), true)) {
            this.protectPos = data.protectPos;
            int slot = this.findItem(Items.OBSIDIAN);
            if (slot != -1) {
                this.doSwap(slot);
                BlockUtil.placeBlock(data.protectPos, true, true);
                // 在保护位置上方也放置黑曜石
                BlockUtil.placeBlock(data.protectPos.up(), true, true);
                this.doSwap(slot);
            }
        }

        // 攻击水晶
        // 在附近找到生成的水晶并攻击
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof EndCrystalEntity && entity.getPos().distanceTo(crystalVec) < 1.0) {
                mc.interactionManager.attackEntity(mc.player, entity);
                mc.player.swingHand(Hand.MAIN_HAND);
                break;
            }
        }
    }

    /**
     * 获取安全的锚点引爆位置
     */
    private AnchorData getSafePosToIgniteAnchor(BlockPos anchorPos, int scanBorder) {
        Vec3d expVec = new Vec3d(anchorPos.getX() + 0.5, anchorPos.getY() + 0.5, anchorPos.getZ() + 0.5);
        int voidY = mc.world.getDimension().minY();

        List<AnchorData> vecList = new ArrayList<>();

        for (BlockPos blockPos : getSphere(scanBorder, expVec)) {
            if (blockPos.equals(anchorPos)) continue;

            Vec3d vec = new Vec3d(blockPos.getX() + 0.5, blockPos.getY(), blockPos.getZ() + 0.5);

            // 检查是否允许进入虚空
            if (!this.allowIntoVoid.getValue() && mc.player.getY() < voidY + 1.0) {
                continue;
            }

            if (!isBlinkVec(vec)) continue;
            if (!canHit(vec, anchorPos, 6.0)) continue;

            // 计算爆炸伤害
            float damage = ExplosionUtil.calculateDamage(expVec, mc.player, mc.player, 6.0f);

            // 无伤害时直接返回
            if (damage == 0.0f) {
                return new AnchorData(vec, null, damage);
            }

            // 如果伤害超过阈值，尝试寻找保护位置
            if ((double) damage > this.maxDamageToSelf.getValue()) {
                Vec3d direction = vec.subtract(expVec).normalize();
                double step = Math.min(this.protectStep.getValue(), 1.0);

                for (double t = 0.1; t < 1.0; t += step) {
                    Vec3d point = vec.add(direction.multiply(t));
                    BlockPos pointPos = new BlockPos((int) point.x, (int) point.y, (int) point.z);

                    if (!BlockUtil.canPlace(pointPos, 6.0, true)) continue;
                    if (hasEntity(pointPos)) continue;

                    vecList.add(new AnchorData(vec,
                        this.allowPlaceProtect.getValue() ? pointPos : null,
                        damage));
                    break;
                }
            } else {
                vecList.add(new AnchorData(vec, null, damage));
            }
        }

        if (!vecList.isEmpty()) {
            return vecList.stream().min(Comparator.comparingDouble(data -> data.damageToSelf)).orElse(null);
        }
        return null;
    }

    /**
     * 获取安全的攻击水晶位置
     */
    private CrystalData getSafePosToAttackCrystal(Vec3d crystalVec, int scanBorder) {
        int voidY = mc.world.getDimension().minY();

        List<CrystalData> vecList = new ArrayList<>();

        for (BlockPos pos : getSphere(scanBorder, crystalVec)) {
            Vec3d vec = new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

            // 虚空检查
            if (!this.allowIntoVoid.getValue() && mc.player.getY() < voidY + 1.0) {
                continue;
            }

            if (!isBlinkVec(vec)) continue;
            if (!canHit(vec, crystalVec, 6.0)) continue;

            float damage = ExplosionUtil.calculateDamage(crystalVec, mc.player, mc.player, 6.0f);

            if (damage == 0.0f) {
                return new CrystalData(vec, null, damage);
            }

            if ((double) damage > this.maxDamageToSelf.getValue()) {
                Vec3d direction = vec.subtract(crystalVec).normalize();
                double step = Math.min(this.protectStep.getValue(), 1.0);

                for (double t = 0.1; t < 1.0; t += step) {
                    Vec3d point = vec.add(direction.multiply(t));
                    BlockPos pointPos = new BlockPos((int) point.x, (int) point.y, (int) point.z);

                    if (!BlockUtil.canPlace(pointPos, 6.0, true)) continue;
                    if (hasEntity(pointPos)) continue;

                    vecList.add(new CrystalData(vec,
                        this.allowPlaceProtect.getValue() ? pointPos : null,
                        damage));
                    break;
                }
            } else {
                vecList.add(new CrystalData(vec, null, damage));
            }
        }

        if (!vecList.isEmpty()) {
            return vecList.stream().min(Comparator.comparingDouble(data -> data.damage)).orElse(null);
        }
        return null;
    }

    private int findItem(Item item) {
        if (this.invSwap.getValue()) {
            return InventoryUtil.findItemInventorySlot(item);
        }
        return InventoryUtil.findItem(item);
    }

    private int findUnBlock() {
        // 查找任意不透明方块（用于右键引爆）
        for (int i = 0; i < 36; i++) {
            Item item = mc.player.getInventory().getStack(i).getItem();
            if (item == Items.OBSIDIAN || item == Items.NETHERRACK || item == Items.STONE) {
                return this.invSwap.getValue() ? InventoryUtil.findItemInventorySlot(item) : InventoryUtil.findItem(item);
            }
        }
        return -1;
    }

    private void doSwap(int slot) {
        if (slot == -1) return;
        if (this.invSwap.getValue()) {
            InventoryUtil.inventorySwap(slot, mc.player.getInventory().selectedSlot);
        } else {
            InventoryUtil.switchToSlot(slot);
        }
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

    private boolean isBlinkVec(Vec3d vec) {
        double dist = mc.player.getPos().distanceTo(vec);
        return dist <= this.moveDistance.getValue() && dist >= 1.0;
    }

    private boolean canHit(Vec3d from, BlockPos toPos, double range) {
        Vec3d to = new Vec3d(toPos.getX() + 0.5, toPos.getY() + 0.5, toPos.getZ() + 0.5);
        if (from.distanceTo(to) > range) return false;
        return true;
    }

    private boolean canHit(Vec3d from, Vec3d toPos, double range) {
        if (from.distanceTo(toPos) > range) return false;
        return true;
    }

    private boolean hasEntity(BlockPos pos) {
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof net.minecraft.entity.player.PlayerEntity) continue;
            if (entity.getBoundingBox().intersects(new Box(pos))) {
                return true;
            }
        }
        return false;
    }

    // 内部数据类
    private static class AnchorData {
        final Vec3d tpVec;
        final BlockPos protectPos;
        final float damageToSelf;

        AnchorData(Vec3d tpVec, BlockPos protectPos, float damageToSelf) {
            this.tpVec = tpVec;
            this.protectPos = protectPos;
            this.damageToSelf = damageToSelf;
        }
    }

    private static class CrystalData {
        final Vec3d tpVec;
        final BlockPos protectPos;
        final float damage;

        CrystalData(Vec3d tpVec, BlockPos protectPos, float damage) {
            this.tpVec = tpVec;
            this.protectPos = protectPos;
            this.damage = damage;
        }
    }
}
