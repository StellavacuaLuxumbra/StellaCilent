package dev.stella.mod.modules.impl.player;

import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.UpdateEvent;
import dev.stella.api.utils.math.Timer;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import dev.stella.mod.modules.settings.impl.SliderSetting;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;

public class NoFallSneak extends Module {
    public static NoFallSneak INSTANCE;
    
    private final SliderSetting minFallDistance = this.add(new SliderSetting("MinFallDistance", 3.0, 0.0, 20.0, 0.5));
    private final BooleanSetting autoDisable = this.add(new BooleanSetting("AutoDisable", false));
    
    private boolean wasFalling = false;
    private final Timer sneakTimer = new Timer();

    public NoFallSneak() {
        super("NoFallSneak", "Sneak on landing to avoid fall damage", Module.Category.Player);
        this.setChinese("\u6f5c\u884c\u65e0\u6454\u843d");
        INSTANCE = this;
    }

    @Override
    public String getInfo() {
        return this.minFallDistance.getValue() + "b";
    }

    @Override
    public void onEnable() {
        if (nullCheck()) return;
        this.wasFalling = false;
    }

    @Override
    public void onDisable() {
        if (!nullCheck() && mc.player.isSneaking()) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
            mc.player.setSneaking(false);
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (nullCheck()) return;

        if (!mc.player.isOnGround() && mc.player.getVelocity().y < -0.5) {
            this.wasFalling = true;
        }

        if (this.wasFalling && mc.player.isOnGround()) {
            this.wasFalling = false;
            
            if (mc.player.fallDistance > this.minFallDistance.getValue()) {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));
                mc.player.setSneaking(true);
                this.sneakTimer.reset();
            }
        }

        if (this.sneakTimer.passedMs(100) && mc.player.isSneaking()) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
            mc.player.setSneaking(false);
            
            if (this.autoDisable.getValue()) {
                this.disable();
            }
        }
    }
}
