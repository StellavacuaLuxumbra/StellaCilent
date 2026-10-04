package dev.stella.asm.mixins;

import dev.stella.stella;
import dev.stella.api.events.Event;
import dev.stella.api.events.impl.JumpEvent;
import dev.stella.api.events.impl.TravelEvent;
import dev.stella.api.utils.Wrapper;
import dev.stella.mod.modules.impl.client.ClientSetting;
import dev.stella.mod.modules.impl.player.InteractTweaks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={PlayerEntity.class})
public abstract class MixinPlayerEntity implements Wrapper {

    // 【关键修复】删除之前报错的 @Shadow canChangeIntoPose
    // 我们改用 Inject 头部拦截，这样就不需要调用那个报错的方法了

    @Inject(method = "updatePose", at = @At("HEAD"), cancellable = true)
    private void onUpdatePose(CallbackInfo ci) {
        // 只有是本地玩家且开启站立飞行时才拦截
        if ((Object) this == MinecraftClient.getInstance().player 
            && dev.stella.mod.modules.impl.movement.EFly.isStandingFly()) {
            
            // 这里的 (PlayerEntity)(Object)this 是为了能调用 setPose 方法
            // setPose 是 Entity 类的公共方法，PlayerEntity 继承了它，所以这样写不会崩
            ((PlayerEntity)(Object)this).setPose(EntityPose.STANDING);
            
            // 直接取消原版方法的执行，原版就不会再去判断是否该“趴下”了
            ci.cancel();
        }
    }

    @Inject(method={"canChangeIntoPose"}, at={@At(value="RETURN")}, cancellable=true)
    private void poseNotCollide(EntityPose pose, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this == MinecraftClient.getInstance().player && !ClientSetting.INSTANCE.crawl.getValue() && pose == EntityPose.SWIMMING) {
            cir.setReturnValue(false);
        }
    }

    // --- 以下是你的其他代码，保持不变 ---

    @Inject(method={"getBlockInteractionRange"}, at={@At(value="HEAD")}, cancellable=true)
    public void getBlockInteractionRangeHook(CallbackInfoReturnable<Double> cir) {
        if (InteractTweaks.INSTANCE.reach()) {
            cir.setReturnValue(InteractTweaks.INSTANCE.blockRange.getValue());
        }
    }

    @Inject(method={"getEntityInteractionRange"}, at={@At(value="HEAD")}, cancellable=true)
    public void getEntityInteractionRangeHook(CallbackInfoReturnable<Double> cir) {
        if (InteractTweaks.INSTANCE.reach()) {
            cir.setReturnValue(InteractTweaks.INSTANCE.entityRange.getValue());
        }
    }

    @Inject(method={"jump"}, at={@At(value="HEAD")})
    private void onJumpPre(CallbackInfo ci) {
        stella.EVENT_BUS.post(JumpEvent.get(Event.Stage.Pre));
    }

    @Inject(method={"jump"}, at={@At(value="RETURN")})
    private void onJumpPost(CallbackInfo ci) {
        stella.EVENT_BUS.post(JumpEvent.get(Event.Stage.Post));
    }

    @Inject(method={"travel"}, at={@At(value="HEAD")}, cancellable=true)
    private void onTravelPre(Vec3d movementInput, CallbackInfo ci) {
        if ((Object) this != MinecraftClient.getInstance().player) return;
        TravelEvent event = TravelEvent.get(Event.Stage.Pre, (PlayerEntity)(Object)this);
        stella.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            ci.cancel();
            stella.EVENT_BUS.post(TravelEvent.get(Event.Stage.Post, (PlayerEntity)(Object)this));
        }
    }

    @Inject(method={"travel"}, at={@At(value="RETURN")})
    private void onTravelPost(Vec3d movementInput, CallbackInfo ci) {
        if ((Object) this != MinecraftClient.getInstance().player) return;
        stella.EVENT_BUS.post(TravelEvent.get(Event.Stage.Post, (PlayerEntity)(Object)this));
    }
}