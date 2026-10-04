package dev.stella.mod.modules.impl.misc;

import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.UpdateEvent;
import dev.stella.api.utils.math.Timer;
import dev.stella.api.utils.player.InventoryUtil;
import dev.stella.api.utils.world.BlockUtil;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.impl.player.PacketMine;
import dev.stella.mod.modules.settings.impl.BindSetting;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import dev.stella.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

public class ChestStealer extends Module {
    public static ChestStealer INSTANCE;
    public final BooleanSetting rotate = this.add(new BooleanSetting("Rotate", true));
    public final Timer timeoutTimer = new Timer();
    final int[] stealCountList = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    private final BooleanSetting autoDisable = this.add(new BooleanSetting("AutoDisable", true));
    private final SliderSetting disableTime = this.add(new SliderSetting("DisableTime", 500, 0, 1000));
    private final BooleanSetting place = this.add(new BooleanSetting("Place", true));
    private final BooleanSetting inventory = this.add(new BooleanSetting("InventorySwap", true));
    private final BooleanSetting preferOpen = this.add(new BooleanSetting("PerferOpen", true));
    private final BooleanSetting open = this.add(new BooleanSetting("Open", true));
    private final SliderSetting range = this.add(new SliderSetting("MaxRange", 4.0, 0.0, 6.0, 0.1));
    private final SliderSetting minRange = this.add(new SliderSetting("MinRange", 1.0, 0.0, 3.0, 0.1));
    private final BooleanSetting mine = this.add(new BooleanSetting("Mine", true));
    private final BooleanSetting take = this.add(new BooleanSetting("Take", true));
    private final BooleanSetting smart = this.add(new BooleanSetting("Smart", true, this.take::getValue).setParent());
    private final BooleanSetting forceMove = this.add(new BooleanSetting("ForceQuickMove", true, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting crystal = this.add(new SliderSetting("Crystal", 256, 0, 512, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting exp = this.add(new SliderSetting("Exp", 256, 0, 512, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting totem = this.add(new SliderSetting("Totem", 6, 0, 36, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting gapple = this.add(new SliderSetting("Gapple", 128, 0, 512, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting obsidian = this.add(new SliderSetting("Obsidian", 64, 0, 512, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting web = this.add(new SliderSetting("Web", 64, 0, 512, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting glowstone = this.add(new SliderSetting("Glowstone", 128, 0, 512, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting anchor = this.add(new SliderSetting("Anchor", 128, 0, 512, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting pearl = this.add(new SliderSetting("Pearl", 16, 0, 64, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting piston = this.add(new SliderSetting("Piston", 64, 0, 512, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting redstone = this.add(new SliderSetting("RedStone", 64, 0, 512, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting bed = this.add(new SliderSetting("Bed", 256, 0, 512, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting speed = this.add(new SliderSetting("Speed", 1, 0, 8, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting resistance = this.add(new SliderSetting("Resistance", 1, 0, 8, () -> this.take.getValue() && this.smart.isOpen()));
    private final SliderSetting strength = this.add(new SliderSetting("Strength", 1, 0, 8, () -> this.take.getValue() && this.smart.isOpen()));
    private final BindSetting placeKey = this.add(new BindSetting("PlaceKey", -1));
    private final Timer timer = new Timer();
    private final List<BlockPos> openList = new ArrayList<>();
    public BlockPos placePos = null;
    private BlockPos openPos;
    private boolean opend = false;
    private boolean on = false;

    public ChestStealer() {
        super("ChestStealer", Category.Misc);
        this.setChinese("鬼手拿");
        INSTANCE = this;
    }

    public int findShulker() {
        if (this.inventory.getValue()) {
            for (int i = 0; i < 36; ++i) {
                ItemStack stack = ChestStealer.mc.player.getInventory().getStack(i);
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
        this.opend = false;
        this.openPos = null;
        this.timeoutTimer.reset();
        this.placePos = null;
        if (ChestStealer.nullCheck()) {
            return;
        }
        if (!this.place.getValue()) {
            return;
        }
        this.doPlace();
    }

    private void doPlace() {
        int oldSlot = ChestStealer.mc.player.getInventory().selectedSlot;
        int shulkerSlot = this.findShulker();
        if (shulkerSlot == -1) {
            this.sendMessage("\u00a74No shulkerbox found.");
            return;
        }

        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;

        for (BlockPos pos : BlockUtil.getSphere((float) this.range.getValue())) {
            // 跳过太近/太远
            double distSq = ChestStealer.mc.player.squaredDistanceTo(pos.toCenterPos());
            if (distSq < this.minRange.getValue() * this.minRange.getValue() || distSq > this.range.getValue() * this.range.getValue()) {
                continue;
            }
            // 必须是空气块且上方是空气（可放置）
            if (!ChestStealer.mc.world.isAir(pos) || !ChestStealer.mc.world.isAir(pos.up())) {
                continue;
            }
            // 检查是否能放置（含碰撞、实体、方向等）
            if (!BlockUtil.clientCanPlace(pos, false)) {
                continue;
            }
            // 获取合法放置面（关键优化：不再硬编码 DOWN）
            BlockPos placeTarget = pos.offset(Direction.DOWN);
            Direction side = BlockUtil.getPlaceSide(placeTarget, this.range.getValue());
            if (side == null) {
                continue;
            }
            // 优先匹配 preferOpen：若该位置已是潜影箱，立即返回（不继续搜索）
            if (this.preferOpen.getValue() && ChestStealer.mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock) {
                bestPos = pos;
                break;
            }
            // 否则按距离排序取最近
            if (MathHelper.sqrt((float) distSq) < bestDist) {
                bestDist = MathHelper.sqrt((float) distSq);
                bestPos = pos;
            }
        }

        if (bestPos == null) {
            this.sendMessage("\u00a74No valid place position found.");
            return;
        }

        if (this.inventory.getValue()) {
            InventoryUtil.inventorySwap(shulkerSlot, oldSlot);
            this.placeBlock(bestPos);
            this.placePos = bestPos;
            InventoryUtil.inventorySwap(shulkerSlot, oldSlot);
        } else {
            InventoryUtil.switchToSlot(shulkerSlot);
            this.placeBlock(bestPos);
            this.placePos = bestPos;
            InventoryUtil.switchToSlot(oldSlot);
        }
        this.timer.reset();
    }

    private void placeBlock(BlockPos pos) {
        if (pos == null || ChestStealer.mc.world == null) return;
        BlockPos target = pos.offset(Direction.DOWN);
        Direction side = BlockUtil.getPlaceSide(target, this.range.getValue());
        if (side == null) {
            side = BlockUtil.getClickSide(target);
        }
        if (side != null && BlockUtil.canClick(target)) {
            BlockUtil.clickBlock(target, side, this.rotate.getValue());
        }
    }

    private void update() {
        this.stealCountList[0] = (int) (this.crystal.getValue() - (double) InventoryUtil.getItemCount(Items.END_CRYSTAL));
        this.stealCountList[1] = (int) (this.exp.getValue() - (double) InventoryUtil.getItemCount(Items.EXPERIENCE_BOTTLE));
        this.stealCountList[2] = (int) (this.totem.getValue() - (double) InventoryUtil.getItemCount(Items.TOTEM_OF_UNDYING));
        this.stealCountList[3] = (int) (this.gapple.getValue() - (double) InventoryUtil.getItemCount(Items.ENCHANTED_GOLDEN_APPLE));
        this.stealCountList[4] = (int) (this.obsidian.getValue() - (double) InventoryUtil.getItemCount(Blocks.OBSIDIAN.asItem()));
        this.stealCountList[5] = (int) (this.web.getValue() - (double) InventoryUtil.getItemCount(Blocks.COBWEB.asItem()));
        this.stealCountList[6] = (int) (this.glowstone.getValue() - (double) InventoryUtil.getItemCount(Blocks.GLOWSTONE.asItem()));
        this.stealCountList[7] = (int) (this.anchor.getValue() - (double) InventoryUtil.getItemCount(Blocks.RESPAWN_ANCHOR.asItem()));
        this.stealCountList[8] = (int) (this.pearl.getValue() - (double) InventoryUtil.getItemCount(Items.ENDER_PEARL));
        this.stealCountList[9] = (int) (this.piston.getValue() - (double) InventoryUtil.getItemCount(Blocks.PISTON.asItem()) - (double) InventoryUtil.getItemCount(Blocks.STICKY_PISTON.asItem()));
        this.stealCountList[10] = (int) (this.redstone.getValue() - (double) InventoryUtil.getItemCount(Blocks.REDSTONE_BLOCK.asItem()));
        this.stealCountList[11] = (int) (this.bed.getValue() - (double) InventoryUtil.getItemCount(BedBlock.class));
        this.stealCountList[12] = (int) (this.speed.getValue() - (double) InventoryUtil.getPotionCount((StatusEffect) StatusEffects.SPEED.value()));
        this.stealCountList[13] = (int) (this.resistance.getValue() - (double) InventoryUtil.getPotionCount((StatusEffect) StatusEffects.RESISTANCE.value()));
        this.stealCountList[14] = (int) (this.strength.getValue() - (double) InventoryUtil.getPotionCount((StatusEffect) StatusEffects.STRENGTH.value()));
    }

    @Override
    public void onDisable() {
        this.opend = false;
        if (this.mine.getValue() && this.placePos != null) {
            PacketMine.INSTANCE.mine(this.placePos);
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (this.smart.getValue()) {
            this.update();
        }
        if (this.placeKey.isPressed() && ChestStealer.mc.currentScreen == null) {
            if (!this.on) {
                this.opend = false;
                this.openPos = null;
                this.timeoutTimer.reset();
                this.placePos = null;
                this.doPlace();
            }
            this.on = true;
        } else {
            this.on = false;
        }
        this.openList.removeIf(pos -> !(ChestStealer.mc.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock));
        if (!(ChestStealer.mc.currentScreen instanceof ShulkerBoxScreen)) {
            if (this.opend) {
                this.opend = false;
                if (this.autoDisable.getValue()) {
                    this.timeoutToDisable();
                }
                if (this.mine.getValue() && this.openPos != null) {
                    if (ChestStealer.mc.world.getBlockState(this.openPos).getBlock() instanceof ShulkerBoxBlock) {
                        PacketMine.INSTANCE.mine(this.openPos);
                    } else {
                        this.openPos = null;
                    }
                }
                return;
            }
            if (this.open.getValue()) {
                if (this.placePos != null
                        && (double) MathHelper.sqrt((float) ChestStealer.mc.player.squaredDistanceTo(this.placePos.toCenterPos())) <= this.range.getValue()
                        && ChestStealer.mc.world.isAir(this.placePos.up())
                        && (!this.timer.passed(500L) || ChestStealer.mc.world.getBlockState(this.placePos).getBlock() instanceof ShulkerBoxBlock)) {
                    if (ChestStealer.mc.world.getBlockState(this.placePos).getBlock() instanceof ShulkerBoxBlock) {
                        this.openPos = this.placePos;
                        BlockUtil.clickBlock(this.placePos, BlockUtil.getClickSide(this.placePos), this.rotate.getValue());
                    }
                } else {
                    boolean found = false;
                    for (BlockPos pos2 : BlockUtil.getSphere((float) this.range.getValue())) {
                        if (this.openList.contains(pos2)) continue;
                        if (!ChestStealer.mc.world.isAir(pos2.up()) && !BlockUtil.canReplace(pos2.up())) continue;
                        if (!(ChestStealer.mc.world.getBlockState(pos2).getBlock() instanceof ShulkerBoxBlock)) continue;
                        this.openPos = pos2;
                        BlockUtil.clickBlock(pos2, BlockUtil.getClickSide(pos2), this.rotate.getValue());
                        found = true;
                        break;
                    }
                    if (!found && this.autoDisable.getValue()) {
                        this.timeoutToDisable();
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
        boolean taken = false;
        ScreenHandler screenHandler = ChestStealer.mc.player.currentScreenHandler;
        if (screenHandler instanceof ShulkerBoxScreenHandler shulker) {
            block1:
            for (Slot slot : shulker.slots) {
                if (slot.id >= 27 || slot.getStack().isEmpty()) continue;
                Type type = this.needSteal(slot.getStack());
                if (!this.smart.getValue() || type == Type.QuickMove || type == Type.Stack && this.forceMove.getValue()) {
                    ChestStealer.mc.interactionManager.clickSlot(shulker.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, (PlayerEntity) ChestStealer.mc.player);
                    taken = true;
                    continue;
                }
                if (type != Type.Stack) continue;
                for (int slot1 = 0; slot1 < 36; ++slot1) {
                    ItemStack stack = ChestStealer.mc.player.getInventory().getStack(slot1);
                    if (stack.isEmpty() || !stack.isStackable() || stack.getItem() != slot.getStack().getItem() || stack.getCount() >= stack.getMaxCount()) continue;
                    int i = (slot1 < 9 ? slot1 + 36 : slot1) + 18;
                    ChestStealer.mc.interactionManager.clickSlot(shulker.syncId, slot.id, 0, SlotActionType.PICKUP, (PlayerEntity) ChestStealer.mc.player);
                    ChestStealer.mc.interactionManager.clickSlot(shulker.syncId, i, 0, SlotActionType.PICKUP, (PlayerEntity) ChestStealer.mc.player);
                    ChestStealer.mc.interactionManager.clickSlot(shulker.syncId, slot.id, 0, SlotActionType.PICKUP, (PlayerEntity) ChestStealer.mc.player);
                    taken = true;
                    continue block1;
                }
            }
        }
        if (this.autoDisable.getValue() && !taken) {
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
            this.stealCountList[0] -= i.getCount();
            return this.stealCountList[0] < 0 ? Type.Stack : Type.QuickMove;
        }
        if (i.getItem().equals(Items.EXPERIENCE_BOTTLE) && this.stealCountList[1] > 0) {
            this.stealCountList[1] -= i.getCount();
            return this.stealCountList[1] < 0 ? Type.Stack : Type.QuickMove;
        }
        if (i.getItem().equals(Items.TOTEM_OF_UNDYING) && this.stealCountList[2] > 0) {
            this.stealCountList[2] -= i.getCount();
            return this.stealCountList[2] < 0 ? Type.Stack : Type.QuickMove;
        }
        if (i.getItem().equals(Items.ENCHANTED_GOLDEN_APPLE) && this.stealCountList[3] > 0) {
            this.stealCountList[3] -= i.getCount();
            return this.stealCountList[3] < 0 ? Type.Stack : Type.QuickMove;
        }
        if (i.getItem().equals(Blocks.OBSIDIAN.asItem()) && this.stealCountList[4] > 0) {
            this.stealCountList[4] -= i.getCount();
            return this.stealCountList[4] < 0 ? Type.Stack : Type.QuickMove;
        }
        if (i.getItem().equals(Blocks.COBWEB.asItem()) && this.stealCountList[5] > 0) {
            this.stealCountList[5] -= i.getCount();
            return this.stealCountList[5] < 0 ? Type.Stack : Type.QuickMove;
        }
        if (i.getItem().equals(Blocks.GLOWSTONE.asItem()) && this.stealCountList[6] > 0) {
            this.stealCountList[6] -= i.getCount();
            return this.stealCountList[6] < 0 ? Type.Stack : Type.QuickMove;
        }
        if (i.getItem().equals(Blocks.RESPAWN_ANCHOR.asItem()) && this.stealCountList[7] > 0) {
            this.stealCountList[7] -= i.getCount();
            return this.stealCountList[7] < 0 ? Type.Stack : Type.QuickMove;
        }
        if (i.getItem().equals(Items.ENDER_PEARL) && this.stealCountList[8] > 0) {
            this.stealCountList[8] -= i.getCount();
            return this.stealCountList[8] < 0 ? Type.Stack : Type.QuickMove;
        }
        if (i.getItem() instanceof BlockItem && ((BlockItem) i.getItem()).getBlock() instanceof PistonBlock && this.stealCountList[9] > 0) {
            this.stealCountList[9] -= i.getCount();
            return this.stealCountList[9] < 0 ? Type.Stack : Type.QuickMove;
        }
        if (i.getItem().equals(Blocks.REDSTONE_BLOCK.asItem()) && this.stealCountList[10] > 0) {
            this.stealCountList[10] -= i.getCount();
            return this.stealCountList[10] < 0 ? Type.Stack : Type.QuickMove;
        }
        if (i.getItem() instanceof BlockItem && ((BlockItem) i.getItem()).getBlock() instanceof BedBlock && this.stealCountList[11] > 0) {
            this.stealCountList[11] -= i.getCount();
            return this.stealCountList[11] < 0 ? Type.Stack : Type.QuickMove;
        }
        if (Item.getRawId(i.getItem()) == Item.getRawId(Items.SPLASH_POTION)) {
            PotionContentsComponent potionContents = (PotionContentsComponent) i.getOrDefault(DataComponentTypes.POTION_CONTENTS, (Object) PotionContentsComponent.DEFAULT);
            for (StatusEffectInstance effect : potionContents.getEffects()) {
                if (effect.getEffectType().value() == StatusEffects.SPEED.value()) {
                    if (this.stealCountList[12] <= 0) continue;
                    this.stealCountList[12] -= i.getCount();
                    return this.stealCountList[12] < 0 ? Type.Stack : Type.QuickMove;
                }
                if (effect.getEffectType().value() == StatusEffects.RESISTANCE.value()) {
                    if (this.stealCountList[13] <= 0) continue;
                    this.stealCountList[13] -= i.getCount();
                    return this.stealCountList[13] < 0 ? Type.Stack : Type.QuickMove;
                }
                if (effect.getEffectType().value() != StatusEffects.STRENGTH.value() || this.stealCountList[14] <= 0) continue;
                this.stealCountList[14] -= i.getCount();
                return this.stealCountList[14] < 0 ? Type.Stack : Type.QuickMove;
            }
        }
        return Type.None;
    }

    private enum Type {
        None,
        Stack,
        QuickMove
    }
}