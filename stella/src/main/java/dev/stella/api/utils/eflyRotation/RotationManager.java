package dev.stella.api.utils.eflyRotation;

import dev.stella.stella;
import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.JumpEvent;
import dev.stella.api.events.impl.KeyboardInputEvent;
import dev.stella.api.events.impl.PacketEvent;
import dev.stella.api.events.impl.SendMovementPacketsEvent;
import dev.stella.api.events.impl.TickEvent;
import dev.stella.api.utils.Wrapper;
import dev.stella.api.utils.path.BaritoneUtil;
import dev.stella.asm.accessors.IClientPlayerEntity;
import dev.stella.mod.modules.impl.client.AntiCheat;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class RotationManager implements Wrapper {
    public static RotationManager INSTANCE = new RotationManager();
    public final List<Rotation> requests = new ArrayList<>();
    
    private float serverYaw, serverPitch;
    private Rotation rotation;
    private boolean rotate;

    public RotationManager() {
        stella.EVENT_BUS.subscribe(this);
    }

    // 监视发出的数据包，记录服务器视角的最后状态
    @EventListener
    public void onPacketSend(PacketEvent.Sent event) {
        if (mc.player == null) return;
        if (event.getPacket() instanceof PlayerMoveC2SPacket packet && packet.changesLook()) {
            serverYaw = packet.getYaw(mc.player.getYaw());
            serverPitch = packet.getPitch(mc.player.getPitch());
        }
    }

    // 每刻更新：从队列里选出优先级最高的旋转请求
    @EventListener
    public void onTick(TickEvent event) {
        if (mc.player == null || requests.isEmpty()) {
            rotation = null;
            return;
        }

        Rotation bestRequest = null;
        int maxPriority = -1;
        for (Rotation r : requests) {
            if (r.getPriority() > maxPriority) {
                maxPriority = r.getPriority();
                bestRequest = r;
            }
        }
        
        if (bestRequest != null) {
            rotation = bestRequest;
            rotate = true;
        }
    }

    // 核心起作用点：拦截移动包，强行塞入队列中的 Yaw/Pitch
    @EventListener(priority = 2000) // 极高优先级
    public void onMovementPackets(SendMovementPacketsEvent event) {
        if (rotation != null && rotate) {
            // 修改发送给服务器的角度
            event.setYaw(rotation.getYaw());
            event.setPitch(rotation.getPitch());
            
            // 如果是瞬时旋转，完成后移除
            if (rotation.isSnap()) {
                requests.remove(rotation);
                rotation = null;
            }
            rotate = false;
        }
    }

    // 侧移修复 (Strafe Fix)：确保你抬头时，按W依然是向前飞
    @EventListener
    public void onKeyInput(KeyboardInputEvent event) {
        if (rotation != null && mc.player != null && AntiCheat.INSTANCE.movementSync()) {
            if (BaritoneUtil.isActive()) return;

            float forward = mc.player.input.movementForward;
            float sideways = mc.player.input.movementSideways;
            
            float delta = (mc.player.getYaw() - rotation.getYaw()) * MathHelper.RADIANS_PER_DEGREE;
            float cos = MathHelper.cos(delta);
            float sin = MathHelper.sin(delta);
            
            mc.player.input.movementSideways = Math.round(sideways * cos - forward * sin);
            mc.player.input.movementForward = Math.round(forward * cos + sideways * sin);
        }
    }

    // 外部调用接口：模块通过这个方法“排队”申请视角控制权
    public void rotateTo(Rotation in) {
        Rotation request = requests.stream()
                .filter(r -> in.getPriority() == r.getPriority())
                .findFirst().orElse(null);

        if (request == null) {
            requests.add(in.fix());
        } else {
            request.setYaw(in.getYaw());
            request.setPitch(in.getPitch());
        }
    }

    public void removeRotation(Rotation r) {
        requests.remove(r);
    }

    public float getServerYaw() { return serverYaw; }
    public float getServerPitch() { return serverPitch; }
    public Rotation getRotation() { return rotation; }
}