package dev.stella.api.utils.world;

import dev.stella.api.utils.Wrapper;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class HoleUtils implements Wrapper {

    private static final Vec3i[] holeOffsets = new Vec3i[]{
            new Vec3i(0, -1, 0), new Vec3i(1, 0, 0), new Vec3i(-1, 0, 0),
            new Vec3i(0, 0, 1), new Vec3i(0, 0, -1)
    };

    private static final Vec3i[] singleOffsets = {
            new Vec3i(-1, 0, 0), new Vec3i(1, 0, 0),
            new Vec3i(0, 0, -1), new Vec3i(0, 0, 1),
            new Vec3i(0, -1, 0)
    };

    private static final Vec3i[] doubleXOffsets = {
            new Vec3i(-1, 0, 0), new Vec3i(0, 0, -1), new Vec3i(0, 0, 1),
            new Vec3i(0, -1, 0), new Vec3i(2, 0, 0),
            new Vec3i(1, 0, -1), new Vec3i(1, 0, 1), new Vec3i(1, -1, 0)
    };

    private static final Vec3i[] doubleZOffsets = {
            new Vec3i(0, 0, -1), new Vec3i(-1, 0, 0), new Vec3i(1, 0, 0),
            new Vec3i(0, -1, 0), new Vec3i(0, 0, 2),
            new Vec3i(-1, 0, 1), new Vec3i(1, 0, 1), new Vec3i(0, -1, 1)
    };

    /**
     * 获取玩家脚下需要围脚的位置
     */
    public static HashSet<BlockPos> getSurroundPositions(PlayerEntity target, boolean extension, boolean floor) {
        HashSet<BlockPos> positions = new HashSet<>();
        HashSet<BlockPos> blacklist = new HashSet<>();

        BlockPos feetPos = target.getBlockPos();
        blacklist.add(feetPos);

        if (extension) {
            for (Direction dir : Direction.values()) {
                if (dir.getAxis().isVertical()) continue;
                BlockPos off = feetPos.offset(dir);

                List<PlayerEntity> collisions = mc.world.getEntitiesByClass(
                        PlayerEntity.class,
                        new Box(off),
                        player -> player != target
                );

                if (collisions.isEmpty()) continue;

                for (PlayerEntity player : collisions) {
                    Box box = player.getBoundingBox();
                    for (int x = (int) Math.floor(box.minX); x < Math.ceil(box.maxX); x++) {
                        for (int z = (int) Math.floor(box.minZ); z < Math.ceil(box.maxZ); z++) {
                            blacklist.add(new BlockPos(x, feetPos.getY(), z));
                        }
                    }
                }
            }
        }

        for (BlockPos pos : blacklist) {
            if (floor) positions.add(pos.down());

            for (Direction dir : Direction.values()) {
                if (!dir.getAxis().isHorizontal()) continue;
                BlockPos off = pos.offset(dir);
                if (!blacklist.contains(off)) positions.add(off);
            }
        }

        return positions;
    }

    /**
     * 检查玩家是否在洞中
     */
    public static boolean isPlayerInHole(PlayerEntity player) {
        HashSet<BlockPos> positions = getSurroundPositions(player, true, true);
        for (BlockPos pos : positions) {
            if (mc.world.getBlockState(pos).isReplaceable()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检测单孔
     */
    public static Hole getSingleHole(BlockPos position, double height) {
        if (!mc.world.getBlockState(position).isOf(Blocks.AIR)) return null;
        if (!mc.world.getBlockState(position.up()).isOf(Blocks.AIR)) return null;
        if (!mc.world.getBlockState(position.up(2)).isOf(Blocks.AIR)) return null;

        HoleSafety safety = null;
        for (Vec3i offset : singleOffsets) {
            var block = mc.world.getBlockState(position.add(offset)).getBlock();
            if (!block.equals(Blocks.BEDROCK) && !block.equals(Blocks.OBSIDIAN) &&
                    !block.equals(Blocks.RESPAWN_ANCHOR) && !block.equals(Blocks.ENDER_CHEST)) {
                return null;
            }

            if (block.equals(Blocks.BEDROCK)) {
                if (safety == HoleSafety.OBSIDIAN) safety = HoleSafety.MIXED;
                else if (safety != HoleSafety.MIXED) safety = HoleSafety.BEDROCK;
            } else {
                if (safety == HoleSafety.BEDROCK) safety = HoleSafety.MIXED;
                else if (safety != HoleSafety.MIXED) safety = HoleSafety.OBSIDIAN;
            }
        }

        if (safety == null) safety = HoleSafety.OBSIDIAN;

        return new Hole(
                new Box(position.getX(), position.getY(), position.getZ(),
                        position.getX() + 1, position.getY() + height, position.getZ() + 1),
                HoleType.SINGLE, safety
        );
    }

    /**
     * 检测双孔
     */
    public static Hole getDoubleHole(BlockPos position, double height) {
        if (!mc.world.getBlockState(position).isOf(Blocks.AIR)) return null;
        if (!mc.world.getBlockState(position.up()).isOf(Blocks.AIR)) return null;
        if (!mc.world.getBlockState(position.up(2)).isOf(Blocks.AIR)) return null;

        boolean x = mc.world.getBlockState(position.add(1, 0, 0)).isOf(Blocks.AIR) &&
                mc.world.getBlockState(position.add(1, 0, 0).up()).isOf(Blocks.AIR) &&
                mc.world.getBlockState(position.add(1, 0, 0).up(2)).isOf(Blocks.AIR);

        boolean z = mc.world.getBlockState(position.add(0, 0, 1)).isOf(Blocks.AIR) &&
                mc.world.getBlockState(position.add(0, 0, 1).up()).isOf(Blocks.AIR) &&
                mc.world.getBlockState(position.add(0, 0, 1).up(2)).isOf(Blocks.AIR);

        if (!x && !z) return null;

        Box box = null;
        HoleSafety safety = null;

        if (x) {
            boolean valid = true;
            for (Vec3i offset : doubleXOffsets) {
                var block = mc.world.getBlockState(position.add(offset)).getBlock();
                if (!block.equals(Blocks.BEDROCK) && !block.equals(Blocks.OBSIDIAN) &&
                        !block.equals(Blocks.RESPAWN_ANCHOR) && !block.equals(Blocks.ENDER_CHEST)) {
                    valid = false;
                    break;
                }

                if (block.equals(Blocks.BEDROCK)) {
                    if (safety == HoleSafety.OBSIDIAN) safety = HoleSafety.MIXED;
                    else if (safety != HoleSafety.MIXED) safety = HoleSafety.BEDROCK;
                } else {
                    if (safety == HoleSafety.BEDROCK) safety = HoleSafety.MIXED;
                    else if (safety != HoleSafety.MIXED) safety = HoleSafety.OBSIDIAN;
                }
            }

            if (valid) {
                box = new Box(position.getX(), position.getY(), position.getZ(),
                        position.getX() + 2, position.getY() + height, position.getZ() + 1);
            }
        }

        if (z && box == null) {
            boolean valid = true;
            for (Vec3i offset : doubleZOffsets) {
                var block = mc.world.getBlockState(position.add(offset)).getBlock();
                if (!block.equals(Blocks.BEDROCK) && !block.equals(Blocks.OBSIDIAN) &&
                        !block.equals(Blocks.RESPAWN_ANCHOR) && !block.equals(Blocks.ENDER_CHEST)) {
                    valid = false;
                    break;
                }

                if (block.equals(Blocks.BEDROCK)) {
                    if (safety == HoleSafety.OBSIDIAN) safety = HoleSafety.MIXED;
                    else if (safety != HoleSafety.MIXED) safety = HoleSafety.BEDROCK;
                } else {
                    if (safety == HoleSafety.BEDROCK) safety = HoleSafety.MIXED;
                    else if (safety != HoleSafety.MIXED) safety = HoleSafety.OBSIDIAN;
                }
            }

            if (valid) {
                box = new Box(position.getX(), position.getY(), position.getZ(),
                        position.getX() + 1, position.getY() + height, position.getZ() + 2);
            }
        }

        if (box == null) return null;
        if (safety == null) safety = HoleSafety.OBSIDIAN;

        return new Hole(box, HoleType.DOUBLE, safety);
    }

    public record Hole(Box box, HoleType type, HoleSafety safety) {}

    public enum HoleType {
        SINGLE, DOUBLE
    }

    public enum HoleSafety {
        OBSIDIAN, MIXED, BEDROCK
    }
}
