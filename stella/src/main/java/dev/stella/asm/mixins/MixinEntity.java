/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.entity.Entity
 *  net.minecraft.entity.player.PlayerEntity
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.ModifyArgs
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 *  org.spongepowered.asm.mixin.injection.invoke.arg.Args
 */
package dev.stella.asm.mixins;

import dev.stella.stella;
import dev.stella.api.events.impl.LookDirectionEvent;
import dev.stella.api.events.impl.SprintEvent;
import dev.stella.api.utils.Wrapper;
// ElytraFly removed, use EFly instead
import dev.stella.mod.modules.impl.movement.Velocity;
import dev.stella.mod.modules.impl.render.NoRender;
import dev.stella.mod.modules.impl.render.ShaderModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(value={Entity.class})
public abstract class MixinEntity {
    @Shadow
    private static final int SPRINTING_FLAG_INDEX = 3;

    @Inject(method={"changeLookDirection"}, at={@At(value="HEAD")}, cancellable=true)
    private void hookChangeLookDirection(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (Entity.class.cast(this) == Wrapper.mc.player) {
            LookDirectionEvent lookDirectionEvent = LookDirectionEvent.get((Entity)Entity.class.cast(this), cursorDeltaX, cursorDeltaY);
            stella.EVENT_BUS.post(lookDirectionEvent);
            if (lookDirectionEvent.isCancelled()) {
                ci.cancel();
            }
        }
    }

    @Inject(at={@At(value="HEAD")}, method={"isInvisibleTo(Lnet/minecraft/entity/player/PlayerEntity;)Z"}, cancellable=true)
    private void onIsInvisibleCheck(PlayerEntity message, CallbackInfoReturnable<Boolean> cir) {
        if (NoRender.INSTANCE.isOn() && NoRender.INSTANCE.invisible.getValue()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method={"isGlowing"}, at={@At(value="HEAD")}, cancellable=true)
    void isGlowingHook(CallbackInfoReturnable<Boolean> cir) {
        if (ShaderModule.INSTANCE.isOn()) {
            cir.setReturnValue(ShaderModule.INSTANCE.shouldRender((Entity)Entity.class.cast(this)));
        }
    }

    @ModifyArgs(method={"pushAwayFrom"}, at=@At(value="INVOKE", target="Lnet/minecraft/entity/Entity;addVelocity(DDD)V"))
    private void pushAwayFromHook(Args args) {
        if (Entity.class.cast(this) == MinecraftClient.getInstance().player && Velocity.INSTANCE.isOn() && Velocity.INSTANCE.entityPush.getValue()) {
            args.set(0, 0.0);
            args.set(1, 0.0);
            args.set(2, 0.0);
        }
    }

    @Inject(method={"isOnFire"}, at={@At(value="HEAD")}, cancellable=true)
    void isOnFireHook(CallbackInfoReturnable<Boolean> cir) {
        if (NoRender.INSTANCE.isOn() && NoRender.INSTANCE.fireEntity.getValue()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method={"getPose"}, at={@At(value="HEAD")}, cancellable=true)
    void getPoseHook(CallbackInfoReturnable<EntityPose> cir) {
        // 只对你自己（玩家）生效
        if (Entity.class.cast(this) != MinecraftClient.getInstance().player) {
            return;
        }

        // 核心：如果 EFly 开启了 Grim 模式，强制返回 STANDING（站立）姿态
        // 这样服务端和客户端渲染都会认为你是站着的，不会进入俯冲动画
        if (dev.stella.mod.modules.impl.movement.EFly.isStandingFly()) {
            cir.setReturnValue(EntityPose.STANDING);
            return;
        }

        // ElytraFly Packet 模式强制站立
        if (dev.stella.mod.modules.impl.movement.ElytraFly.INSTANCE.isOn() && dev.stella.mod.modules.impl.movement.ElytraFly.INSTANCE.packet.getValue()) {
            cir.setReturnValue(EntityPose.STANDING);
            return;
        }

        // ElytraFly removed, use EFly instead

        if (dev.stella.mod.modules.impl.movement.LongJump.INSTANCE.isOn() && dev.stella.mod.modules.impl.movement.LongJump.INSTANCE.mode.is(dev.stella.mod.modules.impl.movement.LongJump.Mode.Grim)) {
            cir.setReturnValue(EntityPose.STANDING);
            return;
        }
    }

    @Inject(method={"setSprinting"}, at={@At(value="HEAD")}, cancellable=true)
    public void setSprintingHook(boolean sprinting, CallbackInfo ci) {
        if (Entity.class.cast(this) == MinecraftClient.getInstance().player) {
            SprintEvent event = SprintEvent.get();
            stella.EVENT_BUS.post(event);
            if (event.isCancelled()) {
                ci.cancel();
                sprinting = event.isSprint();
                this.setFlag(3, sprinting);
            }
        }
    }

    @Shadow
    protected void setFlag(int index, boolean value) {
    }
}

