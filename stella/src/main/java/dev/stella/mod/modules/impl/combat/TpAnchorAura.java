package dev.stella.mod.modules.impl.combat;

import dev.stella.stella;
import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.ClientTickEvent;
import dev.stella.api.utils.combat.CombatUtil;
import dev.stella.api.utils.entity.CopyPlayerEntity;
import dev.stella.api.utils.math.ExplosionUtil;
import dev.stella.api.utils.math.Timer;
import dev.stella.api.utils.path.TPUtils;
import dev.stella.api.utils.player.EntityUtil;
import dev.stella.api.utils.player.InventoryUtil;
import dev.stella.api.utils.world.BlockUtil;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import dev.stella.mod.modules.settings.impl.EnumSetting;
import dev.stella.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * TpAnchorAura - 独立 TP 重生锚光环。
 * 扫描目标周围球形区域选择伤害合格的位置（已存在的锚优先），
 * 瞬移过去后一 tick 内完成：放锚 → 荧石充能 → 非荧石右键引爆 → 瞬移回。
 * 不依赖 AutoAnchor，设置完全独立。
 */
public class TpAnchorAura extends Module {
    public static TpAnchorAura INSTANCE;

    private final BooleanSetting usingPause = this.add(new BooleanSetting("UsingPause", true));
    private final BooleanSetting invSwap = this.add(new BooleanSetting("InvSwap", true));
    private final BooleanSetting rotate = this.add(new BooleanSetting("Rotate", true));
    private final BooleanSetting packet = this.add(new BooleanSetting("Packet", true));

    private final SliderSetting range = this.add(new SliderSetting("Range", 64.0, 1.0, 512.0, 1.0).setSuffix("m"));
    private final SliderSetting maxDamageToSelf = this.add(new SliderSetting("MaxDamageToSelf", 6.0, 0.1, 20.0, 0.1));
    private final SliderSetting minDamageToTarget = this.add(new SliderSetting("MinDamageToTarget", 3.0, 0.1, 37.0, 0.1));
    private final SliderSetting placeDelay = this.add(new SliderSetting("PlaceDelay", 400, 0, 1000));
    private final SliderSetting scanBorder = this.add(new SliderSetting("ScanBorder", 15, 1, 50));
    private final BooleanSetting allowProtect = this.add(new BooleanSetting("AllowProtect", true));

    private final BooleanSetting tpBack = this.add(new BooleanSetting("TpBack", true));
    private final EnumSetting<TPUtils.TeleportType> tpMode = this.add(new EnumSetting<TPUtils.TeleportType>("TpMode", TPUtils.TeleportType.New));
    private final SliderSetting tpRange = this.add(new SliderSetting("TpRange", 64.0, 1.0, 512.0, 1.0).setSuffix("m"));
    private final SliderSetting tpStep = this.add(new SliderSetting("TpStep", 8, 1, 64));

    private PlayerEntity target;
    private final Timer placeTimer = new Timer();
    private boolean tpActive;
    private CopyPlayerEntity tpSelf;

    public TpAnchorAura() {
        super("TpAnchorAura", Category.AntiCheatFree);
        this.setChinese("TP锚光环");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        this.placeTimer.reset();
        this.target = null;
        this.tpActive = false;
        this.tpSelf = null;
    }

    @Override
    public void onDisable() {
        this.target = null;
        this.tpActive = false;
        this.tpSelf = null;
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (nullCheck()) {
            return;
        }
        if (!event.isPre()) {
            return;
        }
        if (mc.player.isSneaking()) {
            return;
        }
        if (this.usingPause.getValue() && mc.player.isUsingItem()) {
            return;
        }
        if (this.invSwap.getValue() && !EntityUtil.inInventory()) {
            return;
        }
        this.target = CombatUtil.getClosestEnemy(this.range.getValue());
        if (this.target == null) {
            return;
        }
        int anchor = this.find(Blocks.RESPAWN_ANCHOR);
        int glowstone = this.find(Blocks.GLOWSTONE);
        int unBlock = this.invSwap.getValue() ? anchor : InventoryUtil.findUnBlock();
        if (anchor == -1 || glowstone == -1 || unBlock == -1) {
            return;
        }
        if (!this.placeTimer.passed(this.placeDelay.getValueInt())) {
            return;
        }
        int obby = this.find(Blocks.OBSIDIAN);
        this.doPlace(anchor, glowstone, unBlock, obby);
    }

    private void doPlace(int anchorSlot, int glowstoneSlot, int unBlockSlot, int obbySlot) {
        double reach = 4.5;
        double effRange = this.tpRange.getValue() + 6.0;
        BlockPos best = null;
        Vec3d bestLanding = null;
        float bestDamage = -1.0f;
        boolean bestExisting = false;
        boolean bestNeedObby = false;
        for (BlockPos pos : BlockUtil.getSphere(this.scanBorder.getValueFloat(), this.target.getPos())) {
            if (pos.equals(this.target.getBlockPos())) continue;
            boolean existing = BlockUtil.getBlock(pos) == Blocks.RESPAWN_ANCHOR && BlockUtil.getClickSideStrict(pos) != null;
            boolean needObby = false;
            if (!existing) {
                if (BlockUtil.canPlace(pos, effRange, true)) {
                    // 可直接放锚
                } else if (this.allowProtect.getValue() && obbySlot != -1 && mc.world.isAir(pos) && BlockUtil.canPlace(pos.down(), effRange, true)) {
                    needObby = true;
                } else {
                    continue;
                }
            }
            float targetDamage = ExplosionUtil.anchorDamage(pos, this.target, this.target);
            if (targetDamage < this.minDamageToTarget.getValue()) continue;
            Vec3d landing;
            if (mc.player.getEyePos().distanceTo(pos.toCenterPos()) <= reach) {
                landing = null;
            } else {
                landing = TPUtils.findTpVec(pos.toCenterPos(), reach, this.tpRange.getValue());
                if (landing == null) continue;
            }
            if (this.selfDamage(pos, landing) > this.maxDamageToSelf.getValue()) continue;
            float score = targetDamage + (existing ? 1000.0f : 0.0f);
            if (score <= bestDamage) continue;
            bestDamage = score;
            best = pos;
            bestLanding = landing;
            bestExisting = existing;
            bestNeedObby = needObby;
        }
        if (best == null) {
            return;
        }
        final BlockPos place = best;
        final Vec3d placeLanding = bestLanding;
        final boolean existing = bestExisting;
        final boolean needObby = bestNeedObby;
        this.runAt(placeLanding, () -> {
            int old = mc.player.getInventory().selectedSlot;
            if (needObby) {
                this.doSwap(obbySlot);
                BlockUtil.placeBlock(place.down(), this.rotate.getValue(), this.packet.getValue());
                this.swapBack(obbySlot, old);
            }
            if (!existing) {
                this.doSwap(anchorSlot);
                BlockUtil.placeBlock(place, this.rotate.getValue(), this.packet.getValue());
                this.swapBack(anchorSlot, old);
            }
            this.doSwap(glowstoneSlot);
            BlockUtil.clickBlock(place, BlockUtil.getClickSide(place), this.rotate.getValue(), Hand.MAIN_HAND, this.packet.getValue());
            this.swapBack(glowstoneSlot, old);
            this.doSwap(unBlockSlot);
            BlockUtil.clickBlock(place, BlockUtil.getClickSide(place), this.rotate.getValue(), Hand.MAIN_HAND, this.packet.getValue());
            this.swapBack(unBlockSlot, old);
            this.placeTimer.reset();
            if (this.rotate.getValue()) {
                stella.ROTATION.snapBack();
            }
        });
    }

    private float selfDamage(BlockPos anchorPos, Vec3d landing) {
        if (landing == null) {
            return ExplosionUtil.anchorDamage(anchorPos, mc.player, mc.player);
        }
        if (this.tpSelf == null) {
            this.tpSelf = new CopyPlayerEntity(mc.player, true);
        }
        this.tpSelf.setPosition(landing.x, landing.y, landing.z);
        return ExplosionUtil.anchorDamage(anchorPos, this.tpSelf, this.tpSelf);
    }

    private void runAt(Vec3d landing, Runnable action) {
        if (landing == null) {
            action.run();
            return;
        }
        this.tpActive = true;
        try {
            if (this.tpBack.getValue()) {
                TPUtils.teleportWithBack(landing, this.tpMode.getValue(), this.tpStep.getValueInt(), action);
            } else {
                TPUtils.newTeleport(landing, this.tpStep.getValueInt());
                action.run();
            }
        } finally {
            this.tpActive = false;
        }
    }

    private int find(Block block) {
        return this.invSwap.getValue() ? InventoryUtil.findBlockInventorySlot(block) : InventoryUtil.findBlock(block);
    }

    private void doSwap(int slot) {
        if (slot == -1) return;
        if (this.invSwap.getValue()) {
            InventoryUtil.inventorySwap(slot, mc.player.getInventory().selectedSlot);
        } else {
            InventoryUtil.switchToSlot(slot);
        }
    }

    private void swapBack(int slot, int old) {
        if (this.invSwap.getValue()) {
            this.doSwap(slot);
            EntityUtil.syncInventory();
        } else {
            InventoryUtil.switchToSlot(old);
        }
    }
}
