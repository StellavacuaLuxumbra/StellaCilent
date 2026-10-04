/*
 * Decompiled with CFR 0.152.
 */
package dev.stella.mod.modules.impl.combat;

import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.UpdateEvent;
import dev.stella.api.utils.math.Timer;
import dev.stella.api.utils.player.InventoryUtil;
import dev.stella.api.utils.world.BlockUtil;

import dev.stella.core.impl.KitManager;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.impl.player.PacketMine;
import dev.stella.mod.modules.settings.impl.BindSetting;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import dev.stella.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class AutoRegear extends Module {
    public static AutoRegear INSTANCE;
    public final BooleanSetting rotate = this.add(new BooleanSetting("Rotate", true));
    public final Timer timeoutTimer = new Timer();
    final int[] stealCountList = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    private final BooleanSetting autoDisable = this.add(new BooleanSetting("AutoDisable", true));
    private final SliderSetting disableTime = this.add(new SliderSetting("DisableTime", 500, 0, 1000));
    private final BooleanSetting place = this.add(new BooleanSetting("Place", true));
    private final BooleanSetting inventory = this.add(new BooleanSetting("InventorySwap", true));
    private final BooleanSetting preferOpen = this.add(new BooleanSetting("PerferOpen", true));
    private final BooleanSetting open = this.add(new BooleanSetting("Open", false));
    private final SliderSetting range = this.add(new SliderSetting("MaxRange", 4.0, 0.0, 6.0, 0.1));
    private final SliderSetting minRange = this.add(new SliderSetting("MinRange", 1.0, 0.0, 3.0, 0.1));
    private final BooleanSetting mine = this.add(new BooleanSetting("Mine", true));
    private final BooleanSetting take = this.add(new BooleanSetting("Take", true));
    private final BooleanSetting smart = this.add(new BooleanSetting("Smart", true, this.take::getValue).setParent());
    private final BooleanSetting forceMove = this.add(new BooleanSetting("ForceQuickMove", true, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting takeSpeed = this.add(new SliderSetting("TakeSpeed", 1, 1, 10, 1, this.take::getValue));
    private final SliderSetting clickDelay = this.add(new SliderSetting("ClickDelay", 150, 50, 500, 5, this.take::getValue));
    private final BooleanSetting instantTake = this.add(new BooleanSetting("InstantTake", false, this.take::getValue));
    private final BindSetting placeKey = this.add(new BindSetting("PlaceKey", -1));
    private final BooleanSetting onlyGround = this.add(new BooleanSetting("OnlyGround", true));
    private final BooleanSetting onlyHotbar = this.add(new BooleanSetting("OnlyHotbar", false));

    // Kit settings
    public String currentKitName = null;
    private final BooleanSetting replaceItem = this.add(new BooleanSetting("ReplaceItem", false));
    
    // 存储 Kit 名称列表
    private List<String> kitNames = new ArrayList<>(Arrays.asList("None"));
    
    // 使用 SliderSetting 来选择 Kit，0 表示 None
    // 初始最大值为 1，稍后在构造函数中会根据实际文件数量更新
    public final SliderSetting kitSetting = this.add(new SliderSetting("Kit", 0, 0, 1, 1));

    // Take speed control
    private int takeProgress = 0;
    private final Timer clickTimer = new Timer();
    private int takenThisCycle = 0;
    
    private final Timer timer = new Timer();
    private final List<BlockPos> openList = new ArrayList<BlockPos>();
    public BlockPos placePos = null;
    private BlockPos openPos;
    private boolean opend = false;
    private boolean on = false;
    private boolean placeKeyPressed = false;

    private boolean hasShownNoShulkerMessage = false;
    private boolean waitingForTransfer = false;
    private final Timer transferTimer = new Timer();
    private final java.util.Map<String, Integer> neededItems = new java.util.HashMap<>();
    private final java.util.Set<String> kitItemIds = new java.util.HashSet<>();

    public AutoRegear() {
        super("AutoRegear", Module.Category.Combat);
        this.setChinese("自动补给");
        INSTANCE = this;
        refreshKitList();
    }
    
    /**
     * 刷新 Kit 列表
     */
    public void refreshKitList() {
        File kitsDir = new File("stella/kits");
        List<String> newKitNames = new ArrayList<>();
        newKitNames.add("None");
        
        if (kitsDir.exists() && kitsDir.isDirectory()) {
            File[] files = kitsDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    String kitName = file.getName().replace(".json", "");
                    // 过滤掉包含小数点的kit名称，避免kit0.0等无效名称
                    if (!kitName.contains(".")) {
                        newKitNames.add(kitName);
                    }
                }
            }
        }
        
        this.kitNames = newKitNames;
        
        // 注意：这里我们不再调用 setMax，因为 SliderSetting 可能不支持动态修改最大值
        // 如果你的 SliderSetting 实现支持 setMax，可以取消下面这行的注释
        // this.kitSetting.setMax(Math.max(1, this.kitNames.size() - 1));
        
        // 更新当前 Kit 名称
        updateCurrentKitName();
    }
    
    /**
     * 根据 SliderSetting 的值更新当前 Kit 名称
     */
    private void updateCurrentKitName() {
        int index = this.kitSetting.getValueInt();
        if (index >= 0 && index < this.kitNames.size()) {
            String name = this.kitNames.get(index);
            this.currentKitName = "None".equals(name) ? null : name;
        } else {
            this.currentKitName = null;
        }
    }

    /**
     * 加载当前 Kit 并计算还缺的物品数量
     */
    private void loadKitItems() {
        this.neededItems.clear();
        if (this.currentKitName == null) return;

        KitManager.KitItem[] kitItems = KitManager.INSTANCE.loadKit(this.currentKitName);
        if (kitItems == null || kitItems.length == 0) return;

        java.util.Map<String, Integer> expected = new java.util.HashMap<>();
        this.kitItemIds.clear();
        for (KitManager.KitItem item : kitItems) {
            if (item == null) continue;
            this.kitItemIds.add(item.itemId);
            expected.merge(item.itemId, item.count, Integer::sum);
        }

        for (java.util.Map.Entry<String, Integer> entry : expected.entrySet()) {
            int have = getPlayerItemCount(entry.getKey());
            int need = entry.getValue() - have;
            if (need > 0) {
                this.neededItems.put(entry.getKey(), need);
            }
        }
    }

    public int findShulker() {
        if (this.inventory.getValue()) {
            int start = 0;
            int end = 36;
            if (this.onlyHotbar.getValue()) {
                start = 0;
                end = 9;
            }
            for (int i = start; i < end; ++i) {
                ItemStack stack = AutoRegear.mc.player.getInventory().getStack(i);
                if (stack.isEmpty()) continue;
                
                Item item = stack.getItem();
                if (!(item instanceof BlockItem)) continue;
                
                BlockItem blockItem = (BlockItem) item;
                if (!(blockItem.getBlock() instanceof ShulkerBoxBlock)) continue;
                
                return i < 9 ? i + 36 : i;
            }
            return -1;
        }
        return InventoryUtil.findClass(ShulkerBoxBlock.class);
    }

    @Override
    public void onEnable() {
        // 刷新 Kit 列表
        refreshKitList();
        
        this.opend = false;
        this.openPos = null;
        this.timeoutTimer.reset();
        this.placePos = null;
        this.takeProgress = 0;
        this.takenThisCycle = 0;
        this.clickTimer.reset();
        this.placeKeyPressed = false;
        this.hasShownNoShulkerMessage = false;
        this.waitingForTransfer = false;
        this.transferTimer.reset();

        if (AutoRegear.nullCheck()) {
            return;
        }

        if (this.onlyGround.getValue() && !AutoRegear.mc.player.isOnGround()) {
            this.sendMessage("\u00a74AutoRegear disabled: Player is not on ground");
            this.disable();
            return;
        }

        // 更新当前 Kit 名称
        updateCurrentKitName();

        // 加载 kit 物品需求
        loadKitItems();

        if (this.currentKitName != null) {
            this.sendMessage("\u00a7aUsing Kit: \u00a7f" + this.currentKitName + " (need " + this.neededItems.size() + " types)");
        }

        if (this.open.getValue()) {
            for (BlockPos pos : BlockUtil.getSphere((float)this.range.getValue())) {
                if (AutoRegear.mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock &&
                    AutoRegear.mc.world.isAir(pos.up())) {
                    if (isValidShulkerBox(pos)) {
                        this.openPos = pos;
                        this.sendMessage("\u00a72Found shulker box at: " + pos.toShortString());
                        break;
                    }
                }
            }
        }

        if (this.openPos == null && this.place.getValue()) {
            if (this.findShulker() == -1) {
                this.sendMessage("\u00a74No shulkerbox found in inventory. AutoRegear disabled.");
                this.disable();
                return;
            }
            this.doPlace();
        } else if (this.openPos != null) {
            this.timer.reset();
        } else {
            this.sendMessage("\u00a74No shulker box found nearby. AutoRegear disabled.");
            this.disable();
        }
    }

    private void doPlace() {
        if (AutoRegear.nullCheck()) {
            return;
        }
        
        int oldSlot = AutoRegear.mc.player.getInventory().selectedSlot;
        BlockPos bestPos = this.findBestPlacePosition();
        
        if (bestPos != null) {
            if (this.findShulker() == -1) {
                this.sendMessage("\u00a74No shulkerbox found. AutoRegear disabled.");
                this.disable();
                return;
            }

            if (AutoRegear.mc.world.getBlockState(bestPos).getBlock() instanceof ShulkerBoxBlock) {
                this.openPos = bestPos;
                this.placePos = bestPos;
                this.sendMessage("\u00a72Found existing shulker at: " + bestPos.toShortString());
                return;
            }

            BlockPos abovePos = bestPos.offset(Direction.UP);
            if (!AutoRegear.mc.world.isAir(abovePos) && !BlockUtil.canReplace(abovePos)) {
                this.sendMessage("\u00a74No enough space above placement position. AutoRegear disabled.");
                this.disable();
                return;
            }

            if (this.inventory.getValue()) {
                int slot = this.findShulker();
                InventoryUtil.inventorySwap(slot, oldSlot);
                this.placeBlock(bestPos);
                this.placePos = bestPos;
                this.openPos = bestPos;
                InventoryUtil.inventorySwap(slot, oldSlot);
            } else {
                InventoryUtil.switchToSlot(this.findShulker());
                this.placeBlock(bestPos);
                this.placePos = bestPos;
                this.openPos = bestPos;
                InventoryUtil.switchToSlot(oldSlot);
            }
            this.timer.reset();
            this.sendMessage("\u00a72Placed shulker box at: " + bestPos.toShortString());
        } else {
            this.sendMessage("\u00a74No valid place position found. AutoRegear disabled.");
            this.disable();
        }
    }

    private BlockPos findBestPlacePosition() {
        List<BlockPos> candidates = new ArrayList<>();
        
        for (BlockPos pos : BlockUtil.getSphere((float)this.range.getValue())) {
            if (!this.isValidPlaceCandidate(pos)) continue;
            candidates.add(pos);
        }
        
        if (candidates.isEmpty()) return null;
        
        candidates.sort((a, b) -> Double.compare(this.getPlaceScore(b), this.getPlaceScore(a)));
        return candidates.get(0);
    }

    private boolean isValidPlaceCandidate(BlockPos pos) {
        BlockPos belowPos = pos.offset(Direction.DOWN);
        BlockPos abovePos = pos.offset(Direction.UP);
        
        if (!AutoRegear.mc.world.isAir(pos)) return false;
        if (!AutoRegear.mc.world.isAir(abovePos) && !BlockUtil.canReplace(abovePos)) return false;
        
        if (AutoRegear.mc.world.isAir(belowPos)) return false;
        if (!AutoRegear.mc.world.getBlockState(belowPos).isSolid()) return false;
        
        if (!AutoRegear.mc.world.getFluidState(belowPos).isEmpty()) return false;
        
        double distSq = AutoRegear.mc.player.squaredDistanceTo(pos.toCenterPos());
        if (distSq < this.minRange.getValue() * this.minRange.getValue()) return false;
        if (distSq > this.range.getValue() * this.range.getValue()) return false;
        
        if (!BlockUtil.clientCanPlace(pos, false)) return false;
        
        if (!AutoRegear.mc.world.getFluidState(pos).isEmpty()) return false;
        
        return true;
    }

    private double getPlaceScore(BlockPos pos) {
        double score = 0.0;
        
        double dist = Math.sqrt(AutoRegear.mc.player.squaredDistanceTo(pos.toCenterPos()));
        score -= dist * 8.0;
        
        int playerY = AutoRegear.mc.player.getBlockPos().getY();
        int posY = pos.getY();
        if (posY == playerY) {
            score += 80.0;
        } else if (posY == playerY - 1) {
            score += 30.0;
        } else if (posY == playerY + 1) {
            score += 15.0;
        }
        
        Vec3d playerPos = AutoRegear.mc.player.getPos();
        Vec3d lookVec = AutoRegear.mc.player.getRotationVec(1.0f);
        Vec3d toPos = pos.toCenterPos().subtract(playerPos).normalize();
        double dot = lookVec.dotProduct(toPos);
        score += dot * 25.0;
        
        score += this.getSafetyScore(pos);
        
        return score;
    }

    private double getSafetyScore(BlockPos pos) {
        double score = 0.0;
        
        Box expandBox = new Box(pos).expand(1.5);
        for (Entity entity : BlockUtil.getEntities(expandBox)) {
            if (entity instanceof EndCrystalEntity) {
                score -= 300.0;
            }
        }
        
        Block block = AutoRegear.mc.world.getBlockState(pos).getBlock();
        Block aboveBlock = AutoRegear.mc.world.getBlockState(pos.up()).getBlock();
        if (block == Blocks.FIRE || aboveBlock == Blocks.FIRE 
            || block == Blocks.SOUL_FIRE || aboveBlock == Blocks.SOUL_FIRE) {
            score -= 150.0;
        }
        if (block == Blocks.LAVA || aboveBlock == Blocks.LAVA) {
            score -= 200.0;
        }
        
        return score;
    }

    @Override
    public void onDisable() {
        this.opend = false;
        this.openPos = null;
        this.placePos = null;
        this.placeKeyPressed = false;

        if (this.mine.getValue() && this.placePos != null && AutoRegear.mc.world != null) {
            if (AutoRegear.mc.world.getBlockState(this.placePos).getBlock() instanceof ShulkerBoxBlock) {
                if (isValidShulkerBox(this.placePos)) {
                    PacketMine.INSTANCE.mine(this.placePos);
                }
            }
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (AutoRegear.nullCheck()) {
            return;
        }

        if (this.onlyGround.getValue() && !AutoRegear.mc.player.isOnGround()) {
            return;
        }

        boolean currentKeyState = this.placeKey.isPressed();
        
        if (currentKeyState && !this.placeKeyPressed && AutoRegear.mc.currentScreen == null) {
            this.placeKeyPressed = true;
            this.opend = false;
            this.openPos = null;
            this.timeoutTimer.reset();
            this.placePos = null;
            this.doPlace();
            this.on = true;
        } else if (!currentKeyState) {
            this.placeKeyPressed = false;
            this.on = false;
        }
        
        this.openList.removeIf(pos -> !(AutoRegear.mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock));
        
        if (this.openPos == null && this.open.getValue()) {
            for (BlockPos pos : BlockUtil.getSphere((float)this.range.getValue())) {
                if (AutoRegear.mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock &&
                    AutoRegear.mc.world.isAir(pos.up())) {
                    if (isValidShulkerBox(pos)) {
                        this.openPos = pos;
                        break;
                    }
                }
            }
        }
        
        if (!(AutoRegear.mc.currentScreen instanceof ShulkerBoxScreen)) {
            if (this.waitingForTransfer) {
                if (this.transferTimer.passed(1000)) {
                    this.waitingForTransfer = false;
                    this.takeProgress = 0;
                }
                return;
            }

            if (this.opend) {
                this.opend = false;
                if (this.autoDisable.getValue()) {
                    this.timeoutToDisable();
                }
                if (this.mine.getValue() && this.openPos != null) {
                    if (AutoRegear.mc.world.getBlockState(this.openPos).getBlock() instanceof ShulkerBoxBlock) {
                        if (isValidShulkerBox(this.openPos)) {
                            PacketMine.INSTANCE.mine(this.openPos);
                        }
                    } else {
                        this.openPos = null;
                    }
                }
                return;
            }
            if (this.open.getValue()) {
                if (AutoRegear.mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler) {
                    return;
                }
                
                if (this.placePos != null && (double)MathHelper.sqrt((float)((float)AutoRegear.mc.player.squaredDistanceTo(this.placePos.toCenterPos()))) <= this.range.getValue() && AutoRegear.mc.world.isAir(this.placePos.up()) && (!this.timer.passed(500L) || AutoRegear.mc.world.getBlockState(this.placePos).getBlock() instanceof ShulkerBoxBlock)) {
                    if (AutoRegear.mc.world.getBlockState(this.placePos).getBlock() instanceof ShulkerBoxBlock) {
                        if (isValidShulkerBox(this.placePos)) {
                            this.openPos = this.placePos;
                            BlockUtil.clickBlock(this.placePos, BlockUtil.getClickSide(this.placePos), this.rotate.getValue());
                        }
                    }
                } else if (this.openPos != null && AutoRegear.mc.world.getBlockState(this.openPos).getBlock() instanceof ShulkerBoxBlock) {
                    if (isValidShulkerBox(this.openPos)) {
                        BlockUtil.clickBlock(this.openPos, BlockUtil.getClickSide(this.openPos), this.rotate.getValue());
                    }
                } else {
                    boolean found = false;
                    for (BlockPos pos2 : BlockUtil.getSphere((float)this.range.getValue())) {
                        if (this.openList.contains(pos2) || !AutoRegear.mc.world.isAir(pos2.up()) && !BlockUtil.canReplace(pos2.up()) || !(AutoRegear.mc.world.getBlockState(pos2).getBlock() instanceof ShulkerBoxBlock)) continue;
                        if (isValidShulkerBox(pos2)) {
                            this.openPos = pos2;
                            BlockUtil.clickBlock(pos2, BlockUtil.getClickSide(pos2), this.rotate.getValue());
                            found = true;
                            break;
                        }
                    }
                    if (!found && this.autoDisable.getValue()) {
                        this.doPlace();
                    }
                }
            } else if (!this.take.getValue() && this.autoDisable.getValue()) {
                this.timeoutToDisable();
            }
            return;
        }
        this.opend = true;
        if (this.openPos != null) {
            this.openList.add(this.openPos);
        }
        if (!this.take.getValue()) {
            if (this.autoDisable.getValue()) {
                this.timeoutToDisable();
            }
            return;
        }

        if (!this.instantTake.getValue() && !this.clickTimer.passed(this.clickDelay.getValueInt())) {
            return;
        }

        ScreenHandler screenHandler = AutoRegear.mc.player.currentScreenHandler;
        if (!(screenHandler instanceof ShulkerBoxScreenHandler)) {
            return;
        }
        ShulkerBoxScreenHandler shulkerCheck = (ShulkerBoxScreenHandler)screenHandler;
        if (shulkerCheck.slots == null || shulkerCheck.slots.isEmpty()) {
            return;
        }

        boolean take = false;
        if (screenHandler instanceof ShulkerBoxScreenHandler) {
            ShulkerBoxScreenHandler shulker = (ShulkerBoxScreenHandler)screenHandler;

            if (this.currentKitName != null && !this.neededItems.isEmpty()) {
                // 按需求量降序排序，优先拿取需求量大的物品
                java.util.List<java.util.Map.Entry<java.lang.String,java.lang.Integer>> sortedNeeded = new ArrayList<>(this.neededItems.entrySet());
                sortedNeeded.sort((a, b) -> b.getValue().compareTo(a.getValue()));
                
                for (java.util.Map.Entry<java.lang.String,java.lang.Integer> entry : sortedNeeded) {
                    java.lang.String neededItemId = entry.getKey();
                    // 在潜影箱物品槽(0-26)中搜索该物品
                    for (Slot slot : shulker.slots) {
                        if (slot.id >= 27 || slot.getStack().isEmpty()) continue;
                        java.lang.String itemId = Registries.ITEM.getId(slot.getStack().getItem()).toString();
                        if (!itemId.equals(neededItemId)) continue;
                        
                        java.lang.Integer need = entry.getValue();
                        if (need <= 0) continue;
                        
                        AutoRegear.mc.interactionManager.clickSlot(shulker.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, (PlayerEntity)AutoRegear.mc.player);
                        take = true;
                        this.takenThisCycle++;
                        
                        int stackCount = slot.getStack().getCount();
                        need -= stackCount;
                        if (need <= 0) {
                            this.neededItems.remove(neededItemId);
                        } else {
                            this.neededItems.put(neededItemId, need);
                        }
                        
                        if (this.takenThisCycle >= this.takeSpeed.getValueInt()) break;
                    }
                    if (this.takenThisCycle >= this.takeSpeed.getValueInt()) break;
                }
            } else {
                for (Slot slot : shulker.slots) {
                    if (slot.id < 27 && !slot.getStack().isEmpty()) {
                        AutoRegear.mc.interactionManager.clickSlot(shulker.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, (PlayerEntity)AutoRegear.mc.player);
                        take = true;
                        this.takenThisCycle++;
                        if (this.takenThisCycle >= this.takeSpeed.getValueInt()) break;
                    }
                }
            }

            // 把背包里不在 kit 中的物品移回潜影盒（只在空槽位放回）
            if (this.replaceItem.getValue() && !this.kitItemIds.isEmpty()) {
                for (int slotId = 27; slotId < shulker.slots.size(); slotId++) {
                    ItemStack stack = shulker.getSlot(slotId).getStack();
                    if (!stack.isEmpty()) continue; // 只在空槽位放回
                    
                    // 在玩家背包中搜索不在kit中的物品
                    for (int invSlot = 0; invSlot < 36; invSlot++) {
                        ItemStack invStack = AutoRegear.mc.player.getInventory().getStack(invSlot);
                        if (invStack.isEmpty()) continue;
                        java.lang.String itemId = Registries.ITEM.getId(invStack.getItem()).toString();
                        if (this.kitItemIds.contains(itemId)) continue;
                        
                        // 移动物品到潜影箱空槽位
                        AutoRegear.mc.interactionManager.clickSlot(AutoRegear.mc.player.currentScreenHandler.syncId, invSlot < 9 ? invSlot + 36 : invSlot, 0, SlotActionType.PICKUP, (PlayerEntity)AutoRegear.mc.player);
                        AutoRegear.mc.interactionManager.clickSlot(shulker.syncId, slotId, 0, SlotActionType.PICKUP, (PlayerEntity)AutoRegear.mc.player);
                        AutoRegear.mc.interactionManager.clickSlot(AutoRegear.mc.player.currentScreenHandler.syncId, invSlot < 9 ? invSlot + 36 : invSlot, 0, SlotActionType.PICKUP, (PlayerEntity)AutoRegear.mc.player);
                        take = true;
                        this.takenThisCycle++;
                        if (this.takenThisCycle >= this.takeSpeed.getValueInt()) break;
                    }
                    if (this.takenThisCycle >= this.takeSpeed.getValueInt()) break;
                }
            }
        }
        if (this.autoDisable.getValue() && !take) {
            this.timeoutToDisable();
        }
    }

    private void timeoutToDisable() {
        if (this.timeoutTimer.passed(this.disableTime.getValueInt())) {
            this.disable();
        }
    }

    private Type needSteal(ItemStack i) {
        if (i.getItem().equals(Items.END_CRYSTAL) && this.stealCountList[0] > 0) {
            this.stealCountList[0] = this.stealCountList[0] - i.getCount();
            if (this.stealCountList[0] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Items.EXPERIENCE_BOTTLE) && this.stealCountList[1] > 0) {
            this.stealCountList[1] = this.stealCountList[1] - i.getCount();
            if (this.stealCountList[1] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Items.TOTEM_OF_UNDYING) && this.stealCountList[2] > 0) {
            this.stealCountList[2] = this.stealCountList[2] - i.getCount();
            if (this.stealCountList[2] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Items.ENCHANTED_GOLDEN_APPLE) && this.stealCountList[3] > 0) {
            this.stealCountList[3] = this.stealCountList[3] - i.getCount();
            if (this.stealCountList[3] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(net.minecraft.block.Blocks.OBSIDIAN.asItem()) && this.stealCountList[4] > 0) {
            this.stealCountList[4] = this.stealCountList[4] - i.getCount();
            if (this.stealCountList[4] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(net.minecraft.block.Blocks.COBWEB.asItem()) && this.stealCountList[5] > 0) {
            this.stealCountList[5] = this.stealCountList[5] - i.getCount();
            if (this.stealCountList[5] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(net.minecraft.block.Blocks.GLOWSTONE.asItem()) && this.stealCountList[6] > 0) {
            this.stealCountList[6] = this.stealCountList[6] - i.getCount();
            if (this.stealCountList[6] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(net.minecraft.block.Blocks.RESPAWN_ANCHOR.asItem()) && this.stealCountList[7] > 0) {
            this.stealCountList[7] = this.stealCountList[7] - i.getCount();
            if (this.stealCountList[7] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Items.ENDER_PEARL) && this.stealCountList[8] > 0) {
            this.stealCountList[8] = this.stealCountList[8] - i.getCount();
            if (this.stealCountList[8] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem() instanceof BlockItem && ((BlockItem)i.getItem()).getBlock() instanceof net.minecraft.block.PistonBlock && this.stealCountList[9] > 0) {
            this.stealCountList[9] = this.stealCountList[9] - i.getCount();
            if (this.stealCountList[9] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(net.minecraft.block.Blocks.REDSTONE_BLOCK.asItem()) && this.stealCountList[10] > 0) {
            this.stealCountList[10] = this.stealCountList[10] - i.getCount();
            if (this.stealCountList[10] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem() instanceof BlockItem && ((BlockItem)i.getItem()).getBlock() instanceof net.minecraft.block.BedBlock && this.stealCountList[11] > 0) {
            this.stealCountList[11] = this.stealCountList[11] - i.getCount();
            if (this.stealCountList[11] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem() == Items.SPLASH_POTION) {
            PotionContentsComponent potionContentsComponent = (PotionContentsComponent)i.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT);
            for (StatusEffectInstance effect : potionContentsComponent.getEffects()) {
                if (effect.getEffectType().value() == StatusEffects.SPEED.value()) {
                    if (this.stealCountList[12] <= 0) continue;
                    this.stealCountList[12] = this.stealCountList[12] - i.getCount();
                    if (this.stealCountList[12] < 0) {
                        return Type.Stack;
                    }
                    return Type.QuickMove;
                }
                if (effect.getEffectType().value() == StatusEffects.RESISTANCE.value()) {
                    if (this.stealCountList[13] <= 0) continue;
                    this.stealCountList[13] = this.stealCountList[13] - i.getCount();
                    if (this.stealCountList[13] < 0) {
                        return Type.Stack;
                    }
                    return Type.QuickMove;
                }
                if (effect.getEffectType().value() != StatusEffects.STRENGTH.value() || this.stealCountList[14] <= 0) continue;
                this.stealCountList[14] = this.stealCountList[14] - i.getCount();
                if (this.stealCountList[14] < 0) {
                    return Type.Stack;
                }
                return Type.QuickMove;
            }
        }
        return Type.None;
    }

    private void placeBlock(BlockPos pos) {
        if (pos == null || AutoRegear.mc.world == null || AutoRegear.mc.player == null) {
            return;
        }
        
        if (AutoRegear.mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler) {
            return;
        }
        
        // AntiRegear.INSTANCE.safe.add(pos); // Assuming this is handled elsewhere or not needed
        BlockUtil.clickBlock(pos.offset(Direction.DOWN), Direction.UP, this.rotate.getValue());
    }

    private int findItemInInventory(String itemId) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = AutoRegear.mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            String stackId = Registries.ITEM.getId(stack.getItem()).toString();
            if (stackId.equals(itemId)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 获取玩家背包中指定物品的总数量
     * @param itemId 物品 ID，必须包含命名空间（如 minecraft:obsidian）
     * @return 物品总数
     */
    private int getPlayerItemCount(String itemId) {
        int count = 0;
        // Main inventory (0-35)
        for (int i = 0; i < 36; i++) {
            ItemStack stack = AutoRegear.mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                String stackId = Registries.ITEM.getId(stack.getItem()).toString();
                if (stackId.equals(itemId)) {
                    count += stack.getCount();
                }
            }
        }
        // Offhand
        ItemStack offhand = AutoRegear.mc.player.getInventory().offHand.get(0);
        if (!offhand.isEmpty()) {
            String stackId = Registries.ITEM.getId(offhand.getItem()).toString();
            if (stackId.equals(itemId)) {
                count += offhand.getCount();
            }
        }
        // Armor (36-39)
        for (int i = 0; i < 4; i++) {
            ItemStack stack = AutoRegear.mc.player.getInventory().getStack(36 + i);
            if (!stack.isEmpty()) {
                String stackId = Registries.ITEM.getId(stack.getItem()).toString();
                if (stackId.equals(itemId)) {
                    count += stack.getCount();
                }
            }
        }
        return count;
    }

    private void swapInventorySlots(int slot1, int slot2) {
        ScreenHandler screenHandler = AutoRegear.mc.player.currentScreenHandler;
        if (screenHandler == null) return;
        
        if (!(screenHandler instanceof ShulkerBoxScreenHandler)) {
            return;
        }
        ShulkerBoxScreenHandler shulker = (ShulkerBoxScreenHandler)screenHandler;
        if (shulker.slots == null || shulker.slots.isEmpty()) {
            return;
        }

        AutoRegear.mc.interactionManager.clickSlot(screenHandler.syncId, slot1, 0, SlotActionType.PICKUP, (PlayerEntity)AutoRegear.mc.player);
        AutoRegear.mc.interactionManager.clickSlot(screenHandler.syncId, slot2, 0, SlotActionType.PICKUP, (PlayerEntity)AutoRegear.mc.player);
        AutoRegear.mc.interactionManager.clickSlot(screenHandler.syncId, slot1, 0, SlotActionType.PICKUP, (PlayerEntity)AutoRegear.mc.player);
    }

    private boolean canPlayerSeePosition(BlockPos pos) {
        if (AutoRegear.mc.player == null || AutoRegear.mc.world == null) {
            return false;
        }
        Vec3d eyesPos = AutoRegear.mc.player.getEyePos();
        Vec3d targetPos = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        
        double dist = eyesPos.distanceTo(targetPos);
        if (dist > this.range.getValue()) {
            return false;
        }
        
        Vec3d direction = targetPos.subtract(eyesPos).normalize();
        int steps = (int)(dist * 2);
        for (int i = 0; i < steps; i++) {
            double checkDist = i * 0.5;
            Vec3d checkPos = eyesPos.add(direction.multiply(checkDist));
            BlockPos checkBlock = new BlockPos((int)checkPos.getX(), (int)checkPos.getY(), (int)checkPos.getZ());
            if (!AutoRegear.mc.world.isAir(checkBlock) && !BlockUtil.canReplace(checkBlock)) {
                return false;
            }
        }
        
        return true;
    }

    private boolean isValidShulkerBox(BlockPos pos) {
        if (pos == null || AutoRegear.mc.world == null) {
            return false;
        }
        try {
            if (!(AutoRegear.mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock)) {
                return false;
            }
            var blockEntity = AutoRegear.mc.world.getBlockEntity(pos);
            if (blockEntity == null) {
                return false;
            }
            if (!(blockEntity instanceof ShulkerBoxBlockEntity)) {
                return false;
            }
            if (blockEntity.isRemoved()) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static enum Type {
        None,
        Stack,
        QuickMove;

    }
}
