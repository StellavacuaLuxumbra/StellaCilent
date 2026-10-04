package dev.stella.mod.modules.impl.combat;

import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.impl.*;
import dev.stella.api.utils.math.Timer;
import dev.stella.api.utils.world.*;
import dev.stella.api.events.impl.*;
import dev.stella.api.utils.combat.*;
import dev.stella.api.events.eventbus.EventListener;
import java.util.*;
import dev.stella.*;
import dev.stella.api.utils.player.*;
import dev.stella.mod.modules.settings.enums.*;
import dev.stella.mod.modules.impl.client.*;
import net.minecraft.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.Hand;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.item.Item;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;

public class PistonCrystal extends Module
{
    public static PistonCrystal INSTANCE;
    private final BooleanSetting rotate;
    private final BooleanSetting pistonPacket;
    private final BooleanSetting noEating;
    private final BooleanSetting eatingBreak;
    private final SliderSetting placeRange;
    private final SliderSetting range;
    private final BooleanSetting fire;
    private final BooleanSetting switchPos;
    private final BooleanSetting onlyGround;
    private final BooleanSetting onlyStatic;
    private final SliderSetting updateDelay;
    private final SliderSetting posUpdateDelay;
    private final SliderSetting stageSetting;
    private final SliderSetting pistonStage;
    private final SliderSetting pistonMaxStage;
    private final SliderSetting powerStage;
    private final SliderSetting powerMaxStage;
    private final SliderSetting crystalStage;
    private final SliderSetting crystalMaxStage;
    private final SliderSetting fireStage;
    private final SliderSetting fireMaxStage;
    private final BooleanSetting inventory;
    private final BooleanSetting debug;
    private final BooleanSetting cleaner;
    private final SliderSetting cleanerDelay;
    private final BooleanSetting cleanerRotate;
    private final BooleanSetting cleanerPacket;
    private final BooleanSetting pistonCleaner;
    private final SliderSetting pistonCleanThreshold;
    private final SliderSetting pistonCleanRange;
    private final Timer timer;
    private final Timer crystalTimer;
    private final Timer cleanerTimer;
    public BlockPos bestPos;
    public BlockPos bestOPos;
    public Direction bestFacing;
    public double getDistance;
    public boolean getPos;
    public int stage;
    private PlayerEntity target;
    private boolean isPiston;
    private BlockPos lastCleanPos;
    private List<BlockPos> cleanQueue;
    
    public PistonCrystal() {
        super("PistonCrystal", Category.Combat);
        this.rotate = this.add(new BooleanSetting("Rotate", false));
        this.pistonPacket = this.add(new BooleanSetting("PistonPacket", false));
        this.noEating = this.add(new BooleanSetting("NoEating", true));
        this.eatingBreak = this.add(new BooleanSetting("EatingBreak", false));
        this.placeRange = this.add(new SliderSetting("PlaceRange", 5.0, 1.0, 8.0));
        this.range = this.add(new SliderSetting("Range", 4.0, 1.0, 8.0));
        this.fire = this.add(new BooleanSetting("Fire", true));
        this.switchPos = this.add(new BooleanSetting("Switch", false));
        this.onlyGround = this.add(new BooleanSetting("SelfGround", true));
        this.onlyStatic = this.add(new BooleanSetting("MovingPause", true));
        this.updateDelay = this.add(new SliderSetting("PlaceDelay", 100, 0, 500));
        this.posUpdateDelay = this.add(new SliderSetting("PosUpdateDelay", 500, 0, 1000));
        this.stageSetting = this.add(new SliderSetting("Stage", 4, 1, 10));
        this.pistonStage = this.add(new SliderSetting("PistonStage", 1, 1, 10));
        this.pistonMaxStage = this.add(new SliderSetting("PistonMaxStage", 1, 1, 10));
        this.powerStage = this.add(new SliderSetting("PowerStage", 3, 1, 10));
        this.powerMaxStage = this.add(new SliderSetting("PowerMaxStage", 3, 1, 10));
        this.crystalStage = this.add(new SliderSetting("CrystalStage", 4, 1, 10));
        this.crystalMaxStage = this.add(new SliderSetting("CrystalMaxStage", 4, 1, 10));
        this.fireStage = this.add(new SliderSetting("FireStage", 2, 1, 10));
        this.fireMaxStage = this.add(new SliderSetting("FireMaxStage", 2, 1, 10));
        this.inventory = this.add(new BooleanSetting("InventorySwap", true));
        this.debug = this.add(new BooleanSetting("Debug", false));
        this.cleaner = this.add(new BooleanSetting("Cleaner", false));
        this.cleanerDelay = this.add(new SliderSetting("CleanerDelay", 100, 0, 500));
        this.cleanerRotate = this.add(new BooleanSetting("CleanerRotate", true));
        this.cleanerPacket = this.add(new BooleanSetting("CleanerPacket", false));
        this.pistonCleaner = this.add(new BooleanSetting("PistonCleaner", false).setParent());
        final String name = "PistonThreshold";
        final int value = 3;
        final int min = 1;
        final int max = 10;
        final BooleanSetting pistonCleaner = this.pistonCleaner;
        Objects.requireNonNull(pistonCleaner);
        this.pistonCleanThreshold = this.add(new SliderSetting(name, value, min, max, pistonCleaner::isOpen));
        final String name2 = "PistonCleanRange";
        final double value2 = 6.0;
        final double min2 = 1.0;
        final double max2 = 10.0;
        final BooleanSetting pistonCleaner2 = this.pistonCleaner;
        Objects.requireNonNull(pistonCleaner2);
        this.pistonCleanRange = this.add(new SliderSetting(name2, value2, min2, max2, pistonCleaner2::isOpen));
        this.timer = new Timer();
        this.crystalTimer = new Timer();
        this.cleanerTimer = new Timer();
        this.bestPos = null;
        this.bestOPos = null;
        this.bestFacing = null;
        this.getDistance = 100.0;
        this.getPos = false;
        this.stage = 1;
        this.target = null;
        this.isPiston = false;
        this.lastCleanPos = null;
        this.cleanQueue = new ArrayList<BlockPos>();
        this.setChinese("\u6d3b\u585e\u6c34\u6676");
        PistonCrystal.INSTANCE = this;
    }
    
    private static boolean canFire(final BlockPos pos) {
        return !BlockUtil.canReplace(pos.down()) && PistonCrystal.mc.world.isAir(pos) && BlockUtil.canClick(pos.up()) && BlockUtil.isStrictDirection(pos.down(), Direction.DOWN);
    }
    
    public void onTick() {
        if (this.pistonStage.getValue() > this.stageSetting.getValue()) {
            this.pistonStage.setValue(this.stageSetting.getValue());
        }
        if (this.fireStage.getValue() > this.stageSetting.getValue()) {
            this.fireStage.setValue(this.stageSetting.getValue());
        }
        if (this.powerStage.getValue() > this.stageSetting.getValue()) {
            this.powerStage.setValue(this.stageSetting.getValue());
        }
        if (this.crystalStage.getValue() > this.stageSetting.getValue()) {
            this.crystalStage.setValue(this.stageSetting.getValue());
        }
        if (this.pistonMaxStage.getValue() > this.stageSetting.getValue()) {
            this.pistonMaxStage.setValue(this.stageSetting.getValue());
        }
        if (this.fireMaxStage.getValue() > this.stageSetting.getValue()) {
            this.fireMaxStage.setValue(this.stageSetting.getValue());
        }
        if (this.powerMaxStage.getValue() > this.stageSetting.getValue()) {
            this.powerMaxStage.setValue(this.stageSetting.getValue());
        }
        if (this.crystalMaxStage.getValue() > this.stageSetting.getValue()) {
            this.crystalMaxStage.setValue(this.stageSetting.getValue());
        }
        if (this.crystalMaxStage.getValue() < this.crystalStage.getValue()) {
            this.crystalStage.setValue(this.crystalMaxStage.getValue());
        }
        if (this.powerMaxStage.getValue() < this.powerStage.getValue()) {
            this.powerStage.setValue(this.powerMaxStage.getValue());
        }
        if (this.pistonMaxStage.getValue() < this.pistonStage.getValue()) {
            this.pistonStage.setValue(this.pistonMaxStage.getValue());
        }
        if (this.fireMaxStage.getValue() < this.fireStage.getValue()) {
            this.fireStage.setValue(this.fireMaxStage.getValue());
        }
    }
    
    @EventListener
    public void onUpdate(final UpdateEvent event) {
        this.onTick();
        this.target = CombatUtil.getClosestEnemy(this.range.getValue());
        if (this.target == null) {
            return;
        }
        if (this.noEating.getValue() && PistonCrystal.mc.player.isUsingItem()) {
            return;
        }
        if (this.check(this.onlyStatic.getValue(), !PistonCrystal.mc.player.isOnGround(), this.onlyGround.getValue())) {
            return;
        }
        final BlockPos pos = EntityUtil.getEntityPos((Entity)this.target, true);
        if (!PistonCrystal.mc.player.isUsingItem() || this.eatingBreak.getValue()) {
            if (this.checkCrystal(pos.up(0))) {
                CombatUtil.attackCrystal(pos.up(0), this.rotate.getValue(), true);
            }
            if (this.checkCrystal(pos.up(1))) {
                CombatUtil.attackCrystal(pos.up(1), this.rotate.getValue(), true);
            }
            if (this.checkCrystal(pos.up(2))) {
                CombatUtil.attackCrystal(pos.up(2), this.rotate.getValue(), true);
            }
        }
        if (this.bestPos != null && PistonCrystal.mc.world.getBlockState(this.bestPos).getBlock() instanceof PistonBlock) {
            this.isPiston = true;
        }
        else if (this.isPiston) {
            this.isPiston = false;
            this.crystalTimer.reset();
            this.bestPos = null;
        }
        if (this.crystalTimer.passedMs(this.posUpdateDelay.getValueInt())) {
            this.stage = 0;
            this.getDistance = 100.0;
            this.getPos = false;
            this.getBestPos(pos.up(2));
            this.getBestPos(pos.up());
        }
        if (!this.timer.passedMs(this.updateDelay.getValueInt())) {
            return;
        }
        if (this.getPos && this.bestPos != null) {
            this.timer.reset();
            if (this.debug.getValue()) {
                this.sendMessage("[Debug] PistonPos:" + String.valueOf(this.bestPos) + " Facing:" + String.valueOf(this.bestFacing) + " CrystalPos:" + String.valueOf(this.bestOPos.offset(this.bestFacing)));
            }
            this.doPistonAura(this.bestPos, this.bestFacing, this.bestOPos);
        }
        if (this.cleaner.getValue()) {
            this.doClean();
        }
    }
    
    public boolean check(final boolean onlyStatic, final boolean onGround, final boolean onlyGround) {
        return (MovementUtil.isMoving() && onlyStatic) || (onGround && onlyGround) || this.findBlock(Blocks.REDSTONE_BLOCK) == -1 || this.findClass(PistonBlock.class) == -1 || this.findItem(Items.END_CRYSTAL) == -1;
    }
    
    private boolean checkCrystal(final BlockPos pos) {
        for (final Entity entity : BlockUtil.getEntities(new Box(pos))) {
            if (entity instanceof EndCrystalEntity) {
                final float damage;
                if ((damage = AutoCrystal.INSTANCE.calculateDamage(entity.getPos(), this.target, this.target)) <= 7.0f) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }
    
    private boolean checkCrystal2(final BlockPos pos) {
        for (final Entity entity : BlockUtil.getEntities(new Box(pos))) {
            if (entity instanceof EndCrystalEntity) {
                if (!EntityUtil.getEntityPos(entity).equals((Object)pos)) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }
    
    @Override
    public String getInfo() {
        if (this.target != null) {
            return this.target.getName().getString();
        }
        return null;
    }
    
    private void getBestPos(final BlockPos pos) {
        for (final Direction i : Direction.values()) {
            if (i != Direction.UP) {
                if (i != Direction.DOWN) {
                    this.getPos(pos, i);
                }
            }
        }
    }
    
    private void getPos(final BlockPos pos, final Direction i) {
        if (!BlockUtil.canPlaceCrystal(pos.offset(i)) && !this.checkCrystal2(pos.offset(i))) {
            return;
        }
        this.getPos(pos.offset(i, 3), i, pos);
        this.getPos(pos.offset(i, 3).up(), i, pos);
        final int offsetX = pos.offset(i).getX() - pos.getX();
        final int offsetZ = pos.offset(i).getZ() - pos.getZ();
        this.getPos(pos.offset(i, 3).add(offsetZ, 0, offsetX), i, pos);
        this.getPos(pos.offset(i, 3).add(-offsetZ, 0, -offsetX), i, pos);
        this.getPos(pos.offset(i, 3).add(offsetZ, 1, offsetX), i, pos);
        this.getPos(pos.offset(i, 3).add(-offsetZ, 1, -offsetX), i, pos);
        this.getPos(pos.offset(i, 2), i, pos);
        this.getPos(pos.offset(i, 2).up(), i, pos);
        this.getPos(pos.offset(i, 2).add(offsetZ, 0, offsetX), i, pos);
        this.getPos(pos.offset(i, 2).add(-offsetZ, 0, -offsetX), i, pos);
        this.getPos(pos.offset(i, 2).add(offsetZ, 1, offsetX), i, pos);
        this.getPos(pos.offset(i, 2).add(-offsetZ, 1, -offsetX), i, pos);
    }
    
    private void getPos(final BlockPos pos, final Direction facing, final BlockPos oPos) {
        if (this.switchPos.getValue() && this.bestPos != null && this.bestPos.equals((Object)pos) && PistonCrystal.mc.world.isAir(this.bestPos)) {
            return;
        }
        if (!BlockUtil.canPlace(pos, this.placeRange.getValue()) && !(this.getBlock(pos) instanceof PistonBlock)) {
            return;
        }
        if (this.findClass(PistonBlock.class) == -1) {
            return;
        }
        if (ClientSetting.INSTANCE.lowVersion.getValue() && !(this.getBlock(pos) instanceof PistonBlock) && (PistonCrystal.mc.player.getY() - pos.getY() <= -2.0 || PistonCrystal.mc.player.getY() - pos.getY() >= 3.0) && BlockUtil.distanceToXZ(pos.getX() + 0.5, pos.getZ() + 0.5) < 2.6) {
            return;
        }
        if (!PistonCrystal.mc.world.isAir(pos.offset(facing, -1)) || PistonCrystal.mc.world.getBlockState(pos.offset(facing, -1)).getBlock() == Blocks.WATER || (this.getBlock(pos.offset(facing.getOpposite())) == Blocks.OBSIDIAN && !this.checkCrystal2(pos.offset(facing.getOpposite())))) {
            return;
        }
        if (!BlockUtil.canPlace(pos, this.placeRange.getValue()) && !this.isPiston(pos, facing)) {
            return;
        }
        if (MathHelper.abs((float)PistonCrystal.mc.player.getEyePos().distanceTo(pos.toCenterPos())) >= this.getDistance && this.bestPos != null) {
            return;
        }
        this.bestPos = pos;
        this.bestOPos = oPos;
        this.bestFacing = facing;
        this.getDistance = MathHelper.abs((float)PistonCrystal.mc.player.getEyePos().distanceTo(pos.toCenterPos()));
        this.getPos = true;
        this.crystalTimer.reset();
    }
    
    private void doPistonAura(final BlockPos pos, final Direction facing, final BlockPos oPos) {
        if (this.stage >= this.stageSetting.getValue()) {
            this.stage = 0;
        }
        ++this.stage;
        if (PistonCrystal.mc.world.isAir(pos)) {
            if (!BlockUtil.canPlace(pos)) {
                return;
            }
            if (this.stage >= this.pistonStage.getValue() && this.stage <= this.pistonMaxStage.getValue()) {
                final Direction side = BlockUtil.getPlaceSide(pos);
                if (side == null) {
                    return;
                }
                final int old = PistonCrystal.mc.player.getInventory().selectedSlot;
                AutoPush.pistonFacing(facing);
                final int piston = this.findClass(PistonBlock.class);
                this.doSwap(piston);
                BlockUtil.placeBlock(pos, false, this.pistonPacket.getValue());
                if (this.inventory.getValue()) {
                    this.doSwap(piston);
                    EntityUtil.syncInventory();
                }
                else {
                    this.doSwap(old);
                }
                final BlockPos neighbour = pos.offset(side);
                final Direction opposite = side.getOpposite();
                if (this.rotate.getValue()) {
                    stella.ROTATION.lookAt(neighbour, opposite);
                }
            }
        }
        if (this.stage >= this.powerStage.getValue() && this.stage <= this.powerMaxStage.getValue()) {
            this.doRedStone(pos, facing, oPos.offset(facing));
        }
        if (this.stage >= this.crystalStage.getValue() && this.stage <= this.crystalMaxStage.getValue()) {
            this.placeCrystal(oPos, facing);
        }
        if (this.stage >= this.fireStage.getValue() && this.stage <= this.fireMaxStage.getValue()) {
            this.doFire(oPos, facing);
        }
    }
    
    private void placeCrystal(final BlockPos pos, final Direction facing) {
        if (!BlockUtil.canPlaceCrystal(pos.offset(facing))) {
            return;
        }
        final int crystal = this.findItem(Items.END_CRYSTAL);
        if (crystal == -1) {
            return;
        }
        final int old = PistonCrystal.mc.player.getInventory().selectedSlot;
        this.doSwap(crystal);
        BlockUtil.placeCrystal(pos.offset(facing), true);
        if (this.inventory.getValue()) {
            this.doSwap(crystal);
            EntityUtil.syncInventory();
        }
        else {
            this.doSwap(old);
        }
        if (this.cleaner.getValue()) {
            this.cleanQueue.add(pos);
            this.cleanQueue.add(pos.offset(facing, -1));
            for (final Direction dir : Direction.values()) {
                if (this.getBlock(pos.offset(dir)) == Blocks.REDSTONE_BLOCK) {
                    this.cleanQueue.add(pos.offset(dir));
                }
            }
        }
    }
    
    private boolean isPiston(final BlockPos pos, final Direction facing) {
        return PistonCrystal.mc.world.getBlockState(pos).getBlock() instanceof PistonBlock && 
            PistonCrystal.mc.world.getBlockState(pos).get(PistonBlock.FACING).getOpposite() == facing && 
            (PistonCrystal.mc.world.isAir(pos.offset(facing, -1)) || this.getBlock(pos.offset(facing, -1)) == Blocks.WATER || this.getBlock(pos.offset(facing.getOpposite())) == Blocks.OBSIDIAN);
    }
    
    private void doFire(final BlockPos pos, final Direction facing) {
        if (!this.fire.getValue()) {
            return;
        }
        final int fire = this.findItem(Items.FLINT_AND_STEEL);
        if (fire == -1) {
            return;
        }
        final int old = PistonCrystal.mc.player.getInventory().selectedSlot;
        final int[] xOffset = { 0, facing.getOffsetX(), -facing.getOffsetX() };
        final int[] yOffset = { 0, 1 };
        final int[] zOffset = { 0, facing.getOffsetZ(), -facing.getOffsetZ() };
        for (final int x : xOffset) {
            for (final int y : yOffset) {
                for (final int z : zOffset) {
                    if (this.getBlock(pos.add(x, y, z)) == Blocks.WATER) {
                        return;
                    }
                }
            }
        }
        for (final int x : xOffset) {
            for (final int y : yOffset) {
                for (final int z : zOffset) {
                    if (canFire(pos.add(x, y, z))) {
                        this.doSwap(fire);
                        this.placeFire(pos.add(x, y, z));
                        if (this.inventory.getValue()) {
                            this.doSwap(fire);
                            EntityUtil.syncInventory();
                        }
                        else {
                            this.doSwap(old);
                        }
                        return;
                    }
                }
            }
        }
    }
    
    public void placeFire(final BlockPos pos) {
        final BlockPos neighbour = pos.up();
        BlockUtil.clickBlock(neighbour, Direction.DOWN, this.rotate.getValue());
    }
    
    private void doRedStone(final BlockPos pos, final Direction facing, final BlockPos crystalPos) {
        if (!PistonCrystal.mc.world.isAir(pos.offset(facing, -1)) && this.getBlock(pos.offset(facing, -1)) != Blocks.WATER && this.getBlock(pos.offset(facing.getOpposite())) != Blocks.OBSIDIAN) {
            return;
        }
        for (final Direction i : Direction.values()) {
            if (this.getBlock(pos.offset(i)) == Blocks.REDSTONE_BLOCK) {
                return;
            }
        }
        final int power = this.findBlock(Blocks.REDSTONE_BLOCK);
        if (power == -1) {
            return;
        }
        final int old = PistonCrystal.mc.player.getInventory().selectedSlot;
        final Direction bestNeighboring = BlockUtil.getBestNeighboring(pos, facing);
        if (bestNeighboring != null && bestNeighboring != facing.getOpposite() && BlockUtil.canPlace(pos.offset(bestNeighboring), this.placeRange.getValue()) && !pos.offset(bestNeighboring).equals((Object)crystalPos)) {
            this.doSwap(power);
            BlockUtil.placeBlock(pos.offset(bestNeighboring), this.rotate.getValue());
            if (this.inventory.getValue()) {
                this.doSwap(power);
                EntityUtil.syncInventory();
            }
            else {
                this.doSwap(old);
            }
            return;
        }
        for (final Direction j : Direction.values()) {
            if (BlockUtil.canPlace(pos.offset(j), this.placeRange.getValue()) && !pos.offset(j).equals((Object)crystalPos) && j != facing.getOpposite()) {
                this.doSwap(power);
                BlockUtil.placeBlock(pos.offset(j), this.rotate.getValue());
                if (this.inventory.getValue()) {
                    this.doSwap(power);
                    EntityUtil.syncInventory();
                }
                else {
                    this.doSwap(old);
                }
                return;
            }
        }
    }
    
    private void doSwap(final int slot) {
        if (this.inventory.getValue()) {
            InventoryUtil.inventorySwap(slot, PistonCrystal.mc.player.getInventory().selectedSlot);
        }
        else {
            InventoryUtil.switchToSlot(slot);
        }
    }
    
    public int findItem(final Item itemIn) {
        if (this.inventory.getValue()) {
            return InventoryUtil.findItemInventorySlot(itemIn);
        }
        return InventoryUtil.findItem(itemIn);
    }
    
    public int findBlock(final Block blockIn) {
        if (this.inventory.getValue()) {
            return InventoryUtil.findBlockInventorySlot(blockIn);
        }
        return InventoryUtil.findBlock(blockIn);
    }
    
    public int findClass(final Class<?> clazz) {
        if (this.inventory.getValue()) {
            return InventoryUtil.findClassInventorySlot(clazz);
        }
        return InventoryUtil.findClass(clazz);
    }
    
    private Block getBlock(final BlockPos pos) {
        return PistonCrystal.mc.world.getBlockState(pos).getBlock();
    }
    
    private void doClean() {
        if (!this.cleanerTimer.passedMs(this.cleanerDelay.getValueInt())) {
            return;
        }
        if (this.pistonCleaner.getValue()) {
            this.checkAndCleanPistons();
        }
        if (!this.cleaner.getValue()) {
            return;
        }
        if (this.cleanQueue.isEmpty()) {
            return;
        }
        final BlockPos pos = this.cleanQueue.remove(0);
        if (pos == null) {
            return;
        }
        if (!PistonCrystal.mc.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            return;
        }
        final Block block = this.getBlock(pos);
        if (block != Blocks.PISTON && block != Blocks.STICKY_PISTON && block != Blocks.REDSTONE_BLOCK && block != Blocks.OBSIDIAN) {
            return;
        }
        if (!PistonCrystal.mc.world.isAir(pos) && block != Blocks.OBSIDIAN && block != Blocks.BEDROCK) {
            this.breakBlock(pos);
            this.cleanerTimer.reset();
        }
    }
    
    private void checkAndCleanPistons() {
        if (this.target == null) {
            return;
        }
        final BlockPos targetPos = EntityUtil.getEntityPos((Entity)this.target, true);
        if (targetPos == null) {
            return;
        }
        int pistonCount = 0;
        BlockPos pistonToClean = null;
        for (int range = (int)this.pistonCleanRange.getValue(), x = -range; x <= range; ++x) {
            for (int y = -range; y <= range; ++y) {
                for (int z = -range; z <= range; ++z) {
                    final BlockPos checkPos = targetPos.add(x, y, z);
                    final Block block = this.getBlock(checkPos);
                    if (block == Blocks.PISTON || block == Blocks.STICKY_PISTON || block == Blocks.OBSIDIAN) {
                        ++pistonCount;
                        if (pistonToClean == null) {
                            pistonToClean = checkPos;
                        }
                    }
                }
            }
        }
        if (pistonCount >= this.pistonCleanThreshold.getValueInt() && pistonToClean != null) {
            if (this.debug.getValue()) {
                this.sendMessage("[PistonCleaner] Detected " + pistonCount + " pistons, threshold: " + (int)this.pistonCleanThreshold.getValue() + ", cleaning at: " + String.valueOf(pistonToClean));
            }
            if (!PistonCrystal.mc.world.isChunkLoaded(pistonToClean.getX() >> 4, pistonToClean.getZ() >> 4)) {
                return;
            }
            this.breakBlock(pistonToClean);
            this.cleanerTimer.reset();
        }
        else if (this.debug.getValue()) {
            this.sendMessage("[PistonCleaner] Detected " + pistonCount + " pistons, threshold: " + (int)this.pistonCleanThreshold.getValue());
        }
    }
    
    private void breakBlock(final BlockPos pos) {
        if (PistonCrystal.mc.world.isAir(pos)) {
            return;
        }
        final int oldSlot = PistonCrystal.mc.player.getInventory().selectedSlot;
        final int bestTool = this.findBestTool(pos);
        if (bestTool != -1) {
            PistonCrystal.mc.player.getInventory().selectedSlot = bestTool;
        }
        Direction side = BlockUtil.getPlaceSide(pos);
        if (side == null) {
            side = Direction.DOWN;
        }
        if (this.cleanerRotate.getValue()) {
            stella.ROTATION.lookAt(pos, side.getOpposite());
        }
        final Direction finalSide = side;
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, finalSide));
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, finalSide));
        EntityUtil.swingHand(Hand.MAIN_HAND, AntiCheat.INSTANCE.interactSwing.getValue());
        if (this.cleanerRotate.getValue()) {
            stella.ROTATION.snapBack();
        }
        if (bestTool != -1) {
            PistonCrystal.mc.player.getInventory().selectedSlot = oldSlot;
        }
    }
    
    private int findBestTool(final BlockPos pos) {
        int index = -1;
        float bestSpeed = 1.0f;
        for (int i = 0; i < 9; ++i) {
            final ItemStack stack = mc.player.getInventory().getStack(i);
            final float speed = stack.getMiningSpeedMultiplier(mc.world.getBlockState(pos));
            if (speed > bestSpeed) {
                bestSpeed = speed;
                index = i;
            }
        }
        return index;
    }
    
    @Override
    public void onDisable() {
        this.cleanQueue.clear();
        this.cleanerTimer.reset();
    }
}