package dev.stella.mod.modules.impl.movement;

import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.JumpEvent;
import dev.stella.api.events.impl.MoveEvent;
import dev.stella.api.events.impl.TravelEvent;
import dev.stella.api.events.impl.UpdateRotateEvent;
import dev.stella.api.events.impl.KeyboardInputEvent;
import dev.stella.api.utils.math.Timer;
import dev.stella.api.utils.eflyRotation.RotationManager;
import dev.stella.api.utils.eflyRotation.RotationUtils;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.impl.player.MiddleClick;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import dev.stella.mod.modules.settings.impl.EnumSetting;
import dev.stella.mod.modules.settings.impl.SliderSetting;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

public class EFly extends Module {
    public static EFly INSTANCE;

    public enum Mode {
        Creative, Grim
    }

    // ==========================================
    // 模块设置
    // ==========================================
    public final EnumSetting<Mode> mode = this.add(new EnumSetting<>("Mode", Mode.Grim));

    private final SliderSetting horizontalSpeed = this.add(new SliderSetting("Horizontal", 3.0, 1.0, 5.0, 0.1, () -> this.mode.getValue() == Mode.Creative));
    private final SliderSetting verticalSpeed = this.add(new SliderSetting("Vertical", 3.0, 1.0, 5.0, 0.1, () -> this.mode.getValue() == Mode.Creative));
    private final BooleanSetting antiKick = this.add(new BooleanSetting("AntiKick", false, () -> this.mode.getValue() == Mode.Creative));

    public final BooleanSetting pitch = this.add(new BooleanSetting("Pitch", false, () -> this.mode.getValue() == Mode.Grim));
    public final BooleanSetting firework = this.add(new BooleanSetting("Firework", false, () -> this.mode.getValue() == Mode.Grim));
    
    // 切甲频率设置 (单位：Ticks)
    private final SliderSetting swapFreq = this.add(new SliderSetting("SwapFreq", 10.0, 1.0, 40.0, 1.0, () -> this.mode.getValue() == Mode.Grim));

    private final Timer antiKickTimer = new Timer();
    private final Timer fireworkDelayTimer = new Timer();

    private static final double GRIM_AIR_FRICTION = 0.0264444413;

    public EFly() {
        super("EFly", Module.Category.Movement);
        this.setChinese("甲飞");
        INSTANCE = this;
        this.antiKickTimer.setMs(3800);
        this.fireworkDelayTimer.setMs(400);
    }

    @Override
    public String getInfo() {
        return this.mode.getValue().name();
    }

    @Override
    public void onEnable() {
        if (nullCheck()) return;
        this.antiKickTimer.reset();
        this.fireworkDelayTimer.reset();
    }

    @Override
    public void onDisable() {
        if (nullCheck()) return;
        if (this.mode.getValue() == Mode.Grim && mc.player.isFallFlying()) {
            mc.player.stopFallFlying();
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        }
    }

    // =================================================================
    // 视角发包欺骗 (Silent Rotation)
    // =================================================================
    @EventListener
    public void onRotate(UpdateRotateEvent event) {
        if (nullCheck()) return;

        if (this.mode.getValue() == Mode.Grim && this.pitch.getValue()) {
            float spoofedYaw = getMoveYaw();
            float spoofedPitch = getControlPitch();

            // 使用 sn0w 风格的调用：向管理器申请 99 优先级的视角锁定
            dev.stella.api.utils.eflyRotation.RotationManager.INSTANCE.rotateTo(
                new dev.stella.api.utils.eflyRotation.Rotation(99, spoofedYaw, spoofedPitch)
            );
        }
    }

    // =================================================================
    // 按键移动修正 (Strafe Fix)
    // =================================================================
    @EventListener(priority = 1000)
    public void onKeyInput(KeyboardInputEvent event) {
        if (nullCheck()) return;
        
        if (this.mode.getValue() == Mode.Grim && this.pitch.getValue()) {
            float forward = mc.player.input.movementForward;
            float sideways = mc.player.input.movementSideways;
            float spoofedYaw = getMoveYaw();
            
            float delta = (mc.player.getYaw() - spoofedYaw) * MathHelper.RADIANS_PER_DEGREE;
            float cos = MathHelper.cos(delta);
            float sin = MathHelper.sin(delta);
            
            mc.player.input.movementSideways = Math.round(sideways * cos - forward * sin);
            mc.player.input.movementForward = Math.round(forward * cos + sideways * sin);
        }
    }

    // =================================================================
    // 移动接管 (阻力消除)
    // =================================================================
    @EventListener(priority = -9999)
    public void onMove(MoveEvent event) {
        if (nullCheck()) return;

        switch (this.mode.getValue()) {
            case Creative:
                handleCreativeMode(event);
                break;
            case Grim:
                if (mc.player.isFallFlying()) {
                    handleGrimMove(event);
                }
                break;
        }
    }

    // =================================================================
    // 切甲与烟花逻辑 (Travel)
    // =================================================================
    @EventListener
    public void onTravel(TravelEvent event) {
        if (nullCheck()) return;

        if (this.mode.getValue() == Mode.Grim) {
            handleGrimTravel(event);
        }
    }

    @EventListener
    public void onJump(JumpEvent event) {
        if (nullCheck()) return;
        if (this.mode.getValue() == Mode.Grim) {
            // 取消原版跳跃，防止被反作弊检测到异常地面起跳
            event.setCancelled(true);
            // 物理离地
            mc.player.setVelocity(mc.player.getVelocity().x, 0.42, mc.player.getVelocity().z);
        }
    }

    private void handleCreativeMode(MoveEvent event) {
        event.setY(0.0);
        if (this.antiKick.getValue() && this.antiKickTimer.passed(3800L)) {
            event.setY(-0.04);
            this.antiKickTimer.reset();
        } else {
            if (mc.options.jumpKey.isPressed()) event.setY(this.verticalSpeed.getValue());
            else if (mc.options.sneakKey.isPressed()) event.setY(-this.verticalSpeed.getValue());
        }

        float speed = this.horizontalSpeed.getValueFloat();
        float forward = mc.player.input.movementForward;
        float strafe = mc.player.input.movementSideways;
        float yaw = mc.player.getYaw();

        if (forward == 0.0f && strafe == 0.0f) {
            event.setX(0.0);
            event.setZ(0.0);
            return;
        }

        double rx = Math.cos(Math.toRadians(yaw + 90.0f));
        double rz = Math.sin(Math.toRadians(yaw + 90.0f));
        event.setX((forward * speed * rx) + (strafe * speed * rz));
        event.setZ((forward * speed * rz) - (strafe * speed * rx));
    }

    private void handleGrimMove(MoveEvent event) {
        float yaw = dev.stella.api.utils.eflyRotation.RotationUtils.getActualYaw();
        final double boostX = GRIM_AIR_FRICTION * Math.cos(Math.toRadians(yaw + 90.0f));
        final double boostZ = GRIM_AIR_FRICTION * Math.sin(Math.toRadians(yaw + 90.0f));
        
        event.setX(event.getX() + boostX);
        event.setZ(event.getZ() + boostZ);
    }

    private void handleGrimTravel(TravelEvent event) {
        int elytraSlot = getElytraSlot();
        boolean hasElytraEquipped = isElytraEquipped();

        if (!hasElytraEquipped && elytraSlot == -1) return;

        boolean isMoving = mc.player.input.movementForward != 0 || mc.player.input.movementSideways != 0;
        boolean isJumping = mc.options.jumpKey.isPressed();
        boolean isSneaking = mc.options.sneakKey.isPressed();

        // 悬停逻辑
        if (!isMoving && !(isJumping && !isSneaking) && !(isSneaking && !isJumping)) {
            event.setCancelled(true);
            return;
        }

        // ==========================================
        // 无缝切甲频率控制
        // ==========================================
        int freq = (int) swapFreq.getValueFloat();
        if (!mc.player.isFallFlying() || (freq > 0 && mc.player.age % freq == 0)) {
            
            boolean swapBack = false;
            
            if (!hasElytraEquipped) {
                swapArmor(elytraSlot);
                swapBack = true;
            }

            // 发起飞包
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            mc.player.startFallFlying();

            if (swapBack) {
                swapArmor(elytraSlot);
            }
        }

        // 自动烟花
        if (this.firework.getValue()) {
            if (this.fireworkDelayTimer.passed(400L) && !mc.player.isOnGround() && isJumping) {
                
                // 获取管理器当前最高优先级的角度
                dev.stella.api.utils.eflyRotation.Rotation r = dev.stella.api.utils.eflyRotation.RotationManager.INSTANCE.getRotation();
                if (r != null) {
                    // 强行先发一个看天的位置包，然后再点烟花
                    mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround(
                        r.getYaw(), r.getPitch(), mc.player.isOnGround()
                    ));
                }

                useFirework();
                this.fireworkDelayTimer.reset();
            }
        }
    } // <--- 修复点 1：补齐了这个括号，正确结束了 handleGrimTravel 方法

    // =================================================================
    // 工具算法
    // =================================================================
    private float getMoveYaw() {
        return dev.stella.api.utils.player.PlayerUtils.getMoveYaw(mc.player.getYaw());
    }

    private float getControlPitch() {
        return dev.stella.api.utils.player.PlayerUtils.getControlPitch();
    }

    private boolean isElytraEquipped() {
        ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        return chestStack.getItem() instanceof ElytraItem || chestStack.getItem() == Items.ELYTRA;
    }

    private int getElytraSlot() {
        for (int i = 9; i < 45; i++) {
            if (mc.player.getInventory().getStack(i >= 36 ? i - 36 : i).getItem() == Items.ELYTRA) return i;
        }
        return -1;
    }

    private int getFireworkSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.FIREWORK_ROCKET) return i;
        }
        return -1;
    }

    private void swapArmor(int slotId) {
        if (slotId == -1) return;
        int syncId = mc.player.playerScreenHandler.syncId;
        mc.interactionManager.clickSlot(syncId, slotId, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, 6, 0, SlotActionType.PICKUP, mc.player); 
        mc.interactionManager.clickSlot(syncId, slotId, 0, SlotActionType.PICKUP, mc.player);
    }

    private void useFirework() {
        int fireworkSlot = getFireworkSlot();
        if (fireworkSlot == -1) return;
        int oldSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = fireworkSlot;
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(fireworkSlot));
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.getInventory().selectedSlot = oldSlot;
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(oldSlot));
    }

    public static boolean isStandingFly() {
        if (INSTANCE == null || !INSTANCE.isOn()) return false;
        return INSTANCE.mode.getValue() == Mode.Grim;
    }

    public static boolean isGrimFlying() {
        if (INSTANCE == null || !INSTANCE.isOn()) return false;
        if (INSTANCE.mode.getValue() != Mode.Grim) return false;
        return INSTANCE.isElytraEquipped() || INSTANCE.getElytraSlot() != -1;
    }

    /**
     * 兼容 ElytraFly.isPacketFlying()
     * EFly 的 Grim 模式使用包控制，所以返回 true
     */
    public static boolean isPacketFlying() {
        return INSTANCE != null && INSTANCE.isOn() && INSTANCE.mode.getValue() == Mode.Grim;
    }
    
    // ==========================================
    // 兼容 ElytraFly 的 API（用於其他模組引用）
    // ==========================================
    
    /** 兼容 ElytraFly.INSTANCE.isFallFlying() */
    public boolean isFallFlying() {
        return mc.player != null && mc.player.isFallFlying();
    }
    
    /** 兼容 ElytraFly.INSTANCE.packet.getValue() - EFly 沒有這個設置，默認返回 false */
    public boolean packetEnabled() {
        return false;
    }
    
    // 注意：Mode enum 已定義在上方（第 30-32 行）
    // 兼容 ElytraFly.Mode.Bounce 和 Control 使用字符串比較
    
    /** 兼容 ElytraFly.INSTANCE.mode.is() */
    public boolean isMode(String modeName) {
        return this.mode.getValue().name().equalsIgnoreCase(modeName);
    }
    
    /** 兼容 ElytraFly.INSTANCE.autoJump.getValue() - EFly 沒有這個設置，默認返回 false */
    public boolean autoJumpEnabled() {
        return false;
    }
    
    /** 兼容 ElytraFly.recastElytra() */
    public static boolean recastElytra(net.minecraft.entity.player.PlayerEntity player) {
        if (INSTANCE == null || !INSTANCE.isOn()) return false;
        // 重新發送飛行包
        if (mc.player != null) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            mc.player.startFallFlying();
            return true;
        }
        return false;
    }
}