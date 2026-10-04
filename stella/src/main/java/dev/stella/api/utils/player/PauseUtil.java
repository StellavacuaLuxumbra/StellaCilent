package dev.stella.api.utils.player;

import net.minecraft.item.Items;

/**
 * 暫停工具類
 * 用於檢查是否應該暫停某些操作
 */
public class PauseUtil {

    /**
     * 檢查是否應該暫停（吃東西時）
     * @param sameHand 是否檢查同一隻手
     * @return 是否應該暫停
     */
    public static boolean checkPause(boolean sameHand) {
        if (InventoryUtil.mc.player == null) return false;
        
        // 檢查是否在使用物品（吃東西、喝藥水等）
        if (InventoryUtil.mc.player.isUsingItem()) {
            // 檢查是否是食物或藥水
            if (InventoryUtil.mc.player.getMainHandStack().getItem() == Items.GOLDEN_APPLE ||
                InventoryUtil.mc.player.getMainHandStack().getItem() == Items.ENCHANTED_GOLDEN_APPLE ||
                InventoryUtil.mc.player.getMainHandStack().getItem() == Items.POTION ||
                InventoryUtil.mc.player.getOffHandStack().getItem() == Items.GOLDEN_APPLE ||
                InventoryUtil.mc.player.getOffHandStack().getItem() == Items.ENCHANTED_GOLDEN_APPLE ||
                InventoryUtil.mc.player.getOffHandStack().getItem() == Items.POTION) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 檢查是否應該暫停（更嚴格的檢查）
     * @return 是否應該暫停
     */
    public static boolean checkPauseStrict() {
        if (InventoryUtil.mc.player == null) return false;
        
        // 只要在使用物品就暫停
        return InventoryUtil.mc.player.isUsingItem();
    }
}
