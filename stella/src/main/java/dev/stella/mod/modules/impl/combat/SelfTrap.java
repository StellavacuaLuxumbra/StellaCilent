package dev.stella.mod.modules.impl.combat;

import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.impl.*;
import dev.stella.api.utils.math.Timer;
import dev.stella.*;
import dev.stella.api.events.eventbus.EventListener;
import dev.stella.mod.modules.impl.exploit.*;
import dev.stella.api.utils.world.*;
import dev.stella.api.utils.combat.*;
import dev.stella.mod.modules.impl.movement.*;
import dev.stella.mod.modules.impl.movement.ElytraFly;
import dev.stella.api.events.impl.*;
import dev.stella.core.impl.*;
import net.minecraft.*;
import dev.stella.mod.modules.impl.player.*;
import java.util.*;
import dev.stella.api.utils.player.*;
import dev.stella.api.utils.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.Hand;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.component.DataComponentTypes;
import dev.stella.api.utils.math.Rotation;
import dev.stella.mod.modules.Module;
import net.minecraft.entity.Entity;

public class SelfTrap extends Module
{
    public static SelfTrap INSTANCE;
    private final EnumSetting<Page> page;
    private final BooleanSetting godMode;
    private final SliderSetting breakTime;
    private final BooleanSetting failedSkip;
    private final SliderSetting placeDelay;
    private final BooleanSetting mineDownward;
    private final BooleanSetting extend;
    private final BooleanSetting head;
    private final BooleanSetting feet;
    private final BooleanSetting chest;
    private final BooleanSetting support;
    private final BooleanSetting mineExtend;
    private final BooleanSetting headExtend;
    private final EnumSetting<TimingMode> timingMode;
    private final BooleanSetting prePlaceExplosion;
    private final BooleanSetting prePlaceTick;
    private final SliderSetting blocksPerTick;
    private final SliderSetting shiftDelay;
    private final BooleanSetting autoDisable;
    private final BindSetting headKey;
    private final BooleanSetting inAir;
    private final BooleanSetting detectMining;
    private final BooleanSetting usingPause;
    private final BooleanSetting noBlockDisable;
    private final BooleanSetting moveDisable;
    private final BooleanSetting jumpDisable;
    private final BooleanSetting rotate;
    private final BooleanSetting yawStep;
    private final BooleanSetting whenElytra;
    private final SliderSetting steps;
    private final BooleanSetting checkFov;
    private final SliderSetting fov;
    private final SliderSetting priority;
    private final Timer timer;
    private final BooleanSetting packetPlace;
    private final BooleanSetting breakCrystal;
    private final BooleanSetting eatPause;
    private final BooleanSetting center;
    private final BooleanSetting inventory;
    private final BooleanSetting enderChest;
    public Vec3d directionVec;
    double startX;
    double startY;
    double startZ;
    int progress;
    private boolean shouldCenter;
    public static final List<BlockPos> airList;
    private List<BlockPos> trapBlocks;
    private Map<BlockPos, Long> placedPackets;
    private int blocksPlacedThisTick;
    private double prevY;
    private boolean initialBurst;
    private long enableTime;
    
    public SelfTrap() {
        super("SelfTrap", Category.Combat);
        this.page = this.add(new EnumSetting<Page>("Page", Page.General));
        this.godMode = this.add(new BooleanSetting("GodMode", true, () -> this.page.is(Page.GodMode)));
        this.breakTime = this.add(new SliderSetting("BreakTime", 2.0, 0.0, 3.0, () -> this.page.is(Page.GodMode)));
        this.failedSkip = this.add(new BooleanSetting("FailedSkip", true, () -> this.page.is(Page.GodMode)));
        this.placeDelay = this.add(new SliderSetting("PlaceDelay", 50, 0, 500, () -> this.page.is(Page.General)));
        this.mineDownward = this.add(new BooleanSetting("MineDownward", false, () -> this.page.is(Page.General)));
        this.extend = this.add(new BooleanSetting("Extend", true, () -> this.page.is(Page.General)));
        this.head = this.add(new BooleanSetting("Head", true, () -> this.page.is(Page.General)));
        this.feet = this.add(new BooleanSetting("Feet", true, () -> this.page.is(Page.General)));
        this.chest = this.add(new BooleanSetting("Chest", true, () -> this.page.is(Page.General)));
        this.support = this.add(new BooleanSetting("Support", false, () -> this.page.is(Page.General)));
        this.mineExtend = this.add(new BooleanSetting("MineExtend", false, () -> this.page.is(Page.General)));
        this.headExtend = this.add(new BooleanSetting("HeadExtend", false, () -> this.page.is(Page.General) && this.head.isOpen()));
        this.timingMode = this.add(new EnumSetting<TimingMode>("TimingMode", TimingMode.VANILLA, () -> this.page.is(Page.General)));
        this.prePlaceExplosion = this.add(new BooleanSetting("PrePlace-Explosion", false, () -> this.page.is(Page.General) && this.timingMode.getValue() == TimingMode.SEQUENTIAL));
        this.prePlaceTick = this.add(new BooleanSetting("PrePlace-Tick", false, () -> this.page.is(Page.General) && this.timingMode.getValue() == TimingMode.SEQUENTIAL));
        this.blocksPerTick = this.add(new SliderSetting("BlocksPerTick", 2, 1, 20, () -> this.page.is(Page.General)));
        this.shiftDelay = this.add(new SliderSetting("ShiftDelay", 0.0, 0.0, 5.0, 0.1, () -> this.page.is(Page.General)));
        this.autoDisable = this.add(new BooleanSetting("AutoDisable", true, () -> this.page.is(Page.General)));
        this.headKey = this.add(new BindSetting("HeadKey", -1, () -> this.page.is(Page.General)));
        this.inAir = this.add(new BooleanSetting("InAir", true, () -> this.page.is(Page.Check)));
        this.detectMining = this.add(new BooleanSetting("DetectMining", false, () -> this.page.is(Page.Check)));
        this.usingPause = this.add(new BooleanSetting("UsingPause", true, () -> this.page.is(Page.Check)));
        this.noBlockDisable = this.add(new BooleanSetting("NoBlockDisable", true, () -> this.page.is(Page.Check)));
        this.moveDisable = this.add(new BooleanSetting("MoveDisable", true, () -> this.page.is(Page.Check)));
        this.jumpDisable = this.add(new BooleanSetting("JumpDisable", true, () -> this.page.is(Page.Check)));
        this.rotate = this.add(new BooleanSetting("Rotate", true, () -> this.page.getValue() == Page.Rotate));
        this.yawStep = this.add(new BooleanSetting("YawStep", false, () -> this.rotate.isOpen() && this.page.getValue() == Page.Rotate).setParent());
        this.whenElytra = this.add(new BooleanSetting("FallFlying", true, () -> this.rotate.isOpen() && this.yawStep.isOpen() && this.page.getValue() == Page.Rotate));
        this.steps = this.add(new SliderSetting("Steps", 0.05, 0.0, 1.0, 0.01, () -> this.page.getValue() == Page.Rotate && this.yawStep.isOpen()));
        this.checkFov = this.add(new BooleanSetting("OnlyLooking", true, () -> this.page.getValue() == Page.Rotate && this.yawStep.isOpen()).setParent());
        this.fov = this.add(new SliderSetting("Fov", 20.0, 0.0, 360.0, 0.1, () -> this.checkFov.isOpen() && this.page.getValue() == Page.Rotate && this.yawStep.isOpen()));
        this.priority = this.add(new SliderSetting("Priority", 10, 0, 100, () -> this.page.getValue() == Page.Rotate && this.yawStep.isOpen()));
        this.timer = new Timer();
        this.packetPlace = this.add(new BooleanSetting("PacketPlace", true, () -> this.page.is(Page.General)));
        this.breakCrystal = this.add(new BooleanSetting("Break", true, () -> this.page.is(Page.General)).setParent());
        this.eatPause = this.add(new BooleanSetting("EatingPause", true, () -> this.page.is(Page.General) && this.breakCrystal.isOpen()));
        this.center = this.add(new BooleanSetting("Center", true, () -> this.page.is(Page.General)));
        this.inventory = this.add(new BooleanSetting("InventorySwap", true, () -> this.page.is(Page.General)));
        this.enderChest = this.add(new BooleanSetting("EnderChest", true, () -> this.page.is(Page.General)));
        this.directionVec = null;
        this.startX = 0.0;
        this.startY = 0.0;
        this.startZ = 0.0;
        this.progress = 0;
        this.shouldCenter = true;
        this.trapBlocks = new ArrayList<BlockPos>();
        this.placedPackets = new HashMap<BlockPos, Long>();
        this.blocksPlacedThisTick = 0;
        this.prevY = 0.0;
        this.setChinese("\u81ea\u6211\u56f0\u4f4f");
        SelfTrap.INSTANCE = this;
        stella.EVENT_BUS.subscribe(new SelfTrapTick());
    }
    
    public static boolean selfIntersectPos(final BlockPos pos) {
        return SelfTrap.mc.player.getBoundingBox().intersects(new Box(pos));
    }
    
    public static Rotation getRotationTo(final Vec3d posFrom, final Vec3d posTo) {
        final Vec3d vec3d = posTo.subtract(posFrom);
        return getRotationFromVec(vec3d);
    }
    
    private static Rotation getRotationFromVec(final Vec3d vec) {
        final double d = vec.x;
        final double d2 = vec.z;
        final double xz = Math.hypot(d, d2);
        final double yaw = normalizeAngle(Math.toDegrees(Math.atan2(d2, d)) - 90.0);
        final double pitch = normalizeAngle(Math.toDegrees(-Math.atan2(vec.y, xz)));
        return new Rotation((float)yaw, (float)pitch);
    }
    
    @EventListener
    public void onRotate(final RotationEvent event) {
        if (this.directionVec != null && this.rotate.getValue() && this.shouldYawStep()) {
            event.setTarget(this.directionVec, this.steps.getValueFloat(), this.priority.getValueFloat());
        }
    }
    
    private static double normalizeAngle(final double angleIn) {
        double angle = angleIn;
        if ((angle %= 360.0) >= 180.0) {
            angle -= 360.0;
        }
        if (angle < -180.0) {
            angle += 360.0;
        }
        return angle;
    }
    
    @Override
    public void onEnable() {
        if (nullCheck()) {
            if (this.moveDisable.getValue() || this.jumpDisable.getValue()) {
                this.disable();
            }
            return;
        }
        this.startX = SelfTrap.mc.player.getX();
        this.startY = SelfTrap.mc.player.getY();
        this.startZ = SelfTrap.mc.player.getZ();
        this.prevY = this.startY;
        this.shouldCenter = true;
        this.trapBlocks.clear();
        this.placedPackets.clear();
        this.initialBurst = true;
        this.enableTime = System.currentTimeMillis();
    }
    
    @Override
    public void onDisable() {
        this.trapBlocks.clear();
        this.placedPackets.clear();
        SelfTrap.airList.clear();
    }
    
    @EventListener
    public void onTick(final ClientTickEvent event) {
        if (nullCheck()) {
            return;
        }
        if (this.inventory.getValue() && !EntityUtil.inInventory()) {
            return;
        }
        if ((this.timingMode.getValue() == TimingMode.Pre && event.isPost()) || (this.timingMode.getValue() == TimingMode.Post && event.isPre())) {
            return;
        }
        if (this.initialBurst && System.currentTimeMillis() - this.enableTime > 500) {
            this.initialBurst = false;
        }
        if (!this.initialBurst && !this.timer.passed((long)this.placeDelay.getValue())) {
            return;
        }
        this.directionVec = null;
        this.progress = 0;
        if (!MovementUtil.isMoving() && !SelfTrap.mc.options.sneakKey.isPressed()) {
            this.startX = SelfTrap.mc.player.getX();
            this.startY = SelfTrap.mc.player.getY();
            this.startZ = SelfTrap.mc.player.getZ();
        }
        final BlockPos pos = EntityUtil.getPlayerPos(true);
        final double distanceToStart = Math.sqrt((float)SelfTrap.mc.player.squaredDistanceTo(this.startX, this.startY, this.startZ));
        if (this.autoDisable.getValue() && (SelfTrap.mc.player.getY() - this.prevY > 0.5 || SelfTrap.mc.player.fallDistance > 1.5f)) {
            this.disable();
            return;
        }
        if (this.getBlock() == -1) {
            if (this.noBlockDisable.getValue()) {
                this.disable();
            }
            return;
        }
        if ((this.moveDisable.getValue() && distanceToStart > 1.0) || (this.jumpDisable.getValue() && SelfTrap.mc.player.input.jumping)) {
            this.disable();
            return;
        }
        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) {
            return;
        }
        if (this.usingPause.getValue() && SelfTrap.mc.player.isUsingItem() && !SelfTrap.mc.player.getActiveItem().getComponents().contains(DataComponentTypes.FOOD)) {
            return;
        }
        if (!this.inAir.getValue() && !SelfTrap.mc.player.isOnGround()) {
            return;
        }
        this.blocksPlacedThisTick = 0;
        this.trapBlocks = this.getTrapBlocks(pos);
        if (this.trapBlocks.isEmpty()) {
            return;
        }
        if (this.breakCrystal.getValue()) {
            this.attackBlockingCrystals();
        }
        if (this.feet.getValue()) {
            this.doSurround(pos);
        }
        if (this.chest.getValue()) {
            this.doSurround(pos.up());
        }
        if (this.head.getValue()) {
            this.doHeadCross(pos);
        }
        SelfTrap.airList.clear();
        this.prevY = SelfTrap.mc.player.getY();
    }
    
    private List<BlockPos> getTrapBlocks(final BlockPos playerPos) {
        final ArrayList<BlockPos> trapBlocks = new ArrayList<BlockPos>();
        final ArrayList<BlockPos> playerBlocks = new ArrayList<BlockPos>();
        if (this.extend.getValue()) {
            final double minX = Math.floor(SelfTrap.mc.player.getBoundingBox().minX);
            final double minY = Math.floor(SelfTrap.mc.player.getBoundingBox().minY);
            final double minZ = Math.floor(SelfTrap.mc.player.getBoundingBox().minZ);
            final double maxX = Math.ceil(SelfTrap.mc.player.getBoundingBox().maxX);
            final double maxY = Math.ceil(SelfTrap.mc.player.getBoundingBox().maxY);
            final double maxZ = Math.ceil(SelfTrap.mc.player.getBoundingBox().maxZ);
            for (int x = (int)minX; x < maxX; ++x) {
                for (int y = (int)minY; y < maxY; ++y) {
                    for (int z = (int)minZ; z < maxZ; ++z) {
                        playerBlocks.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        else {
            playerBlocks.add(playerPos);
        }
        for (final BlockPos pos : playerBlocks) {
            for (final Direction dir : Direction.values()) {
                if (dir.getAxis().isHorizontal()) {
                    final BlockPos pos2 = pos.offset(dir);
                    if (!trapBlocks.contains(pos2)) {
                        if (!playerBlocks.contains(pos2)) {
                            trapBlocks.add(pos2);
                            trapBlocks.add(pos2.up());
                        }
                    }
                }
            }
        }
        if (this.head.getValue()) {
            boolean support = false;
            final ArrayList<BlockPos> headBlocks = new ArrayList<BlockPos>();
            for (final BlockPos pos3 : playerBlocks) {
                final BlockPos headPos = pos3.up(2);
                if (!SelfTrap.mc.world.getBlockState(headPos).isAir()) {
                    support = true;
                }
                headBlocks.add(headPos);
            }
            if (!BlockUtil.allowAirPlace()) {
                BlockPos supportingPos = null;
                double min = Double.MAX_VALUE;
                for (final BlockPos pos4 : trapBlocks) {
                    final BlockPos pos5 = pos4.up(2);
                    if (!SelfTrap.mc.world.getBlockState(pos5).isAir()) {
                        support = true;
                        break;
                    }
                    final double dist = SelfTrap.mc.player.squaredDistanceTo(pos5.toCenterPos());
                    if (dist >= min) {
                        continue;
                    }
                    supportingPos = pos5;
                    min = dist;
                }
                if (supportingPos != null && !support) {
                    trapBlocks.add(supportingPos);
                }
            }
            trapBlocks.addAll(headBlocks);
        }
        for (final BlockPos pos6 : playerBlocks) {
            if (pos6.equals((Object)playerPos)) {
                continue;
            }
            trapBlocks.add(pos6.down());
        }
        if (this.mineExtend.getValue()) {
            for (final BlockPos trapPos : new ArrayList<BlockPos>(trapBlocks)) {
                final boolean secondLayer = trapPos.getY() != playerPos.getY();
                if (this.headExtend.getValue() || !secondLayer) {
                    if (!stella.BREAK.isMining(trapPos)) {
                        continue;
                    }
                    for (final Direction direction : Direction.values()) {
                        if (direction != Direction.UP) {
                            if (direction != Direction.DOWN || !secondLayer) {
                                final BlockPos blockerPos = trapPos.offset(direction);
                                if (!playerBlocks.contains(blockerPos)) {
                                    if (!stella.BREAK.isMining(blockerPos)) {
                                        trapBlocks.add(blockerPos);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return trapBlocks;
    }
    
    private void attackBlockingCrystals() {
        for (final BlockPos pos : this.trapBlocks) {
            final Entity crystal = (Entity)SelfTrap.mc.world.getEntitiesByClass(EndCrystalEntity.class, new Box(pos), e -> true).stream().findFirst().orElse(null);
            if (crystal == null) {
                continue;
            }
            CombatUtil.attackCrystal(pos, this.rotate.getValue(), this.eatPause.getValue() && !SelfTrap.mc.player.getActiveItem().getItem().getComponents().contains(DataComponentTypes.FOOD));
        }
    }
    
    private boolean shouldYawStep() {
        // 检查是否使用ElytraFly飞行
        boolean isElytraFlying = ElytraFly.INSTANCE != null && ElytraFly.INSTANCE.isOn() && ElytraFly.INSTANCE.isFallFlying();
        // 检查是否使用EFly飞行
        boolean isEFlyFlying = EFly.INSTANCE != null && EFly.INSTANCE.isOn() && mc.player.isFallFlying();
        
        return (this.whenElytra.getValue() || (!SelfTrap.mc.player.isFallFlying() && !isElytraFlying && !isEFlyFlying)) && this.yawStep.getValue() && !Velocity.INSTANCE.noRotation();
    }
    
    @EventListener(priority = -1)
    public void onMove(final MoveEvent event) {
        if (Module.nullCheck() || !this.center.getValue() || SelfTrap.mc.player.isFallFlying()) {
            return;
        }
        final BlockPos blockPos = EntityUtil.getPlayerPos(true);
        if (SelfTrap.mc.player.getX() - blockPos.getX() - 0.5 <= 0.2 && SelfTrap.mc.player.getX() - blockPos.getX() - 0.5 >= -0.2 && SelfTrap.mc.player.getZ() - blockPos.getZ() - 0.5 <= 0.2 && SelfTrap.mc.player.getZ() - 0.5 - blockPos.getZ() >= -0.2) {
            if (this.shouldCenter && (SelfTrap.mc.player.isOnGround() || MovementUtil.isMoving())) {
                event.setX(0.0);
                event.setZ(0.0);
                this.shouldCenter = false;
            }
        }
        else if (this.shouldCenter) {
            final Vec3d centerPos = EntityUtil.getPlayerPos(true).toCenterPos();
            final float rotation = getRotationTo(SelfTrap.mc.player.getPos(), centerPos).getYaw();
            final float yawRad = rotation / 180.0f * 3.1415927f;
            final double dist = SelfTrap.mc.player.getPos().distanceTo(new Vec3d(centerPos.x, SelfTrap.mc.player.getY(), centerPos.z));
            final double cappedSpeed = Math.min(0.2873, dist);
            final double x = -(float)Math.sin(yawRad) * cappedSpeed;
            final double z = (float)Math.cos(yawRad) * cappedSpeed;
            event.setX(x);
            event.setZ(z);
        }
    }
    
    private void doSurround(final BlockPos pos) {
        for (final Direction i : Direction.values()) {
            if (i != Direction.DOWN) {
                BlockPos offsetPos = pos.offset(i);
                if (this.godMode.getValue()) {
                    for (final BreakManager.BreakData breakData : stella.BREAK.breakMap.values()) {
                        if (breakData.getEntity() != null && (!this.failedSkip.getValue() || !breakData.failed)) {
                            if (!breakData.pos.equals((Object)offsetPos)) {
                                continue;
                            }
                            if (breakData.timer.getMs() < this.breakTime.getValue() * 1000.0) {
                                break;
                            }
                            SelfTrap.airList.add(offsetPos);
                            break;
                        }
                    }
                }
                boolean needsPlace = false;
                if (BlockUtil.getPlaceSide(offsetPos) != null) {
                    needsPlace = true;
                }
                else if (BlockUtil.canReplace(offsetPos)) {
                    final BlockPos supportPos;
                    if (this.support.getValue() && (supportPos = this.getHelperPos(offsetPos)) != null) {
                        this.tryPlaceBlock(supportPos);
                    }
                    if ((offsetPos = this.getHelperPos(offsetPos)) != null) {
                        needsPlace = true;
                    }
                }
                if (needsPlace) {
                    if (!selfIntersectPos(offsetPos) || !this.extend.getValue()) {
                        this.tryPlaceBlock(offsetPos);
                    }
                    if (this.extend.getValue()) {
                        for (final Direction i2 : Direction.values()) {
                            if (i2 != Direction.DOWN) {
                                final BlockPos offsetPos2 = offsetPos.offset(i2);
                                if (this.godMode.getValue()) {
                                    for (final BreakManager.BreakData breakData2 : stella.BREAK.breakMap.values()) {
                                        if (breakData2.getEntity() != null && (!this.failedSkip.getValue() || !breakData2.failed)) {
                                            if (!breakData2.pos.equals((Object)offsetPos2)) {
                                                continue;
                                            }
                                            if (breakData2.timer.getMs() < this.breakTime.getValue() * 1000.0) {
                                                break;
                                            }
                                            SelfTrap.airList.add(offsetPos2);
                                            break;
                                        }
                                    }
                                }
                                if (selfIntersectPos(offsetPos2)) {
                                    for (final Direction i3 : Direction.values()) {
                                        if (i3 != Direction.DOWN) {
                                            if (this.canPlaceBlock(offsetPos2)) {
                                                this.tryPlaceBlock(offsetPos2);
                                            }
                                            final BlockPos offsetPos3 = offsetPos2.offset(i3);
                                            final BlockPos placePos = (BlockUtil.getPlaceSide(offsetPos3) != null || !BlockUtil.canReplace(offsetPos3)) ? offsetPos3 : this.getHelperPos(offsetPos3);
                                            if (placePos != null) {
                                                if (this.canPlaceBlock(placePos)) {
                                                    this.tryPlaceBlock(placePos);
                                                }
                                            }
                                        }
                                    }
                                }
                                if (this.canPlaceBlock(offsetPos2)) {
                                    this.tryPlaceBlock((BlockUtil.getPlaceSide(offsetPos2) != null || !BlockUtil.canReplace(offsetPos2)) ? offsetPos2 : this.getHelperPos(offsetPos2));
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private void doHeadCross(final BlockPos pos) {
        // 如果玩家正在趴下，不围头
        if (SelfTrap.mc.player.isCrawling()) {
            return;
        }
        final BlockPos headPos = pos.up(2);
        if (this.headExtend.getValue()) {
            for (final Direction dir : Direction.values()) {
                if (dir != Direction.UP) {
                    if (dir != Direction.DOWN) {
                        final BlockPos trapPos = headPos.offset(dir);
                        if (this.canPlaceBlock(trapPos)) {
                            this.tryPlaceBlock(trapPos);
                        }
                    }
                }
            }
        }
        final Block block = BlockUtil.getBlock(headPos);
        if (block == Blocks.OBSIDIAN || block == Blocks.BEDROCK) {
            this.clickDown(headPos);
        }
        else if (this.canPlaceBlock(headPos)) {
            this.tryPlaceBlock(headPos);
        }
    }
    
    private boolean canPlaceBlock(final BlockPos pos) {
        if (pos == null) {
            return false;
        }
        if (!BlockUtil.canPlace(pos, 6.0, true)) {
            return false;
        }
        if (!SelfTrap.mc.world.getBlockState(pos).isAir()) {
            this.placedPackets.remove(pos);
            return false;
        }
        this.placedPackets.remove(pos);
        int maxBlocks = this.initialBurst ? 20 : (int)this.blocksPerTick.getValue();
        return this.blocksPlacedThisTick < maxBlocks;
    }
    
    private boolean faceVector(final Vec3d directionVec) {
        if (!this.shouldYawStep()) {
            stella.ROTATION.lookAt(directionVec);
            return true;
        }
        this.directionVec = directionVec;
        return stella.ROTATION.inFov(directionVec, this.fov.getValueFloat()) || !this.checkFov.getValue();
    }
    
    private void clickDown(final BlockPos pos) {
        if (pos == null) {
            return;
        }
        if (this.detectMining.getValue() && stella.BREAK.isMining(pos)) {
            return;
        }
        if (this.blocksPlacedThisTick >= this.blocksPerTick.getValue()) {
            return;
        }
        final int block = this.getBlock();
        if (block == -1) {
            return;
        }
        final Direction side = Direction.UP;
        final Vec3d directionVec = new Vec3d(pos.getX() + 0.5 + side.getVector().getX() * 0.5, pos.getY() + 0.5 + side.getVector().getY() * 0.5, pos.getZ() + 0.5 + side.getVector().getZ() * 0.5);
        if (!BlockUtil.clientCanPlace(pos, true) || !SelfTrap.airList.contains(pos)) {
            return;
        }
        if (this.rotate.getValue() && !this.faceVector(directionVec)) {
            return;
        }
        if (this.breakCrystal.getValue()) {
            CombatUtil.attackCrystal(pos, this.rotate.getValue(), this.eatPause.getValue() && !SelfTrap.mc.player.getActiveItem().getItem().getComponents().contains(DataComponentTypes.FOOD));
        }
        else if (BlockUtil.hasEntity(pos, false)) {
            return;
        }
        final int old = SelfTrap.mc.player.getInventory().selectedSlot;
        this.doSwap(block);
        BlockUtil.placedPos.add(pos);
        if (BlockUtil.allowAirPlace()) {
            BlockUtil.airPlace(pos, false, Hand.MAIN_HAND, this.packetPlace.getValue());
        }
        else {
            BlockUtil.clickBlock(pos.offset(side), side.getOpposite(), false, Hand.MAIN_HAND, this.packetPlace.getValue());
        }
        this.timer.reset();
        this.placedPackets.put(pos, System.currentTimeMillis());
        ++this.blocksPlacedThisTick;
        if (this.inventory.getValue()) {
            this.doSwap(block);
            EntityUtil.syncInventory();
        }
        else {
            this.doSwap(old);
        }
        if (this.rotate.getValue() && !this.shouldYawStep()) {
            stella.ROTATION.snapBack();
        }
        ++this.progress;
    }
    
    private void tryPlaceBlock(final BlockPos pos) {
        if (pos == null) {
            return;
        }
        if (this.detectMining.getValue() && stella.BREAK.isMining(pos)) {
            return;
        }
        if (this.blocksPlacedThisTick >= this.blocksPerTick.getValue()) {
            return;
        }
        final BlockPos self = EntityUtil.getPlayerPos(true);
        if (this.mineDownward.getValue() && Objects.equals(PacketMine.getBreakPos(), self.down()) && Objects.equals(PacketMine.getBreakPos(), pos)) {
            return;
        }
        final int block = this.getBlock();
        if (block == -1) {
            return;
        }
        final Direction side = BlockUtil.getPlaceSide(pos);
        if (side == null) {
            return;
        }
        final Vec3d directionVec = new Vec3d(pos.getX() + 0.5 + side.getVector().getX() * 0.5, pos.getY() + 0.5 + side.getVector().getY() * 0.5, pos.getZ() + 0.5 + side.getVector().getZ() * 0.5);
        if (!BlockUtil.canPlace(pos, 6.0, true)) {
            return;
        }
        if (this.rotate.getValue() && !this.faceVector(directionVec)) {
            return;
        }
        if (this.breakCrystal.getValue()) {
            CombatUtil.attackCrystal(pos, this.rotate.getValue(), this.eatPause.getValue());
        }
        else if (BlockUtil.hasEntity(pos, false)) {
            return;
        }
        final int old = SelfTrap.mc.player.getInventory().selectedSlot;
        this.doSwap(block);
        BlockUtil.placedPos.add(pos);
        if (BlockUtil.allowAirPlace()) {
            BlockUtil.airPlace(pos, false, Hand.MAIN_HAND, this.packetPlace.getValue());
        }
        else {
            BlockUtil.clickBlock(pos.offset(side), side.getOpposite(), false, Hand.MAIN_HAND, this.packetPlace.getValue());
        }
        this.timer.reset();
        this.placedPackets.put(pos, System.currentTimeMillis());
        ++this.blocksPlacedThisTick;
        if (this.inventory.getValue()) {
            this.doSwap(block);
            EntityUtil.syncInventory();
        }
        else {
            this.doSwap(old);
        }
        if (this.rotate.getValue() && !this.shouldYawStep()) {
            stella.ROTATION.snapBack();
        }
        ++this.progress;
    }
    
    private void doSwap(final int slot) {
        if (this.inventory.getValue()) {
            InventoryUtil.inventorySwap(slot, SelfTrap.mc.player.getInventory().selectedSlot);
        }
        else {
            InventoryUtil.switchToSlot(slot);
        }
    }
    
    private int getBlock() {
        if (this.inventory.getValue()) {
            if (InventoryUtil.findBlockInventorySlot(Blocks.OBSIDIAN) != -1 || !this.enderChest.getValue()) {
                return InventoryUtil.findBlockInventorySlot(Blocks.OBSIDIAN);
            }
            return InventoryUtil.findBlockInventorySlot(Blocks.ENDER_CHEST);
        }
        else {
            if (InventoryUtil.findBlock(Blocks.OBSIDIAN) != -1 || !this.enderChest.getValue()) {
                return InventoryUtil.findBlock(Blocks.OBSIDIAN);
            }
            return InventoryUtil.findBlock(Blocks.ENDER_CHEST);
        }
    }
    
    public BlockPos getHelperPos(final BlockPos pos) {
        for (final Direction i : Direction.values()) {
            if ((!this.detectMining.getValue() || !stella.BREAK.isMining(pos.offset(i))) && BlockUtil.isStrictDirection(pos.offset(i), i.getOpposite()) && BlockUtil.canPlace(pos.offset(i))) {
                return pos.offset(i);
            }
        }
        return null;
    }
    
    static {
        airList = new ArrayList<BlockPos>();
    }
    
    public enum Page
    {
        General, 
        Rotate, 
        Check, 
        GodMode;
    }
    
    public enum TimingMode
    {
        VANILLA, 
        SEQUENTIAL, 
        Pre, 
        Post;
    }
    
    public class SelfTrapTick
    {
        boolean pressed;
        
        public SelfTrapTick() {
            this.pressed = false;
        }
        
        @EventListener
        public void onTick(final ClientTickEvent event) {
            if (Module.nullCheck()) {
                return;
            }
            if ((SelfTrap.INSTANCE.timingMode.getValue() == TimingMode.Pre && event.isPost()) || (SelfTrap.INSTANCE.timingMode.getValue() == TimingMode.Post && event.isPre())) {
                return;
            }
            if (!SelfTrap.INSTANCE.headKey.isPressed()) {
                this.pressed = false;
                return;
            }
            if (this.pressed) {
                return;
            }
            this.pressed = true;
            SelfTrap.INSTANCE.directionVec = null;
            SelfTrap.INSTANCE.progress = 0;
            final BlockPos pos = EntityUtil.getPlayerPos(true);
            if (SelfTrap.INSTANCE.getBlock() == -1) {
                return;
            }
            if (!SelfTrap.INSTANCE.inAir.getValue() && !Wrapper.mc.player.isOnGround()) {
                return;
            }
            final Block block = BlockUtil.getBlock(pos.up(2));
            if (block == Blocks.OBSIDIAN || block == Blocks.BEDROCK) {
                SelfTrap.INSTANCE.clickDown(pos.up(2));
            }
            else {
                SelfTrap.INSTANCE.tryPlaceBlock(pos.up(2));
            }
        }
    }
}