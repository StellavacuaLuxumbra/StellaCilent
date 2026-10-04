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
    private final SliderSetting moveDistance = this.add(new SliderSetting("MoveDistance", 64.0, 1.0, 128.0));
    private final BooleanSetting backVar = this.add(new BooleanSetting("Back", false));

    // 设置分组 - Calc
    private final SliderSetting range = this.add(new SliderSetting("Range", 50.0, 1.0, 200.0));
    private final SliderSetting maxDamageToSelf = this.add(new SliderSetting("MaxDamageToSelf", 6.0, 0.1, 20.0, 0.1));
    private final SliderSetting minDamageToTarget = this.add(new SliderSetting("MinDamageToTarget", 3.0, 0.1, 37.0, 0.1));
    private final SliderSetting placeDelay = this.add(new SliderSetting("PlaceDelay", 400, 0, 1000));
    private final SliderSetting scanBorder = this.add(new SliderSetting("ScanBorder", 15, 1, 50));
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

        Vec3d targetVec = target.getPos();
        List<BlockPos> sphere = this.getSphere(this.scanBorder.getValueInt(), targetVec);

        List<Data> dataList = new ArrayList<>();

        for (BlockPos pos : sphere) {
            if (pos.equals(target.getBlockPos())) continue;

            Vec3d vec = new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

            // 计算伤害
            float selfDamage = ExplosionUtil.calculateDamage(vec, mc.player, mc.player, 6.0f);
            float targetDamage = ExplosionUtil.calculateDamage(vec, target, target, 6.0f);

            if (selfDamage <= this.maxDamageToSelf.getValue() && targetDamage >= this.minDamageToTarget.getValue()) {
                Data data = new Data();
                data.anchorPos = pos;
                data.protectPos = pos.down();
                dataList.add(data);
            }
        }

        if (dataList.isEmpty()) return null;

        dataList.sort((a, b) -> Double.compare(a.anchorPos.toCenterPos().squaredDistanceTo(targetVec), b.anchorPos.toCenterPos().squaredDistanceTo(targetVec)));
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

    private static class Data {
        BlockPos anchorPos;
        BlockPos protectPos;
    }
}