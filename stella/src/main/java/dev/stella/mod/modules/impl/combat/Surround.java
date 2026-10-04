package dev.stella.mod.modules.impl.combat;

import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.impl.*;
import dev.stella.api.utils.math.Timer;
import dev.stella.*;
import dev.stella.api.events.eventbus.EventListener;
import dev.stella.core.impl.*;
import dev.stella.mod.modules.impl.exploit.*;
import dev.stella.api.utils.combat.*;
import dev.stella.api.utils.world.*;
import dev.stella.mod.modules.impl.movement.*;
import dev.stella.mod.modules.impl.movement.ElytraFly;
import net.minecraft.component.DataComponentTypes;
import dev.stella.api.events.impl.*;
import dev.stella.mod.modules.impl.player.*;
import java.util.*;
import dev.stella.api.utils.player.*;
import net.minecraft.*;
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
import dev.stella.api.utils.math.Rotation;
import dev.stella.mod.modules.Module;
import net.minecraft.entity.Entity;

public class Surround extends Module
{
    public static Surround INSTANCE;
    public final EnumSetting<Page> page;
    public final SliderSetting placeDelay;
    private final BooleanSetting mineDownward;
    public final BooleanSetting extend;
    public final BooleanSetting onlySelf;
    private final BooleanSetting coverHead;
    private final BooleanSetting support;
    private final BooleanSetting mineExtend;
    private final EnumSetting<TimingMode> timingMode;
    private final BooleanSetting prePlaceExplosion;
    private final BooleanSetting prePlaceTick;
    private final SliderSetting blocksPerTick;
    private final SliderSetting shiftDelay;
    private final BooleanSetting inAir;
    private final BooleanSetting detectMining;
    private final BooleanSetting usingPause;
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
    private final BooleanSetting allowEating;
    private final BooleanSetting center;
    private final BooleanSetting inventory;
    private final BooleanSetting enderChest;
    public Vec3d directionVec;
    double startX;
    double startY;
    double startZ;
    int progress;
    private boolean shouldCenter;
    private List<BlockPos> surroundBlocks;
    private Map<BlockPos, Long> placedPackets;
    private int blocksPlacedThisTick;
    private boolean initialBurst;
    private long enableTime;
    
    public Surround() {
        super("Surround", "Surrounds you with Obsidian", Category.Combat);
        this.page = this.add(new EnumSetting<Page>("Page", Page.General));
        this.placeDelay = this.add(new SliderSetting("PlaceDelay", 50, 0, 500, () -> this.page.is(Page.General)));
        this.mineDownward = this.add(new BooleanSetting("MineDownward", false, () -> this.page.is(Page.General)));
        this.extend = this.add(new BooleanSetting("Extend", true, () -> this.page.is(Page.General))).setParent();
        this.onlySelf = this.add(new BooleanSetting("OnlySelf", false, () -> this.page.is(Page.General) && this.extend.isOpen()));
        this.coverHead = this.add(new BooleanSetting("CoverHead", false, () -> this.page.is(Page.General)));
        this.support = this.add(new BooleanSetting("Support", false, () -> this.page.is(Page.General)));
        this.mineExtend = this.add(new BooleanSetting("MineExtend", false, () -> this.page.is(Page.General)));
        this.timingMode = this.add(new EnumSetting<TimingMode>("TimingMode", TimingMode.VANILLA, () -> this.page.is(Page.General)));
        this.prePlaceExplosion = this.add(new BooleanSetting("PrePlace-Explosion", false, () -> this.page.is(Page.General) && this.timingMode.getValue() == TimingMode.SEQUENTIAL));
        this.prePlaceTick = this.add(new BooleanSetting("PrePlace-Tick", false, () -> this.page.is(Page.General) && this.timingMode.getValue() == TimingMode.SEQUENTIAL));
        this.blocksPerTick = this.add(new SliderSetting("BlocksPerTick", 2, 1, 20, () -> this.page.is(Page.General)));
        this.shiftDelay = this.add(new SliderSetting("ShiftDelay", 0.0, 0.0, 5.0, 0.10000000149011612, () -> this.page.is(Page.General)));
        this.inAir = this.add(new BooleanSetting("InAir", true, () -> this.page.is(Page.Check)));
        this.detectMining = this.add(new BooleanSetting("DetectMining", false, () -> this.page.is(Page.Check)));
        this.usingPause = this.add(new BooleanSetting("UsingPause", true, () -> this.page.is(Page.Check)));
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
        this.eatPause = this.add(new BooleanSetting("EatingPause", true, () -> this.page.is(Page.General) && this.breakCrystal.isOpen()).setParent());
        this.allowEating = this.add(new BooleanSetting("AllowEating", false, () -> this.page.is(Page.General) && this.breakCrystal.isOpen() && this.eatPause.isOpen()));
        this.center = this.add(new BooleanSetting("Center", true, () -> this.page.is(Page.General)));
        this.inventory = this.add(new BooleanSetting("InventorySwap", true, () -> this.page.is(Page.General)));
        this.enderChest = this.add(new BooleanSetting("EnderChest", true, () -> this.page.is(Page.General)));
        this.directionVec = null;
        this.startX = 0.0;
        this.startY = 0.0;
        this.startZ = 0.0;
        this.progress = 0;
        this.shouldCenter = true;
        this.surroundBlocks = new ArrayList<BlockPos>();
        this.placedPackets = new HashMap<BlockPos, Long>();
        this.blocksPlacedThisTick = 0;
        this.setChinese("\u56f4\u811a");
        Surround.INSTANCE = this;
    }
    
    public static boolean selfIntersectPos(final BlockPos pos) {
        return Surround.mc.player.getBoundingBox().intersects(new Box(pos));
    }
    
    public static boolean otherIntersectPos(final BlockPos pos) {
        for (final PlayerEntity player : stella.THREAD.getPlayers()) {
            if (!player.getBoundingBox().intersects(new Box(pos))) {
                continue;
            }
            return true;
        }
        return false;
    }
    
    public static Rotation getRotationTo(final Vec3d posFrom, final Vec3d posTo) {
        final Vec3d vec3d = posTo.subtract(posFrom);
        return getRotationFromVec(vec3d);
    }
    
    private static Rotation getRotationFromVec(final Vec3d vec) {
        final double d = vec.x;
        double d2 = vec.z;
        final double xz = Math.hypot(d, d2);
        d2 = vec.z;
        final double d3 = vec.x;
        final double yaw = normalizeAngle(Math.toDegrees(Math.atan2(d2, d3)) - 90.0);
        final double pitch = normalizeAngle(Math.toDegrees(-Math.atan2(vec.y, xz)));
        return new Rotation((float)yaw, (float)pitch);
    }
    
    private static double normalizeAngle(final double angleIn) {
        double angle = angleIn;
        angle %= 360.0;
        if (angle >= 180.0) {
            angle -= 360.0;
        }
        if (angle < -180.0) {
            angle += 360.0;
        }
        return angle;
    }
    
    @EventListener
    public void onRotate(final RotationEvent event) {
        if (this.directionVec != null && this.rotate.getValue() && this.shouldYawStep()) {
            event.setTarget(this.directionVec, this.steps.getValueFloat(), this.priority.getValueFloat());
        }
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
        if (!MovementUtil.isMoving() && !Surround.mc.options.sneakKey.isPressed()) {
            this.startX = Surround.mc.player.getX();
            this.startY = Surround.mc.player.getY();
            this.startZ = Surround.mc.player.getZ();
        }
        final double distanceToStart = MathHelper.abs((float)Surround.mc.player.squaredDistanceTo(this.startX, this.startY, this.startZ));
        if (this.getBlock() == -1) {
            CommandManager.sendMessageId("§4No block found", this.hashCode() - 1);
            this.disable();
            return;
        }
        if ((this.moveDisable.getValue() && distanceToStart > 1.0) || (this.jumpDisable.getValue() && Surround.mc.player.input.jumping)) {
            this.disable();
            return;
        }
        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) {
            return;
        }
        if (this.usingPause.getValue() && Surround.mc.player.isUsingItem() && !(Surround.mc.player.getActiveItem().getComponents().contains(DataComponentTypes.FOOD) && this.allowEating.getValue())) {
            return;
        }
        if (!this.inAir.getValue() && !Surround.mc.player.isOnGround()) {
            return;
        }
        this.blocksPlacedThisTick = 0;
        this.surroundBlocks = this.getSurroundBlocks();
        if (this.surroundBlocks.isEmpty()) {
            return;
        }
        if (this.breakCrystal.getValue()) {
            this.attackBlockingCrystals();
        }
        this.doSurround(BlockPos.ofFloored(Surround.mc.player.getX(), Surround.mc.player.getY(), Surround.mc.player.getZ()));
        this.doSurround(BlockPos.ofFloored(Surround.mc.player.getX(), Surround.mc.player.getY() + 0.8, Surround.mc.player.getZ()));
        if (this.coverHead.getValue()) {
            final BlockPos headPos = EntityUtil.getPlayerPos(true).up(2);
            this.tryPlaceBlock(headPos);
        }
    }
    
    private List<BlockPos> getSurroundBlocks() {
        final List<BlockPos> surroundBlocks = new ArrayList<BlockPos>();
        final BlockPos playerPos = EntityUtil.getPlayerPos(true);
        final List<BlockPos> playerBlocks = new ArrayList<BlockPos>();
        if (this.extend.getValue()) {
            final double minX = Math.floor(Surround.mc.player.getBoundingBox().minX);
            final double minY = Math.floor(Surround.mc.player.getBoundingBox().minY);
            final double minZ = Math.floor(Surround.mc.player.getBoundingBox().minZ);
            final double maxX = Math.ceil(Surround.mc.player.getBoundingBox().maxX);
            final double maxY = Math.ceil(Surround.mc.player.getBoundingBox().maxY);
            final double maxZ = Math.ceil(Surround.mc.player.getBoundingBox().maxZ);
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
                    if (!surroundBlocks.contains(pos2)) {
                        if (!playerBlocks.contains(pos2)) {
                            surroundBlocks.add(pos2);
                        }
                    }
                }
            }
        }
        if (this.mineExtend.getValue()) {
            for (final BlockPos surroundPos : new ArrayList<BlockPos>(surroundBlocks)) {
                if (!stella.BREAK.isMining(surroundPos)) {
                    continue;
                }
                for (final Direction direction : Direction.values()) {
                    if (direction != Direction.UP) {
                        if (direction != Direction.DOWN) {
                            final BlockPos blockerPos = surroundPos.offset(direction);
                            if (!playerBlocks.contains(blockerPos)) {
                                if (!stella.BREAK.isMining(blockerPos)) {
                                    surroundBlocks.add(blockerPos);
                                }
                            }
                        }
                    }
                }
            }
        }
        return surroundBlocks;
    }
    
    private void attackBlockingCrystals() {
        for (final BlockPos pos : this.surroundBlocks) {
            final Entity crystal = (Entity)Surround.mc.world.getEntitiesByClass(EndCrystalEntity.class, new Box(pos), e -> true).stream().findFirst().orElse(null);
            if (crystal != null) {
                CombatUtil.attackCrystal(pos, this.rotate.getValue(), (this.eatPause.getValue() && !Surround.mc.player.getActiveItem().getComponents().contains(DataComponentTypes.FOOD)) || (!this.eatPause.getValue()));
            }
        }
    }
    
    public void doSurround(final BlockPos pos) {
        for (final Direction i : Direction.values()) {
            if (i != Direction.DOWN) {
                BlockPos offsetPos = pos.offset(i);
                boolean needsPlace = false;
                if (BlockUtil.getPlaceSide(offsetPos) != null) {
                    needsPlace = true;
                }
                else if (BlockUtil.canReplace(offsetPos)) {
                    if (this.support.getValue()) {
                        final BlockPos supportPos = this.getHelperPos(offsetPos);
                        if (supportPos != null) {
                            this.tryPlaceBlock(supportPos);
                        }
                    }
                    offsetPos = this.getHelperPos(offsetPos);
                    if (offsetPos != null) {
                        needsPlace = true;
                    }
                }
                if (needsPlace) {
                    if ((!selfIntersectPos(offsetPos) && (this.onlySelf.getValue() || !otherIntersectPos(offsetPos))) || !this.extend.getValue()) {
                        this.tryPlaceBlock(offsetPos);
                    }
                    if (this.extend.getValue()) {
                        for (final Direction i2 : Direction.values()) {
                            if (i2 != Direction.DOWN) {
                                final BlockPos offsetPos2 = offsetPos.offset(i2);
                                if (selfIntersectPos(offsetPos2) || (!this.onlySelf.getValue() && otherIntersectPos(offsetPos2))) {
                                    for (final Direction i3 : Direction.values()) {
                                        if (i3 != Direction.DOWN) {
                                            if (this.canPlaceBlock(offsetPos2)) {
                                                this.tryPlaceBlock(offsetPos2);
                                            }
                                            final BlockPos offsetPos3 = offsetPos2.offset(i3);
                                            final BlockPos placePos = (BlockUtil.getPlaceSide(offsetPos3) != null || !BlockUtil.canReplace(offsetPos3)) ? offsetPos3 : this.getHelperPos(offsetPos3);
                                            if (placePos != null && this.canPlaceBlock(placePos)) {
                                                this.tryPlaceBlock(placePos);
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
    
    private boolean canPlaceBlock(final BlockPos pos) {
        if (pos == null) {
            return false;
        }
        if (!BlockUtil.canPlace(pos, 6.0, true)) {
            return false;
        }
        final Long placedTime = this.placedPackets.get(pos);
        int maxBlocks = this.initialBurst ? 20 : (int)this.blocksPerTick.getValue();
        return (placedTime == null || this.shiftDelay.getValueFloat() <= 0.0f || System.currentTimeMillis() - placedTime >= (long)(this.shiftDelay.getValueFloat() * 50.0f)) && this.blocksPlacedThisTick < maxBlocks;
    }
    
    @Override
    public void onEnable() {
        if (nullCheck()) {
            if (this.moveDisable.getValue() || this.jumpDisable.getValue()) {
                this.disable();
            }
            return;
        }
        this.startX = Surround.mc.player.getX();
        this.startY = Surround.mc.player.getY();
        this.startZ = Surround.mc.player.getZ();
        this.shouldCenter = true;
        this.surroundBlocks.clear();
        this.placedPackets.clear();
        this.initialBurst = true;
        this.enableTime = System.currentTimeMillis();
    }
    
    @Override
    public void onDisable() {
        this.surroundBlocks.clear();
        this.placedPackets.clear();
    }
    
    private boolean shouldYawStep() {
        // 检查是否使用ElytraFly飞行
        boolean isElytraFlying = ElytraFly.INSTANCE != null && ElytraFly.INSTANCE.isOn() && ElytraFly.INSTANCE.isFallFlying();
        // 检查是否使用EFly飞行
        boolean isEFlyFlying = EFly.INSTANCE != null && EFly.INSTANCE.isOn() && EFly.INSTANCE.isFallFlying();
        
        return (this.whenElytra.getValue() || (!Surround.mc.player.isFallFlying() && !isElytraFlying && !isEFlyFlying)) && this.yawStep.getValue() && !Velocity.INSTANCE.noRotation();
    }
    
    @EventListener(priority = -1)
    public void onMove(final MoveEvent event) {
        if (Module.nullCheck() || !this.center.getValue() || Surround.mc.player.isFallFlying()) {
            return;
        }
        final BlockPos blockPos = EntityUtil.getPlayerPos(true);
        if (Surround.mc.player.getX() - blockPos.getX() - 0.5 <= 0.2 && Surround.mc.player.getX() - blockPos.getX() - 0.5 >= -0.2 && Surround.mc.player.getZ() - blockPos.getZ() - 0.5 <= 0.2 && Surround.mc.player.getZ() - 0.5 - blockPos.getZ() >= -0.2) {
            if (this.shouldCenter && (Surround.mc.player.isOnGround() || MovementUtil.isMoving())) {
                event.setX(0.0);
                event.setZ(0.0);
                this.shouldCenter = false;
            }
        }
        else if (this.shouldCenter) {
            final Vec3d centerPos = EntityUtil.getPlayerPos(true).toCenterPos();
            final float rotation = getRotationTo(Surround.mc.player.getPos(), centerPos).getYaw();
            final float yawRad = rotation / 180.0f * 3.1415927f;
            final double dist = Surround.mc.player.getPos().distanceTo(new Vec3d(centerPos.x, Surround.mc.player.getY(), centerPos.z));
            final double cappedSpeed = Math.min(0.2873, dist);
            final double x = -(float)Math.sin(yawRad) * cappedSpeed;
            final double z = (float)Math.cos(yawRad) * cappedSpeed;
            event.setX(x);
            event.setZ(z);
        }
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
            CombatUtil.attackCrystal(pos, this.rotate.getValue(), this.eatPause.getValue() && !Surround.mc.player.getActiveItem().getItem().getComponents().contains(DataComponentTypes.FOOD));
        }
        else if (BlockUtil.hasEntity(pos, false)) {
            return;
        }
        final int old = Surround.mc.player.getInventory().selectedSlot;
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
    
    private boolean faceVector(final Vec3d directionVec) {
        if (!this.shouldYawStep()) {
            stella.ROTATION.lookAt(directionVec);
            return true;
        }
        this.directionVec = directionVec;
        return stella.ROTATION.inFov(directionVec, this.fov.getValueFloat()) || !this.checkFov.getValue();
    }
    
    private void doSwap(final int slot) {
        if (this.inventory.getValue()) {
            InventoryUtil.inventorySwap(slot, Surround.mc.player.getInventory().selectedSlot);
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
    
    public enum Page
    {
        General, 
        Rotate, 
        Check;
    }
    
    public enum TimingMode
    {
        VANILLA, 
        SEQUENTIAL, 
        Pre, 
        Post;
    }
}