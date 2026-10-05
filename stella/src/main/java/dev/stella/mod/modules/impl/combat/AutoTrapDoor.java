package dev.stella.mod.modules.impl.combat;

import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.ClientTickEvent;
import dev.stella.api.utils.combat.CombatUtil;
import dev.stella.api.utils.math.Timer;
import dev.stella.api.utils.player.EntityUtil;
import dev.stella.api.utils.player.InventoryUtil;
import dev.stella.api.utils.world.BlockUtil;
import dev.stella.core.impl.RotationManager;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.impl.render.PlaceRender;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import dev.stella.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.BlockState;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 *  -
 * 
 */
public class AutoTrapDoor extends Module {
    public static AutoTrapDoor INSTANCE;
    
    private final SliderSetting range = this.add(new SliderSetting("Range", 5.0, 1.0, 6.0));
    private final SliderSetting delay = this.add(new SliderSetting("Delay", 50, 0, 500).setSuffix("ms"));
    private final SliderSetting predict = this.add(new SliderSetting("Predict", 2.0, 0.0, 8.0).setSuffix("tick"));
    private final BooleanSetting rotate = this.add(new BooleanSetting("Rotate", true));
    private final BooleanSetting packet = this.add(new BooleanSetting("Packet", true));
    private final BooleanSetting inventory = this.add(new BooleanSetting("InventorySwap", true));
    private final BooleanSetting onlySurrounded = this.add(new BooleanSetting("OnlySurrounded", true));
    private final BooleanSetting strictSurround = this.add(new BooleanSetting("StrictSurround", false, this.onlySurrounded::getValue));
    private final BooleanSetting spam = this.add(new BooleanSetting("Spam", true));
    private final BooleanSetting replaceObs = this.add(new BooleanSetting("ReplaceObs", false));
    private final BooleanSetting usingPause = this.add(new BooleanSetting("UsingPause", true));
    
    // Grim v2 绕过设置
    private final BooleanSetting grimV2 = this.add(new BooleanSetting("GrimV2", false).setParent());
    private final SliderSetting grimDelay = this.add(new SliderSetting("GrimDelay", 100, 0, 500, 10, this.grimV2::isOpen));
    private final BooleanSetting grimSilentRotate = this.add(new BooleanSetting("SilentRotate", true, this.grimV2::isOpen));

    private final Timer timer = new Timer();
    private BlockPos currentTrapdoorPos = null;
    private final Timer obsidianPlaceTimer = new Timer();
    private BlockPos lastObsidianPos = null;

    public AutoTrapDoor() {
        super("AutoTrapDoor", Category.Combat);
        this.setChinese("自动活板门");
        INSTANCE = this;
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (nullCheck()) return;
        if (!event.isPre()) return;
        if (this.usingPause.getValue() && mc.player.isUsingItem()) return;
        
        PlayerEntity target = CombatUtil.getClosestEnemy(this.range.getValue());
        if (target == null) return;

        if (!this.timer.passedMs(this.delay.getValueInt())) return;

        if (this.onlySurrounded.getValue()) {
            if (this.strictSurround.getValue()) {
                if (!isStrictlySurrounded(target)) {
                    return;
                }
            } else {
                if (!isSurrounded(target)) {
                    return;
                }
            }
        }

        BlockPos feetPos = EntityUtil.getEntityPos(target, true);
        BlockState feetState = mc.world.getBlockState(feetPos);
        boolean hasFeetTrapdoor = feetState.getBlock() instanceof TrapdoorBlock;
        BlockPos currentHeadPos = EntityUtil.getEntityPos(target, true).up();

        handleFeetTrapdoor(feetPos, target);

        if (!hasFeetTrapdoor) {
            List<BlockPos> headPositions = new ArrayList<>();
            headPositions.add(currentHeadPos);

            if (this.predict.getValue() > 0) {
                Vec3d velocity = target.getVelocity();
                if (velocity.lengthSquared() > 0.0001) {
                    Vec3d predictedVec = target.getPos().add(velocity.multiply(this.predict.getValue()));
                    BlockPos predictedHeadPos = BlockPos.ofFloored(predictedVec).up();
                    if (!predictedHeadPos.equals(currentHeadPos)) {
                        headPositions.add(predictedHeadPos);
                    }
                }
            }

            for (BlockPos headPos : headPositions) {
                boolean isRealTarget = target.getBoundingBox().intersects(new Box(headPos));
                Direction closestWall = getClosestWall(target, headPos);

                if (closestWall != null) {
                    if (!isRealTarget || getDistanceToWall(target, closestWall) <= 0.4875) {
                         handleHeadTrapdoor(target, headPos, closestWall, isRealTarget);
                    }
                }
            }
        }

        if (this.replaceObs.getValue()) {
            handleReplaceObsidian(target, currentHeadPos);
            checkObsidianInstantMining();
        }
    }

    private boolean isSurrounded(PlayerEntity target) {
        BlockPos pos = EntityUtil.getEntityPos(target, true);
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP || dir == Direction.DOWN) continue;
            BlockPos offset = pos.offset(dir);
            if (mc.world.isAir(offset) && BlockUtil.canReplace(offset)) {
                return false;
            }
        }
        return true;
    }

    private boolean isStrictlySurrounded(PlayerEntity target) {
        BlockPos pos = EntityUtil.getEntityPos(target, true);
        BlockPos headPos = pos.up();

        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP || dir == Direction.DOWN) continue;
            BlockPos offset = pos.offset(dir);
            if (mc.world.isAir(offset) && BlockUtil.canReplace(offset)) {
                return false;
            }
        }

        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP || dir == Direction.DOWN) continue;
            BlockPos offset = headPos.offset(dir);
            if (mc.world.isAir(offset) && BlockUtil.canReplace(offset)) {
                return false;
            }
        }

        return true;
    }

    private Direction getClosestWall(PlayerEntity target, BlockPos headPos) {
        Direction bestDir = null;
        double minDesc = Double.MAX_VALUE;

        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP || dir == Direction.DOWN) continue;

            // Check if there is a block to attach to at head level
            BlockPos wallPos = headPos.offset(dir);
            if (!mc.world.isAir(wallPos) && !BlockUtil.canReplace(wallPos)) {
                double dist = getDistanceToWall(target, dir);
                if (dist < minDesc) {
                    minDesc = dist;
                    bestDir = dir;
                }
            }
        }
        return bestDir;
    }

    private double getDistanceToWall(PlayerEntity target, Direction dir) {
        double pX = target.getX();
        double pZ = target.getZ();

        double relX = pX - Math.floor(pX);
        double relZ = pZ - Math.floor(pZ);

        switch (dir) {
            case NORTH:
                return relZ;
            case SOUTH:
                return 1.0 - relZ;
            case WEST:
                return relX;
            case EAST:
                return 1.0 - relX;
        }
        return 1.0;
    }

    private boolean handleHeadTrapdoor(PlayerEntity target, BlockPos pos, Direction wallDir, boolean isRealTarget) {
        if (target.isCrawling() || target.isSwimming()) {
            return false;
        }

        BlockState state = mc.world.getBlockState(pos);

        if (state.getBlock() instanceof TrapdoorBlock) {
            if (isRealTarget) {
                if (!target.isSwimming() && !target.isCrawling()) {
                     if (this.spam.getValue()) {
                         BlockUtil.clickBlock(pos, Direction.UP, this.rotate.getValue(), Hand.MAIN_HAND, this.packet.getValue());
                         this.timer.reset();
                         return true;
                     } else {
                         if (!state.get(TrapdoorBlock.OPEN)) {
                            BlockUtil.clickBlock(pos, Direction.UP, this.rotate.getValue(), Hand.MAIN_HAND, this.packet.getValue());
                            this.timer.reset();
                            return true;
                        }
                     }
                }
            } else {
                if (!state.get(TrapdoorBlock.OPEN)) {
                    BlockUtil.clickBlock(pos, Direction.UP, this.rotate.getValue(), Hand.MAIN_HAND, this.packet.getValue());
                    this.timer.reset();
                    return true;
                }
            }
        } else {
            if (BlockUtil.canPlace(pos) || state.isAir() || state.isReplaceable()) {
                if (placeTrapdoorAgainstWall(pos, wallDir)) {
                    this.timer.reset();
                    return true;
                }
            }
        }
        return false;
    }

    private void handleFeetTrapdoor(BlockPos pos, PlayerEntity target) {
        if (target == null) return;

        BlockState state = mc.world.getBlockState(pos);

        if (state.getBlock() instanceof TrapdoorBlock) {
            if (!target.isCrawling() && !target.isSwimming()) {
                BlockUtil.clickBlock(pos, Direction.UP, this.rotate.getValue(), Hand.MAIN_HAND, this.packet.getValue());
                this.timer.reset();
            } else if (state.get(TrapdoorBlock.OPEN)) {
                BlockUtil.clickBlock(pos, Direction.UP, this.rotate.getValue(), Hand.MAIN_HAND, this.packet.getValue());
                this.timer.reset();
            }
        } else {
             if (BlockUtil.canPlace(pos) || state.isAir() || state.isReplaceable()) {
                if (placeTrapdoor(pos, target)) {
                    this.timer.reset();
                }
            }
        }
    }

    private void handleReplaceObsidian(PlayerEntity target, BlockPos headPos) {
        if (!target.isCrawling() && !target.isSwimming()) {
            return;
        }

        BlockState state = mc.world.getBlockState(headPos);

        if (state.getBlock() instanceof TrapdoorBlock) {
            this.currentTrapdoorPos = headPos;

            if (BlockUtil.canPlace(headPos)) {
                // 使用 PacketMine 进行挖掘
                dev.stella.mod.modules.impl.player.PacketMine.INSTANCE.mine(headPos);
                this.timer.reset();
                return;
            }
        } else if (state.isAir() || state.isReplaceable()) {
            if (this.currentTrapdoorPos != null && this.currentTrapdoorPos.equals(headPos)) {
                if (placeObsidian(headPos)) {
                    this.currentTrapdoorPos = null;
                    this.lastObsidianPos = headPos;
                    this.obsidianPlaceTimer.reset();
                    this.timer.reset();
                }
            }
        } else {
            this.currentTrapdoorPos = null;
        }
    }

    private boolean placeObsidian(BlockPos pos) {
        int slot = this.findItem(net.minecraft.item.Items.OBSIDIAN);
        int oldSlot = mc.player.getInventory().selectedSlot;

        if (slot != -1) {
            this.doSwap(slot);

            BlockUtil.placeBlock(pos, this.rotate.getValue(), this.packet.getValue());

            if (this.inventory.getValue()) {
                this.doSwap(slot);
                EntityUtil.syncInventory();
            } else {
                InventoryUtil.switchToSlot(oldSlot);
            }
            PlaceRender.INSTANCE.create(pos);
            return true;
        }
        return false;
    }

    private void checkObsidianInstantMining() {
        if (this.lastObsidianPos == null || !this.obsidianPlaceTimer.passedMs(100)) {
            return;
        }

        BlockState state = mc.world.getBlockState(this.lastObsidianPos);
        if (state.isAir() || state.isReplaceable()) {
            clearPacketMinePosition();
            this.lastObsidianPos = null;
        }
    }

    private void clearPacketMinePosition() {
        try {
            dev.stella.mod.modules.impl.player.PacketMine packetMine = dev.stella.mod.modules.impl.player.PacketMine.INSTANCE;

            java.lang.reflect.Field breakPosField = packetMine.getClass().getDeclaredField("breakPos");
            breakPosField.setAccessible(true);
            breakPosField.set(packetMine, null);
        } catch (Exception ignored) {
        }
    }

    private void doSwap(int slot) {
        if (this.inventory.getValue()) {
            if (mc.player != null) {
                InventoryUtil.inventorySwap(slot, mc.player.getInventory().selectedSlot);
            }
        } else {
            InventoryUtil.switchToSlot(slot);
        }
    }

    private int findItem(net.minecraft.item.Item item) {
        if (this.inventory.getValue()) {
            return InventoryUtil.findItemInventorySlot(item);
        }
        return InventoryUtil.findItem(item);
    }

    private int findClass(Class<?> clazz) {
        if (this.inventory.getValue()) {
            return InventoryUtil.findClassInventorySlot(clazz);
        }
        return InventoryUtil.findClass(clazz);
    }

    private boolean placeTrapdoorAgainstWall(BlockPos pos, Direction wallDir) {
         int slot = this.findClass(TrapdoorBlock.class);
         if (slot == -1) return false;

         BlockPos neighbor = pos.offset(wallDir);
         // 修复：检查 neighbor 是否是有效放置面，不能对着空气放置
         if (mc.world.isAir(neighbor) || BlockUtil.canReplace(neighbor)) {
             return false;
         }

         int oldSlot = mc.player.getInventory().selectedSlot;
         this.doSwap(slot);

         Direction side = wallDir.getOpposite();

         Vec3d hitVec = neighbor.toCenterPos().add(
             side.getVector().getX() * 0.5,
             0.9,
             side.getVector().getZ() * 0.5
         );

         clickBlock(neighbor, side, this.rotate.getValue(), this.packet.getValue(), hitVec);

         if (this.inventory.getValue()) {
             this.doSwap(slot);
             EntityUtil.syncInventory();
         } else {
             InventoryUtil.switchToSlot(oldSlot);
         }
         PlaceRender.INSTANCE.create(pos);
         return true;
    }

    private boolean placeTrapdoor(BlockPos pos, PlayerEntity target) {
        int slot = this.findClass(TrapdoorBlock.class);
        int oldSlot = mc.player.getInventory().selectedSlot;

        if (slot != -1) {
            this.doSwap(slot);

            boolean placed = placeBlockTop(pos, this.rotate.getValue(), this.packet.getValue(), target);

            if (this.inventory.getValue()) {
                this.doSwap(slot);
                EntityUtil.syncInventory();
            } else {
                InventoryUtil.switchToSlot(oldSlot);
            }

            if (placed) {
                PlaceRender.INSTANCE.create(pos);
                return true;
            }
        }
        return false;
    }

    /**
     * 检查目标玩家是否在指定位置
     * 不会忽略玩家实体
     */
    private boolean isPlayerAtPosition(BlockPos pos, PlayerEntity target) {
        // 使用玩家的 BoundingBox 来检查是否在目标位置
        return target.getBoundingBox().intersects(new Box(pos));
    }

    private boolean placeBlockTop(BlockPos pos, boolean rotate, boolean packet, PlayerEntity target) {
        // 检查目标玩家是否在放置位置
        // 不会忽略玩家实体，确保活版门可以放置在玩家身上
        boolean playerAtPos = isPlayerAtPosition(pos, target);
        
        if (packet) {
            // Packet 模式逻辑修复：寻找有效的放置面
            Direction bestSide = null;
            double maxDist = -1.0;

            // 1. 尝试根据距离寻找最佳面
            for (Direction side : Direction.values()) {
                if (side == Direction.UP || side == Direction.DOWN) continue;
                double dist = getDistanceToWall(target, side);
                if (dist > maxDist) {
                    maxDist = dist;
                    bestSide = side;
                }
            }

            Direction finalSide = null;

            // 2. 验证最佳面是否有效（不能对着空气放置）
            if (bestSide != null) {
                BlockPos neighbor = pos.offset(bestSide);
                if (!mc.world.isAir(neighbor) && !BlockUtil.canReplace(neighbor)) {
                    finalSide = bestSide;
                }
            }

            // 3. 如果最佳面无效，尝试寻找任意有效的水平面
            if (finalSide == null) {
                for (Direction side : Direction.values()) {
                    if (side == Direction.UP || side == Direction.DOWN) continue;
                    BlockPos neighbor = pos.offset(side);
                    if (!mc.world.isAir(neighbor) && !BlockUtil.canReplace(neighbor)) {
                        finalSide = side;
                        break;
                    }
                }
            }

            // 4. 如果水平面都无效，尝试头顶（天花板悬挂）
            if (finalSide == null) {
                BlockPos up = pos.up();
                if (!mc.world.isAir(up) && !BlockUtil.canReplace(up)) {
                    finalSide = Direction.UP;
                }
            }

            // 5. 如果还是没有有效位置，但有玩家在位置，则强制放置
            if (finalSide == null) {
                if (playerAtPos) {
                    // 玩家在位置，尝试寻找任意有效的放置面
                    for (Direction side : Direction.values()) {
                        if (side == Direction.UP || side == Direction.DOWN) continue;
                        BlockPos neighbor = pos.offset(side);
                        if (!mc.world.isAir(neighbor) && !BlockUtil.canReplace(neighbor)) {
                            finalSide = side;
                            break;
                        }
                    }
                    // 如果还是没有，使用默认的北方
                    if (finalSide == null) {
                        finalSide = Direction.NORTH;
                    }
                } else {
                    return false;
                }
            }

            // 6. 执行放置
            BlockPos neighbor;
            Direction opp;
            Vec3d hitVec;

            if (finalSide == Direction.UP) {
                neighbor = pos.up();
                opp = Direction.DOWN;
                hitVec = neighbor.toCenterPos().add(0, -0.5, 0);
            } else {
                neighbor = pos.offset(finalSide);
                opp = finalSide.getOpposite();
                hitVec = neighbor.toCenterPos().add(
                    opp.getVector().getX() * 0.5,
                    0.9,
                    opp.getVector().getZ() * 0.5
                );
            }

            clickBlock(neighbor, opp, false, true, hitVec);
            return true;
        } else {
            Direction bestSide = null;
            double maxDist = -1.0;

            for (Direction side : Direction.values()) {
                if (side == Direction.UP || side == Direction.DOWN) continue;
                double dist = getDistanceToWall(target, side);
                if (dist < maxDist) {
                    maxDist = dist;
                    bestSide = side;
                }
            }

            if (bestSide != null) {
                BlockPos neighbor = pos.offset(bestSide);
                if (BlockUtil.canClick(neighbor) && !BlockUtil.canReplace(neighbor)) {
                    Direction side = bestSide.getOpposite();
                    Vec3d hitVec = neighbor.toCenterPos().add(
                        side.getVector().getX() * 0.5,
                        0.9,
                        side.getVector().getZ() * 0.5
                    );
                    clickBlock(neighbor, side, rotate, packet, hitVec);
                    return true;
                }
            }

            bestSide = null;
            BlockPos bestNeighbor = null;

            if (BlockUtil.canClick(pos.up()) && !BlockUtil.canReplace(pos.up())) {
                bestSide = Direction.DOWN;
                bestNeighbor = pos.up();
            }

            if (bestSide == null) {
                for (Direction side : Direction.values()) {
                    if (side == Direction.UP || side == Direction.DOWN) continue;
                    BlockPos neighbor = pos.offset(side);
                    if (BlockUtil.canClick(neighbor) && !BlockUtil.canReplace(neighbor)) {
                        bestSide = side.getOpposite();
                        bestNeighbor = neighbor;
                        break;
                    }
                }
            }

            // 如果没找到有效面但有玩家在位置，强制放置
            if (bestSide == null && playerAtPos) {
                // 尝试寻找任意有效的水平面
                for (Direction side : Direction.values()) {
                    if (side == Direction.UP || side == Direction.DOWN) continue;
                    BlockPos neighbor = pos.offset(side);
                    if (!mc.world.isAir(neighbor) && !BlockUtil.canReplace(neighbor)) {
                        bestSide = side.getOpposite();
                        bestNeighbor = neighbor;
                        break;
                    }
                }
                // 如果还是没有，使用默认值
                if (bestSide == null) {
                    bestSide = Direction.DOWN;
                    bestNeighbor = pos.up();
                }
            }

            if (bestSide != null) {
                Vec3d hitVec;
                if (bestSide == Direction.DOWN) {
                    hitVec = bestNeighbor.toCenterPos().add(0, -0.5, 0);
                } else {
                    hitVec = bestNeighbor.toCenterPos().add(
                        bestSide.getVector().getX() * 0.5,
                        0.9,
                        bestSide.getVector().getZ() * 0.5
                    );
                }

                clickBlock(bestNeighbor, bestSide, rotate, packet, hitVec);
                return true;
            }
        }
        return false;
    }

    private void clickBlock(BlockPos pos, Direction side, boolean rotate, boolean packet, Vec3d hitVec) {
        BlockHitResult result = new BlockHitResult(hitVec, side, pos, false);

        if (packet) {
            // Grim v2 绕过：延迟发送并可选静默旋转
            if (this.grimV2.getValue()) {
                // 延迟发送交互包
                new Thread(() -> {
                    try {
                        Thread.sleep(this.grimDelay.getValueInt());
                        
                        // 静默旋转：发送旋转包但不更新客户端视角
                        if (this.grimSilentRotate.getValue()) {
                            float yaw = RotationManager.INSTANCE.getLastYaw();
                            float pitch = RotationManager.INSTANCE.getLastPitch();
                            Module.sendSequencedPacket(id -> new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full(
                                mc.player.getX(), mc.player.getY(), mc.player.getZ(), 
                                yaw, pitch, mc.player.isOnGround()
                            ));
                        }
                        
                        Module.sendSequencedPacket(id -> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, result, id));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }).start();
            } else {
                Module.sendSequencedPacket(id -> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, result, id));
            }
        } else {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, result);
        }

        mc.itemUseCooldown = 4;
    }
}
