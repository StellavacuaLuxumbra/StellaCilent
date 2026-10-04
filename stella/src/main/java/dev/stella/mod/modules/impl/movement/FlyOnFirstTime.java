package dev.stella.mod.modules.impl.movement;

import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.TickEvent;
import dev.stella.api.events.impl.MoveEvent;
import dev.stella.api.events.impl.EntitySpawnEvent;
import dev.stella.api.utils.math.Timer;
import dev.stella.api.utils.player.InventoryUtil;
import dev.stella.api.utils.player.PlayerUtils;
import dev.stella.api.utils.ggboy.InventoryUtils;
import dev.stella.api.utils.ggboy.Utils;
import dev.stella.api.utils.ggboy.ArmorBotton;
import dev.stella.asm.accessors.IVec3d;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import dev.stella.mod.modules.settings.impl.SliderSetting;
import dev.stella.mod.modules.settings.impl.BindSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ArmorItem;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.util.ArrayList;
import java.util.List;

/**
 * 盔甲飞行模块
 * 功能：自动切换胸甲/鞘翅、飞行控制、按键绑定、珍珠脱困等
 */
public class FlyOnFirstTime extends Module {

    // ======================== 配置项 ========================

    // 盔甲飞行模式切换按键绑定
    public final BindSetting armorFlyBind = this.add(new BindSetting("ArmorFlyBind", -1));

    // 盔甲飞行总开关
    public final BooleanSetting armorFly = this.add(new BooleanSetting("ArmorFly", false));

    // 松开按键时关闭模块
    public final BooleanSetting toggleOnRelease = this.add(new BooleanSetting("ToggleOnRelease", true));

    // 切换装备延迟（单位：毫秒）
    public final SliderSetting swapDelay = this.add(new SliderSetting("SwapDelay", 0, 0, 1000));

    // 自动跳跃（落地时自动跳起）
    public final BooleanSetting autoJump = this.add(new BooleanSetting("AutoJump", false));

    // 重置速度（关闭飞行时重置水平速度）
    public final BooleanSetting resetSpeed = this.add(new BooleanSetting("ResetSpeed", true));

    // 使用珍珠（在方块内自动丢末影珍珠脱困）
    public final BooleanSetting usePearl = this.add(new BooleanSetting("UsePearl", false));

    // 自动禁用（落地后自动关闭模块）
    public final BooleanSetting autoDisable = this.add(new BooleanSetting("AutoDisable", true));

    // 键盘控制飞行方向（用 WASD 控制飞行）
    public final BooleanSetting keyControl = this.add(new BooleanSetting("KeyControl", true));

    // 飞行模式（悬停）
    public final BooleanSetting flight = this.add(new BooleanSetting("Flight", true));

    // 烟花延迟（秒）
    public final SliderSetting fireworkDelay = this.add(new SliderSetting("FireworkDelay", 1.0, 0.0, 2.0));

    // ======================== 状态变量 ========================

    private boolean flyed = false;                    // 是否已进入飞行状态
    private Timer delayFire = new Timer();            // 烟花发射计时器
    private Timer swapDelayTimer = new Timer();       // 装备切换计时器
    private boolean shouldChangeArmor = false;        // 是否需要切换胸甲
    private boolean couldDisable = false;             // 是否允许自动关闭
    private boolean shouldCart = false;               // 是否应该使用珍珠
    private float preYaw = -999.0f;                   // 保存的偏航角
    private float prePitch = -999.0f;                 // 保存的俯仰角
    private float lastTickPrePitch = -999.0f;         // 上一 tick 的俯仰角
    private boolean flighted = false;                 // 是否处于悬停状态
    private boolean resetSpeedFlag = false;           // 是否需要重置速度

    public FlyOnFirstTime() {
        super("FlyOnFirstTime", "盔甲飞行", Module.Category.Movement);
        this.setChinese("盔甲飞行");
    }

    // ======================== 生命周期方法 ========================

    @Override
    public void onEnable() {
        if (nullCheck()) return;
        
        this.flyed = false;
        this.shouldChangeArmor = false;
        this.delayFire.setMs(0);
        this.couldDisable = false;
        this.shouldCart = false;
        this.flighted = false;
        this.preYaw = -999.0f;
        this.prePitch = -999.0f;
        this.resetSpeedFlag = false;
    }

    @Override
    public void onDisable() {
        this.flyed = false;
        this.shouldChangeArmor = false;
        this.delayFire.setMs(0);
        this.couldDisable = false;
        this.shouldCart = false;
        this.flighted = false;
        this.preYaw = -999.0f;
        this.prePitch = -999.0f;
        this.resetSpeedFlag = false;
        
        // 恢复玩家角度
        if (this.preYaw != -999.0f) {
            mc.player.setYaw(this.preYaw);
        }
        if (this.prePitch != -999.0f) {
            mc.player.setPitch(this.prePitch);
        }
    }

    // ======================== 核心逻辑 ========================

    @EventListener
    public void onTick(TickEvent event) {
        if (nullCheck()) return;
        if (!this.armorFly.getValue() && !this.shouldChangeArmor) return;

        // 重置速度逻辑
        if (this.resetSpeedFlag && this.resetSpeed.getValue()) {
            if (!mc.player.isOnGround() && !mc.player.isFallFlying()) {
                net.minecraft.util.math.Vec3d vel = mc.player.getVelocity();
                ((IVec3d) (Object) vel).setX(0.0);
                ((IVec3d) (Object) vel).setZ(0.0);
            } else {
                this.resetSpeedFlag = false;
            }
        }

        // 键盘控制飞行方向
        if (this.keyControl.getValue() && this.armorFly.getValue()) {
            this.preYaw = mc.player.getYaw();

            // A/D 键控制左右偏航
            if (mc.options.rightKey.isPressed()) {      // D 键 → 右转
                mc.player.setYaw(this.preYaw + 90.0f);
            } else if (mc.options.backKey.isPressed()) { // S 键 → 后转
                mc.player.setYaw(this.preYaw + 180.0f);
            } else if (mc.options.leftKey.isPressed()) { // A 键 → 左转
                mc.player.setYaw(this.preYaw - 90.0f);
            }

            // 保存原始俯仰角
            this.prePitch = mc.player.getPitch();

            // 空格/Ctrl 控制俯仰
            if (mc.options.jumpKey.isPressed()) {        // 空格键 → 向上
                if (this.lastTickPrePitch != -999.0f && Math.abs(this.prePitch - this.lastTickPrePitch) > 45.0f) {
                    this.prePitch = this.lastTickPrePitch;
                }
                mc.player.setPitch(-90.0f);             // 抬头
            } else if (mc.options.sneakKey.isPressed()) { // Ctrl → 向下
                mc.player.setPitch(90.0f);              // 低头
            }
        }

        // ========== 盔甲飞行主逻辑 ==========
        if (this.armorFly.getValue()) {
            this.flyed = false;

            // 珍珠脱困逻辑（在方块内时自动丢珍珠）
            if (!this.shouldCart && mc.player.isOnGround() 
                && this.isInBlock() && this.usePearl.getValue()) {
                this.shouldCart = true;
            }

            if (this.shouldCart && this.usePearl.getValue()) {
                if (this.isInBlock()) {
                    int slot = InventoryUtil.findItemInventorySlot(Items.ENDER_PEARL);
                    if (slot != -1) {
                        // 丢珍珠逻辑
                        int oldSlot = mc.player.getInventory().selectedSlot;
                        int hotbarSlot = slot >= 36 ? slot - 36 : slot;
                        
                        // 切换到珍珠槽位
                        mc.player.getInventory().selectedSlot = hotbarSlot;
                        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(hotbarSlot));
                        
                        // 发送丢珍珠的包
                        Module.sendSequencedPacket(id -> new PlayerMoveC2SPacket.LookAndOnGround(
                            mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround()
                        ));
                        
                        // 使用珍珠
                        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                        
                        // 切回原槽位
                        mc.player.getInventory().selectedSlot = oldSlot;
                        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(oldSlot));
                    } else {
                        this.shouldCart = false;  // 没有珍珠了
                    }
                } else {
                    this.shouldCart = false;  // 不在方块内了
                }
            }

            // 自动关闭逻辑：落地且穿着鞘翅时关闭模块
            if (this.autoDisable.getValue() && mc.player.isOnGround()
                && this.couldDisable
                && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
                this.armorFly.setValue(false);
                this.couldDisable = false;
                this.flighted = false;
                return;
            }

            // 飞行中且有鞘翅时，自动换胸甲（准备降落）
            if ((mc.player.isFallFlying() || !mc.player.isOnGround() || this.shouldChangeArmor)
                && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
                int bestArmorSlot = this.getBestArmor();
                if (bestArmorSlot != -1) {
                    this.swapArmor(bestArmorSlot);
                    mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(
                        mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                    this.shouldChangeArmor = false;
                }
                return;
            }

            // 不在飞行中且穿着胸甲时，自动换鞘翅（准备起飞）
            if (!mc.player.isOnGround()
                && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() instanceof ArmorItem
                && this.swapDelayTimer.passed((long) this.swapDelay.getValue())) {

                this.couldDisable = true;
                int elytraSlot = this.findElytraSlot();
                if (elytraSlot == -1) return;

                this.swapArmor(elytraSlot);
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(
                    mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));

                // 关闭烟花发射器
                // 注意：Stella 中可能需要手动处理烟花逻辑

                this.swapDelayTimer.reset();
            }

            // 地面上且装备着鞘翅/胸甲时，换最佳胸甲
            if (mc.player.isOnGround() && !mc.player.isFallFlying()
                && (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA
                || mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() instanceof ArmorItem)) {
                int bestArmorSlot = this.getBestArmor();
                if (bestArmorSlot != -1) {
                    this.swapArmor(bestArmorSlot);
                    mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(
                        mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                }
                return;
            }

            // 自动跳跃
            if (mc.player.isOnGround() && this.autoJump.getValue()) {
                mc.player.jump();
            }
        }
        // ========== 盔甲飞行关闭时的逻辑 ==========
        else {
            this.shouldCart = false;

            // 飞行中且穿着鞘翅时，标记已飞行
            if (mc.player.isFallFlying() && !this.flyed
                && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
                this.flyed = true;
                this.shouldChangeArmor = true;
            }

            // 飞行结束或不再穿着鞘翅时重置状态
            if (this.flyed && (mc.player.isOnGround()
                || mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA)) {
                this.flyed = false;
            }
        }
    }

    // ======================== TickPostEvent 处理 ========================

    @EventListener
    public void onTickPost(TickEvent event) {
        if (nullCheck()) return;
        if (!event.isPost()) return;

        if (this.keyControl.getValue() && this.armorFly.getValue()) {
            // 恢复偏航角
            if (this.preYaw != -999.0f) {
                mc.player.setYaw(this.preYaw);
                this.preYaw = -999.0f;
            }
            // 恢复俯仰角
            if (this.prePitch != -999.0f) {
                this.lastTickPrePitch = this.prePitch;
                mc.player.setPitch(this.prePitch);
                this.prePitch = -999.0f;
            }
        }
    }

    // ======================== 移动事件 ========================

    @EventListener
    public void onMove(MoveEvent event) {
        if (nullCheck()) return;

        // 珍珠脱困：将移动速度归零
        if (this.shouldCart) {
            event.setY(0.0);
            event.setX(0.0);
            event.setZ(0.0);
        }

        // 悬停模式：没有按任何移动键时，在空中悬浮
        if (this.armorFly.getValue() && this.flight.getValue()
            && !mc.options.forwardKey.isPressed()
            && !mc.options.backKey.isPressed()
            && !mc.options.leftKey.isPressed()
            && !mc.options.rightKey.isPressed()
            && !mc.options.jumpKey.isPressed()
            && !mc.options.sneakKey.isPressed()
            && !this.flighted) {

            this.flighted = true;
            event.setY(0.0);
            event.setX(0.0);
            event.setZ(0.0);
        }
    }

    // ======================== 实体生成事件 ========================

    @EventListener
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (nullCheck()) return;
        
        Entity entity = event.getEntity();
        if (entity instanceof FireworkRocketEntity) {
            FireworkRocketEntity rocket = (FireworkRocketEntity) entity;
            if (rocket.getOwner() == mc.player) {
                this.delayFire.reset();
            }
        }
    }

    // ======================== 辅助方法 ========================

    /**
     * 判断是否在固体方块内（用于珍珠脱困）
     */
    public boolean isInSolidBlock() {
        BlockPos pos = mc.player.getBlockPos();
        if (mc.world.getBlockState(pos).isSolidBlock(mc.world, pos)) {
            return true;
        }
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.offset(direction);
            if (!mc.world.getBlockState(neighbor).isSolidBlock(mc.world, neighbor)) continue;
            if (!mc.player.getBoundingBox().intersects(
                neighbor.getX(), neighbor.getY(), neighbor.getZ(),
                neighbor.getX() + 1, neighbor.getY() + 1, neighbor.getZ() + 1)) continue;
            return true;
        }
        return false;
    }

    /**
     * 判断是否在方块内（头部位置有固体方块）
     */
    public boolean isInBlock() {
        BlockPos pos = mc.player.getBlockPos();
        BlockPos above = pos.up();
        BlockPos twoAbove = pos.up(2);

        // 头部在流体中且头顶有固体方块
        if (mc.player.isSubmergedInWater() 
            && mc.world.getBlockState(above).isSolidBlock(mc.world, above)) {
            return true;
        }
        // 头部上方两个位置有固体方块
        if (mc.world.getBlockState(twoAbove).isSolidBlock(mc.world, twoAbove)) {
            return true;
        }
        return false;
    }

    /**
     * 获取最佳胸甲槽位
     */
    private int getBestArmor() {
        int bestArmorSlot = -1;
        int bestArmorScore = 0;

        // 使用 Ggboy InventoryUtils 查找所有盔甲物品
        List<Integer> armorSlots = InventoryUtils.findSoltsByItemClass(ArmorItem.class);
        if (armorSlots.isEmpty()) return -1;

        // 收集胸甲槽位
        ArrayList<Integer> chestplateSlots = new ArrayList<>();
        for (int slot : armorSlots) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!(stack.getItem() instanceof ArmorItem)) continue;
            ArmorItem armor = (ArmorItem) stack.getItem();
            if (armor.getSlotType() != EquipmentSlot.CHEST) continue;
            chestplateSlots.add(slot);
        }

        if (chestplateSlots.isEmpty()) return -1;

        // 找评分最高的胸甲
        for (int slot : chestplateSlots) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            int score = this.getScore(stack);
            if (score > bestArmorScore) {
                bestArmorScore = score;
                bestArmorSlot = slot;
            }
        }
        return bestArmorSlot;
    }

    /**
     * 计算胸甲评分（基于保护值和耐久）
     */
    private int getScore(ItemStack itemStack) {
        if (!(itemStack.getItem() instanceof ArmorItem)) return 0;

        int score = 0;

        // 使用 Ggboy Utils 获取附魔
        Object2IntMap<RegistryEntry<net.minecraft.enchantment.Enchantment>> enchantments = new Object2IntArrayMap<>();
        Utils.getEnchantments(itemStack, enchantments);

        // 获取各附魔等级
        RegistryEntry<net.minecraft.enchantment.Enchantment> protection = 
            mc.world.getRegistryManager().get(Enchantments.PROTECTION.getRegistryRef())
                .getEntry(Enchantments.PROTECTION).get();
        RegistryEntry<net.minecraft.enchantment.Enchantment> projectileProtection = 
            mc.world.getRegistryManager().get(Enchantments.PROJECTILE_PROTECTION.getRegistryRef())
                .getEntry(Enchantments.PROJECTILE_PROTECTION).get();
        RegistryEntry<net.minecraft.enchantment.Enchantment> fireProtection = 
            mc.world.getRegistryManager().get(Enchantments.FIRE_PROTECTION.getRegistryRef())
                .getEntry(Enchantments.FIRE_PROTECTION).get();
        RegistryEntry<net.minecraft.enchantment.Enchantment> blastProtection = 
            mc.world.getRegistryManager().get(Enchantments.BLAST_PROTECTION.getRegistryRef())
                .getEntry(Enchantments.BLAST_PROTECTION).get();
        RegistryEntry<net.minecraft.enchantment.Enchantment> thorns = 
            mc.world.getRegistryManager().get(Enchantments.THORNS.getRegistryRef())
                .getEntry(Enchantments.THORNS).get();
        RegistryEntry<net.minecraft.enchantment.Enchantment> unbreaking = 
            mc.world.getRegistryManager().get(Enchantments.UNBREAKING.getRegistryRef())
                .getEntry(Enchantments.UNBREAKING).get();

        score += Utils.getEnchantmentLevel(enchantments, protection);
        score += Utils.getEnchantmentLevel(enchantments, projectileProtection);
        score += Utils.getEnchantmentLevel(enchantments, fireProtection);
        score += Utils.getEnchantmentLevel(enchantments, blastProtection);
        score += Utils.getEnchantmentLevel(enchantments, thorns);
        score += 3 * Utils.getEnchantmentLevel(enchantments, unbreaking);

        // 护甲值
        ArmorItem armor = (ArmorItem) itemStack.getItem();
        score += armor.getProtection();

        return score;
    }

    /**
     * 查找鞘翅槽位
     */
    private int findElytraSlot() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.ELYTRA) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 交换盔甲槽位
     */
    private void swapArmor(int slot) {
        InventoryUtils.inventorySwapArmor(slot, ArmorBotton.CHESTPLATE);
    }

    /**
     * 使用烟花火箭
     */
    private void useFirework() {
        int fireworkSlot = InventoryUtil.findItem(Items.FIREWORK_ROCKET);
        if (fireworkSlot == -1) return;

        int oldSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = fireworkSlot;
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(fireworkSlot));
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.getInventory().selectedSlot = oldSlot;
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(oldSlot));
    }

    public String getInfo() {
        return this.armorFly.getValue() ? "开启" : "关闭";
    }
}
