/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.util.math.MatrixStack
 */
package dev.stella.mod.modules.impl.player;

import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.KeyboardInputEvent;
import dev.stella.api.events.impl.UpdateEvent;
import dev.stella.api.events.impl.UpdateRotateEvent;
import dev.stella.api.utils.math.MathUtil;
import dev.stella.core.impl.RotationManager;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import dev.stella.mod.modules.settings.impl.SliderSetting;
import net.minecraft.client.util.math.MatrixStack;

public class Freecam
extends Module {
    public static Freecam INSTANCE;
    private final SliderSetting speed = this.add(new SliderSetting("HSpeed", 1.0, 0.0, 3.0));
    private final SliderSetting hspeed = this.add(new SliderSetting("VSpeed", 0.42, 0.0, 3.0));
    private final BooleanSetting rotate = this.add(new BooleanSetting("Rotate", true));
    private float fakeYaw;
    private float fakePitch;
    private float prevFakeYaw;
    private float prevFakePitch;
    private double fakeX;
    private double fakeY;
    private double fakeZ;
    private double prevFakeX;
    private double prevFakeY;
    private double prevFakeZ;
    private float playerYaw;
    private float playerPitch;

    public Freecam() {
        super("Freecam", Module.Category.Player);
        this.setChinese("\u81ea\u7531\u76f8\u673a");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        if (Freecam.nullCheck()) {
            this.disable();
            return;
        }
        this.playerYaw = this.getYaw();
        this.playerPitch = this.getPitch();
        this.fakePitch = this.getPitch();
        this.fakeYaw = this.getYaw();
        this.prevFakePitch = this.fakePitch;
        this.prevFakeYaw = this.fakeYaw;
        this.fakeX = Freecam.mc.player.getX();
        this.fakeY = Freecam.mc.player.getY() + (double)Freecam.mc.player.getEyeHeight(Freecam.mc.player.getPose());
        this.fakeZ = Freecam.mc.player.getZ();
        this.prevFakeX = this.fakeX;
        this.prevFakeY = this.fakeY;
        this.prevFakeZ = this.fakeZ;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (this.rotate.getValue() && Freecam.mc.crosshairTarget != null && Freecam.mc.crosshairTarget.getPos() != null) {
            float[] angle = RotationManager.getRotation(Freecam.mc.crosshairTarget.getPos());
            this.playerYaw = angle[0];
            this.playerPitch = angle[1];
        }
    }

    @EventListener(priority=200)
    public void onRotate(UpdateRotateEvent event) {
        if (event.isModified()) {
            return;
        }
        event.setYawWithoutSync(this.playerYaw);
        event.setPitchWithoutSync(this.playerPitch);
    }

    @Override
    public void onRender3D(MatrixStack matrixStack) {
        this.prevFakeYaw = this.fakeYaw;
        this.prevFakePitch = this.fakePitch;
        this.fakeYaw = this.getYaw();
        this.fakePitch = this.getPitch();
    }

    private float getYaw() {
        return Freecam.mc.player.getYaw();
    }

    private float getPitch() {
        return Freecam.mc.player.getPitch();
    }

    @EventListener
    public void onKeyboardInput(KeyboardInputEvent event) {
        if (Freecam.mc.player == null) {
            return;
        }

        // 直接读取按键状态
        boolean forward = Freecam.mc.options.forwardKey.isPressed();
        boolean back = Freecam.mc.options.backKey.isPressed();
        boolean left = Freecam.mc.options.leftKey.isPressed();
        boolean right = Freecam.mc.options.rightKey.isPressed();
        boolean jump = Freecam.mc.options.jumpKey.isPressed();
        boolean sneak = Freecam.mc.options.sneakKey.isPressed();

        // 计算相机移动
        double speedValue = this.speed.getValue();
        double forwardAmt = forward ? 1.0 : (back ? -1.0 : 0.0);
        double strafeAmt = left ? 1.0 : (right ? -1.0 : 0.0);
        double vertical = 0.0;

        if (jump) {
            vertical += this.hspeed.getValue();
        }
        if (sneak) {
            vertical -= this.hspeed.getValue();
        }

        // 根据相机朝向计算移动方向
        double yaw = Math.toRadians(this.fakeYaw);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);

        this.prevFakeX = this.fakeX;
        this.prevFakeY = this.fakeY;
        this.prevFakeZ = this.fakeZ;

        // 修复方向：W 前进，S 后退
        this.fakeX -= forwardAmt * speedValue * sin;
        this.fakeZ += forwardAmt * speedValue * cos;
        this.fakeX += strafeAmt * speedValue * cos;
        this.fakeZ += strafeAmt * speedValue * sin;
        this.fakeY += vertical;

        // 清除玩家输入，防止玩家实际移动
        Freecam.mc.player.input.movementForward = 0.0f;
        Freecam.mc.player.input.movementSideways = 0.0f;
        Freecam.mc.player.setVelocity(0.0, 0.0, 0.0);

        // 取消事件，防止玩家实际移动
        event.cancel();
    }

    public float getFakeYaw() {
        return MathUtil.interpolate(this.prevFakeYaw, this.fakeYaw, mc.getRenderTickCounter().getTickDelta(true));
    }

    public float getFakePitch() {
        return MathUtil.interpolate(this.prevFakePitch, this.fakePitch, mc.getRenderTickCounter().getTickDelta(true));
    }

    public double getFakeX() {
        return MathUtil.interpolate(this.prevFakeX, this.fakeX, (double)mc.getRenderTickCounter().getTickDelta(true));
    }

    public double getFakeY() {
        return MathUtil.interpolate(this.prevFakeY, this.fakeY, (double)mc.getRenderTickCounter().getTickDelta(true));
    }

    public double getFakeZ() {
        return MathUtil.interpolate(this.prevFakeZ, this.fakeZ, (double)mc.getRenderTickCounter().getTickDelta(true));
    }
}