package dev.stella.mod.modules.impl.anticheatfree;

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
    private final SliderSetting moveDistance = this.add(new SliderSetting("MoveDistance", 64.0, 1.0, 128.0));
    private final BooleanSetting backVar = this.add(new BooleanSetting("Back", false));

    // 设置分组 - Calc
    private final SliderSetting range = this.add(new SliderSetting("Range", 50.0, 1.0, 200.0));
    private final SliderSetting maxDamageToSelf = this.add(new SliderSetting("MaxDamageToSelf", 6.0, 0.1, 20.0, 0.1));
    private final SliderSetting minDamageToTarget = this.add(new SliderSetting("MinDamageToTarget", 3.0, 0.1, 37.0, 0.1));
    private final SliderSetting placeDelay = this.add(new SliderSetting("PlaceDelay", 400, 0, 1000));
    private final SliderSetting breakDelay = this.add(new SliderSetting("BreakDelay", 200, 0, 500));
    private final SliderSetting scanBorder = this.add(new SliderSetting("ScanBorder", 15, 1, 50));
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
        if (target == null) {
            return null;
        }

        Vec3d targetVec = target.getPos();
        List<BlockPos> sphere = this.getSphere(this.scanBorder.getValueInt(), targetVec);

        List<PlaceData> dataList = new ArrayList<>();

        for (BlockPos pos : sphere) {
            if (pos.equals(target.getBlockPos())) continue;

            Vec3d vec = new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

            // 计算伤害
            float selfDamage = ExplosionUtil.calculateDamage(vec, mc.player, mc.player, 6.0f);
            float targetDamage = ExplosionUtil.calculateDamage(vec, target, target, 6.0f);

            if (selfDamage <= this.maxDamageToSelf.getValue() && targetDamage >= this.minDamageToTarget.getValue()) {
                PlaceData data = new PlaceData();
                data.obbyPos = pos;
                data.tpVec = vec;
                dataList.add(data);
            }
        }

        if (dataList.isEmpty()) return null;

        dataList.sort((a, b) -> Double.compare(a.obbyPos.toCenterPos().squaredDistanceTo(targetVec), b.obbyPos.toCenterPos().squaredDistanceTo(targetVec)));
        return dataList.get(0);
    }

    private BreakData getFinalBreakDataFromTarget(PlayerEntity target) {
        if (target == null) {
            return null;
        }

        Vec3d targetVec = target.getPos();
        List<BlockPos> sphere = this.getSphere(this.scanBorder.getValueInt(), targetVec);

        List<BreakData> dataList = new ArrayList<>();

        for (BlockPos pos : sphere) {
            if (pos.equals(target.getBlockPos())) continue;

            Vec3d vec = new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

            // 计算伤害
            float selfDamage = ExplosionUtil.calculateDamage(vec, mc.player, mc.player, 6.0f);
            float targetDamage = ExplosionUtil.calculateDamage(vec, target, target, 6.0f);

            if (selfDamage <= this.maxDamageToSelf.getValue() && targetDamage >= this.minDamageToTarget.getValue()) {
                BreakData data = new BreakData();
                data.protect = pos;
                data.tpVec = vec;
                dataList.add(data);
            }
        }

        if (dataList.isEmpty()) return null;

        dataList.sort((a, b) -> Double.compare(a.protect.toCenterPos().squaredDistanceTo(targetVec), b.protect.toCenterPos().squaredDistanceTo(targetVec)));
        return dataList.get(0);
    }

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

    private static class PlaceData {
        BlockPos obbyPos;
        Vec3d tpVec;
    }

    private static class BreakData {
        BlockPos protect;
        Vec3d tpVec;
    }
}