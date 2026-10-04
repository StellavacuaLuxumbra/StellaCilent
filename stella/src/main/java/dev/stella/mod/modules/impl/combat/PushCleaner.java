package dev.stella.mod.modules.impl.combat;

import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.enums.*;
import dev.stella.mod.modules.settings.impl.*;
import dev.stella.*;
import dev.stella.api.events.impl.*;
import dev.stella.api.utils.world.*;
import dev.stella.api.utils.math.Timer;
import java.util.*;
import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.utils.player.*;
import dev.stella.api.utils.combat.*;
import dev.stella.mod.modules.impl.client.*;
import dev.stella.api.utils.math.ExplosionUtil;
import net.minecraft.block.*;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;

public class PushCleaner extends Module
{
    public static PushCleaner INSTANCE;
    private final EnumSetting<Page> page;
    private final SliderSetting placeRange;
    private final SliderSetting wallRange;
    private final SliderSetting maxSelfDmg;
    private final SliderSetting placeDelay;
    private final SliderSetting breakDelay;
    private final SliderSetting updateDelay;
    private final EnumSetting<SwapMode> autoSwap;
    private final EnumSetting<SwingSide> swingMode;
    private final BooleanSetting rotate;
    private final BooleanSetting onBreak;
    private final BooleanSetting yawStep;
    private final SliderSetting steps;
    private final BooleanSetting checkLook;
    private final SliderSetting fov;
    private final BooleanSetting eatingPause;
    private final BooleanSetting burOnly;
    private final BooleanSetting checkRedstone;
    private final BooleanSetting checkFeetPush;
    public static final Timer placeTimer;
    public final Timer lastBreakTimer;
    private final Timer delayTimer;
    private Vec3d directionVec;
    private int flag;
    
    public PushCleaner() {
        super("PushCleaner", Category.Combat);
        this.page = this.add(new EnumSetting<Page>("Page", Page.General));
        this.placeRange = this.add(new SliderSetting("PlaceRange", 6.0, 1.0, 10.0, () -> this.page.getValue() == Page.General));
        this.wallRange = this.add(new SliderSetting("WallRange", 6.0, 0.0, 6.0, () -> this.page.getValue() == Page.General));
        this.maxSelfDmg = this.add(new SliderSetting("MaxSelfDmg", 3.0, 0.0, 36.0, () -> this.page.getValue() == Page.General));
        this.placeDelay = this.add(new SliderSetting("PlaceDelay", 300.0, 0.0, 1000.0, () -> this.page.getValue() == Page.General));
        this.breakDelay = this.add(new SliderSetting("BreakDelay", 0.0, 0.0, 1000.0, () -> this.page.getValue() == Page.General));
        this.updateDelay = this.add(new SliderSetting("UpdateDelay", 250.0, 0.0, 1000.0, () -> this.page.getValue() == Page.General));
        this.autoSwap = this.add(new EnumSetting<SwapMode>("AutoSwap", SwapMode.Off, () -> this.page.getValue() == Page.General));
        this.swingMode = this.add(new EnumSetting<SwingSide>("Swing", SwingSide.Server, () -> this.page.getValue() == Page.General));
        this.rotate = this.add(new BooleanSetting("Rotate", true, () -> this.page.getValue() == Page.Rotate));
        this.onBreak = this.add(new BooleanSetting("OnBreak", false, () -> this.rotate.isOpen() && this.page.getValue() == Page.Rotate));
        this.yawStep = this.add(new BooleanSetting("YawStep", false, () -> this.rotate.isOpen() && this.page.getValue() == Page.Rotate));
        this.steps = this.add(new SliderSetting("Steps", 0.3, 0.1, 1.0, () -> this.rotate.isOpen() && this.yawStep.getValue() && this.page.getValue() == Page.Rotate));
        this.checkLook = this.add(new BooleanSetting("CheckLook", true, () -> this.rotate.isOpen() && this.yawStep.getValue() && this.page.getValue() == Page.Rotate));
        this.fov = this.add(new SliderSetting("Fov", 30.0, 0.0, 90.0, () -> this.rotate.isOpen() && this.yawStep.getValue() && this.checkLook.getValue() && this.page.getValue() == Page.Rotate));
        this.eatingPause = this.add(new BooleanSetting("EatingPause", true, () -> this.page.getValue() == Page.Check));
        this.burOnly = this.add(new BooleanSetting("BurOnly", true, () -> this.page.getValue() == Page.Check));
        this.checkRedstone = this.add(new BooleanSetting("CheckRedstone", true, () -> this.page.getValue() == Page.Check));
        this.checkFeetPush = this.add(new BooleanSetting("CheckFeetPush", true, () -> this.page.getValue() == Page.Check));
        this.lastBreakTimer = new Timer();
        this.delayTimer = new Timer();
        this.directionVec = null;
        this.flag = 0;
        this.setChinese("\u6e05\u9664\u6d3b\u585e");
        PushCleaner.INSTANCE = this;
    }
    
    @Override
    public String getInfo() {
        if (this.flag != 0) {
            return "PushCleaning...";
        }
        return null;
    }
    
    public boolean faceVector(final Vec3d directionVec) {
        if (!this.yawStep.getValue()) {
            stella.ROTATION.lookAt(directionVec);
            return true;
        }
        this.directionVec = directionVec;
        return stella.ROTATION.inFov(directionVec, this.fov.getValueFloat()) || !this.checkLook.getValue();
    }
    
    public List<BlockPos> getRedStonePos() {
        final BlockPosX pos1 = new BlockPosX(PushCleaner.mc.player.getX() + 0.3, PushCleaner.mc.player.getY() + 0.5, PushCleaner.mc.player.getZ() + 0.3);
        final BlockPosX pos2 = new BlockPosX(PushCleaner.mc.player.getX() - 0.3, PushCleaner.mc.player.getY() + 0.5, PushCleaner.mc.player.getZ() + 0.3);
        final BlockPosX pos3 = new BlockPosX(PushCleaner.mc.player.getX() + 0.3, PushCleaner.mc.player.getY() + 0.5, PushCleaner.mc.player.getZ() - 0.3);
        final BlockPosX pos4 = new BlockPosX(PushCleaner.mc.player.getX() - 0.3, PushCleaner.mc.player.getY() + 0.5, PushCleaner.mc.player.getZ() - 0.3);
        final BlockPos playerPos = EntityUtil.getPlayerPos(true);
        final ArrayList<BlockPos> blocks = new ArrayList<BlockPos>();
        for (final BlockPos burrow : new BlockPos[] { playerPos, pos1, pos2, pos3, pos4 }) {
            for (final Direction direction : Direction.values()) {
                if (direction != Direction.UP) {
                    if (direction != Direction.DOWN) {
                        final BlockPos surround = burrow.offset(direction);
                        if (!this.checkSelf(surround)) {
                            for (final int i : new int[] { 0, 1 }) {
                                if (i != 0 || this.checkFeetPush.getValue()) {
                                    final BlockPos piston = burrow.up(i).offset(direction);
                                    final Block block = PushCleaner.mc.world.getBlockState(piston).getBlock();
                                    if (block instanceof PistonBlock) {
                                        blocks.add(piston);
                                    }
                                    if (this.checkRedstone.getValue()) {
                                        for (final Direction facing : Direction.values()) {
                                            if (facing != Direction.UP) {
                                                if (facing != Direction.DOWN) {
                                                    final BlockPos redstone = piston.offset(facing);
                                                    if (!this.checkSelf(redstone)) {
                                                        if (!blocks.contains(redstone)) {
                                                            if (PushCleaner.mc.world.getBlockState(redstone).getBlock() == Blocks.REDSTONE_WIRE) {
                                                                blocks.add(redstone);
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (blocks.isEmpty()) {
            return null;
        }
        return blocks;
    }
    
    private boolean checkSelf(final BlockPos pos) {
        return PushCleaner.mc.player.getBoundingBox().intersects(new Box(pos));
    }
    
    @EventListener
    public void onUpdate(final UpdateEvent event) {
        if (nullCheck()) {
            return;
        }
        this.directionVec = null;
        this.flag = 0;
        if (this.burOnly.getValue() && !EntityUtil.isInsideBlock()) {
            return;
        }
        if (this.eatingPause.getValue() && PushCleaner.mc.player.isUsingItem()) {
            return;
        }
        if (!this.delayTimer.passedMs((double)(long)this.updateDelay.getValue())) {
            return;
        }
        this.delayTimer.reset();
        final List<BlockPos> redStonePos = this.getRedStonePos();
        if (redStonePos == null) {
            return;
        }
        if (EntityUtil.isInsideBlock() || !this.burOnly.getValue()) {
            for (final BlockPos pos : BlockUtil.getSphere((float)this.placeRange.getValue())) {
                if (pos.getY() - PushCleaner.mc.player.getY() > 2.0) {
                    continue;
                }
                if (!this.canTouch(pos)) {
                    continue;
                }
                if (!this.canPlaceCrystal(pos, true, false)) {
                    continue;
                }
                if (!this.canExplodeReach(pos, redStonePos.get(0), 6.0f)) {
                    continue;
                }
                final Vec3d crystalPos = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                final float selfDmg = ExplosionUtil.calculateDamage(crystalPos, (LivingEntity)PushCleaner.mc.player, (LivingEntity)PushCleaner.mc.player, 6.0f);
                if (selfDmg > this.maxSelfDmg.getValue()) {
                    continue;
                }
                this.doPlace(pos);
                this.doBreak(pos);
                this.flag = 1;
            }
        }
        redStonePos.clear();
    }
    
    private void doPlace(final BlockPos pos) {
        if (PushCleaner.mc.player.getMainHandStack().getItem() != Items.END_CRYSTAL && PushCleaner.mc.player.getOffHandStack().getItem() != Items.END_CRYSTAL && !this.findCrystal()) {
            return;
        }
        if (!this.canTouch(pos)) {
            return;
        }
        final BlockPos obsPos = pos;
        final Direction facing = BlockUtil.getClickSide(obsPos);
        if (facing == null) {
            return;
        }
        final Vec3d vec = Vec3d.ofCenter(obsPos).add(facing.getOffsetX() * 0.5, facing.getOffsetY() * 0.5, facing.getOffsetZ() * 0.5);
        if (this.rotate.getValue() && !this.onBreak.getValue() && !this.faceVector(vec)) {
            return;
        }
        if (!PushCleaner.placeTimer.passedMs((double)(long)this.placeDelay.getValue())) {
            return;
        }
        PushCleaner.placeTimer.reset();
        if (PushCleaner.mc.player.getMainHandStack().getItem() == Items.END_CRYSTAL || PushCleaner.mc.player.getOffHandStack().getItem() == Items.END_CRYSTAL) {
            this.placeCrystal(pos);
        }
        else if (this.findCrystal()) {
            final int old = PushCleaner.mc.player.getInventory().selectedSlot;
            final int crystal = this.getCrystal();
            if (crystal == -1) {
                return;
            }
            this.doSwap(crystal);
            this.placeCrystal(pos);
            if (this.autoSwap.getValue() == SwapMode.Silent) {
                this.doSwap(old);
            }
            else if (this.autoSwap.getValue() == SwapMode.Inventory) {
                this.doSwap(crystal);
                EntityUtil.syncInventory();
            }
        }
    }
    
    private boolean findCrystal() {
        return this.autoSwap.getValue() != SwapMode.Off && this.getCrystal() != -1;
    }
    
    private int getCrystal() {
        if (this.autoSwap.getValue() == SwapMode.Silent || this.autoSwap.getValue() == SwapMode.Normal) {
            return InventoryUtil.findItem(Items.END_CRYSTAL);
        }
        if (this.autoSwap.getValue() == SwapMode.Inventory) {
            return InventoryUtil.findItemInventorySlot(Items.END_CRYSTAL);
        }
        return -1;
    }
    
    private void doSwap(final int slot) {
        if (this.autoSwap.getValue() == SwapMode.Silent || this.autoSwap.getValue() == SwapMode.Normal) {
            InventoryUtil.switchToSlot(slot);
        }
        else if (this.autoSwap.getValue() == SwapMode.Inventory) {
            InventoryUtil.inventorySwap(slot, PushCleaner.mc.player.getInventory().selectedSlot);
        }
    }
    
    public void placeCrystal(final BlockPos pos) {
        final boolean offhand = PushCleaner.mc.player.getMainHandStack().getItem() == Items.END_CRYSTAL;
        final BlockPos obsPos = pos;
        final Direction facing = BlockUtil.getClickSide(obsPos);
        if (facing == null) {
            return;
        }
        EntityUtil.swingHand(offhand ? Hand.OFF_HAND : Hand.MAIN_HAND, this.swingMode.getValue());
        BlockUtil.placeBlock(pos, this.rotate.getValue(), false);
    }
    
    private void doBreak(final BlockPos pos) {
        this.lastBreakTimer.reset();
        if (!CombatUtil.breakTimer.passedMs((double)(long)this.breakDelay.getValue())) {
            return;
        }
        final Box crystalBox = new Box((double)pos.getX(), (double)pos.getY(), (double)pos.getZ(), (double)(pos.getX() + 1), (double)(pos.getY() + 2), (double)(pos.getZ() + 1));
        for (final Entity entity : PushCleaner.mc.world.getEntitiesByClass(EndCrystalEntity.class, crystalBox, e -> true)) {
            if (this.rotate.getValue() && !this.faceVector(entity.getPos().add(0.0, 0.25, 0.0))) {
                return;
            }
            CombatUtil.breakTimer.reset();
            CombatUtil.attackCrystal(pos, this.rotate.getValue(), true);
            final boolean offhand = PushCleaner.mc.player.getMainHandStack().getItem() == Items.END_CRYSTAL;
            EntityUtil.swingHand(offhand ? Hand.OFF_HAND : Hand.MAIN_HAND, this.swingMode.getValue());
        }
    }
    
    public boolean behindWall(final BlockPos pos) {
        final Vec3d testVec = ClientSetting.INSTANCE.lowVersion.getValue() ? new Vec3d(pos.getX() + 0.5, (double)pos.getY(), pos.getZ() + 0.5) : new Vec3d(pos.getX() + 0.5, pos.getY() + 1.7, pos.getZ() + 0.5);
        return !EntityUtil.canSee(pos, Direction.UP) && PushCleaner.mc.player.getEyePos().distanceTo(Vec3d.ofCenter(pos).add(0.0, -0.5, 0.0)) > this.wallRange.getValue();
    }
    
    private boolean canTouch(final BlockPos pos) {
        final Direction side = BlockUtil.getClickSideStrict(pos);
        if (side == null) {
            return false;
        }
        final Vec3d vec = new Vec3d(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
        return Vec3d.ofCenter(pos).add(vec).distanceTo(PushCleaner.mc.player.getEyePos()) <= this.placeRange.getValue();
    }
    
    public boolean canPlaceCrystal(final BlockPos pos, final boolean ignoreCrystal, final boolean ignoreItem) {
        final BlockPos obsPos = pos;
        final BlockPos boost = obsPos.up();
        final Block block = PushCleaner.mc.world.getBlockState(obsPos).getBlock();
        return (block == Blocks.OBSIDIAN || block == Blocks.BEDROCK) && BlockUtil.getClickSideStrict(obsPos) != null && !this.hasEntityBlockCrystal(boost, ignoreCrystal, ignoreItem) && !this.hasEntityBlockCrystal(boost.up(), ignoreCrystal, ignoreItem);
    }
    
    public boolean hasEntityBlockCrystal(final BlockPos pos, final boolean ignoreCrystal, final boolean ignoreItem) {
        final Box entityBox = new Box(pos);
        for (final Entity entity : PushCleaner.mc.world.getEntitiesByClass(Entity.class, entityBox, e -> true)) {
            if (!entity.isAlive()) {
                continue;
            }
            if (ignoreItem && entity instanceof EndCrystalEntity) {
                continue;
            }
            return !(entity instanceof EndCrystalEntity) || ignoreCrystal || true;
        }
        return false;
    }
    
    public boolean canExplodeReach(final BlockPos explosionCenter, final BlockPos targetBlock, final float power) {
        if (explosionCenter.getY() > targetBlock.getY()) {
            return false;
        }
        final Vec3d start = new Vec3d(explosionCenter.getX() + 0.5, explosionCenter.getY() + 0.5, explosionCenter.getZ() + 0.5);
        final Vec3d end = new Vec3d(targetBlock.getX() + 0.5, targetBlock.getY() + 0.5, targetBlock.getZ() + 0.5);
        final Vec3d direction = end.subtract(start).normalize();
        for (double distance = start.distanceTo(end), step = 0.5, d = 0.0; d < distance; d += step) {
            final Vec3d currentPos = start.add(direction.multiply(d));
            final BlockPos currentBlockPos = BlockPos.ofFloored(currentPos);
            final Block block = PushCleaner.mc.world.getBlockState(currentBlockPos).getBlock();
            if (!block.getDefaultState().isAir() && block.getDefaultState().getBlock().getBlastResistance() > power) {
                return false;
            }
        }
        return true;
    }
    
    @Override
    public void onEnable() {
        this.directionVec = null;
        this.lastBreakTimer.reset();
    }
    
    @Override
    public void onDisable() {
        this.directionVec = null;
    }
    
    static {
        placeTimer = new Timer();
    }
    
    public enum Page
    {
        General, 
        Rotate, 
        Check;
    }
    
    public enum SwapMode
    {
        Off, 
        Silent, 
        Normal, 
        Inventory;
    }
}