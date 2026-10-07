package dev.stella.mod.modules.impl.combat;

import dev.stella.stella;
import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.ClientTickEvent;
import dev.stella.api.utils.combat.CombatUtil;
import dev.stella.api.utils.entity.CopyPlayerEntity;
import dev.stella.api.utils.math.ExplosionUtil;
import dev.stella.api.utils.math.MathUtil;
import dev.stella.api.utils.math.Timer;
import dev.stella.api.utils.path.TPUtils;
import dev.stella.api.utils.player.EntityUtil;
import dev.stella.api.utils.player.InventoryUtil;
import dev.stella.api.utils.world.BlockUtil;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.enums.SwingSide;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import dev.stella.mod.modules.settings.impl.EnumSetting;
import dev.stella.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.Block;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * TpCrystalAura - 独立 TP 水晶光环。
 * 扫描目标周围球形区域，选择伤害合格的放晶/打晶位置，
 * 超出触及距离时先瞬移过去（TPUtils 真步进）再执行，可选瞬移回。
 * 不依赖 AutoCrystal，设置完全独立。
 */
public class TpCrystalAura extends Module {
    public static TpCrystalAura INSTANCE;

    private final BooleanSetting usingPause = this.add(new BooleanSetting("UsingPause", true));
    private final BooleanSetting invSwap = this.add(new BooleanSetting("InvSwap", true));
    private final BooleanSetting rotate = this.add(new BooleanSetting("Rotate", true));
    private final BooleanSetting packet = this.add(new BooleanSetting("Packet", true));

    private final SliderSetting range = this.add(new SliderSetting("Range", 64.0, 1.0, 512.0, 1.0).setSuffix("m"));
    private final SliderSetting maxDamageToSelf = this.add(new SliderSetting("MaxDamageToSelf", 6.0, 0.1, 20.0, 0.1));
    private final SliderSetting minDamageToTarget = this.add(new SliderSetting("MinDamageToTarget", 3.0, 0.1, 37.0, 0.1));
    private final SliderSetting placeDelay = this.add(new SliderSetting("PlaceDelay", 400, 0, 1000));
    private final SliderSetting breakDelay = this.add(new SliderSetting("BreakDelay", 200, 0, 500));
    private final SliderSetting scanBorder = this.add(new SliderSetting("ScanBorder", 15, 1, 50));
    private final BooleanSetting allowProtect = this.add(new BooleanSetting("AllowProtect", true));

    private final BooleanSetting tpBack = this.add(new BooleanSetting("TpBack", true));
    private final EnumSetting<TPUtils.TeleportType> tpMode = this.add(new EnumSetting<TPUtils.TeleportType>("TpMode", TPUtils.TeleportType.New));
    private final SliderSetting tpRange = this.add(new SliderSetting("TpRange", 64.0, 1.0, 512.0, 1.0).setSuffix("m"));
    private final SliderSetting tpStep = this.add(new SliderSetting("TpStep", 8, 1, 64));

    private PlayerEntity target;
    private final Timer placeTimer = new Timer();
    private final Timer breakTimer = new Timer();
    private boolean tpActive;
    private CopyPlayerEntity tpSelf;

    public TpCrystalAura() {
        super("TpCrystalAura", Category.AntiCheatFree);
        this.setChinese("TP水晶光环");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        this.placeTimer.reset();
        this.breakTimer.reset();
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
        int crystal = this.find(Items.END_CRYSTAL);
        if (crystal == -1) {
            return;
        }
        int obby = this.find(Items.OBSIDIAN);
        if (this.doBreak()) {
            return;
        }
        this.doPlace(crystal, obby);
    }

    private boolean doBreak() {
        if (!this.breakTimer.passed(this.breakDelay.getValueInt())) {
            return false;
        }
        double reach = 4.0;
        Box area = new Box(this.target.getBlockPos()).expand(this.scanBorder.getValueInt());
        for (EndCrystalEntity crystal : BlockUtil.getEndCrystals(area)) {
            if (!crystal.isAlive()) continue;
            Vec3d pos = crystal.getPos();
            Vec3d landing;
            if (mc.player.getEyePos().distanceTo(pos) <= reach) {
                landing = null;
            } else {
                landing = TPUtils.findTpVec(pos, reach, this.tpRange.getValue());
                if (landing == null) continue;
            }
            float targetDamage = ExplosionUtil.calculateDamage(pos, this.target, this.target, 6.0f);
            if (targetDamage < this.minDamageToTarget.getValue()) continue;
            if (this.selfDamage(pos, landing) > this.maxDamageToSelf.getValue()) continue;
            final EndCrystalEntity attackTarget = crystal;
            final Vec3d attackLanding = landing;
            this.runAt(attackLanding, () -> {
                if (this.rotate.getValue()) {
                    stella.ROTATION.lookAt(MathUtil.getClosestPointToBox(mc.player.getEyePos(), attackTarget.getBoundingBox()));
                }
                mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(attackTarget, mc.player.isSneaking()));
                mc.player.resetLastAttackedTicks();
                EntityUtil.swingHand(Hand.MAIN_HAND, SwingSide.All);
                if (this.rotate.getValue()) {
                    stella.ROTATION.snapBack();
                }
                this.breakTimer.reset();
            });
            return true;
        }
        return false;
    }

    private void doPlace(int crystalSlot, int obbySlot) {
        if (!this.placeTimer.passed(this.placeDelay.getValueInt())) {
            return;
        }
        double reach = 4.5;
        double effRange = this.tpRange.getValue() + 6.0;
        BlockPos best = null;
        Vec3d bestLanding = null;
        float bestDamage = -1.0f;
        boolean bestNeedObby = false;
        for (BlockPos pos : BlockUtil.getSphere(this.scanBorder.getValueFloat(), this.target.getPos())) {
            if (pos.equals(this.target.getBlockPos())) continue;
            boolean needObby;
            if (BlockUtil.canPlaceCrystal(pos)) {
                needObby = false;
            } else if (this.allowProtect.getValue() && obbySlot != -1 && mc.world.isAir(pos) && BlockUtil.canPlace(pos.down(), effRange, true)) {
                needObby = true;
            } else {
                continue;
            }
            Vec3d vec = new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            float targetDamage = ExplosionUtil.calculateDamage(vec, this.target, this.target, 6.0f);
            if (targetDamage < this.minDamageToTarget.getValue()) continue;
            Vec3d landing;
            if (mc.player.getEyePos().distanceTo(vec) <= reach) {
                landing = null;
            } else {
                landing = TPUtils.findTpVec(vec, reach, this.tpRange.getValue());
                if (landing == null) continue;
            }
            if (this.selfDamage(vec, landing) > this.maxDamageToSelf.getValue()) continue;
            if (targetDamage <= bestDamage) continue;
            bestDamage = targetDamage;
            best = pos;
            bestLanding = landing;
            bestNeedObby = needObby;
        }
        if (best == null) {
            return;
        }
        final BlockPos place = best;
        final Vec3d placeLanding = bestLanding;
        final boolean needObby = bestNeedObby;
        this.runAt(placeLanding, () -> {
            int old = mc.player.getInventory().selectedSlot;
            if (needObby) {
                this.doSwap(obbySlot);
                BlockUtil.placeBlock(place.down(), this.rotate.getValue(), this.packet.getValue());
                this.swapBack(obbySlot, old);
            } else {
                this.doSwap(crystalSlot);
                BlockUtil.placeCrystal(place, this.rotate.getValue());
                this.swapBack(crystalSlot, old);
            }
            this.placeTimer.reset();
            if (this.rotate.getValue()) {
                stella.ROTATION.snapBack();
            }
        });
    }

    private float selfDamage(Vec3d explosion, Vec3d landing) {
        if (landing == null) {
            return ExplosionUtil.calculateDamage(explosion, mc.player, mc.player, 6.0f);
        }
        if (this.tpSelf == null) {
            this.tpSelf = new CopyPlayerEntity(mc.player, true);
        }
        this.tpSelf.setPosition(landing.x, landing.y, landing.z);
        return ExplosionUtil.calculateDamage(explosion, this.tpSelf, this.tpSelf, 6.0f);
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

    private int find(Item item) {
        return this.invSwap.getValue() ? InventoryUtil.findItemInventorySlot(item) : InventoryUtil.findItem(item);
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
