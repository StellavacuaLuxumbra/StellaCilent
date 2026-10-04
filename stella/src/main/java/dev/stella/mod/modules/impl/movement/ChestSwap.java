package dev.stella.mod.modules.impl.movement;

import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.UpdateEvent;
import dev.stella.api.utils.player.ArmorUtil;
import dev.stella.api.utils.player.InventoryUtil;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;

public class ChestSwap extends Module {
    public static ChestSwap INSTANCE;

    public final BooleanSetting autoFirework = this.add(new BooleanSetting("AutoFirework", false));

    public ChestSwap() {
        super("ChestSwap", Category.Movement);
        this.setChinese("胸甲切换");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        if (nullCheck()) {
            disable();
            return;
        }

        ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        
        // 如果当前穿着胸甲，切换到鞘翅
        if (ArmorUtil.isChestplate(chestStack)) {
            int elytraSlot = InventoryUtil.findItemInventorySlot(Items.ELYTRA);
            if (elytraSlot != -1) {
                // 切换到鞘翅
                InventoryUtil.swapArmor(2, elytraSlot);
                
                // 自动烟花
                if (autoFirework.getValue() && !mc.player.isOnGround()) {
                    // 开始滑翔
                    mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                    mc.player.startFallFlying();
                    
                    // 使用烟花
                    int fireworkSlot = InventoryUtil.findItemInventorySlot(Items.FIREWORK_ROCKET);
                    if (fireworkSlot != -1) {
                        useFirework(fireworkSlot);
                    }
                }
            }
        }
        // 如果当前穿着鞘翅，切换到最好的胸甲
        else if (chestStack.getItem() == Items.ELYTRA) {
            int bestChestplateSlot = ArmorUtil.getBestChestplateSlot();
            if (bestChestplateSlot != -1) {
                InventoryUtil.swapArmor(2, bestChestplateSlot);
            }
        }

        disable();
    }

    private void useFirework(int fireworkSlot) {
        int oldSlot = mc.player.getInventory().selectedSlot;
        
        // 切换到烟花槽位
        int hotbarSlot = fireworkSlot >= 36 ? fireworkSlot - 36 : fireworkSlot;
        mc.player.getInventory().selectedSlot = hotbarSlot;
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(hotbarSlot));
        
        // 使用烟花
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        
        // 切换回原来的槽位
        mc.player.getInventory().selectedSlot = oldSlot;
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(oldSlot));
    }
}
