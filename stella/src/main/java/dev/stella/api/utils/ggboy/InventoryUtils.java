package dev.stella.api.utils.ggboy;

import dev.stella.api.utils.ggboy.ArmorBotton;
import dev.stella.api.utils.ggboy.IMinecraft;
import dev.stella.api.utils.ggboy.SlotUtils;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * InventoryUtils 工具类
 * 功能：物品栏操作工具类（切换物品、查找物品、装备交换等）
 */
public class InventoryUtils implements IMinecraft {

    // ======================== 常量定义 ========================

    public static String[] SWITCH_MODES;        // 切换模式
    public static String[] HOTBAR_SWITCH_MODES; // 快捷栏切换模式
    public static String[] SWAP_MODES;          // 交换模式

    public static int HOTBAR_START;      // 快捷栏起始槽位 (0)
    public static int HOTBAR_END;        // 快捷栏结束槽位 (8)
    public static int INVENTORY_START;   // 物品栏起始槽位 (9)
    public static int INVENTORY_END;     // 物品栏结束槽位 (35)

    static {
        // 初始化切换模式数组
        SWITCH_MODES = new String[]{"Normal", "AltSwap", "Hotbar", "InventoryMove", "AltMove"};

        // 初始化快捷栏切换模式
        HOTBAR_SWITCH_MODES = new String[]{"Normal", "AltSwap", "Hotbar"};

        // 初始化交换模式
        SWAP_MODES = new String[]{"InventoryMove", "AltMove"};

        // 初始化槽位范围
        HOTBAR_START = 0;      // 快捷栏起始
        HOTBAR_END = 8;        // 快捷栏结束
        INVENTORY_START = 9;   // 物品栏起始
        INVENTORY_END = 35;    // 物品栏结束
    }

    // ======================== 物品栏信息获取 ========================

    /**
     * 获取快捷栏的物品映射
     */
    public static HashMap<Integer, Item> getHotBarMsg() {
        HashMap<Integer, Item> result = new HashMap<>();
        for (int i = 0; i < 9; i++) {
            result.put(i, mc.player.getInventory().getStack(i).getItem());
        }
        return result;
    }

    /**
     * 同步物品栏到服务端
     */
    public static void sync() {
        // 使用 Stella 的 Module.sendSequencedPacket 方法
        // 这里简单更新物品栏即可
        mc.player.getInventory().updateItems();
    }

    /**
     * 将索引转换为槽位ID
     */
    public static int indexToSlot(int index) {
        if (index >= 0 && index <= 8) {
            return 36 + index;
        }
        return index;
    }

    // ======================== 物品查找方法 ========================

    /**
     * 获取某物品的总数量
     */
    public static int getCount(Item item) {
        int result = 0;
        for (int i = INVENTORY_END; i >= HOTBAR_START; i--) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() != item) continue;
            result += stack.getCount();
        }
        return result;
    }

    /**
     * 查找物品（按物品实例）
     */
    public static int find(Item item) {
        return find(item, HOTBAR_START, INVENTORY_END);
    }

    /**
     * 按顺序查找物品（从起始到结束）
     */
    public static int findByOrder(Item item) {
        return findByOrder(item, HOTBAR_START, INVENTORY_END);
    }

    /**
     * 在快捷栏查找物品
     */
    public static int findHotbar(Item item) {
        return find(item, HOTBAR_START, HOTBAR_END);
    }

    /**
     * 在物品栏（非快捷栏）查找物品
     */
    public static int findInventory(Item item) {
        return find(item, INVENTORY_START, INVENTORY_END);
    }

    /**
     * 查找非指定物品的快捷栏槽位
     */
    public static int findElseInHotbar(Item item) {
        for (int i = HOTBAR_END; i >= HOTBAR_START; i--) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == item) continue;
            return i;
        }
        return -1;
    }

    /**
     * 按顺序查找物品（正向遍历）
     */
    public static int findByOrder(Item item, int start, int end) {
        for (int i = start; i <= end; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() != item) continue;
            return i;
        }
        return -1;
    }

    /**
     * 查找物品（反向遍历，优先找靠后的槽位）
     */
    public static int find(Item item, int start, int end) {
        for (int i = end; i >= start; i--) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() != item) continue;
            return i;
        }
        return -1;
    }

    /**
     * 按物品类型查找（类匹配）
     */
    public static int find(Class<? extends Item> item) {
        return find(item, HOTBAR_START, INVENTORY_END);
    }

    public static int findHotbar(Class<? extends Item> item) {
        return find(item, HOTBAR_START, HOTBAR_END);
    }

    public static int findInventory(Class<? extends Item> item) {
        return find(item, INVENTORY_START, INVENTORY_END);
    }

    public static int find(Class<? extends Item> item, int start, int end) {
        for (int i = end; i >= start; i--) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!item.isInstance(stack.getItem())) continue;
            return i;
        }
        return -1;
    }

    /**
     * 查找指定数量以上的物品
     */
    public static int findInventory(Item item, int count) {
        for (int i = INVENTORY_END; i >= INVENTORY_START; i--) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() != item || stack.getCount() < count) continue;
            return i;
        }
        return -1;
    }

    // ======================== 工具查找 ========================

    /**
     * 查找硬度最高的方块（用于挖掘）
     */
    public static int findHardestBlock(int start, int end) {
        float bestHardness = -1.0f;
        int bestSlot = -1;
        for (int i = start; i <= end; i++) {
            Item item = mc.player.getInventory().getStack(i).getItem();
            if (!(item instanceof BlockItem)) continue;
            BlockItem blockItem = (BlockItem) item;
            float hardness = blockItem.getBlock().getHardness();
            if (hardness == -1.0f) {
                return i;  // 无法破坏的方块（如基岩）
            }
            if (!(hardness > bestHardness)) continue;
            bestHardness = hardness;
            bestSlot = i;
        }
        return bestSlot;
    }

    /**
     * 查找挖掘指定方块最快的工具
     */
    public static int findFastestItem(net.minecraft.block.BlockState blockState, int start, int end) {
        double bestScore = -1.0;
        int bestSlot = -1;
        for (int i = start; i <= end; i++) {
            double score = mc.player.getInventory().getStack(i).getMiningSpeedMultiplier(blockState);
            if (!(score > bestScore)) continue;
            bestScore = score;
            bestSlot = i;
        }
        return bestSlot;
    }

    /**
     * 查找最好的剑（优先级：下界合金 > 钻石 > 铁 > 金 > 石 > 木）
     */
    public static int findBestSword(int start, int end) {
        int netheriteSlot = -1;
        int diamondSlot = -1;
        int ironSlot = -1;
        int goldenSlot = -1;
        int stoneSlot = -1;
        int woodenSlot = -1;

        for (int i = end; i >= start; i--) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.NETHERITE_SWORD) {
                netheriteSlot = i;
            }
            if (stack.getItem() == Items.DIAMOND_SWORD) {
                diamondSlot = i;
            }
            if (stack.getItem() == Items.IRON_SWORD) {
                ironSlot = i;
            }
            if (stack.getItem() == Items.GOLDEN_SWORD) {
                goldenSlot = i;
            }
            if (stack.getItem() == Items.STONE_SWORD) {
                stoneSlot = i;
            }
            if (stack.getItem() == Items.WOODEN_SWORD) {
                woodenSlot = i;
            }
        }

        if (netheriteSlot != -1) return netheriteSlot;
        if (diamondSlot != -1) return diamondSlot;
        if (ironSlot != -1) return ironSlot;
        if (goldenSlot != -1) return goldenSlot;
        if (stoneSlot != -1) return stoneSlot;
        return woodenSlot;
    }

    /**
     * 查找空槽位
     */
    public static int findEmptySlot(int start, int end) {
        for (int i = end; i >= start; i--) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) continue;
            return i;
        }
        return -1;
    }

    /**
     * 按物品类型查找所有槽位
     */
    public static List<Integer> findSoltsByItemClass(Class itemClass) {
        ArrayList<Integer> result = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            if (!itemClass.isInstance(mc.player.getInventory().getStack(i).getItem())) continue;
            result.add(i);
        }
        return result;
    }

    // ======================== 装备操作 ========================

    /**
     * 交换盔甲（用于装备胸甲/鞘翅）
     * @param slot 物品栏槽位
     * @param armorSlot 盔甲槽位枚举
     */
    public static void inventorySwapArmor(int slot, ArmorBotton armorSlot) {
        // 点击源槽位
        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,  // 同步ID
            SlotUtils.indexToId(slot),              // 源槽位
            0,                                      // 按钮
            SlotActionType.PICKUP,                  // 动作类型
            mc.player                               // 玩家
        );
        // 点击目标盔甲槽位
        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            armorSlot.getSlotBotton(),              // 盔甲槽位
            0,
            SlotActionType.PICKUP,
            mc.player
        );
        // 再次点击源槽位（完成交换）
        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            SlotUtils.indexToId(slot),
            0,
            SlotActionType.PICKUP,
            mc.player
        );
        // 更新物品栏
        mc.player.getInventory().updateItems();
    }

    // ======================== 槽位判断 ========================

    /**
     * 判断是否在物品栏界面
     */
    public static boolean inInventoryScreen() {
        return mc.currentScreen instanceof InventoryScreen
            || mc.currentScreen instanceof CraftingScreen
            || mc.currentScreen instanceof GenericContainerScreen;
    }

    /**
     * 是否为主手
     */
    public static boolean isMainHand(int slot) {
        return slot == mc.player.getInventory().selectedSlot;
    }

    /**
     * 是否为副手
     */
    public static boolean isOffhand(int slot) {
        return slot == PlayerInventory.OFF_HAND_SLOT;
    }

    /**
     * 是否为快捷栏槽位
     */
    public static boolean isHotbar(int slot) {
        return slot >= 0 && slot <= 8;
    }

    /**
     * 是否为主物品栏（非快捷栏）
     */
    public static boolean isMain(int slot) {
        return slot >= 9 && slot <= 35;
    }

    /**
     * 是否为盔甲槽位
     */
    public static boolean isArmor(int slot) {
        return slot >= 36 && slot <= 39;
    }
}
