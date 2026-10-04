package dev.stella.mod.modules.impl.misc;

import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.UpdateEvent;
import dev.stella.core.impl.KitManager;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;

public class AutoGear extends Module {
    public static AutoGear INSTANCE;

    public AutoGear() {
        super("AutoGear", Category.Misc);
        this.setChinese("自动装备");
        INSTANCE = this;
    }

    public final SliderSetting actionDelay = this.add(new SliderSetting("ActionDelay", 50, 0, 500, 1));
    public final SliderSetting clicksPerAction = this.add(new SliderSetting("Click/Action", 1, 1, 108, 1));
    public final SliderSetting itemsPerTick = this.add(new SliderSetting("ItemsPerTick", 1, 1, 9, 1));

    private HashMap<Integer, KitManager.KitItem> expectedInv = new HashMap<>();
    private int delay = 0;
    private int itemsMoved = 0;
    private int totalItemsToMove = 0;
    private boolean isEquipping = false;

    @Override
    public void onEnable() {
        itemsMoved = 0;
        isEquipping = true;
        setup();
    }

    public void setup() {
        String selectedKit = KitManager.INSTANCE.getSelectedKit();

        if (selectedKit == null || selectedKit.isEmpty()) {
            sendMessage("No kit is selected! Use .kit save <name> and .kit load <name>");
            disable();
            return;
        }

        KitManager.KitItem[] kitItems = KitManager.INSTANCE.loadKit(selectedKit);

        if (kitItems == null || kitItems.length == 0) {
            sendMessage("Kit '" + selectedKit + "' is empty or corrupted!");
            disable();
            return;
        }

        sendMessage("Loading kit: " + Formatting.AQUA + selectedKit + Formatting.RESET + " (" + kitItems.length + " items)");

        expectedInv = new HashMap<>();
        for (KitManager.KitItem item : kitItems) {
            if (item != null) {
                expectedInv.put(item.slot, item);
            }
        }

        totalItemsToMove = expectedInv.size();
        itemsMoved = 0;
        isEquipping = true;
    }

    /**
     * 外部调用装备套装
     */
    public void equipKit() {
        if (!isOn()) {
            enable();
        } else {
            setup();
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (!isEquipping || expectedInv.isEmpty()) {
            return;
        }

        if (delay > 0) {
            delay--;
            return;
        }

        if (mc.player == null || mc.player.currentScreenHandler == null) {
            return;
        }

        ScreenHandler handler = mc.player.currentScreenHandler;

        // ✅ 全容器支持：基于 Screen 类名识别，彻底解决潜影箱兼容问题
        boolean isContainerScreen =
                mc.currentScreen != null &&
                        (
                                mc.currentScreen.getClass().getName().contains("GenericContainerScreen") ||
                                        mc.currentScreen.getClass().getName().contains("ShulkerBoxScreen") ||
                                        mc.currentScreen.getClass().getName().contains("BrewingStandScreen") ||
                                        mc.currentScreen.getClass().getName().contains("CraftingScreen")
                        );

        if (!isContainerScreen) {
            return;
        }

        int actions = 0;
        int itemsThisTick = 0;

        ArrayList<Integer> clickSequence = buildClickSequence(handler);

        if (clickSequence.isEmpty()) {
            if (expectedInv.isEmpty()) {
                sendMessage("Kit equipped successfully!");
                isEquipping = false;
            }
            return;
        }

        for (int s : clickSequence) {
            if (itemsThisTick >= itemsPerTick.getValue())
                break;

            clickSlot(s);
            actions++;
            itemsThisTick++;
            itemsMoved++;

            if (actions >= clicksPerAction.getValue())
                break;
        }
        delay = actionDelay.getValueInt();
    }

    private int searchInContainer(String itemId, boolean isSingleChest, ScreenHandler handler) {
        ItemStack cursorStack = handler.getCursorStack();

        // 检查光标上的物品
        if (!cursorStack.isEmpty() && getItemId(cursorStack).equals(itemId))
            return -2;

        // 搜索容器中的物品
        // 单箱容器槽位 0-26，双箱容器槽位 0-53
        int end = isSingleChest ? 26 : 53;

        for (int i = 0; i <= end; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (!stack.isEmpty() && getItemId(stack).equals(itemId))
                return i;
        }
        return -1;
    }

    private String getItemId(ItemStack stack) {
        String id = Registries.ITEM.getId(stack.getItem()).toString();
        if (stack.getItem() instanceof net.minecraft.item.PotionItem) {
            PotionContentsComponent potion = stack.get(DataComponentTypes.POTION_CONTENTS);
            if (potion != null) {
                return id + potion.getColor();
            }
        }
        return id;
    }

    private ArrayList<Integer> buildClickSequence(ScreenHandler handler) {
        ArrayList<Integer> clicks = new ArrayList<>();
        HashMap<Integer, KitManager.KitItem> remaining = new HashMap<>(expectedInv);

        for (int slot : remaining.keySet()) {
            KitManager.KitItem kitItem = remaining.get(slot);
            if (kitItem == null) continue;

            int lower = slot < 9 ? slot + 54 : slot + 18;
            int upper = slot < 9 ? slot + 81 : slot + 45;
            int targetSlot = handler.slots.size() == 63 ? lower : upper;

            ItemStack itemInSlot = handler.slots.get(targetSlot).getStack();

            // 检查槽位是否已经有正确物品
            if (!itemInSlot.isEmpty() && getItemId(itemInSlot).equals(kitItem.itemId)) {
                expectedInv.remove(slot);
                continue;
            }

            // 在容器中搜索物品
            int sourceSlot = searchInContainer(kitItem.itemId, handler.slots.size() == 63, handler);

            if (sourceSlot == -2) {
                // 物品在光标上，点击目标槽位放置
                clicks.add(targetSlot);
                expectedInv.remove(slot);
            } else if (sourceSlot != -1) {
                // 点击源槽位拾取
                clicks.add(sourceSlot);
                // 点击目标槽位放置
                clicks.add(targetSlot);
                // 如果有物品在目标槽位，需要交换，再点击源槽位
                if (!itemInSlot.isEmpty()) {
                    clicks.add(sourceSlot);
                }
                expectedInv.remove(slot);
            }
        }
        return clicks;
    }

    private void clickSlot(int slot) {
        if (mc.player != null && mc.player.currentScreenHandler != null) {
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    @Override
    public String getInfo() {
        if (isEquipping && totalItemsToMove > 0) {
            return itemsMoved + "/" + totalItemsToMove;
        }
        return KitManager.INSTANCE.getSelectedKit();
    }
}