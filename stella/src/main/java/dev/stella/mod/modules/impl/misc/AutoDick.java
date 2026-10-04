package dev.stella.mod.modules.impl.misc;

import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.Render3DEvent;
import dev.stella.api.events.impl.UpdateEvent;
import dev.stella.api.utils.world.BlockUtil;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import dev.stella.mod.modules.settings.impl.ColorSetting;
import dev.stella.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class AutoDick extends Module {
    
    private final SliderSetting placeDelay;
    private final SliderSetting renderDuration;
    private final ColorSetting renderColor;
    private final BooleanSetting ghostHand;
    
    private int delayTimer = 0;
    private boolean hasPlaced = false;
    private final List<BlockPos> targetPositions = new ArrayList<>();
    private long renderStartTime = -1L;
    
    public AutoDick() {
        super("AutoDick", Category.Misc);
        this.setChinese("自动迪克");
        
        this.placeDelay = this.add(new SliderSetting("PlaceDelay", 5, 0, 20, 1));
        this.renderDuration = this.add(new SliderSetting("RenderDuration", 40, 0, 100, 1));
        this.renderColor = this.add(new ColorSetting("RenderColor", new Color(255, 0, 0, 100)));
        // 添加鬼手选项：开启后放置时不挥动手臂，用于绕过 Grim 等反作弊的检测
        this.ghostHand = this.add(new BooleanSetting("GhostHand", false));
    }
    
    @Override
    public void onEnable() {
        super.onEnable();
        this.delayTimer = 0;
        this.hasPlaced = false;
        this.targetPositions.clear();
        this.renderStartTime = System.currentTimeMillis();
        
        if (mc.player == null || mc.player.isCreative()) {
            this.disable();
            return;
        }
        
        BlockPos playerPos = mc.player.getBlockPos();
        Direction facing = mc.player.getHorizontalFacing();
        
        List<BlockPos> offsets = getStructureOffsets();
        for (BlockPos offset : offsets) {
            BlockPos rotated = rotateOffset(offset, facing);
            BlockPos worldPos = playerPos.add(rotated);
            this.targetPositions.add(worldPos);
        }
    }
    
    @Override
    public void onDisable() {
        super.onDisable();
        this.targetPositions.clear();
        this.renderStartTime = -1L;
    }
    
    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (hasPlaced) return;
        
        if (delayTimer > 0) {
            delayTimer--;
            return;
        }
        
        if (mc.player == null) {
            this.disable();
            return;
        }
        
        for (BlockPos pos : targetPositions) {
            if (BlockUtil.canPlace(pos)) {
                // 根据 GhostHand 设置决定是否挥动：开启鬼手则不挥动 (false)
                BlockUtil.placeBlock(pos, false, !this.ghostHand.getValue());
                delayTimer = placeDelay.getValueInt();
                return;
            }
        }
        
        hasPlaced = true;
        this.disable();
    }
    
    @EventListener
    public void onRender(Render3DEvent event) {
        if (targetPositions.isEmpty() || renderStartTime == -1L) return;
        
        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - renderStartTime;
        int durationMs = renderDuration.getValueInt() * 50;
        
        if (durationMs == 0) return;
        
        double progress = Math.min((double) elapsed / durationMs, 1.0);
        
        for (BlockPos pos : targetPositions) {
            double x1 = pos.getX() + 0.5 - (0.5 * progress);
            double y1 = pos.getY() + 0.5 - (0.5 * progress);
            double z1 = pos.getZ() + 0.5 - (0.5 * progress);
            double x2 = pos.getX() + 0.5 + (0.5 * progress);
            double y2 = pos.getY() + 0.5 + (0.5 * progress);
            double z2 = pos.getZ() + 0.5 + (0.5 * progress);
            
            Box box = new Box(x1, y1, z1, x2, y2, z2);
            event.drawBox(box, renderColor.getValue());
        }
    }
    
    private List<BlockPos> getStructureOffsets() {
        List<BlockPos> offsets = new ArrayList<>();
        offsets.add(new BlockPos(-1, 0, -2));
        offsets.add(new BlockPos(1, 0, -2));
        offsets.add(new BlockPos(0, 1, -2));
        offsets.add(new BlockPos(0, 2, -2));
        return offsets;
    }
    
    private BlockPos rotateOffset(BlockPos offset, Direction facing) {
        switch (facing) {
            case NORTH:
                return offset;
            case SOUTH:
                return new BlockPos(-offset.getX(), offset.getY(), -offset.getZ());
            case WEST:
                return new BlockPos(-offset.getZ(), offset.getY(), -offset.getX());
            case EAST:
                return new BlockPos(offset.getZ(), offset.getY(), offset.getX());
            default:
                return offset;
        }
    }
}
