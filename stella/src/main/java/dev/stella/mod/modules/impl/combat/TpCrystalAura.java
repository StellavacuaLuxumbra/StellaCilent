package dev.stella.mod.modules.impl.combat;

import dev.stella.stella;
import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.ClientTickEvent;
import dev.stella.api.utils.combat.CombatUtil;
import dev.stella.api.utils.entity.CopyPlayerEntity;
import dev.stella.api.utils.entity.PlayerEntityPredict;
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
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * TpCrystalAura - 独立 TP 水晶光环。
 * 扫描目标周围球形区域，选择伤害合格的放晶/打晶位置，
 * 超出触及距离时先瞬移过去（TPUtils 真步进）再执行，可选瞬移回。
 * 支持目标运动预测、致命优先、黑曜石自保盾、空中落点/中途点开关。
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
    private final BooleanSetting shield = this.add(new BooleanSetting("Shield", true));
    private final BooleanSetting smart = this.add(new BooleanSetting("Smart", true));
    private final BooleanSetting noSuicide = this.add(new BooleanSetting("NoSuicide", true));
    private final SliderSetting predict = this.add(new SliderSetting("Predict", 4, 0, 20).setSuffix("ticks"));
    private final SliderSetting selfPredict = this.add(new SliderSetting("SelfPredict", 4, 0, 20).setSuffix("ticks"));
    private final SliderSetting maxBreak = this.add(new SliderSetting("MaxBreak", 1, 1, 4));
    private final BooleanSetting combo = this.add(new BooleanSetting("Combo", true));

    private final BooleanSetting tpBack = this.add(new BooleanSetting("TpBack", true));
    private final EnumSetting<TPUtils.TeleportType> tpMode = this.add(new EnumSetting<TPUtils.TeleportType>("TpMode", TPUtils.TeleportType.New));
    private final BooleanSetting tpStepped = this.add(new BooleanSetting("TpStepped", true));
    private final SliderSetting tpStep = this.add(new SliderSetting("TpStep", 8, 1, 512, () -> this.tpStepped.getValue()));
    private final SliderSetting tpRange = this.add(new SliderSetting("TpRange", 64.0, 1.0, 512.0, 1.0).setSuffix("m"));
    private final SliderSetting tpDelay = this.add(new SliderSetting("TpDelay", 100, 0, 1000).setSuffix("ms"));
    private final BooleanSetting airPath = this.add(new BooleanSetting("AirPath", true));
    private final BooleanSetting airTarget = this.add(new BooleanSetting("AirTarget", true));

    private PlayerEntity target;
    private PlayerEntityPredict targetPredict;
    private PlayerEntityPredict selfPair;
    private final Timer placeTimer = new Timer();
    private final Timer breakTimer = new Timer();
    private final Timer tpTimer = new Timer();
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
        this.tpTimer.reset();
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
        this.targetPredict = new PlayerEntityPredict(this.target, 0.34, this.predict.getValueInt(), 5, false, false, false, true);
        this.selfPair = new PlayerEntityPredict((PlayerEntity)mc.player, 0.34, this.selfPredict.getValueInt(), 5, false, false, false, true);
        int crystal = this.find(Items.END_CRYSTAL);
        if (crystal == -1) {
            return;
        }
        int obby = this.find(Items.OBSIDIAN);
        boolean broke = this.doBreak(obby);
        if (broke && !this.combo.getValue()) {
            return;
        }
        this.doPlace(crystal, obby);
    }

    private boolean doBreak(int obbySlot) {
        if (!this.breakTimer.passed(this.breakDelay.getValueInt())) {
            return false;
        }
        double reach = 4.0;
        Box area = new Box(this.target.getBlockPos()).expand(this.scanBorder.getValueInt());
        List<BreakCand> all = new ArrayList<BreakCand>();
        for (EndCrystalEntity crystal : BlockUtil.getEndCrystals(area)) {
            if (!crystal.isAlive()) continue;
            Vec3d pos = crystal.getPos();
            float dmg = this.damageTo(pos);
            if (dmg < this.minDamageToTarget.getValue()) continue;
            Vec3d landing;
            if (mc.player.getEyePos().distanceTo(pos) <= reach) {
                landing = null;
            } else {
                landing = TPUtils.findTpVec(pos, reach, this.tpRange.getValue(), this.airTarget.getValue());
                if (landing == null) continue;
            }
            float rawSelf = this.selfDamage(pos, landing);
            BlockPos useShield = null;
            if (this.bad(dmg, rawSelf)) {
                if (!this.shield.getValue() || obbySlot == -1) continue;
                useShield = this.findShield(pos, landing, pos);
                if (useShield == null) continue;
                float simSelf = this.selfDamageSim(pos, landing, useShield);
                if (this.bad(dmg, simSelf) || simSelf >= rawSelf) continue;
            }
            all.add(new BreakCand(crystal, landing, useShield, this.score(dmg)));
        }
        if (all.isEmpty()) {
            return false;
        }
        all.sort((a, b) -> Float.compare(b.score, a.score));
        List<BreakCand> direct = new ArrayList<BreakCand>();
        BreakCand tpCand = null;
        for (BreakCand cand : all) {
            if (cand.landing == null) {
                if (direct.size() < this.maxBreak.getValueInt()) {
                    direct.add(cand);
                }
            } else if (tpCand == null) {
                tpCand = cand;
            }
        }
        if (!direct.isEmpty()) {
            final BreakCand first = direct.get(0);
            if (first.shield != null) {
                this.placeShield(first.shield, obbySlot);
            }
            if (this.rotate.getValue()) {
                stella.ROTATION.lookAt(MathUtil.getClosestPointToBox(mc.player.getEyePos(), first.crystal.getBoundingBox()));
            }
            for (BreakCand cand : direct) {
                mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(cand.crystal, mc.player.isSneaking()));
                mc.player.resetLastAttackedTicks();
            }
            EntityUtil.swingHand(Hand.MAIN_HAND, SwingSide.All);
            if (this.rotate.getValue()) {
                stella.ROTATION.snapBack();
            }
            this.breakTimer.reset();
            return true;
        }
        if (tpCand == null) {
            return false;
        }
        final BreakCand attack = tpCand;
        return this.runAt(attack.landing, () -> {
            if (attack.shield != null) {
                this.placeShield(attack.shield, obbySlot);
            }
            if (this.rotate.getValue()) {
                stella.ROTATION.lookAt(MathUtil.getClosestPointToBox(mc.player.getEyePos(), attack.crystal.getBoundingBox()));
            }
            mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(attack.crystal, mc.player.isSneaking()));
            mc.player.resetLastAttackedTicks();
            EntityUtil.swingHand(Hand.MAIN_HAND, SwingSide.All);
            if (this.rotate.getValue()) {
                stella.ROTATION.snapBack();
            }
            this.breakTimer.reset();
        });
    }

    private void doPlace(int crystalSlot, int obbySlot) {
        if (!this.placeTimer.passed(this.placeDelay.getValueInt())) {
            return;
        }
        double reach = 4.5;
        double effRange = this.tpRange.getValue() + 6.0;
        List<PlaceCand> cands = new ArrayList<PlaceCand>();
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
            float dmg = this.damageTo(vec);
            if (dmg < this.minDamageToTarget.getValue()) continue;
            Vec3d landing;
            if (mc.player.getEyePos().distanceTo(vec) <= reach) {
                landing = null;
            } else {
                landing = TPUtils.findTpVec(vec, reach, this.tpRange.getValue(), this.airTarget.getValue());
                if (landing == null) continue;
            }
            float rawSelf = this.selfDamage(vec, landing);
            BlockPos useShield = null;
            if (this.bad(dmg, rawSelf)) {
                if (!this.shield.getValue() || obbySlot == -1) continue;
                useShield = this.findShield(vec, landing, vec);
                if (useShield == null) continue;
                float simSelf = this.selfDamageSim(vec, landing, useShield);
                if (this.bad(dmg, simSelf) || simSelf >= rawSelf) continue;
            }
            cands.add(new PlaceCand(pos, landing, useShield, needObby, this.score(dmg)));
        }
        if (cands.isEmpty()) {
            return;
        }
        cands.sort((a, b) -> Float.compare(b.score, a.score));
        final PlaceCand best = cands.get(0);
        final boolean runNeedObby = best.needObby;
        final BlockPos runShield = best.shield;
        this.runAt(best.landing, () -> {
            int old = mc.player.getInventory().selectedSlot;
            if (runShield != null) {
                this.placeShield(runShield, obbySlot);
            }
            if (runNeedObby) {
                this.doSwap(obbySlot);
                BlockUtil.placeBlock(best.pos.down(), this.rotate.getValue(), this.packet.getValue());
                this.swapBack(obbySlot, old);
            }
            this.doSwap(crystalSlot);
            BlockUtil.placeCrystal(best.pos, this.rotate.getValue());
            this.swapBack(crystalSlot, old);
            this.placeTimer.reset();
            if (this.rotate.getValue()) {
                stella.ROTATION.snapBack();
            }
        });
    }

    private float damageTo(Vec3d pos) {
        return ExplosionUtil.calculateDamage(pos, this.targetPredict.player, this.targetPredict.predict, 6.0f);
    }

    private float selfDamage(Vec3d explosion, Vec3d landing) {
        if (landing != null) {
            if (this.tpSelf == null) {
                this.tpSelf = new CopyPlayerEntity(mc.player, true);
            }
            this.tpSelf.setPosition(landing.x, landing.y, landing.z);
            return ExplosionUtil.calculateDamage(explosion, this.tpSelf, this.tpSelf, 6.0f);
        }
        return ExplosionUtil.calculateDamage(explosion, this.selfPair.player, this.selfPair.predict, 6.0f);
    }

    private float selfDamageSim(Vec3d explosion, Vec3d landing, BlockPos shieldPos) {
        CombatUtil.modifyPos = shieldPos;
        CombatUtil.modifyBlockState = Blocks.OBSIDIAN.getDefaultState();
        try {
            return this.selfDamage(explosion, landing);
        } finally {
            CombatUtil.modifyPos = null;
            CombatUtil.modifyBlockState = null;
        }
    }

    private boolean bad(float dmg, float self) {
        if (self > this.maxDamageToSelf.getValue()) {
            return true;
        }
        if (this.noSuicide.getValue() && self >= EntityUtil.getHealth(mc.player)) {
            return true;
        }
        if (this.smart.getValue() && self > dmg) {
            return true;
        }
        return false;
    }

    private float score(float dmg) {
        return dmg + (dmg >= EntityUtil.getHealth(this.target) ? 1000.0f : 0.0f);
    }

    /**
     * 在自己与爆点连线附近找一个可放置的黑曜石格：
     * 需可替换、脚下有支撑、不与自身碰撞箱重叠、离爆点足够远、
     * 在落点眼点触及内，且模拟放置后不会挡住自己到目标的动作射线。
     */
    private BlockPos findShield(Vec3d explosion, Vec3d landing, Vec3d actionPoint) {
        Vec3d feet = landing != null ? landing : mc.player.getPos();
        Vec3d eye = landing != null ? new Vec3d(landing.x, landing.y + mc.player.getStandingEyeHeight(), landing.z) : mc.player.getEyePos();
        Vec3d hd = new Vec3d(explosion.x - feet.x, 0.0, explosion.z - feet.z);
        if (hd.lengthSquared() < 1.0E-4) {
            return null;
        }
        hd = hd.normalize();
        double px = -hd.z;
        double pz = hd.x;
        int feetY = (int)Math.floor(feet.y);
        Vec3d shift = landing != null ? landing.subtract(mc.player.getPos()) : Vec3d.ZERO;
        Box selfBox = mc.player.getBoundingBox().offset(shift.x, shift.y, shift.z);
        double[][] moves = new double[][]{{1.0, 0.0}, {1.5, 0.0}, {1.0, 0.7}, {1.0, -0.7}, {2.0, 0.0}, {1.5, 1.0}, {1.5, -1.0}};
        for (double[] move : moves) {
            double x = feet.x + hd.x * move[0] + px * move[1];
            double z = feet.z + hd.z * move[0] + pz * move[1];
            BlockPos cell = new BlockPos((int)Math.floor(x), feetY, (int)Math.floor(z));
            if (cell.equals(BlockPos.ofFloored(actionPoint)) || Vec3d.ofCenter(cell).distanceTo(actionPoint) <= 1.5) {
                continue;
            }
            if (!mc.world.getBlockState(cell).isReplaceable()) {
                continue;
            }
            if (selfBox.intersects(new Box(cell))) {
                continue;
            }
            if (eye.distanceTo(Vec3d.ofCenter(cell)) > 4.5) {
                continue;
            }
            if (!BlockUtil.canPlace(cell, 1000.0, true)) {
                continue;
            }
            if (this.shieldBlocksAction(cell, eye, actionPoint)) {
                continue;
            }
            return cell;
        }
        return null;
    }

    private boolean shieldBlocksAction(BlockPos shieldPos, Vec3d eye, Vec3d actionPoint) {
        CombatUtil.modifyPos = shieldPos;
        CombatUtil.modifyBlockState = Blocks.OBSIDIAN.getDefaultState();
        try {
            BlockHitResult hit = mc.world.raycast(new RaycastContext(eye, actionPoint, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            return hit.getType() != HitResult.Type.MISS && hit.getPos().distanceTo(actionPoint) < 1.5;
        } finally {
            CombatUtil.modifyPos = null;
            CombatUtil.modifyBlockState = null;
        }
    }

    private void placeShield(BlockPos shieldPos, int obbySlot) {
        if (shieldPos == null || obbySlot == -1) return;
        int old = mc.player.getInventory().selectedSlot;
        this.doSwap(obbySlot);
        BlockUtil.placeBlock(shieldPos, this.rotate.getValue(), this.packet.getValue());
        this.swapBack(obbySlot, old);
    }

    private boolean runAt(Vec3d landing, Runnable action) {
        if (landing == null) {
            action.run();
            return true;
        }
        if (this.tpActive || !this.tpTimer.passed(this.tpDelay.getValueInt())) {
            return false;
        }
        this.tpActive = true;
        boolean ok;
        try {
            if (this.tpBack.getValue()) {
                ok = TPUtils.teleportWithBack(landing, this.tpMode.getValue(), this.tpStep.getValueInt(), this.tpStepped.getValue(), this.airPath.getValue(), action);
            } else {
                ok = TPUtils.newTeleport(landing, this.tpStep.getValueInt(), this.tpStepped.getValue(), this.airPath.getValue());
                if (ok) {
                    action.run();
                }
            }
        } finally {
            this.tpActive = false;
        }
        if (!ok) {
            return false;
        }
        this.tpTimer.reset();
        return true;
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

    private static final class BreakCand {
        final EndCrystalEntity crystal;
        final Vec3d landing;
        final BlockPos shield;
        final float score;

        BreakCand(EndCrystalEntity crystal, Vec3d landing, BlockPos shield, float score) {
            this.crystal = crystal;
            this.landing = landing;
            this.shield = shield;
            this.score = score;
        }
    }

    private static final class PlaceCand {
        final BlockPos pos;
        final Vec3d landing;
        final BlockPos shield;
        final boolean needObby;
        final float score;

        PlaceCand(BlockPos pos, Vec3d landing, BlockPos shield, boolean needObby, float score) {
            this.pos = pos;
            this.landing = landing;
            this.shield = shield;
            this.needObby = needObby;
            this.score = score;
        }
    }
}
