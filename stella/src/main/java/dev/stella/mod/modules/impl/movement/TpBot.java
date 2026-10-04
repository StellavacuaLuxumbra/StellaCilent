package dev.stella.mod.modules.impl.movement;

import dev.stella.stella;
import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.ClientTickEvent;
import dev.stella.api.utils.player.PredictUtils;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import dev.stella.mod.modules.settings.impl.SliderSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

public class TpBot extends Module {
    public static TpBot INSTANCE;
    
    private final SliderSetting range = this.add(new SliderSetting("Range", 256.0, 1.0, 256.0));
    private final SliderSetting delay = this.add(new SliderSetting("Delay", 10.0, 1.0, 100.0));
    private final BooleanSetting predict = this.add(new BooleanSetting("Predict", false));
    private final SliderSetting predictTicks = this.add(new SliderSetting("Predict Ticks", 5.0, 1.0, 20.0, () -> this.predict.getValue()));
    private final SliderSetting xOffset = this.add(new SliderSetting("X Offset", 0.0, -10.0, 10.0));
    private final SliderSetting yOffset = this.add(new SliderSetting("Y Offset", 0.0, -10.0, 10.0));
    private final SliderSetting zOffset = this.add(new SliderSetting("Z Offset", 0.0, -10.0, 10.0));
    private final BooleanSetting ignoreFriends = this.add(new BooleanSetting("Ignore Friends", true));
    
    private int tickTimer = 0;
    private PlayerEntity currentTarget = null;
    
    public TpBot() {
        super("TpBot", Category.Movement);
        this.setChinese("自动传送");
        INSTANCE = this;
    }
    
    @EventListener
    public void onTick(ClientTickEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (event.stage != dev.stella.api.events.Event.Stage.Pre) return;
        
        tickTimer++;
        if (tickTimer < delay.getValue()) return;
        tickTimer = 0;
        
        currentTarget = findNearestEnemy();
        if (currentTarget == null) return;
        
        Vec3d targetPos = calculateTargetPosition(currentTarget);
        sendPacketPath(targetPos);
        syncActualPosition(targetPos);
    }
    
    private PlayerEntity findNearestEnemy() {
        PlayerEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        
        for (Entity entity : stella.THREAD.getEntities()) {
            if (!(entity instanceof PlayerEntity player)) continue;
            if (player == mc.player) continue;
            if (!player.isAlive()) continue;
            
            double distance = mc.player.distanceTo(player);
            if (distance > range.getValue()) continue;
            
            if (ignoreFriends.getValue() && stella.FRIEND.isFriend(player)) continue;
            
            if (distance < nearestDist) {
                nearestDist = distance;
                nearest = player;
            }
        }
        
        return nearest;
    }
    
    private Vec3d calculateTargetPosition(PlayerEntity target) {
        Vec3d basePos = target.getPos();
        
        if (predict.getValue()) {
            basePos = PredictUtils.predictPosition(target, (int)predictTicks.getValue());
        }
        
        basePos = basePos.add(xOffset.getValue(), yOffset.getValue(), zOffset.getValue());
        return basePos;
    }
    
    private void sendPacketPath(Vec3d targetPos) {
        if (mc.player == null || mc.player.networkHandler == null) return;
        
        Vec3d currentPos = mc.player.getPos();
        double distance = currentPos.distanceTo(targetPos);
        int steps = (int) Math.ceil(distance / 10.0);
        
        for (int i = 1; i <= steps; i++) {
            double ratio = (double) i / steps;
            double x = currentPos.x + (targetPos.x - currentPos.x) * ratio;
            double y = currentPos.y + (targetPos.y - currentPos.y) * ratio;
            double z = currentPos.z + (targetPos.z - currentPos.z) * ratio;
            
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, mc.player.isOnGround()));
        }
    }
    
    private void syncActualPosition(Vec3d targetPos) {
        if (mc.player == null) return;
        
        mc.player.updatePosition(targetPos.x, targetPos.y, targetPos.z);
        mc.player.setVelocity(0, 0, 0);
        
        if (mc.player.networkHandler != null) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(targetPos.x, targetPos.y, targetPos.z, mc.player.isOnGround()));
        }
    }
    
    @Override
    public void onEnable() {
        super.onEnable();
        tickTimer = 0;
        currentTarget = null;
    }
    
    @Override
    public void onDisable() {
        super.onDisable();
        currentTarget = null;
    }
}
