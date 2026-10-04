package dev.stella.api.utils.player;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * 護甲工具類
 */
public class ArmorUtil {

    /**
     * 獲取物品的護甲值
     * @param stack 物品堆疊
     * @return 護甲值
     */
    public static double getArmorValue(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0.0;
        
        Item item = stack.getItem();
        
        // 胸甲護甲值
        if (item == Items.DIAMOND_CHESTPLATE) return 8.0;
        if (item == Items.IRON_CHESTPLATE) return 6.0;
        if (item == Items.GOLDEN_CHESTPLATE) return 5.0;
        if (item == Items.CHAINMAIL_CHESTPLATE) return 5.0;
        if (item == Items.LEATHER_CHESTPLATE) return 3.0;
        if (item == Items.NETHERITE_CHESTPLATE) return 8.0;
        
        // 如果是護甲物品，使用原版方法
        if (item instanceof ArmorItem) {
            ArmorItem armor = (ArmorItem) item;
            return armor.getProtection();
        }
        
        return 0.0;
    }

    /**
     * 檢查物品是否是胸甲
     * @param stack 物品堆疊
     * @return 是否是胸甲
     */
    public static boolean isChestplate(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        
        Item item = stack.getItem();
        return item == Items.DIAMOND_CHESTPLATE ||
               item == Items.IRON_CHESTPLATE ||
               item == Items.GOLDEN_CHESTPLATE ||
               item == Items.CHAINMAIL_CHESTPLATE ||
               item == Items.LEATHER_CHESTPLATE ||
               item == Items.NETHERITE_CHESTPLATE;
    }

    /**
     * 獲取最佳胸甲槽位
     * @return 槽位，-1 表示沒有找到
     */
    public static int getBestChestplateSlot() {
        int bestSlot = -1;
        double bestValue = 0.0;
        
        for (int i = 0; i < 36; i++) {
            ItemStack stack = InventoryUtil.mc.player.getInventory().getStack(i);
            if (isChestplate(stack)) {
                double value = getArmorValue(stack);
                if (value > bestValue) {
                    bestValue = value;
                    bestSlot = i;
                }
            }
        }
        
        return bestSlot;
    }
}
