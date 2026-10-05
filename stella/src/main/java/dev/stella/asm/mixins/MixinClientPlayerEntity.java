/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  com.mojang.authlib.GameProfile
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.input.Input
 *  net.minecraft.client.network.AbstractClientPlayerEntity
 *  net.minecraft.client.network.ClientPlayerEntity
 *  net.minecraft.client.network.ClientPlayNetworkHandler
 *  net.minecraft.client.world.ClientWorld
 *  net.minecraft.entity.MovementType
 *  net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
 *  net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
 *  net.minecraft.util.math.MathHelper
 *  net.minecraft.util.math.Vec3d
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.At$Shift
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.Redirect
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package dev.stella.asm.mixins;

import com.mojang.authlib.GameProfile;
import dev.stella.stella;
import dev.stella.api.events.Event;
import dev.stella.api.events.impl.MoveEvent;
import dev.stella.api.events.impl.MovedEvent;
import dev.stella.api.events.impl.MovementPacketsEvent;
import dev.stella.api.events.impl.SendMovementPacketsEvent;
import dev.stella.api.events.impl.TickEvent;
import dev.stella.api.events.impl.TickMovementEvent;
import dev.stella.asm.accessors.IClientPlayerEntity;
import dev.stella.core.impl.CommandManager;
import dev.stella.core.impl.RotationManager;
import dev.stella.mod.modules.impl.client.ClientSetting;
import dev.stella.mod.modules.impl.exploit.PacketControl;
import dev.stella.mod.modules.impl.movement.NoSlow;
import dev.stella.mod.modules.impl.movement.Velocity;
import dev.stella.mod.modules.impl.player.Freecam;
import dev.stella.api.utils.player.MovementUtil;
import net.minecraft.entity.EntityPose;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.MovementType;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ClientPlayerEntity.class})
public abstract class MixinClientPlayerEntity
extends AbstractClientPlayerEntity {
    @Shadow
    public Input input;
   
    @Final
    @Shadow
    protected MinecraftClient client;
    @Unique
    private double preX;
    @Unique
    private double preY;
    @Unique
    private double preZ;
    @Unique
    private float preYaw;
    @Unique
    private float prePitch;
    @Unique
    private boolean rotation = false;
    @Shadow
    private double lastX;
    @Shadow
    private double lastBaseY;
    @Shadow
    private double lastZ;
    @Shadow
    private int ticksSinceLastPositionPacketSent;
    @Shadow
    private float lastYaw;
    @Shadow
    private float lastPitch;

    @Shadow
    private boolean lastOnGround;

    @Shadow
    private boolean lastSneaking;

    @Shadow
    protected ClientPlayNetworkHandler networkHandler;

    @Shadow
    protected abstract void sendSprintingPacket();

    @Shadow
    public abstract boolean isSneaking();

    // hasVehicle() is inherited from Entity class
    // Remove @Shadow and call directly since it's a public method
    // @Shadow(remap = false)
    // public abstract boolean hasVehicle();

    // isCamera() is a protected method in ClientPlayerEntity
    // Use remap = false to let Mixin handle it at runtime
    // @Shadow(remap = false)
    // protected abstract boolean isCamera();

    // getVelocity() is a public method inherited from Entity class
    // Can be called directly without @Shadow
    // @Shadow(remap = false)
    // public abstract Vec3d getVelocity();

    // isFallFlying() is a public method inherited from LivingEntity class
    // Can be called directly without @Shadow
    // @Shadow(remap = false)
    // public abstract boolean isFallFlying();

    public MixinClientPlayerEntity(ClientWorld world, GameProfile profile) {
        super(world, profile);
    }

    @Inject(method={"pushOutOfBlocks"}, at={@At(value="HEAD")}, cancellable=true)
    private void onPushOutOfBlocksHook(double x, double d, CallbackInfo info) {
        if (Velocity.INSTANCE.isOn() && Velocity.INSTANCE.blockPush.getValue()) {
            info.cancel();
        }
    }

    @Redirect(method={"tickMovement"}, at=@At(value="INVOKE", target="Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"), require=0)
    private boolean tickMovementHook(ClientPlayerEntity player) {
        if (NoSlow.INSTANCE.noSlow()) {
            return false;
        }
        return player.isUsingItem();
    }

    @Inject(at={@At(value="HEAD")}, method={"tickNausea"}, cancellable=true)
    private void updateNausea(CallbackInfo ci) {
        if (ClientSetting.INSTANCE.portalGui()) {
            ci.cancel();
        }
    }

    @Inject(method={"move"}, at={@At(value="HEAD")}, cancellable=true)
    private void onMoveHead(MovementType movementType, Vec3d movement, CallbackInfo ci) {
        MoveEvent event = MoveEvent.get(movement.x, movement.y, movement.z);
        stella.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            ci.cancel();
        } else if (event.modify) {
            ci.cancel();
            super.move(movementType, new Vec3d(event.getX(), event.getY(), event.getZ()));
            stella.EVENT_BUS.post(MovedEvent.INSTANCE);
        }
    }

    @Inject(method={"move"}, at={@At(value="INVOKE", target="Lnet/minecraft/client/network/AbstractClientPlayerEntity;move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V")}, cancellable=true)
    public void onMoveHook(MovementType movementType, Vec3d movement, CallbackInfo ci) {
        MoveEvent event = MoveEvent.get(movement.x, movement.y, movement.z);
        stella.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            ci.cancel();
        } else if (event.modify) {
            ci.cancel();
            super.move(movementType, new Vec3d(event.getX(), event.getY(), event.getZ()));
            stella.EVENT_BUS.post(MovedEvent.INSTANCE);
        }
    }

    @Inject(method={"move"}, at={@At(value="TAIL")})
    public void onMoveReturnHook(MovementType movementType, Vec3d movement, CallbackInfo ci) {
        stella.EVENT_BUS.post(MovedEvent.INSTANCE);
    }

    @Shadow
    public abstract float getPitch(float var1);

    @Shadow
    public abstract float getYaw(float var1);

    @Inject(method={"tick"}, at={@At(value="HEAD")})
    private void tickHead(CallbackInfo ci) {
        block2: {
            try {
                // Freecam - 清除玩家输入防止移动
                if (Freecam.INSTANCE != null && Freecam.INSTANCE.isOn()) {
                    ((ClientPlayerEntity)(Object)this).input.movementForward = 0.0f;
                    ((ClientPlayerEntity)(Object)this).input.movementSideways = 0.0f;
                }
                stella.EVENT_BUS.post(TickEvent.get(Event.Stage.Pre));
            }
            catch (Exception e) {
                e.printStackTrace();
                if (!ClientSetting.INSTANCE.debug.getValue()) break block2;
                CommandManager.sendMessage("\u00a74An error has occurred (ClientPlayerEntity.tick() [HEAD]) Message: [" + e.getMessage() + "]");
            }
        }
    }

    @Inject(method={"tick"}, at={@At(value="RETURN")})
    private void tickReturn(CallbackInfo ci) {
        block2: {
            try {
                stella.EVENT_BUS.post(TickEvent.get(Event.Stage.Post));
            }
            catch (Exception e) {
                e.printStackTrace();
                if (!ClientSetting.INSTANCE.debug.getValue()) break block2;
                CommandManager.sendMessage("\u00a74An error has occurred (ClientPlayerEntity.tick() [RETURN]) Message: [" + e.getMessage() + "]");
            }
        }
    }

    @Inject(method={"sendMovementPackets"}, at={@At(value="HEAD")}, cancellable=true)
    private void onSendMovementPacketsHead(CallbackInfo info) {
        // Post movement tick event
        stella.EVENT_BUS.post(TickEvent.get(Event.Stage.Post));
        
        // Rotation spoof via MovementPacketsEvent
        MovementPacketsEvent movementPacketsEvent = MovementPacketsEvent.get(
            this.getX(), this.getY(), this.getZ(),
            this.getYaw(), this.getPitch(), this.isOnGround()
        );
        stella.EVENT_BUS.post(movementPacketsEvent);
        
        double x = movementPacketsEvent.getX();
        double y = movementPacketsEvent.getY();
        double z = movementPacketsEvent.getZ();
        float yaw = movementPacketsEvent.getYaw();
        float pitch = movementPacketsEvent.getPitch();
        boolean ground = movementPacketsEvent.isOnGround();
        
        if (movementPacketsEvent.isCancelled()) {
            info.cancel();
            this.sendSprintingPacket();
            boolean bl = this.isSneaking();
            if (bl != this.lastSneaking) {
                ClientCommandC2SPacket.Mode mode = bl ? ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY : ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY;
                this.networkHandler.sendPacket(new ClientCommandC2SPacket(this, mode));
                this.lastSneaking = bl;
            }
            if (this.client.getCameraEntity() == this) {
                double d = x - this.lastX;
                double e = y - this.lastBaseY;
                double f = z - this.lastZ;
                double g = yaw - this.lastYaw;
                double h = pitch - this.lastPitch;
                ++this.ticksSinceLastPositionPacketSent;
                boolean bl2 = MathHelper.squaredMagnitude(d, e, f) > MathHelper.square(2.0E-4) || this.ticksSinceLastPositionPacketSent >= 20;
                boolean bl3 = g != 0.0 || h != 0.0;
                
                // 应用服务器旋转
                float sendYaw = yaw;
                float sendPitch = pitch;
                if (RotationManager.hasServerRotation()) {
                    sendYaw = RotationManager.serverYaw;
                    sendPitch = RotationManager.serverPitch;
                }

                if (this.hasVehicle()) {
                    Vec3d vec3d = this.getVelocity();
                    this.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(vec3d.x, -999.0, vec3d.z, sendYaw, sendPitch, ground));
                    bl2 = false;
                } else if (bl2 && bl3) {
                    this.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(x, y, z, sendYaw, sendPitch, ground));
                } else if (bl2) {
                    this.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, ground));
                } else if (bl3) {
                    this.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(sendYaw, sendPitch, ground));
                } else if (this.lastOnGround != ground) {
                    this.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(ground));
                }
                if (bl2) {
                    this.lastX = x;
                    this.lastBaseY = y;
                    this.lastZ = z;
                    this.ticksSinceLastPositionPacketSent = 0;
                }
        if (bl3) {
                    this.lastYaw = yaw;
                    this.lastPitch = pitch;
                }
                this.lastOnGround = ground;
            }
            
            // Post event after packets sent
            stella.EVENT_BUS.post(TickEvent.get(Event.Stage.Post));
            return;
        }
        
        // Original logic for non-cancelled events
        this.rotation();
        if (PacketControl.INSTANCE.isOn() && PacketControl.INSTANCE.positionSync.getValue() && this.ticksSinceLastPositionPacketSent >= PacketControl.INSTANCE.positionDelay.getValueInt() - 1) {
            ((IClientPlayerEntity)((Object)this)).setTicksSinceLastPositionPacketSent(50);
        }
        if (RotationManager.snapBack) {
            ((IClientPlayerEntity)((Object)this)).setTicksSinceLastPositionPacketSent(50);
            ((IClientPlayerEntity)((Object)this)).setLastYaw(999.0f);
            RotationManager.snapBack = false;
            return;
        }

        if (dev.stella.mod.modules.impl.movement.EFly.isStandingFly() && this.isFallFlying()) {
    this.setOnGround(false);
}
    }

    @Inject(method={"tick"}, at={@At(value="INVOKE", target="Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V", ordinal=0)})
    private void onTickHasVehicleBeforeSendPackets(CallbackInfo info) {
        this.rotation();
    }

    @Unique
    private void rotation() {
        this.rotation = true;
        // 保存原始坐标和旋转
        this.preX = this.getX();
        this.preY = this.getY();
        this.preZ = this.getZ();
        this.preYaw = this.getYaw();
        this.prePitch = this.getPitch();

        float yaw = this.getYaw();
        float pitch = this.getPitch();

        // 应用服务器旋转（如 EFly 的转头）
        if (RotationManager.hasServerRotation()) {
            yaw = RotationManager.serverYaw;
            pitch = RotationManager.serverPitch;
        }

        SendMovementPacketsEvent event = SendMovementPacketsEvent.get(this.getX(), this.getY(), this.getZ(), yaw, pitch);
        stella.EVENT_BUS.post(event);
        stella.ROTATION.rotationYaw = event.getYaw();
        stella.ROTATION.rotationPitch = event.getPitch();
        // 使用事件中的坐标和旋转值
        this.setPosition(event.getX(), event.getY(), event.getZ());
        this.setYaw(event.getYaw());
        this.setPitch(event.getPitch());
    }

    @Inject(method={"sendMovementPackets"}, at={@At(value="TAIL")})
    private void onSendMovementPacketsTail(CallbackInfo info) {
        if (this.rotation) {
            // 恢复原始坐标和旋转
            this.setPosition(this.preX, this.preY, this.preZ);
            this.setYaw(this.preYaw);
            this.setPitch(this.prePitch);
            this.rotation = false;
        }
    }



    @Inject(method={"tick"}, at={@At(value="INVOKE", target="Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V", ordinal=1, shift=At.Shift.AFTER)})
    private void onTickHasVehicleAfterSendPackets(CallbackInfo info) {
        if (this.rotation) {
            // 恢复原始坐标和旋转
            this.setPosition(this.preX, this.preY, this.preZ);
            this.setYaw(this.preYaw);
            this.setPitch(this.prePitch);
            this.rotation = false;
        }
    }

    @Inject(method={"tickMovement"}, at={@At(value="HEAD")})
    private void tickMovement(CallbackInfo ci) {
        stella.EVENT_BUS.post(TickMovementEvent.INSTANCE);
    }
}

