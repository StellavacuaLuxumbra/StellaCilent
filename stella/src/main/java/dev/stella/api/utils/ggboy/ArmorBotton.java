package dev.stella.api.utils.ggboy;

/**
 * ArmorBotton 枚举
 * 功能：定义盔甲槽位的映射关系
 */
public enum ArmorBotton {
    /**
     * 靴子槽位
     */
    BOOTS(0),
    /**
     * 护腿槽位
     */
    LEGGINGS(1),
    /**
     * 胸甲槽位
     */
    CHESTPLATE(2),
    /**
     * 头盔槽位
     */
    HELMET(3);

    private final int slotBotton;

    ArmorBotton(int slot) {
        this.slotBotton = slot;
    }

    /**
     * 获取槽位ID（在 ScreenHandler 中的ID）
     * Minecraft 盔甲槽位ID： boots=8, leggings=7, chestplate=6, helmet=5
     */
    public int getSlotBotton() {
        return 8 - this.slotBotton;
    }
}
