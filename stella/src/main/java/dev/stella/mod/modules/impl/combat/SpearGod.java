package dev.stella.mod.modules.impl.combat;

import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.ClientTickEvent;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.impl.player.AntiLag;
import dev.stella.mod.modules.settings.impl.SliderSetting;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

public class SpearGod extends Module {
    public static SpearGod INSTANCE;
    
    private final SliderSetting backDistance = this.add(new SliderSetting("Power-Distance", 5.0, 0.5, 30.0));
    private final SliderSetting segmentDistance = this.add(new SliderSetting("Move-Distance", 2.0, 1.0, 10.0));
    private final SliderSetting waitTicks = this.add(new SliderSetting("Wait-Ticks", 5, 0, 20));
    
    private boolean isRightClicking = false;
    private int clickTimer = 0;
    private int stage = 0;
    private Vec3d startPosition = null;
    private Vec3d targetPosition = null;
    
    public SpearGod() {
        super("SpearGod", Module.Category.Combat);
        this.setChinese("牛逼长矛");
        INSTANCE = this;
    }
    
    @Override
    public void onEnable() {
        resetState();
        // 自动开启 AntiLag
        if (AntiLag.INSTANCE != null && !AntiLag.INSTANCE.isOn()) {
            AntiLag.INSTANCE.enable();
        }
    }
    
    @Override
    public void onDisable() {
        resetState();
        // 自动关闭 AntiLag
        if (AntiLag.INSTANCE != null && AntiLag.INSTANCE.isOn()) {
            AntiLag.INSTANCE.disable();
        }
    }
    
    private void resetState() {
        isRightClicking = false;
        clickTimer = 0;
        stage = 0;
        startPosition = null;
        targetPosition = null;
    }
    
    @EventListener
    public void onTick(ClientTickEvent event) {
        if (SpearGod.nullCheck()) return;
        if (event.isPre()) return;
        
        if (mc.options.useKey.isPressed()) {
            if (!isRightClicking) {
                isRightClicking = true;
                clickTimer = 0;
                startPosition = mc.player.getPos();
            } else {
                clickTimer++;
                
                if (clickTimer >= waitTicks.getValueInt() && stage == 0) {
                    stage = 1;
                    calculateTargetPosition();
                    moveBackward();
                }
            }
        } else {
            if (isRightClicking) {
                resetState();
            }
        }
    }
    
    private void calculateTargetPosition() {
        if (mc.player == null || startPosition == null) return;
        
        float yaw = mc.player.getYaw();
        double yawRad = Math.toRadians(yaw + 180);
        
        double x = startPosition.x - Math.sin(yawRad) * backDistance.getValue();
        double z = startPosition.z + Math.cos(yawRad) * backDistance.getValue();
        double y = startPosition.y;
        
        targetPosition = new Vec3d(x, y, z);
    }
    
    private void moveBackward() {
        if (mc.player == null || startPosition == null || targetPosition == null) return;
        
        doTeleport(startPosition, targetPosition, segmentDistance.getValue(), mc.player.isOnGround());
        
        stage = 2;
        moveReturn();
    }
    
    private void moveReturn() {
        if (mc.player == null || startPosition == null || targetPosition == null) return;
        
        doTeleport(targetPosition, startPosition, segmentDistance.getValue(), mc.player.isOnGround());
        
        // 返回原位后发送正常位置包
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            mc.player.getX(),
            mc.player.getY(),
            mc.player.getZ(),
            false
        ));
        
        resetState();
    }
    
    private void doTeleport(Vec3d from, Vec3d to, double maxDist, boolean onGround) {
        double dist = from.distanceTo(to);
        int packets = (int) Math.ceil(dist / maxDist);
        
        for (int i = 0; i < packets; i++) {
            double progress = (double) (i + 1) / packets;
            Vec3d intermediate = from.lerp(to, progress);
            
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                intermediate.x,
                intermediate.y,
                intermediate.z,
                onGround
            ));
        }
    }
}
