package dev.stella.mod.modules.impl.movement;

import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.impl.*;
import dev.stella.api.events.impl.*;
import dev.stella.api.events.eventbus.*;
import net.minecraft.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.block.BlockState;

public class SuperVClip extends Module
{
    public static SuperVClip INSTANCE;
    public final EnumSetting<Page> page;
    public final EnumSetting<Mode> mode;
    private final SliderSetting height;
    private final BooleanSetting autoDisable;
    private final SliderSetting packets;
    private final SliderSetting offset;
    private final BooleanSetting calculateAuto;
    private final BooleanSetting rotate;
    private final BooleanSetting onlyInBlock;
    private final BooleanSetting onGroundCheck;
    private int tickCount;
    private boolean hasClipped;
    
    public SuperVClip() {
        super("SuperVClip", Category.Movement);
        this.page = this.add(new EnumSetting<Page>("Page", Page.General));
        this.mode = this.add(new EnumSetting<Mode>("Mode", Mode.Normal, () -> this.page.getValue() == Page.General));
        this.height = this.add(new SliderSetting("Height", 100.0, 1.0, 500.0, 1.0, () -> this.page.getValue() == Page.General && (this.mode.getValue() == Mode.Normal || this.mode.getValue() == Mode.LongJump)));
        this.autoDisable = this.add(new BooleanSetting("AutoDisable", true, () -> this.page.getValue() == Page.General));
        this.packets = this.add(new SliderSetting("Packets", 100, 1, 500, () -> this.page.getValue() == Page.Packet));
        this.offset = this.add(new SliderSetting("Offset", 1.0, 0.001, 5.0, 0.001, () -> this.page.getValue() == Page.Packet));
        this.calculateAuto = this.add(new BooleanSetting("CalculateAuto", true, () -> this.page.getValue() == Page.Packet && this.mode.getValue() == Mode.Packet));
        this.rotate = this.add(new BooleanSetting("Rotate", false, () -> this.page.getValue() == Page.Check));
        this.onlyInBlock = this.add(new BooleanSetting("OnlyInBlock", false, () -> this.page.getValue() == Page.Check));
        this.onGroundCheck = this.add(new BooleanSetting("OnGroundCheck", false, () -> this.page.getValue() == Page.Check));
        this.tickCount = 0;
        this.hasClipped = false;
        this.setChinese("\u8d85\u7ea7\u7eb5\u5411\u7a7f\u5899");
        SuperVClip.INSTANCE = this;
    }
    
    @Override
    public void onEnable() {
        this.tickCount = 0;
        this.hasClipped = false;
        if (SuperVClip.mc.player == null || SuperVClip.mc.world == null) {
            this.disable();
            return;
        }
        if (this.onlyInBlock.getValue() && !this.isInBlock()) {

            if (this.autoDisable.getValue()) {
                this.disable();
            }
            return;
        }

    }
    
    @Override
    public void onDisable() {
        this.tickCount = 0;
        this.hasClipped = false;
    }
    
    @EventListener
    public void onUpdate(final UpdateEvent event) {
        if (nullCheck()) {
            return;
        }
        if (this.hasClipped && this.autoDisable.getValue()) {
            this.disable();
            return;
        }
        if (this.hasClipped) {
            return;
        }
        if (this.onlyInBlock.getValue() && !this.isInBlock()) {
            if (this.autoDisable.getValue()) {
                this.disable();
            }
            return;
        }
        if (this.onGroundCheck.getValue() && !SuperVClip.mc.player.isOnGround()) {
            return;
        }
        switch (this.mode.getValue().ordinal()) {
            case 0: {
                this.executeNormalVClip();
                break;
            }
            case 1: {
                this.executePacketVClip();
                break;
            }
            case 2: {
                this.executeGlideVClip();
                break;
            }
            case 3: {
                this.executeJumpVClip();
                break;
            }
            case 4: {
                this.executeTeleportVClip();
                break;
            }
            case 5: {
                this.executeLongJumpVClip();
                break;
            }
        }
    }
    
    private void executeNormalVClip() {
        final double posY = mc.player.getY();
        final double targetY = posY + this.height.getValue();
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), targetY, mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch(), true));
        mc.player.setPosition(mc.player.getX(), targetY, mc.player.getZ());
        this.hasClipped = true;
        if (this.autoDisable.getValue()) {
            this.disable();
        }
    }
    
    private void executePacketVClip() {
        final double posX = mc.player.getX();
        final double posY = mc.player.getY();
        final double posZ = mc.player.getZ();
        final double offsetVal = this.offset.getValue();
        int packetCount = (int)this.packets.getValue();
        if (this.calculateAuto.getValue()) {
            final double targetHeight = this.height.getValue();
            packetCount = (int)Math.ceil(targetHeight / offsetVal);
        }
        for (int i = 0; i < packetCount; ++i) {
            final double newY = posY + offsetVal * (i + 1);
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(posX, newY, posZ, mc.player.getYaw(), mc.player.getPitch(), false));
        }
        final double finalY = posY + offsetVal * packetCount;
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(posX, finalY, posZ, mc.player.getYaw(), mc.player.getPitch(), true));
        mc.player.setPosition(posX, finalY, posZ);
        this.hasClipped = true;
        if (this.autoDisable.getValue()) {
            this.disable();
        }
    }
    
    private void executeGlideVClip() {
        if (this.tickCount < 20) {
            final double posY = mc.player.getY();
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), posY + 0.15, mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch(), false));
            mc.player.setPosition(mc.player.getX(), posY + 0.15, mc.player.getZ());
            ++this.tickCount;
        }
        else {
            this.hasClipped = true;
            if (this.autoDisable.getValue()) {
                this.disable();
            }
        }
    }

    private void executeJumpVClip() {
        if (!mc.player.isOnGround()) {
            return;
        }
        mc.player.jump();
        final double posY = mc.player.getY();
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), posY + 0.42, mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch(), false));
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), posY + 0.75, mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch(), false));
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), posY + 1.0, mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch(), true));
        mc.player.setPosition(mc.player.getX(), posY + 1.0, mc.player.getZ());
        this.hasClipped = true;
        if (this.autoDisable.getValue()) {
            this.disable();
        }
    }

    private void executeTeleportVClip() {
        final double targetY = Math.round(mc.player.getY()) + this.height.getValue();
        final double halfY = 0.005;
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), Math.round(mc.player.getY()) - halfY, mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround()));
        mc.player.setPosition(mc.player.getX(), Math.round(mc.player.getY()) - halfY, mc.player.getZ());
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), Math.round(mc.player.getY()) - halfY * 300.0, mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround()));
        mc.player.setPosition(mc.player.getX(), Math.round(mc.player.getY()) - halfY * 300.0, mc.player.getZ());
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), targetY, mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch(), true));
        mc.player.setPosition(mc.player.getX(), targetY, mc.player.getZ());
        this.hasClipped = true;
        if (this.autoDisable.getValue()) {
            this.disable();
        }
    }

    private boolean isInBlock() {
        if (mc.world == null) {
            return false;
        }
        final BlockPos playerPos = BlockPos.ofFloored(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        final BlockPos headPos = BlockPos.ofFloored(mc.player.getX(), mc.player.getY() + 1.0, mc.player.getZ());
        final BlockPos aboveHead = BlockPos.ofFloored(mc.player.getX(), mc.player.getY() + 2.0, mc.player.getZ());
        final BlockState feetBlock = mc.world.getBlockState(playerPos);
        final BlockState headBlock = mc.world.getBlockState(headPos);
        final BlockState aboveBlock = mc.world.getBlockState(aboveHead);
        final boolean feetInBlock = !feetBlock.isAir();
        final boolean headInBlock = !headBlock.isAir();
        final boolean hasSpaceAbove = aboveBlock.isAir();
        return (feetInBlock || headInBlock) && hasSpaceAbove;
    }

    private Vec3d getClipDirection() {
        if (mc.player.isSneaking()) {
            return new Vec3d(0.0, -this.height.getValue(), 0.0);
        }
        return new Vec3d(0.0, this.height.getValue(), 0.0);
    }

    private void executeLongJumpVClip() {
        final double posX = mc.player.getX();
        final double posY = mc.player.getY();
        final double posZ = mc.player.getZ();
        final double targetHeight = this.height.getValue();
        final double batchSize = 50.0;
        final int batches = (int)Math.ceil(targetHeight / batchSize);
        double currentHeight = 0.0;
        for (int batch = 0; batch < batches; ++batch) {
            final double remainingHeight = targetHeight - currentHeight;
            final double thisBatchHeight = Math.min(remainingHeight, batchSize);
            for (int i = 0; i < 5; ++i) {
                final double stepHeight = thisBatchHeight / 5.0;
                final double newY = posY + currentHeight + stepHeight * (i + 1);
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(posX, newY, posZ, mc.player.getYaw(), mc.player.getPitch(), false));
            }
            currentHeight += thisBatchHeight;
        }
        final double finalY = posY + targetHeight;
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(posX, finalY, posZ, mc.player.getYaw(), mc.player.getPitch(), true));
        mc.player.setPosition(posX, finalY, posZ);
        this.hasClipped = true;
        if (this.autoDisable.getValue()) {
            this.disable();
        }
    }
    
    @Override
    public String getInfo() {
        return this.mode.getValue().name();
    }
    
    public enum Page
    {
        General, 
        Packet, 
        Check;
    }
    
    public enum Mode
    {
        Normal, 
        Packet, 
        Glide, 
        Jump, 
        Teleport, 
        LongJump;
    }
}