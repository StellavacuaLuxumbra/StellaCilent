package dev.stella.mod.modules.impl.movement;

import dev.stella.stella;
import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.UpdateRotateEvent;
import dev.stella.api.utils.player.MovementUtil;
import dev.stella.core.impl.RotationManager;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import dev.stella.mod.modules.settings.impl.SliderSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class TargetFollow extends Module {
    public static TargetFollow INSTANCE;

    private final SliderSetting range = this.add(new SliderSetting("Range", 50.0, 1.0, 200.0, 1.0));
    private final BooleanSetting holdW = this.add(new BooleanSetting("HoldW", true));
    private final BooleanSetting prioritizeHeight = this.add(new BooleanSetting("PrioritizeHeight", false));

    public TargetFollow() {
        super("TargetFollow", Category.Movement);
        this.setChinese("目标跟随");
        INSTANCE = this;
    }

    @Override
    public String getInfo() {
        if (ElytraFly.INSTANCE.isOn() && this.getClosestPlayer() != null) {
            return stella.ROTATION.rotationYaw + "";
        }
        return null;
    }

    @Override
    public void onEnable() {
        if (nullCheck()) return;
        if (ElytraFly.INSTANCE.isOn() && ElytraFly.INSTANCE.mode.is(ElytraFly.Mode.Control)) {
            this.disable();
            sendMessage("§4TargetFollow disabled due to ElytraFly mode is Control.");
        }
    }

    @EventListener(priority = -9999)
    public void onRotation(UpdateRotateEvent event) {
        if (nullCheck()) return;
        if (!mc.player.isFallFlying() && !ElytraFly.INSTANCE.isFallFlying()) return;

        // 如果玩家物理上按了WASD键，则不抢转头
        boolean wasdPressed = mc.options.forwardKey.isPressed() || 
                              mc.options.backKey.isPressed() || 
                              mc.options.leftKey.isPressed() || 
                              mc.options.rightKey.isPressed();
        
        // 如果 ElytraFly 的 Mode 是 Control，通常不建议覆盖，但如果是其他模式 (如 Rotation) 可以覆盖
        // 这里假设在 Rotation 模式下运行

        PlayerEntity target = this.getClosestPlayer();
        if (target != null && !wasdPressed) {
            Box box = target.getBoundingBox();
            // 如果开启了 prioritizeHeight，瞄准头部，否则瞄准身体中心
            Vec3d lookPos = prioritizeHeight.getValue() ? target.getPos().add(0, 0.6, 0) : target.getBoundingBox().getCenter();

            // 计算旋转
            float[] rotations = RotationManager.getRotation(mc.player.getEyePos(), lookPos);
            float yaw = rotations[0];
            float pitch = rotations[1];

            event.setYaw(yaw);
            event.setPitch(pitch);

            // 自动前进
            if (this.holdW.getValue()) {
                mc.options.forwardKey.setPressed(true);
            }
        } else {
            // 如果没有目标或WASD被按下且开启了 HoldW，松开 W
            if (this.holdW.getValue()) {
                mc.options.forwardKey.setPressed(false);
            }
        }
    }

    @Override
    public void onDisable() {
        // 松开 W 键防止卡键
        mc.options.forwardKey.setPressed(false);
    }

    private PlayerEntity getClosestPlayer() {
        PlayerEntity target = null;
        double closestDistance = Double.MAX_VALUE;

        for (Entity entity : stella.THREAD.getEntities()) {
            if (entity instanceof PlayerEntity player && entity != mc.player && !stella.FRIEND.isFriend(player.getName().getString())) {
                double distance = mc.player.squaredDistanceTo(entity);
                if (distance <= range.getValue() * range.getValue()) {
                    if (distance < closestDistance) {
                        target = player;
                        closestDistance = distance;
                    }
                }
            }
        }
        return target;
    }
}