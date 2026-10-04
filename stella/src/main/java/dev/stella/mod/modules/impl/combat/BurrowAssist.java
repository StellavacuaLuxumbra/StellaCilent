package dev.stella.mod.modules.impl.combat;

import dev.stella.stella;
import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.UpdateEvent;
import dev.stella.api.utils.combat.CombatUtil;
import dev.stella.api.utils.math.ExplosionUtil;
import dev.stella.api.utils.math.Timer;
import dev.stella.api.utils.player.EntityUtil;
import dev.stella.api.utils.world.BlockPosX;
import dev.stella.api.utils.world.BlockUtil;
import dev.stella.core.impl.BreakManager;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.impl.exploit.Blink;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import dev.stella.mod.modules.settings.impl.SliderSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

public class BurrowAssist extends Module {
    public static BurrowAssist INSTANCE;

    public static Timer delay = new Timer();
    private final SliderSetting Delay = this.add(new SliderSetting("Delay", 100, 0, 1000));
    public final BooleanSetting pause = this.add(new BooleanSetting("UsingPause", true));
    public final SliderSetting speed = this.add(new SliderSetting("MaxSpeed", 8, 0, 20));
    public final BooleanSetting cCheck = this.add(new BooleanSetting("CheckCrystal", true).setParent());
    private final SliderSetting cRange = this.add(new SliderSetting("Range", 5.0, 0.0, 6.0, cCheck::isOpen));
    private final SliderSetting breakMinSelf = this.add(new SliderSetting("BreakSelf", 12.0, 0.0, 36.0, cCheck::isOpen));
    public final BooleanSetting mCheck = this.add(new BooleanSetting("CheckMine", true).setParent());
    public final BooleanSetting mSelf = this.add(new BooleanSetting("Self", true, mCheck::isOpen));
    private final SliderSetting predictTicks = this.add(new SliderSetting("PredictTicks", 4, 0, 10));
    private final BooleanSetting terrainIgnore = this.add(new BooleanSetting("TerrainIgnore", true));

    private double lastX = 0;
    private double lastY = 0;
    private double lastZ = 0;
    private long lastUpdateTime = 0;

    public BurrowAssist() {
        super("BurrowAssist", Category.Combat);
        this.setChinese("埋身助手");
        INSTANCE = this;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (nullCheck()) return;
        if (!delay.passed((long) Delay.getValue())) return;
        if (pause.getValue() && mc.player.isUsingItem()) {
            return;
        }
        if (mc.options.jumpKey.isPressed()) {
            return;
        }
        if (!Burrow.INSTANCE.hasBurrowItem) return;
        if (!canBurrow()) {
            return;
        }
        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) return;
        
        // 更新玩家速度
        updatePlayerSpeed();
        
        if (mc.player.isOnGround() &&
                getPlayerSpeed(mc.player) < speed.getValueInt() &&
                shouldTrigger()) {

            if (Burrow.INSTANCE.isOn()) return;
            Burrow.INSTANCE.enable();
            delay.reset();
        }
    }
    
    private boolean shouldTrigger() {
        boolean crystalCheck = !cCheck.getValue() || findCrystal();
        boolean mineCheck = !mCheck.getValue() || checkMine(mSelf.getValue());
        return crystalCheck && mineCheck;
    }

    public boolean findCrystal() {
        PlayerAndPredict self = new PlayerAndPredict(mc.player);
        for (Entity crystal : mc.world.getEntities()) {
            if (!(crystal instanceof EndCrystalEntity)) continue;
            if (mc.player.getEyePos().distanceTo(crystal.getPos()) > cRange.getValue()) continue;
            float selfDamage = calculateDamage(crystal.getPos(), self.player, self.predict);
            if (selfDamage < breakMinSelf.getValue()) continue;
            return true;
        }
        return false;
    }

    public double getPlayerSpeed(PlayerEntity player) {
        // 計算玩家實時速度
        double dx = player.getX() - player.lastRenderX;
        double dy = player.getY() - player.lastRenderY;
        double dz = player.getZ() - player.lastRenderZ;
        double speedSq = dx * dx + dy * dy + dz * dz;
        return turnIntoKpH(speedSq);
    }

    public double turnIntoKpH(double input) {
        return (double) MathHelper.sqrt((float) input) * 71.2729367892;
    }

    private void updatePlayerSpeed() {
        // 更新玩家速度計算所需的變量
        // 這個方法在 onUpdate 中被調用，用於準備速度計算
    }

    public float calculateDamage(Vec3d pos, PlayerEntity player, PlayerEntity predict) {
        if (terrainIgnore.getValue()) {
            CombatUtil.terrainIgnore = true;
        }
        float damage = ExplosionUtil.calculateDamage(pos, (LivingEntity) player, (LivingEntity) predict, 6f);
        CombatUtil.terrainIgnore = false;
        return damage;
    }

    public boolean checkMine(boolean self) {
        ArrayList<BlockPos> pos = new ArrayList<>();
        pos.add(EntityUtil.getPlayerPos(true));
        pos.add(new BlockPosX(mc.player.getX() + 0.4, mc.player.getY() + 0.5, mc.player.getZ() + 0.4));
        pos.add(new BlockPosX(mc.player.getX() - 0.4, mc.player.getY() + 0.5, mc.player.getZ() + 0.4));
        pos.add(new BlockPosX(mc.player.getX() + 0.4, mc.player.getY() + 0.5, mc.player.getZ() - 0.4));
        pos.add(new BlockPosX(mc.player.getX() - 0.4, mc.player.getY() + 0.5, mc.player.getZ() - 0.4));
        for (BreakManager.BreakData breakData : new HashMap<>(stella.BREAK.breakMap).values()) {
            if (breakData == null || breakData.getEntity() == null) continue;
            for (BlockPos pos1 : pos) {
                if (pos1.equals(breakData.pos) && breakData.getEntity() != mc.player) {
                    return true;
                }
            }
        }
        // Simplified - just check breakMap
        return self && !stella.BREAK.breakMap.isEmpty();
    }

    public class PlayerAndPredict {
        PlayerEntity player;
        PlayerEntity predict;
        public PlayerAndPredict(PlayerEntity player) {
            this.player = player;
            // Simplified prediction - just use the player itself
            predict = player;
        }
    }

    private static boolean canBurrow() {
        BlockPos pos1 = new BlockPosX(mc.player.getX() + 0.3, mc.player.getY() + 0.5, mc.player.getZ() + 0.3);
        BlockPos pos2 = new BlockPosX(mc.player.getX() - 0.3, mc.player.getY() + 0.5, mc.player.getZ() + 0.3);
        BlockPos pos3 = new BlockPosX(mc.player.getX() + 0.3, mc.player.getY() + 0.5, mc.player.getZ() - 0.3);
        BlockPos pos4 = new BlockPosX(mc.player.getX() - 0.3, mc.player.getY() + 0.5, mc.player.getZ() - 0.3);
        return Burrow.INSTANCE.canPlacePublic(pos1) || Burrow.INSTANCE.canPlacePublic(pos2) || Burrow.INSTANCE.canPlacePublic(pos3) || Burrow.INSTANCE.canPlacePublic(pos4);
    }
}
