package dev.stella.api.utils.ggboy;

/**
 * SlotUtils 工具类
 * 功能：槽位ID转换工具
 */
public class SlotUtils {
    
    /**
     * 将物品栏索引转换为 ScreenHandler 槽位ID
     * @param index 物品栏索引 (0-35)
     * @return ScreenHandler 槽位ID
     */
    public static int indexToId(int index) {
        if (index >= 0 && index <= 8) {
            // 快捷栏: 0-8 -> 36-44
            return 36 + index;
        }
        // 物品栏: 9-35 -> 9-35
        return index;
    }
}
