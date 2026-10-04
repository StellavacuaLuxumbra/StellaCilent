package dev.stella.mod.modules.impl.combat;

import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.ClientTickEvent;
import dev.stella.api.events.impl.MoveEvent;
import dev.stella.api.events.impl.PacketEvent;
import dev.stella.api.events.impl.RotationEvent;
import dev.stella.api.utils.combat.CombatUtil;
import dev.stella.api.utils.math.MathUtil;
import dev.stella.api.utils.math.Timer;
import dev.stella.api.utils.player.EntityUtil;
import dev.stella.api.utils.player.InventoryUtil;
import dev.stella.api.utils.world.BlockUtil;
import dev.stella.stella;
import dev.stella.mod.modules.Module;

import dev.stella.mod.modules.settings.impl.BooleanSetting;
import dev.stella.mod.modules.settings.impl.EnumSetting;
import dev.stella.mod.modules.settings.impl.SliderSetting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class AutoMace extends Module {
    public static AutoMace INSTANCE;
    
    private final EnumSetting<Mode> mode = this.add(new EnumSetting<>("Mode", Mode.Legit));
    private final SliderSetting targetRange = this.add(new SliderSetting("TargetRange", 10.0, 1.0, 30.0, 0.5, () -> this.mode.is(Mode.Legit)).setSuffix("m"));
    private final SliderSetting attackRange = this.add(new SliderSetting("AttackRange", 6.0, 3.0, 6.0, 0.1, () -> this.mode.is(Mode.Legit)).setSuffix("m"));
    private final SliderSetting height = this.add(new SliderSetting("Height", 15, 5, 50, 1, () -> this.mode.is(Mode.Rage)).setSuffix("blocks"));
    private final SliderSetting delay = this.add(new SliderSetting("Delay", 0, 0, 2000, 50, () -> this.mode.is(Mode.Rage)).setSuffix("ms"));
    private final BooleanSetting rotate = this.add(new BooleanSetting("Rotate", false, () -> this.mode.is(Mode.Legit)));
    private final BooleanSetting track = this.add(new BooleanSetting("Track", true, () -> this.mode.is(Mode.Legit)));
    private final BooleanSetting lagback = this.add(new BooleanSetting("Lagback", true, () -> this.mode.is(Mode.Legit)));
    private final BooleanSetting post = this.add(new BooleanSetting("Post", false, () -> this.mode.is(Mode.Legit)));
    private final SliderSetting postDelay = this.add(new SliderSetting("PostDelay", 500, 50, 2000, 50, () -> this.mode.is(Mode.Legit) && this.post.getValue()).setSuffix("ms"));
    private final BooleanSetting windCharge = this.add(new BooleanSetting("WindCharge", false, () -> this.mode.is(Mode.Rage)));
    private final BooleanSetting switchMace = this.add(new BooleanSetting("AutoSwitch", true));
    private final Timer timer = new Timer();
    private final Timer postTimer = new Timer();
    
    private PlayerEntity target = null;
    private long lastAttackTime = 0;
    private boolean reset = false;
    private boolean switched = false;
    private double startY = Double.NaN;
    private int stage = 0; // 0=none, 1=flying, 2=attacking

    public AutoMace() {
        super("AutoMace", Category.Combat);
        this.setChinese("自动锤人");
        INSTANCE = this;
    }

    @Override
    public String getInfo() {
        return this.mode.getValue().name();
    }

    @Override
    public void onEnable() {
        if (nullCheck()) return;
        this.target = null;
        this.lastAttackTime = 0;
        this.reset = false;
        this.stage = 0;
        this.startY = Double.NaN;
        
        if (isElytraEquipped() && mc.player.isFallFlying()) {
            mc.player.stopFallFlying();
        }
    }

    @Override
    public void onDisable() {
        this.target = null;
        this.lastAttackTime = 0;
        this.reset = false;
        this.stage = 0;
        this.switched = false;
        
        if (!nullCheck() && this.switched) {
            EntityUtil.syncInventory();
        }
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (nullCheck() || event.isPost()) return;

        switch (this.mode.getValue()) {
            case Legit:
                handleLegitMode();
                break;
            case Rage:
                handleRageMode();
                break;
        }
    }

    private void handleLegitMode() {
        this.target = getTarget();

        if (this.target == null || mc.player.distanceTo(this.target) > this.attackRange.getValue()) {
            return;
        }

        int slot = getHotbarItemSlot(Items.MACE);
        if (slot == -1) return;

        if (this.rotate.getValue()) {
            Vec3d hitVec = MathUtil.getClosestPointToBox(mc.player.getEyePos(), this.target.getBoundingBox());
            stella.ROTATION.lookAt(hitVec);
        }

        if (System.currentTimeMillis() - this.lastAttackTime > 1000) {
            attackTarget(slot);
            this.lastAttackTime = System.currentTimeMillis();
            
            if (this.post.getValue()) {
                this.postTimer.reset();
            }
        }
        
        if (this.post.getValue() && this.postTimer.passedMs(this.postDelay.getValueInt())) {
            doPostAttack();
        }
    }

    private void handleRageMode() {
        if (!this.timer.passedMs(this.delay.getValueInt())) return;
        
        this.target = getTarget();
        if (this.target == null) return;

        int slot = getHotbarItemSlot(Items.MACE);
        if (slot == -1) return;

        double distance = mc.player.distanceTo(this.target);
        
        if (distance > this.attackRange.getValue()) {
            if (!mc.player.isFallFlying()) {
                if (mc.player.isOnGround() || mc.world.getBlockState(mc.player.getBlockPos().up(2)).isSolidBlock(mc.world, mc.player.getBlockPos().up(2))) {
                    this.startY = mc.player.getY();
                    this.stage = 1;
                    equipElytra();
                    mc.player.jump();
                }
            }
            
            if (mc.player.isFallFlying() && this.windCharge.getValue()) {
                useWindCharge();
            }
        } else {
            if (mc.player.getY() - this.startY >= this.height.getValue()) {
                attackTarget(slot);
                this.lastAttackTime = System.currentTimeMillis();
                this.timer.reset();
                this.stage = 0;
            }
        }
    }

    private void doPostAttack() {
        if (!mc.player.isFallFlying()) {
            equipElytra();
        }
        
        int fireworkSlot = InventoryUtil.findItemInventorySlot(Items.FIREWORK_ROCKET);
        if (fireworkSlot != -1) {
            int oldSlot = mc.player.getInventory().selectedSlot;
            InventoryUtil.switchToSlot(fireworkSlot);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            InventoryUtil.switchToSlot(oldSlot);
        }
    }

    @EventListener
    public void onRotate(RotationEvent event) {
        if (!this.rotate.getValue() || this.target == null || nullCheck()) return;
        
        Vec3d hitVec = MathUtil.getClosestPointToBox(mc.player.getEyePos(), this.target.getBoundingBox());
        if (hitVec != null) {
            event.setTarget(hitVec, 1.0f, 100.0f);
        }
    }

    @EventListener
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof PlayerPositionLookS2CPacket && this.reset) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(mc.player.isOnGround()));
            this.reset = false;
        }
    }

    @EventListener
    public void onMove(MoveEvent event) {
        if (!this.track.getValue() || nullCheck() || mc.player.isOnGround() || this.target == null) return;
        if (mc.player.distanceTo(this.target) <= this.attackRange.getValue()) return;
        if (System.currentTimeMillis() - this.lastAttackTime < 200) return;

        if (mc.player.squaredDistanceTo(this.target.getX(), mc.player.getY(), this.target.getZ()) > 36.0) {
            if (!mc.player.isFallFlying()) {
                equipElytra();
            }
        } else {
            if (mc.player.isFallFlying()) {
                disEquipElytra();
            }
        }

        Vec3d playerPos = mc.player.getPos();
        Vec3d targetPos = this.target.getPos();
        double dX = targetPos.x - playerPos.x;
        double dZ = targetPos.z - playerPos.z;
        double dist = Math.sqrt(dX * dX + dZ * dZ);

        if (dist > 0) {
            dX /= dist;
            dZ /= dist;

            double speed = mc.player.isFallFlying() ? 0.15 : 0.2873;
            event.setX(dX * speed);
            event.setZ(dZ * speed);
        }
    }

    private void attackTarget(int slot) {
        int previousSlot = mc.player.getInventory().selectedSlot;
        Vec3d previous = mc.player.getPos();

        if (this.switchMace.getValue() && slot != previousSlot) {
            InventoryUtil.switchToSlot(slot);
            this.switched = true;
        }

        mc.interactionManager.attackEntity(mc.player, this.target);
        mc.player.swingHand(Hand.MAIN_HAND);

        if (this.switchMace.getValue() && slot != previousSlot) {
            InventoryUtil.switchToSlot(previousSlot);
        }

        if (this.lagback.getValue()) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(false));
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1, mc.player.getZ(), false));
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(previous.x, previous.y, previous.z, false));
            this.reset = true;
        }

        if (this.rotate.getValue()) {
            stella.ROTATION.snapBack();
        }
    }

    private void useWindCharge() {
        int slot = getHotbarItemSlot(Items.WIND_CHARGE);
        if (slot != -1) {
            int oldSlot = mc.player.getInventory().selectedSlot;
            mc.player.getInventory().selectedSlot = slot;
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.getInventory().selectedSlot = oldSlot;
        }
    }

    private PlayerEntity getTarget() {
        PlayerEntity optimalTarget = null;
        double closestDistance = Double.MAX_VALUE;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (!player.isAlive() || player.getHealth() <= 0.0f) continue;
            if (mc.player.squaredDistanceTo(player) > MathHelper.square(this.targetRange.getValue())) continue;

            double distance = mc.player.distanceTo(player);
            if (distance < closestDistance) {
                closestDistance = distance;
                optimalTarget = player;
            }
        }

        return optimalTarget;
    }

    private int getHotbarItemSlot(net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    private boolean isElytraEquipped() {
        return mc.player.getInventory().getArmorStack(2).getItem() == Items.ELYTRA;
    }

    private void equipElytra() {
        for (int i = 0; i < 4; i++) {
            if (mc.player.getInventory().getArmorStack(i).getItem() == Items.ELYTRA) {
                mc.player.getInventory().armor.set(2, mc.player.getInventory().getArmorStack(i));
                mc.player.getInventory().armor.set(i, net.minecraft.item.ItemStack.EMPTY);
                break;
            }
        }
    }

    private void disEquipElytra() {
        for (int i = 0; i < 4; i++) {
            if (i != 2 && mc.player.getInventory().getArmorStack(i).isEmpty()) {
                mc.player.getInventory().armor.set(i, mc.player.getInventory().getArmorStack(2));
                mc.player.getInventory().armor.set(2, net.minecraft.item.ItemStack.EMPTY);
                break;
            }
        }
    }

    public enum Mode {
        Legit,
        Rage
    }
}
