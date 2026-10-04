/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.entity.Entity
 *  net.minecraft.entity.decoration.EndCrystalEntity
 *  net.minecraft.network.packet.Packet
 *  net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
 *  net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket$InteractType
 *  net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
 *  net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket$PositionAndOnGround
 *  net.minecraft.util.math.Box
 */
package dev.stella.mod.modules.impl.combat;

import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.PacketEvent;
import dev.stella.api.events.impl.UpdateEvent;
import dev.stella.api.utils.math.Timer;
import dev.stella.api.utils.player.EntityUtil;
import dev.stella.api.utils.player.MovementUtil;
import dev.stella.api.utils.world.BlockUtil;
import dev.stella.asm.accessors.IPlayerMoveC2SPacket;
import dev.stella.core.impl.RotationManager;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.impl.exploit.Blink;
import dev.stella.mod.modules.impl.exploit.BowBomb;
import dev.stella.mod.modules.impl.exploit.Phase;
import dev.stella.mod.modules.impl.player.AutoPearl;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import dev.stella.mod.modules.settings.impl.EnumSetting;
import dev.stella.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class Criticals
extends Module {
    public static Criticals INSTANCE;
    public final EnumSetting<Mode> mode = this.add(new EnumSetting<Mode>("Mode", Mode.OldNCP));
    public final BooleanSetting onlyGround = this.add(new BooleanSetting("OnlyGround", true, () -> !this.mode.is(Mode.Ground)));
    private final BooleanSetting setOnGround = this.add(new BooleanSetting("SetNoGround", false, () -> this.mode.is(Mode.Ground)));
    private final BooleanSetting blockCheck = this.add(new BooleanSetting("BlockCheck", true, () -> this.mode.is(Mode.Ground)));
    private final BooleanSetting autoJump = this.add(new BooleanSetting("AutoJump", true, () -> this.mode.is(Mode.Ground)).setParent());
    private final BooleanSetting mini = this.add(new BooleanSetting("Mini", true, () -> this.mode.is(Mode.Ground) && this.autoJump.isOpen()));
    private final SliderSetting y = this.add(new SliderSetting("MotionY", 0.05, 0.0, 1.0, 1.0E-10, () -> this.mode.is(Mode.Ground) && this.autoJump.isOpen()));
    private final BooleanSetting autoDisable = this.add(new BooleanSetting("AutoDisable", true, () -> this.mode.is(Mode.Ground)));
    private final BooleanSetting crawlingDisable = this.add(new BooleanSetting("CrawlingDisable", true, () -> this.mode.is(Mode.Ground)));
    private final BooleanSetting flight = this.add(new BooleanSetting("Flight", false, () -> this.mode.is(Mode.Ground)));
    
    // Grim 模式设置
    private final BooleanSetting phaseOnly = this.add(new BooleanSetting("PhaseOnly", false, () -> this.mode.is(Mode.Grim)));
    private final BooleanSetting wallsOnly = this.add(new BooleanSetting("WallsOnly", false, () -> this.mode.is(Mode.Grim) && this.phaseOnly.isOpen()));
    private final BooleanSetting moveFix = this.add(new BooleanSetting("MoveFix", false, () -> this.mode.is(Mode.Grim)));
    
    private final Timer attackTimer = new Timer();
    private boolean postUpdateGround;
    private boolean postUpdateSprint;
    
    boolean requireJump = false;

    public Criticals() {
        super("Criticals", Module.Category.Combat);
        this.setChinese("\u5200\u5200\u66b4\u51fb");
        INSTANCE = this;
        this.attackTimer.setMs(999999);
    }

    @Override
    public String getInfo() {
        return this.mode.getValue().name();
    }

    @Override
    public void onDisable() {
        this.postUpdateGround = false;
        this.postUpdateSprint = false;
    }

    @EventListener
    public void onPacketSend(PacketEvent.Send event) {
        Entity entity;
        PlayerInteractEntityC2SPacket packet;
        if (event.isCancelled()) {
            return;
        }
        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) {
            return;
        }
        if (this.mode.is(Mode.Ground)) {
            if (BowBomb.send) {
                return;
            }
            if (AutoPearl.throwing || Phase.INSTANCE.isOn()) {
                return;
            }
            if (!this.setOnGround.getValue()) {
                return;
            }
            if (event.getPacket() instanceof PlayerMoveC2SPacket) {
                ((IPlayerMoveC2SPacket)event.getPacket()).setOnGround(false);
            }
            return;
        }
        Packet<?> packet2 = event.getPacket();
        if (!(!(packet2 instanceof PlayerInteractEntityC2SPacket) || Criticals.getInteractType(packet = (PlayerInteractEntityC2SPacket)packet2) != PlayerInteractEntityC2SPacket.InteractType.ATTACK || (entity = Criticals.getEntity(packet)) instanceof EndCrystalEntity || this.onlyGround.getValue() && !Criticals.mc.player.isOnGround() && !Criticals.mc.player.getAbilities().flying || Criticals.mc.player.isInLava() || Criticals.mc.player.isTouchingWater() || entity == null)) {
            this.doCrit(entity);
        }
    }

    @Override
    public void onLogout() {
        if (this.mode.is(Mode.Ground) && this.autoDisable.getValue()) {
            this.disable();
        }
    }

    @Override
    public void onEnable() {
        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) {
            return;
        }
        this.requireJump = true;
        if (this.mode.is(Mode.Ground)) {
            if (Criticals.nullCheck()) {
                if (this.autoDisable.getValue()) {
                    this.disable();
                }
            } else if (MovementUtil.isMoving() && this.autoDisable.getValue()) {
                this.disable();
            } else if (this.crawlingDisable.getValue() && Criticals.mc.player.isCrawling()) {
                this.disable();
            } else if (Criticals.mc.player.isOnGround() && this.autoJump.getValue() && (!this.blockCheck.getValue() || BlockUtil.canCollide((Entity)Criticals.mc.player, new Box(EntityUtil.getPlayerPos(true).up(2))))) {
                this.jump();
            }
        }
    }

    public void jump() {
        if (this.mini.getValue()) {
            MovementUtil.setMotionY(this.y.getValue());
        } else {
            Criticals.mc.player.jump();
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) {
            return;
        }
        if (this.mode.is(Mode.Ground)) {
            if (this.crawlingDisable.getValue() && Criticals.mc.player.isCrawling()) {
                this.disable();
            } else if (MovementUtil.isMoving() && this.autoDisable.getValue()) {
                this.disable();
            } else if (this.flight.getValue() && Criticals.mc.player.fallDistance > 0.0f) {
                MovementUtil.setMotionY(0.0);
                MovementUtil.setMotionX(0.0);
                MovementUtil.setMotionZ(0.0);
                this.requireJump = false;
            } else if (this.blockCheck.getValue() && !BlockUtil.canCollide((Entity)Criticals.mc.player, new Box(EntityUtil.getPlayerPos(true).up(2)))) {
                this.requireJump = true;
            } else if (Criticals.mc.player.isOnGround() && this.autoJump.getValue() && (this.flight.getValue() || this.requireJump)) {
                this.jump();
                this.requireJump = false;
            }
        }
    }

    public void doCrit(Entity entity) {
        switch (this.mode.getValue().ordinal()) {
            case 7: { // BBTT
                if (MovementUtil.isMoving() || !MovementUtil.isStatic()) {
                    return;
                }
                mc.getNetworkHandler().sendPacket((Packet)new PlayerMoveC2SPacket.PositionAndOnGround(Criticals.mc.player.getX(), Criticals.mc.player.getY(), Criticals.mc.player.getZ(), true));
                mc.getNetworkHandler().sendPacket((Packet)new PlayerMoveC2SPacket.PositionAndOnGround(Criticals.mc.player.getX(), Criticals.mc.player.getY() + 0.0625, Criticals.mc.player.getZ(), false));
                mc.getNetworkHandler().sendPacket((Packet)new PlayerMoveC2SPacket.PositionAndOnGround(Criticals.mc.player.getX(), Criticals.mc.player.getY() + 0.045, Criticals.mc.player.getZ(), false));
                break;
            }
            case 1: { // Strict
                Criticals.mc.player.addCritParticles(entity);
                mc.getNetworkHandler().sendPacket((Packet)new PlayerMoveC2SPacket.PositionAndOnGround(Criticals.mc.player.getX(), Criticals.mc.player.getY() + 0.062600301692775, Criticals.mc.player.getZ(), false));
                mc.getNetworkHandler().sendPacket((Packet)new PlayerMoveC2SPacket.PositionAndOnGround(Criticals.mc.player.getX(), Criticals.mc.player.getY() + 0.07260029960661, Criticals.mc.player.getZ(), false));
                mc.getNetworkHandler().sendPacket((Packet)new PlayerMoveC2SPacket.PositionAndOnGround(Criticals.mc.player.getX(), Criticals.mc.player.getY(), Criticals.mc.player.getZ(), false));
                mc.getNetworkHandler().sendPacket((Packet)new PlayerMoveC2SPacket.PositionAndOnGround(Criticals.mc.player.getX(), Criticals.mc.player.getY(), Criticals.mc.player.getZ(), false));
                break;
            }
            case 2: { // NCP
                Criticals.mc.player.addCritParticles(entity);
                mc.getNetworkHandler().sendPacket((Packet)new PlayerMoveC2SPacket.PositionAndOnGround(Criticals.mc.player.getX(), Criticals.mc.player.getY() + 0.0625, Criticals.mc.player.getZ(), false));
                mc.getNetworkHandler().sendPacket((Packet)new PlayerMoveC2SPacket.PositionAndOnGround(Criticals.mc.player.getX(), Criticals.mc.player.getY(), Criticals.mc.player.getZ(), false));
                break;
            }
            case 3: { // OldNCP
                Criticals.mc.player.addCritParticles(entity);
                mc.getNetworkHandler().sendPacket((Packet)new PlayerMoveC2SPacket.PositionAndOnGround(Criticals.mc.player.getX(), Criticals.mc.player.getY() + 1.058293536E-5, Criticals.mc.player.getZ(), false));
                mc.getNetworkHandler().sendPacket((Packet)new PlayerMoveC2SPacket.PositionAndOnGround(Criticals.mc.player.getX(), Criticals.mc.player.getY() + 9.16580235E-6, Criticals.mc.player.getZ(), false));
                mc.getNetworkHandler().sendPacket((Packet)new PlayerMoveC2SPacket.PositionAndOnGround(Criticals.mc.player.getX(), Criticals.mc.player.getY() + 1.0371854E-7, Criticals.mc.player.getZ(), false));
                break;
            }
            case 0: { // UpdatedNCP
                Criticals.mc.player.addCritParticles(entity);
                mc.getNetworkHandler().sendPacket((Packet)new PlayerMoveC2SPacket.PositionAndOnGround(Criticals.mc.player.getX(), Criticals.mc.player.getY() + 2.71875E-7, Criticals.mc.player.getZ(), false));
                mc.getNetworkHandler().sendPacket((Packet)new PlayerMoveC2SPacket.PositionAndOnGround(Criticals.mc.player.getX(), Criticals.mc.player.getY(), Criticals.mc.player.getZ(), false));
                break;
            }
            case 4: { // Hypixel2K22
                Criticals.mc.player.addCritParticles(entity);
                mc.getNetworkHandler().sendPacket((Packet)new PlayerMoveC2SPacket.PositionAndOnGround(Criticals.mc.player.getX(), Criticals.mc.player.getY() + 0.0045, Criticals.mc.player.getZ(), true));
                mc.getNetworkHandler().sendPacket((Packet)new PlayerMoveC2SPacket.PositionAndOnGround(Criticals.mc.player.getX(), Criticals.mc.player.getY() + 1.52121E-4, Criticals.mc.player.getZ(), false));
                mc.getNetworkHandler().sendPacket((Packet)new PlayerMoveC2SPacket.PositionAndOnGround(Criticals.mc.player.getX(), Criticals.mc.player.getY() + 0.3, Criticals.mc.player.getZ(), false));
                mc.getNetworkHandler().sendPacket((Packet)new PlayerMoveC2SPacket.PositionAndOnGround(Criticals.mc.player.getX(), Criticals.mc.player.getY() + 0.025, Criticals.mc.player.getZ(), false));
                break;
            }
            case 5: { // Packet
                Criticals.mc.player.addCritParticles(entity);
                mc.getNetworkHandler().sendPacket((Packet)new PlayerMoveC2SPacket.PositionAndOnGround(Criticals.mc.player.getX(), Criticals.mc.player.getY() + 5.0E-4, Criticals.mc.player.getZ(), false));
                mc.getNetworkHandler().sendPacket((Packet)new PlayerMoveC2SPacket.PositionAndOnGround(Criticals.mc.player.getX(), Criticals.mc.player.getY() + 1.0E-4, Criticals.mc.player.getZ(), false));
                break;
            }
            case 8: { // Grim - 从 Shoreline 移植
                handleGrimCrit(entity);
                break;
            }
            case 9: { // GrimV3 - 从 Shoreline 移植
                handleGrimV3Crit(entity);
                break;
            }
        }
    }

    /**
     * Grim 模式刀爆逻辑 (针对 Grim v3 优化 - 3c3u.org服务器专用)
     */
    private void handleGrimCrit(Entity entity) {
        // 智能Grim服务器检测
        if (!isGrimServer()) {
            return;
        }
        
        // PhaseOnly 检查
        if (this.phaseOnly.getValue() && (this.wallsOnly.getValue() ? !isDoublePhased() : !isPhased())) {
            return;
        }

        // MoveFix 检查
        if (this.moveFix.getValue() && MovementUtil.isMoving()) {
            return;
        }

        // Timer 检查
        if (!this.attackTimer.passed(250) || !mc.player.isOnGround() || mc.player.isCrawling()) {
            return;
        }

        // 获取当前坐标和旋转角度
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        float yaw = RotationManager.INSTANCE.getLastYaw();
        float pitch = RotationManager.INSTANCE.getLastPitch();

        // --- Grim v3 绕过序列 (3c3u.org专用) ---
        // 核心原理：精确控制Y轴变化，模拟合法跳跃状态
        
        // 1. 基础地面确认 (确保服务器认为你在地面上)
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
            x, y, z, yaw, pitch, true));
            
        // 2. 微小起跳 (Y + 0.0625 - 标准起跳高度)
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
            x, y + 0.0625, z, yaw, pitch, false));
            
        // 3. 精确下落 (Y + 0.04535 - Grim v3最佳匹配值)
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
            x, y + 0.04535, z, yaw, pitch, false));
            
        // 4. 最终确认 (回到地面状态)
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
            x, y, z, yaw, pitch, true));

        // 添加暴击粒子
        mc.player.addCritParticles(entity);
        
        // 重置计时器
        this.attackTimer.reset();
    }
    
    /**
     * 检测是否在Grim反作弊服务器上运行
     */
    private boolean isGrimServer() {
        // 检查服务器地址是否包含grim相关关键词
        if (mc.getNetworkHandler() == null || mc.getNetworkHandler().getConnection() == null) {
            return false;
        }
        
        String serverAddress = mc.getNetworkHandler().getConnection().getAddress().toString();
        return serverAddress.contains("grim") || 
               serverAddress.contains("3c3u") || 
               serverAddress.contains("3c3u.org") ||
               serverAddress.contains("grimpvp");
    }

    /**
     * GrimV3 模式刀爆逻辑 (来自 Shoreline)
     */
    private void handleGrimV3Crit(Entity entity) {
        // PhaseOnly 检查
        if (this.phaseOnly.getValue() && (this.wallsOnly.getValue() ? !isDoublePhased() : !isPhased())) {
            return;
        }

        // MoveFix 检查
        if (this.moveFix.getValue() && MovementUtil.isMoving()) {
            return;
        }

        // 检查是否在地面上且未爬行
        if (!mc.player.isOnGround() || mc.player.isCrawling()) {
            return;
        }

        // 获取旋转角度
        float yaw = RotationManager.INSTANCE.getLastYaw();
        float pitch = RotationManager.INSTANCE.getLastPitch();

        // 发送 GrimV3 模式假地面包
        mc.getNetworkHandler().sendPacket((Packet)new PlayerMoveC2SPacket.Full(
            mc.player.getX(), mc.player.getY(), mc.player.getZ(), 
            yaw, pitch, true));
        mc.getNetworkHandler().sendPacket((Packet)new PlayerMoveC2SPacket.Full(
            mc.player.getX(), mc.player.getY() + 0.0625f, mc.player.getZ(), 
            yaw, pitch, false));
        mc.getNetworkHandler().sendPacket((Packet)new PlayerMoveC2SPacket.Full(
            mc.player.getX(), mc.player.getY() + 0.04535f, mc.player.getZ(), 
            yaw, pitch, false));

        // 添加暴击粒子
        mc.player.addCritParticles(entity);
    }

    /**
     * 检查是否完全被困在方块中（双相检测）
     */
    private boolean isDoublePhased() {
        for (BlockPos pos : BlockPos.iterate(
            BlockPos.ofFloored(mc.player.getBoundingBox().minX, mc.player.getBoundingBox().minY, mc.player.getBoundingBox().minZ),
            BlockPos.ofFloored(mc.player.getBoundingBox().maxX, mc.player.getBoundingBox().maxY, mc.player.getBoundingBox().maxZ)
        )) {
            BlockState state = mc.world.getBlockState(pos);
            BlockState state2 = mc.world.getBlockState(pos.up());
            if (state.blocksMovement() && state2.blocksMovement()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否部分被困在方块中（单相检测）
     */
    private boolean isPhased() {
        for (BlockPos pos : BlockPos.iterate(
            BlockPos.ofFloored(mc.player.getBoundingBox().minX, mc.player.getBoundingBox().minY, mc.player.getBoundingBox().minZ),
            BlockPos.ofFloored(mc.player.getBoundingBox().maxX, mc.player.getBoundingBox().maxY, mc.player.getBoundingBox().maxZ)
        )) {
            if (mc.world.getBlockState(pos).blocksMovement()) {
                return true;
            }
        }
        return false;
    }

    public static Entity getEntity(PlayerInteractEntityC2SPacket packet) {
        return Criticals.mc.world == null ? null : Criticals.mc.world.getEntityById(((dev.stella.asm.accessors.IPlayerInteractEntityC2SPacket)packet).getEntityId());
    }

    public static PlayerInteractEntityC2SPacket.InteractType getInteractType(PlayerInteractEntityC2SPacket packet) {
        final PlayerInteractEntityC2SPacket.InteractType[] result = new PlayerInteractEntityC2SPacket.InteractType[1];
        packet.handle(new PlayerInteractEntityC2SPacket.Handler() {
            @Override
            public void interact(net.minecraft.util.Hand hand) {
                result[0] = PlayerInteractEntityC2SPacket.InteractType.INTERACT;
            }

            @Override
            public void interactAt(net.minecraft.util.Hand hand, net.minecraft.util.math.Vec3d pos) {
                result[0] = PlayerInteractEntityC2SPacket.InteractType.INTERACT_AT;
            }

            @Override
            public void attack() {
                result[0] = PlayerInteractEntityC2SPacket.InteractType.ATTACK;
            }
        });
        return result[0];
    }

    public static enum Mode {
        UpdatedNCP,
        Strict,
        NCP,
        OldNCP,
        Hypixel2K22,
        Packet,
        Ground,
        BBTT,
        Grim;

    }
}

