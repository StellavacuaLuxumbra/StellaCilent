package dev.stella.mod.modules.impl.combat;

import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.UpdateEvent;
import dev.stella.api.utils.player.InventoryUtil;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import dev.stella.mod.modules.settings.impl.SliderSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * ModuleLacrymiraAura - Lacrymira 重锤光环
 * 包含破甲模式 (Breach) 和普通模式 (Miss)
 * 支持分包瞬移 (Packet TP) 绕过反作弊
 */
public class ModuleLacrymiraAura extends Module {
    public static ModuleLacrymiraAura INSTANCE;

    // --- 设置项 ---
    private final SliderSetting range = this.add(new SliderSetting("Range", 50, 1, 100));
    private final SliderSetting delay = this.add(new SliderSetting("Delay", 1, 0, 10));
    private final SliderSetting maxTarget = this.add(new SliderSetting("MaxTarget", 1, 1, 10));
    
    private final BooleanSetting breach = this.add(new BooleanSetting("Breach", true)); // 破甲模式
    private final SliderSetting ignoreCount = this.add(new SliderSetting("IgnoreCount", 0, 0, 4)); // 护甲数量阈值
    private final SliderSetting breachMoveDistance = this.add(new SliderSetting("BreachDist", 128, 1, 128)); // 破甲模式发包距离
    private final SliderSetting missMoveDistance = this.add(new SliderSetting("MissDist", 10, 1, 128)); // 普通模式发包距离

    // 高度列表 (使用字符串输入或固定列表，这里简化为固定逻辑或 Slider)
    // 为简化 GUI，这里使用逻辑内置高度：Miss [10, 15, 20, 25, 30], Breach [20, 60, 120]
    // 用户可通过修改代码调整，或者后续添加 StringSetting

    private List<Entity> targets = new ArrayList<>();
    private int timer = 0;

    public ModuleLacrymiraAura() {
        super("ModuleLacrymiraAura", Category.Combat);
        this.setChinese("Lacrymira 重锤光环");
        INSTANCE = this;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (Module.nullCheck()) return;
        if (mc.interactionManager == null) return;

        if (timer > 0) {
            timer--;
            return;
        }

        updateTargets();

        if (targets.isEmpty()) return;

        // 范围检查
        boolean inRange = false;
        for (Entity e : targets) {
            if (e.squaredDistanceTo(mc.player.getPos()) <= range.getValue() * range.getValue()) {
                inRange = true;
                break;
            }
        }
        if (!inRange) return;

        FindItemResult mace = findMace();
        if (!mace.found()) return;

        Entity target = targets.remove(0); // 取出第一个目标

        Vec3d playerVec = mc.player.getPos();

        if (isPlayerWithArmor(target) && breach.getValue()) {
            // 破甲模式：高落差瞬移
            // 高度序列：20, 60, 120
            double[] attackHeights = new double[]{20.0, 60.0, 120.0};
            for (double h : attackHeights) {
                executeBreachAttack(target, playerVec, h, mace, breachMoveDistance.getValue());
            }
        } else {
            // 普通模式：低落差瞬移
            // 高度序列：10, 15, 20, 25, 30
            double[] attackHeights = new double[]{10.0, 15.0, 20.0, 25.0, 30.0};
            for (double h : attackHeights) {
                executeMissAttack(target, playerVec, h, mace, missMoveDistance.getValue());
            }
        }

        timer = (int) delay.getValue();
    }

    private void updateTargets() {
        targets = StreamSupport.stream(mc.world.getEntities().spliterator(), false)
                .filter(this::isValidTarget)
                .filter(e -> e.distanceTo(mc.player) <= range.getValue() * 1.5) // 稍大一点的搜索范围
                .sorted(Comparator.comparingDouble(e -> e.distanceTo(mc.player)))
                .limit((int) maxTarget.getValue())
                .collect(Collectors.toList());
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == mc.player) return false;
        if (!entity.isAlive()) return false;
        if (entity instanceof PlayerEntity p) {
            return !p.isSpectator() && !p.getAbilities().invulnerable;
        }
        return true;
    }

    private boolean isPlayerWithArmor(Entity target) {
        if (target instanceof PlayerEntity p) {
            int armorCount = 0;
            for (var stack : p.getInventory().armor) {
                if (!stack.isEmpty()) armorCount++;
            }
            return armorCount > (int) ignoreCount.getValue();
        }
        return false;
    }

    // 执行破甲攻击逻辑
    private void executeBreachAttack(Entity target, Vec3d playerPos, double height, FindItemResult mace, double moveDist) {
        Vec3d topVec = new Vec3d(target.getX(), target.getY() + height, target.getZ());
        Vec3d groundVec = new Vec3d(target.getX(), target.getY(), target.getZ());

        // 1. 瞬移到头顶
        warp(playerPos, topVec, moveDist);
        // 2. 瞬移到脚下 (触发下落攻击)
        warp(topVec, groundVec, moveDist);

        // 3. 切换武器并攻击
        swapAndAttack(target, mace);

        // 4. 返回原位
        warp(groundVec, playerPos, moveDist);
    }

    // 执行普通攻击逻辑
    private void executeMissAttack(Entity target, Vec3d playerPos, double height, FindItemResult mace, double moveDist) {
        Vec3d topVec = new Vec3d(target.getX(), target.getY() + height, target.getZ());
        Vec3d groundVec = new Vec3d(target.getX(), target.getY(), target.getZ());

        // 分包瞬移到目标头顶
        // 注意：为了安全，通常先计算一个安全路径，这里简化为直接直线瞬移
        // 实际 Lacrymira 逻辑会寻找 VClip 安全点
        
        // 1. 瞬移到头顶
        warp(playerPos, topVec, moveDist);
        // 2. 瞬移到目标脚下
        warp(topVec, groundVec, moveDist);

        // 3. 攻击
        swapAndAttack(target, mace);

        // 4. 返回原位
        warp(groundVec, playerPos, moveDist);
    }

    private void swapAndAttack(Entity target, FindItemResult mace) {
        if (mace.found()) {
            InventoryUtil.switchToSlot(mace.slot);
        }
        
        // 攻击
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);

        // 切回 (简单实现：切回原槽位)
        if (mace.found()) {
            InventoryUtil.switchToSlot(mace.original_slot);
        }
    }

    private FindItemResult findMace() {
        // 优先找快捷栏
        int hotbarSlot = InventoryUtil.findItem(Items.MACE);
        if (hotbarSlot != -1) {
            return new FindItemResult(hotbarSlot, hotbarSlot);
        }
        // 找背包
        int invSlot = InventoryUtil.findItemInventorySlot(Items.MACE);
        if (invSlot != -1) {
            return new FindItemResult(invSlot, mc.player.getInventory().selectedSlot);
        }
        return new FindItemResult(-1, -1);
    }

    /**
     * 核心：分包瞬移发包
     * 将长距离移动拆分为多个短距离数据包，防止被反作弊踢出
     */
    private void warp(Vec3d from, Vec3d to, double maxDist) {
        double dist = from.distanceTo(to);
        if (dist <= maxDist) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                to.x, to.y, to.z, mc.player.isOnGround()));
        } else {
            // 计算步数
            int steps = (int) Math.ceil(dist / maxDist);
            for (int i = 1; i <= steps; i++) {
                double t = (double) i / steps;
                double curX = from.x + (to.x - from.x) * t;
                double curY = from.y + (to.y - from.y) * t;
                double curZ = from.z + (to.z - from.z) * t;
                
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    curX, curY, curZ, mc.player.isOnGround()));
            }
        }
    }

    // 简单的物品查找结果类 (如果 Stella 没有)
    public static class FindItemResult {
        public final int slot;
        public final int original_slot;
        public FindItemResult(int slot, int original) {
            this.slot = slot;
            this.original_slot = original;
        }
        public boolean found() { return slot != -1; }
    }
}
