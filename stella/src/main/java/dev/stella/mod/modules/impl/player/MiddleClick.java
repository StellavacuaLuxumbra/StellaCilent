package dev.stella.mod.modules.impl.player;

import dev.stella.stella;
import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.ClientTickEvent;
import dev.stella.api.utils.player.InventoryUtil;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import dev.stella.mod.modules.settings.impl.SliderSetting;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;

public class MiddleClick extends Module {
    public static MiddleClick INSTANCE;
    
    private final BooleanSetting firework = this.add(new BooleanSetting("Firework", true));
    private final BooleanSetting fireworkPacket = this.add(new BooleanSetting("FireworkPacket", true, this.firework::isOpen));
    private final SliderSetting fireworkDelay = this.add(new SliderSetting("FireworkDelay", 400, 0, 5000, 100, this.firework::isOpen));

    public boolean fireworkSchedule = false;
    private long lastFireworkTime = 0L;

    public MiddleClick() {
        super("MiddleClick", Category.Player);
        this.setChinese("中键点击");
        INSTANCE = this;
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (Module.nullCheck() || event.isPost()) {
            return;
        }
    }

    public void doFirework() {
        if (!this.firework.getValue()) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        if (currentTime - this.lastFireworkTime < this.fireworkDelay.getValueInt()) {
            return;
        }
        
        int fireworkSlot = InventoryUtil.findItemInventorySlot(Items.FIREWORK_ROCKET);
        if (fireworkSlot == -1) {
            return;
        }

        if (this.fireworkPacket.getValue()) {
            // Use packet method for more stealth
            mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, mc.player.getYaw(), mc.player.getPitch()));
        } else {
            // Use inventory swap method
            int oldSlot = mc.player.getInventory().selectedSlot;
            InventoryUtil.switchToSlot(fireworkSlot);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            InventoryUtil.switchToSlot(oldSlot);
        }
        
        this.lastFireworkTime = currentTime;
    }
}
