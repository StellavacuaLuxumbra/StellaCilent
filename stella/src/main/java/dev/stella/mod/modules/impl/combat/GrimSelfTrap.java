package dev.stella.mod.modules.impl.combat;

import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.BlockBreakingProgressEvent;
import dev.stella.api.events.impl.ClientTickEvent;
import dev.stella.api.utils.math.Timer;
import dev.stella.api.utils.player.EntityUtil;
import dev.stella.api.utils.player.InventoryUtil;
import dev.stella.api.utils.world.BlockUtil;
import dev.stella.api.utils.world.HoleUtils;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.impl.*;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;

import java.util.ArrayList;
import java.util.List;

/**
 * GrimSelfTrap - 基于 Sydney HoleFill 的自我困住模块
 * 特点：Smart 模式检测洞、精确填充
 */
public class GrimSelfTrap extends Module {
    public static GrimSelfTrap INSTANCE;

    // 设置
    private final EnumSetting<Page> page = this.add(new EnumSetting<>("Page", Page.General));
    private final SliderSetting placeDelay = this.add(new SliderSetting("PlaceDelay", 20, 0, 500, () -> this.page.is(Page.General)));
    private final SliderSetting blocksPerTick = this.add(new SliderSetting("BlocksPerTick", 1, 1, 20, () -> this.page.is(Page.General)));
    private final SliderSetting range = this.add(new SliderSetting("Range", 5.0, 0.0, 12.0, () -> this.page.is(Page.General)));
    private final BooleanSetting webs = this.add(new BooleanSetting("Webs", false, () -> this.page.is(Page.General)));
    private final BooleanSetting head = this.add(new BooleanSetting("Head", true, () -> this.page.is(Page.General)));
    private final BooleanSetting center = this.add(new BooleanSetting("Center", true, () -> this.page.is(Page.General)));
    private final BooleanSetting rotate = this.add(new BooleanSetting("Rotate", true, () -> this.page.is(Page.General)));
    private final BooleanSetting crystalDestruction = this.add(new BooleanSetting("CrystalDestruction", true, () -> this.page.is(Page.General)));
    private final BooleanSetting selfDisable = this.add(new BooleanSetting("SelfDisable", false, () -> this.page.is(Page.Check)));
    private final BooleanSetting itemDisable = this.add(new BooleanSetting("ItemDisable", true, () -> this.page.is(Page.Check)));
    private final BooleanSetting inventory = this.add(new BooleanSetting("InventorySwap", true, () -> this.page.is(Page.General)));
    private final BooleanSetting enderChest = this.add(new BooleanSetting("EnderChest", true, () -> this.page.is(Page.General)));
    private final BooleanSetting support = this.add(new BooleanSetting("Support", true, () -> this.page.is(Page.General))); // 辅助放置

    private final Timer timer = new Timer();
    private final List<BlockPos> targetPositions = new ArrayList<>();
    private int ticks = 0;
    private int blocksPlaced = 0;

    public GrimSelfTrap() {
        super("GrimSelfTrap", Category.Combat);
        this.setChinese("Grim自我困住");
        INSTANCE = this;
    }

    @Override
    public String getInfo() {
        return String.valueOf(targetPositions.size());
    }

    @Override
    public void onEnable() {
        if (nullCheck()) return;

        targetPositions.clear();
        ticks = 0;
        blocksPlaced = 0;
        
        // 移除了严格的 isPlayerInHole 检查，允许在任何地形使用
    }

    @Override
    public void onDisable() {
        targetPositions.clear();
        ticks = 0;
        blocksPlaced = 0;
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (nullCheck()) return;
        if (!event.isPre()) return;

        // 延迟检查
        if (ticks < placeDelay.getValueInt()) {
            ticks++;
            return;
        }

        // 获取可用方块槽位
        int slot = getBlockSlot();
        if (slot == -1) {
            if (itemDisable.getValue()) {
                sendMessage("§4No blocks found!");
                disable();
            }
            targetPositions.clear();
            return;
        }

        // 获取需要填充的位置
        targetPositions.clear();
        targetPositions.addAll(getHolePositions());

        if (targetPositions.isEmpty()) {
            if (selfDisable.getValue()) disable();
            return;
        }

        // 攻击干扰水晶
        if (crystalDestruction.getValue()) {
            attackBlockingCrystals();
        }

        // 切换物品
        int previousSlot = mc.player.getInventory().selectedSlot;
        if (inventory.getValue()) {
            InventoryUtil.inventorySwap(slot, previousSlot);
        } else {
            InventoryUtil.switchToSlot(slot);
        }

        // 放置方块
        blocksPlaced = 0;
        List<BlockPos> placedPositions = new ArrayList<>();

        for (BlockPos position : targetPositions) {
            if (blocksPlaced >= blocksPerTick.getValueInt()) break;

            Direction direction = BlockUtil.getPlaceSide(position, range.getValueFloat());
            
            // 如果 getPlaceSide 返回 null，尝试使用 support 逻辑找辅助位置
            if (direction == null && this.support.getValue()) {
                BlockPos helperPos = getHelperPos(position);
                if (helperPos != null) {
                    direction = BlockUtil.getPlaceSide(helperPos, range.getValueFloat());
                    if (direction != null) {
                        // 使用辅助位置进行放置
                        BlockUtil.clickBlock(helperPos.offset(direction), direction.getOpposite(), rotate.getValue(), Hand.MAIN_HAND, true);
                        placedPositions.add(helperPos);
                        blocksPlaced++;
                        timer.reset();
                        continue;
                    }
                }
            }
            
            if (direction == null) continue;

            // 执行点击放置 (BlockUtil.clickBlock 内部已处理旋转)
            BlockUtil.clickBlock(position.offset(direction), direction.getOpposite(), rotate.getValue(), Hand.MAIN_HAND, true);

            placedPositions.add(position);
            blocksPlaced++;
            timer.reset();
        }

        // 切换回原物品
        if (inventory.getValue()) {
            InventoryUtil.inventorySwap(slot, previousSlot);
            EntityUtil.syncInventory();
        } else {
            InventoryUtil.switchToSlot(previousSlot);
        }

        ticks = 0;
    }

    @EventListener
    public void onBlockBreak(BlockBreakingProgressEvent event) {
        if (nullCheck()) return;
        
        // 检查是否是玩家自己破坏的方块
        if (event.getBreakerId() != mc.player.getId()) return;
        
        // 检查是否是需要保护的位置
        BlockPos brokenPos = event.getPosition();
        if (targetPositions.contains(brokenPos)) {
            // 立即重新放置
            int slot = getBlockSlot();
            if (slot != -1) {
                int previousSlot = mc.player.getInventory().selectedSlot;
                if (inventory.getValue()) {
                    InventoryUtil.inventorySwap(slot, previousSlot);
                } else {
                    InventoryUtil.switchToSlot(slot);
                }
                
                Direction direction = BlockUtil.getPlaceSide(brokenPos, range.getValueFloat());
                if (direction == null && this.support.getValue()) {
                    BlockPos helperPos = getHelperPos(brokenPos);
                    if (helperPos != null) {
                        direction = BlockUtil.getPlaceSide(helperPos, range.getValueFloat());
                        if (direction != null) {
                            BlockUtil.clickBlock(helperPos.offset(direction), direction.getOpposite(), rotate.getValue(), Hand.MAIN_HAND, true);
                            timer.reset();
                        }
                    }
                }
                
                if (direction != null) {
                    BlockUtil.clickBlock(brokenPos.offset(direction), direction.getOpposite(), rotate.getValue(), Hand.MAIN_HAND, true);
                    timer.reset();
                }
                
                if (inventory.getValue()) {
                    InventoryUtil.inventorySwap(slot, previousSlot);
                    EntityUtil.syncInventory();
                } else {
                    InventoryUtil.switchToSlot(previousSlot);
                }
            }
        }
    }

    private int getBlockSlot() {
        if (webs.getValue()) {
            int slot = InventoryUtil.findItem(Blocks.COBWEB.asItem());
            if (slot != -1) return slot;
        }

        if (inventory.getValue()) {
            int obsidian = InventoryUtil.findBlockInventorySlot(Blocks.OBSIDIAN);
            if (obsidian != -1) return obsidian;
            if (enderChest.getValue()) {
                return InventoryUtil.findBlockInventorySlot(Blocks.ENDER_CHEST);
            }
            return -1;
        } else {
            int obsidian = InventoryUtil.findBlock(Blocks.OBSIDIAN);
            if (obsidian != -1) return obsidian;
            if (enderChest.getValue()) {
                return InventoryUtil.findBlock(Blocks.ENDER_CHEST);
            }
            return -1;
        }
    }

    private List<BlockPos> getHolePositions() {
        List<BlockPos> positions = new ArrayList<>();
        BlockPos playerPos = mc.player.getBlockPos();
        float rangeSq = range.getValueFloat() * range.getValueFloat();

        // 脚下 (0) 和 身体 (1) 周围
        for (int y = 0; y <= 1; y++) {
            BlockPos base = playerPos.up(y);
            addPosition(positions, base.north(), rangeSq);
            addPosition(positions, base.south(), rangeSq);
            addPosition(positions, base.west(), rangeSq);
            addPosition(positions, base.east(), rangeSq);
        }

        // 头部 (2)
        if (head.getValue()) {
            addPosition(positions, playerPos.up(2), rangeSq);
        }

        return positions;
    }

    private void addPosition(List<BlockPos> positions, BlockPos pos, float rangeSq) {
        if (mc.world.getBlockState(pos).isReplaceable()) {
             if (mc.player.squaredDistanceTo(Vec3d.ofCenter(pos)) <= rangeSq) {
                 positions.add(pos);
             }
        }
    }

    private void attackBlockingCrystals() {
        for (BlockPos pos : targetPositions) {
            EndCrystalEntity crystal = mc.world.getEntitiesByClass(
                    EndCrystalEntity.class,
                    new Box(pos),
                    e -> true
            ).stream().findFirst().orElse(null);

            if (crystal != null) {
                mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, mc.player.isSneaking()));
                mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            }
        }
    }

    /**
     * 获取辅助放置位置（类似原版 Surround 的逻辑）
     * 当目标位置没有可放置的侧面时，寻找目标位置周围有固体方块且可放置的位置
     * 这样可以实现从旁边已有方块延伸过来的效果
     */
    private BlockPos getHelperPos(BlockPos targetPos) {
        if (mc.player == null || mc.world == null) {
            return null;
        }

        // 第一优先级：检查目标位置本身是否可以直接放置
        if (BlockUtil.canPlace(targetPos, range.getValueFloat(), true)) {
            return targetPos;
        }

        // 第二优先级：检查上下左右前后六个方向的相邻位置
        Direction[] searchOrder = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        for (Direction dir : searchOrder) {
            BlockPos neighborPos = targetPos.offset(dir);
            // 检查相邻位置是否有固体方块且可放置
            if (mc.world.getBlockState(neighborPos).isSolid() && 
                BlockUtil.canPlace(neighborPos, range.getValueFloat(), true)) {
                return neighborPos;
            }
        }

        // 第三优先级：检查下方位置（虚空搭方块）
        if (targetPos.getY() > mc.world.getBottomY() + 1) {
            BlockPos belowPos = targetPos.down();
            // 检查下方位置的相邻是否有固体方块
            for (Direction dir : searchOrder) {
                if (dir == Direction.UP) continue;
                BlockPos neighborPos = belowPos.offset(dir);
                if (mc.world.getBlockState(neighborPos).isSolid() && 
                    BlockUtil.canPlace(belowPos, range.getValueFloat(), true)) {
                    return belowPos;
                }
            }
        }

        // 第四优先级：检查对角线位置
        Direction[][] diagonalDirs = {
            {Direction.NORTH, Direction.UP}, {Direction.SOUTH, Direction.UP},
            {Direction.WEST, Direction.UP}, {Direction.EAST, Direction.UP},
            {Direction.NORTH, Direction.DOWN}, {Direction.SOUTH, Direction.DOWN},
            {Direction.WEST, Direction.DOWN}, {Direction.EAST, Direction.DOWN}
        };
        
        for (Direction[] dirs : diagonalDirs) {
            BlockPos diagPos = targetPos.offset(dirs[0]).offset(dirs[1]);
            if (mc.world.getBlockState(diagPos).isSolid() && 
                BlockUtil.canPlace(diagPos, range.getValueFloat(), true)) {
                return diagPos;
            }
        }

        return null;
    }

    public enum Page {
        General, Check
    }
}
