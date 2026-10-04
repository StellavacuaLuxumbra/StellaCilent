package dev.stella.mod.modules.impl.anticheatfree;

import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.PacketEvent;
import dev.stella.api.utils.combat.CombatUtil;
import dev.stella.api.utils.math.ExplosionUtil;
import dev.stella.api.utils.player.InventoryUtil;
import dev.stella.api.utils.world.BlockUtil;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.impl.client.AntiCheat;
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
    private final SliderSetting scanBorder = this.add(new SliderSetting("ScanBorder", 15, 1, 50));

    // 保护方块设置
    private final BooleanSetting allowPlaceProtect = this.add(new BooleanSetting("AllowPlaceProtect", true));
    private final SliderSetting protectStep = this.add(new SliderSetting("ProtectStep", 0.1, 0.01, 1.0, 0.01));

    // 传送设置
    private final SliderSetting moveDistance = this.add(new SliderSetting("MoveDistance", 64.0, 1.0, 128.0));
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
            if (!canHit(vec, new Vec3d(anchorPos.getX() + 0.5, anchorPos.getY() + 0.5, anchorPos.getZ() + 0.5), 6.0)) continue;

            // 计算爆炸伤害
            float damage = ExplosionUtil.calculateDamage(vec, mc.player, mc.player, 6.0f);
            if (damage <= this.maxDamageToSelf.getValue()) {
                AnchorData data = new AnchorData();
                data.tpVec = vec;
                data.protectPos = blockPos;
                vecList.add(data);
            }
        }

        if (vecList.isEmpty()) return null;

        vecList.sort(Comparator.comparingDouble(a -> a.tpVec.distanceTo(mc.player.getPos())));
        return vecList.get(0);
    }

    /**
     * 获取安全的水晶攻击位置
     */
    private CrystalData getSafePosToAttackCrystal(Vec3d crystalVec, int scanBorder) {
        int voidY = mc.world.getDimension().minY();

        List<CrystalData> vecList = new ArrayList<>();

        for (BlockPos blockPos : getSphere(scanBorder, crystalVec)) {
            Vec3d vec = new Vec3d(blockPos.getX() + 0.5, blockPos.getY(), blockPos.getZ() + 0.5);

            // 检查是否允许进入虚空
            if (!this.allowIntoVoid.getValue() && mc.player.getY() < voidY + 1.0) {
                continue;
            }

            if (!isBlinkVec(vec)) continue;
            if (!canHit(vec, new Vec3d(crystalVec.x, crystalVec.y, crystalVec.z), 6.0)) continue;

            // 计算爆炸伤害
            float damage = ExplosionUtil.calculateDamage(vec, mc.player, mc.player, 6.0f);
            if (damage <= this.maxDamageToSelf.getValue()) {
                CrystalData data = new CrystalData();
                data.tpVec = vec;
                data.protectPos = blockPos;
                vecList.add(data);
            }
        }

        if (vecList.isEmpty()) return null;

        vecList.sort(Comparator.comparingDouble(a -> a.tpVec.distanceTo(mc.player.getPos())));
        return vecList.get(0);
    }

    /**
     * 判断是否可以到达该位置
     */
    private boolean isBlinkVec(Vec3d vec) {
        if (vec.y < mc.world.getDimension().minY()) return false;
        if (vec.y > mc.world.getDimension().height()) return false;
        return true;
    }

    /**
     * 判断是否可以攻击到目标
     */
    private boolean canHit(Vec3d from, Vec3d to, double range) {
        return from.distanceTo(to) <= range;
    }

    /**
     * 获取球形区域内的所有方块
     */
    private List<BlockPos> getSphere(int radius, Vec3d center) {
        List<BlockPos> list = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = new BlockPos((int) (center.x + x), (int) (center.y + y), (int) (center.z + z));
                    if (pos.toCenterPos().squaredDistanceTo(center) <= radius * radius) {
                        list.add(pos);
                    }
                }
            }
        }
        return list;
    }

    /**
     * 查找物品
     */
    private int findItem(Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 查找不透明方块
     */
    private int findUnBlock() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getMaxDamage() > 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 切换物品
     */
    private void doSwap(int slot) {
        if (slot == -1) return;
        if (mc.player.getInventory().selectedSlot != slot) {
            mc.player.getInventory().selectedSlot = slot;
        }
    }

    /**
     * 锚点数据
     */
    private static class AnchorData {
        Vec3d tpVec;
        BlockPos protectPos;
    }

    /**
     * 水晶数据
     */
    private static class CrystalData {
        Vec3d tpVec;
        BlockPos protectPos;
    }
}