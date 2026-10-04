/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.block.AnvilBlock
 *  net.minecraft.block.Block
 *  net.minecraft.block.ChestBlock
 *  net.minecraft.block.EnderChestBlock
 *  net.minecraft.client.gui.screen.DeathScreen
 *  net.minecraft.item.Items
 *  net.minecraft.item.PickaxeItem
 *  net.minecraft.item.SwordItem
 *  net.minecraft.network.packet.Packet
 *  net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
 */
package dev.stella.mod.modules.impl.player;

import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.PacketEvent;
import dev.stella.api.events.impl.UpdateEvent;
import dev.stella.api.utils.player.EntityUtil;
import dev.stella.api.utils.player.InventoryUtil;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import dev.stella.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.screen.slot.SlotActionType;

public class InteractTweaks
extends Module {
    public static InteractTweaks INSTANCE;
    public final BooleanSetting noEntityTrace = this.add(new BooleanSetting("NoEntityTrace", true).setParent());
    public final BooleanSetting onlyPickaxe = this.add(new BooleanSetting("OnlyPickaxe", true, this.noEntityTrace::isOpen));
    public final BooleanSetting multiTask = this.add(new BooleanSetting("MultiTask", true));
    public final BooleanSetting respawn = this.add(new BooleanSetting("Respawn", true));
    public final BooleanSetting ghostHand = this.add(new BooleanSetting("IgnoreBedrock", false));
    private final BooleanSetting noAbort = this.add(new BooleanSetting("NoMineAbort", false));
    private final BooleanSetting noReset = this.add(new BooleanSetting("NoMineReset", false));
    private final BooleanSetting noDelay = this.add(new BooleanSetting("NoMineDelay", false));
    private final BooleanSetting noInteract = this.add(new BooleanSetting("NoInteract", false));
    private final BooleanSetting pickaxeSwitch = this.add(new BooleanSetting("SwitchEat", false).setParent());
    private final BooleanSetting allowSword = this.add(new BooleanSetting("Sword", true, this.pickaxeSwitch::isOpen));
    private final BooleanSetting allowPickaxe = this.add(new BooleanSetting("Pickaxe", true, this.pickaxeSwitch::isOpen));
    private final BooleanSetting allowTotem = this.add(new BooleanSetting("Totem", false, this.pickaxeSwitch::isOpen));
    private final BooleanSetting reach = this.add(new BooleanSetting("Reach", false));
    public final SliderSetting blockRange = this.add(new SliderSetting("BlockRange", 5.0, 0.0, 15.0, 0.1, this.reach::getValue));
    public final SliderSetting entityRange = this.add(new SliderSetting("EntityRange", 5.0, 0.0, 15.0, 0.1, this.reach::getValue));
    private final SliderSetting delay = this.add(new SliderSetting("UseDelay", 4.0, 0.0, 4.0, 1.0));
    public boolean isActive;
    boolean swapped = false;
    int lastSlot = 0;
    int appleSwapSlot = -1;

    public InteractTweaks() {
        super("InteractTweaks", Module.Category.Player);
        this.setChinese("\u4ea4\u4e92\u8c03\u6574");
        INSTANCE = this;
    }

    private int findAppleInInventory() {
        for (int i = 0; i < 36; ++i) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE) {
                return i;
            }
        }
        for (int i = 0; i < 36; ++i) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.GOLDEN_APPLE) {
                return i;
            }
        }
        return -1;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (this.respawn.getValue() && InteractTweaks.mc.currentScreen instanceof DeathScreen) {
            InteractTweaks.mc.player.requestRespawn();
            mc.setScreen(null);
        }
        if (InteractTweaks.mc.itemUseCooldown <= 4 - this.delay.getValueInt()) {
            InteractTweaks.mc.itemUseCooldown = 0;
        }
        
        // 切换逻辑（剑/镐/图腾 → 苹果）
        if (this.pickaxeSwitch.getValue()) {
            boolean holdingSword = InteractTweaks.mc.player.getMainHandStack().getItem() instanceof SwordItem;
            boolean holdingPickaxe = InteractTweaks.mc.player.getMainHandStack().getItem() instanceof PickaxeItem;
            boolean holdingTotemMain = InteractTweaks.mc.player.getMainHandStack().getItem() == Items.TOTEM_OF_UNDYING;
            boolean holdingGapple = InteractTweaks.mc.player.getMainHandStack().getItem() == Items.ENCHANTED_GOLDEN_APPLE;
            boolean holdingNormalApple = InteractTweaks.mc.player.getMainHandStack().getItem() == Items.GOLDEN_APPLE;

            // 检查当前手持物品是否是允许切换的类型（剑、镐、图腾或苹果）
            boolean holdingAllowedItem = holdingSword || holdingPickaxe || holdingTotemMain || holdingGapple || holdingNormalApple;

            if (!holdingAllowedItem) {
                if (this.swapped) {
                    if (this.appleSwapSlot != this.lastSlot) {
                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, this.appleSwapSlot, this.lastSlot, SlotActionType.SWAP, mc.player);
                        EntityUtil.syncInventory();
                    }
                    this.swapped = false;
                }
                return;
            }

            // 寻找苹果（整个背包，优先附魔金苹果）
            int gappleSlot = this.findAppleInInventory();

            // 如果没有苹果，恢复原物品
            if (gappleSlot == -1) {
                if (this.swapped) {
                    if (this.appleSwapSlot != this.lastSlot) {
                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, this.appleSwapSlot, this.lastSlot, SlotActionType.SWAP, mc.player);
                        EntityUtil.syncInventory();
                    }
                    this.swapped = false;
                }
                return;
            }

            // 按使用键时切换
            if (InteractTweaks.mc.options.useKey.isPressed()) {
                // 检查当前是否可以切换：主手是剑/镐/图腾，且当前主手不是苹果
                boolean canSwitch = ((holdingSword && this.allowSword.getValue())
                                  || (holdingPickaxe && this.allowPickaxe.getValue())
                                  || (holdingTotemMain && this.allowTotem.getValue()))
                                  && !holdingGapple && !holdingNormalApple;

                if (canSwitch && !this.swapped) {
                    this.lastSlot = InteractTweaks.mc.player.getInventory().selectedSlot;
                    this.appleSwapSlot = gappleSlot;

                    if (gappleSlot != this.lastSlot) {
                        // 统一使用 SWAP 交换到主手（不改 selectedSlot）
                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, gappleSlot, this.lastSlot, SlotActionType.SWAP, mc.player);
                        EntityUtil.syncInventory();
                    }
                    this.swapped = true;
                }
            } else if (this.swapped) {
                // 松开使用键后恢复原物品
                if (this.appleSwapSlot != this.lastSlot) {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, this.appleSwapSlot, this.lastSlot, SlotActionType.SWAP, mc.player);
                    EntityUtil.syncInventory();
                }
                this.swapped = false;
            }
        }
    }

    @EventListener
    public void onPacket(PacketEvent.Send event) {
        Packet<?> packet;
        if (InteractTweaks.nullCheck() || !this.noInteract.getValue() || !((packet = event.getPacket()) instanceof PlayerInteractBlockC2SPacket)) {
            return;
        }
        PlayerInteractBlockC2SPacket packet2 = (PlayerInteractBlockC2SPacket)packet;
        Block block = InteractTweaks.mc.world.getBlockState(packet2.getBlockHitResult().getBlockPos()).getBlock();
        if (!InteractTweaks.mc.player.isSneaking() && (block instanceof ChestBlock || block instanceof EnderChestBlock || block instanceof AnvilBlock)) {
            event.cancel();
        }
    }

    @Override
    public void onDisable() {
        this.isActive = false;
    }

    public boolean reach() {
        return this.isOn() && this.reach.getValue();
    }

    public boolean noAbort() {
        return this.isOn() && this.noAbort.getValue() && !InteractTweaks.mc.options.useKey.isPressed();
    }

    public boolean noReset() {
        return this.isOn() && this.noReset.getValue();
    }

    public boolean noDelay() {
        return this.isOn() && this.noDelay.getValue();
    }

    public boolean multiTask() {
        return this.isOn() && this.multiTask.getValue();
    }

    public boolean noEntityTrace() {
        if (this.isOff() || !this.noEntityTrace.getValue()) {
            return false;
        }
        if (this.onlyPickaxe.getValue()) {
            return InteractTweaks.mc.player.getMainHandStack().getItem() instanceof PickaxeItem || InteractTweaks.mc.player.isUsingItem() && !(InteractTweaks.mc.player.getMainHandStack().getItem() instanceof SwordItem);
        }
        return true;
    }

    public boolean ghostHand() {
        return this.isOn() && this.ghostHand.getValue() && !InteractTweaks.mc.options.useKey.isPressed() && !InteractTweaks.mc.options.sneakKey.isPressed();
    }
}

