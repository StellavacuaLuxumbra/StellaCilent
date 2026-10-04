package dev.stella.mod.modules.impl.movement;

import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.impl.*;
import dev.stella.api.events.eventbus.*;
import dev.stella.api.events.impl.*;
import dev.stella.api.utils.player.*;
import net.minecraft.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ElytraItem;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.screen.slot.SlotActionType;

public class LongJump extends Module
{
    public static LongJump INSTANCE;
    public final EnumSetting<Mode> mode;
    private final SliderSetting boost;
    private final BooleanSetting autoDisable;
    private final BooleanSetting onGround;
    private boolean hasBeenGrounded;
    private int stage;
    private double moveSpeed;
    private double lastDist;

    public LongJump() {
        super("LongJump", Category.Movement);
        this.mode = this.add(new EnumSetting<Mode>("Mode", Mode.Boost));
        this.boost = this.add(new SliderSetting("Boost", 0.4, 0.2, 10.0, 0.1));
        this.autoDisable = this.add(new BooleanSetting("AutoDisable", true));
        this.onGround = this.add(new BooleanSetting("OnGround", true));
        this.hasBeenGrounded = false;
        this.stage = 0;
        this.setChinese("\u957f\u8df3");
        LongJump.INSTANCE = this;
    }

    @Override
    public String getInfo() {
        return this.mode.getValue().name();
    }

    @Override
    public void onEnable() {
        if (nullCheck()) {
            return;
        }
        this.hasBeenGrounded = false;
        this.stage = 0;
    }

    @Override
    public void onDisable() {
        this.stage = 0;
        this.hasBeenGrounded = false;
    }

    @EventListener
    public void onUpdate(final UpdateEvent event) {
        if (nullCheck()) {
            return;
        }
        if (this.mode.is(Mode.Grim)) {}
        if (!this.hasBeenGrounded) {
            this.hasBeenGrounded = mc.player.isOnGround();
        }
        if (!this.hasBeenGrounded && this.onGround.getValue()) {
            return;
        }
        this.lastDist = Math.sqrt((mc.player.getX() - mc.player.prevX) * (mc.player.getX() - mc.player.prevX) + (mc.player.getZ() - mc.player.prevZ) * (mc.player.getZ() - mc.player.prevZ));
        if (this.canSprint()) {
            mc.player.setSprinting(true);
        }
    }

    @EventListener
    public void onMove(final MoveEvent event) {
        if (nullCheck()) {
            return;
        }
        if (this.mode.is(Mode.Grim)) {
            // Grim mode uses EFly for movement
            return;
        }
        else {
            if (!this.hasBeenGrounded && this.onGround.getValue()) {
                return;
            }
            if ((this.mode.is(Mode.Strict) || this.mode.is(Mode.StrictHigh)) && !this.canDoLongjump()) {
                return;
            }
            if (mc.player.isFallFlying() || mc.player.isTouchingWater() || mc.player.isInLava()) {
                return;
            }
            
            // 重置检测：如果玩家在地面上且没有移动输入，重置 stage
            if (mc.player.isOnGround() && mc.player.input.movementForward == 0.0f && mc.player.input.movementSideways == 0.0f) {
                this.stage = 0;
                this.lastDist = 0.0;
                return;
            }
            
            // 在地面上且有移动输入时，重置 stage 以重新触发跳跃
            if (mc.player.isOnGround() && (mc.player.input.movementForward != 0.0f || mc.player.input.movementSideways != 0.0f) && this.stage > 2) {
                this.stage = 0;
                this.lastDist = 0.0;
            }
            
            switch (this.stage) {
                case 0: {
                    ++this.stage;
                    this.lastDist = 0.0;
                    break;
                }
                case 1: {
                    this.moveSpeed = 2.149 * this.getBaseMoveSpeed();
                    ++this.stage;
                    break;
                }
                case 2: {
                    double motionY = 0.40123128;
                    if (mc.player.hasStatusEffect(StatusEffects.SPEED)) {
                        motionY += (mc.player.getStatusEffect(StatusEffects.SPEED).getAmplifier() + 1) * 0.1f;
                    }
                    event.setY(motionY);
                    this.moveSpeed *= 2.149;
                    ++this.stage;
                    break;
                }
                case 3: {
                    this.moveSpeed = this.lastDist - 0.76 * (this.lastDist - this.getBaseMoveSpeed());
                    ++this.stage;
                    break;
                }
                default: {
                    if (mc.world.getCollisions(mc.player, mc.player.getBoundingBox().offset(0.0, mc.player.getY() - mc.player.getBoundingBox().minY, 0.0)).iterator().hasNext() || mc.player.horizontalCollision) {
                        this.stage = 1;
                    }
                    this.moveSpeed = this.lastDist - this.lastDist / 159.0;
                    break;
                }
            }
            this.moveSpeed = Math.max(this.moveSpeed, this.getBaseMoveSpeed());
            double forward = mc.player.input.movementForward;
            double strafe = mc.player.input.movementSideways;
            final double yaw = mc.player.getYaw();
            if (forward != 0.0 && strafe != 0.0) {
                forward *= Math.sin(0.7853981633974483);
                strafe *= Math.cos(0.7853981633974483);
            }
            else {
                event.setX(0.0);
                event.setZ(0.0);
            }
            event.setX((forward * this.moveSpeed * -Math.sin(Math.toRadians(yaw)) + strafe * this.moveSpeed * Math.cos(Math.toRadians(yaw))) * 0.99);
            event.setZ((forward * this.moveSpeed * Math.cos(Math.toRadians(yaw)) - strafe * this.moveSpeed * -Math.sin(Math.toRadians(yaw))) * 0.99);
        }
    }

    @EventListener
    public void onTravel(final TravelEvent event) {
        if (nullCheck()) {
            return;
        }
        if (this.mode.is(Mode.Grim)) {
            final int slot = InventoryUtil.findItemInventorySlot(Items.ELYTRA);
            if (slot == -1) {
                return;
            }
            if (!mc.player.isFallFlying()) {
                if (!this.isElytraEquipped()) {
                    if (slot == -1) {
                        return;
                    }
                    this.swapArmor(2, slot);
                }
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                mc.player.startFallFlying();
                if (!this.isElytraEquipped()) {
                    this.swapArmor(2, slot);
                }
            }
            if (mc.player.isOnGround()) {
                mc.player.stopFallFlying();
            }
        }
    }

    private double getBaseMoveSpeed() {
        double baseSpeed = 0.272;
        if (mc.player.hasStatusEffect(StatusEffects.SPEED)) {
            final int amplifier = mc.player.getStatusEffect(StatusEffects.SPEED).getAmplifier();
            baseSpeed *= 1.0 + 0.2 * (amplifier + 1);
        }
        return baseSpeed * this.boost.getValue();
    }

    private boolean canSprint() {
        return (mc.player.input.movementSideways != 0.0f || mc.player.input.movementForward != 0.0f) && !mc.player.isSneaking() && !mc.player.isClimbing() && !mc.player.input.jumping && mc.player.getHungerManager().getFoodLevel() > 6;
    }

    private boolean canDoLongjump() {
        return true;
    }

    private boolean isElytraEquipped() {
        final ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        return chestStack.getItem() instanceof ElytraItem;
    }

    private void swapArmor(final int armorSlot, final int inSlot) {
        int slot = inSlot;
        if (slot < 9) {
            slot += 36;
        }
        final ItemStack stack = mc.player.getInventory().getStack(armorSlot);
        final int armorSlotId = 8 - armorSlot;
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 0, SlotActionType.QUICK_MOVE, mc.player);
        final boolean rt = !stack.isEmpty();
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, armorSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);
        if (rt) {
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 0, SlotActionType.QUICK_MOVE, mc.player);
        }
    }

    public static boolean isGrimJumping() {
        if (LongJump.INSTANCE == null || !LongJump.INSTANCE.isOn()) {
            return false;
        }
        if (!LongJump.INSTANCE.mode.is(Mode.Grim)) {
            return false;
        }
        final int slot = InventoryUtil.findItemInventorySlot(Items.ELYTRA);
        return slot != -1 || isElytraEquippedStatic();
    }

    private static boolean isElytraEquippedStatic() {
        if (mc.player == null) {
            return false;
        }
        final ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        return chestStack.getItem() instanceof ElytraItem;
    }

    public enum Mode
    {
        Boost,
        Grim,
        Strict,
        StrictHigh;
    }
}
