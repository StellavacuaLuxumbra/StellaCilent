package dev.stella.mod.modules.impl.combat;

import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.UpdateEvent;
import dev.stella.api.utils.math.Timer;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import dev.stella.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * WebCleaner - 使用序列号发包机制瞬间破坏蜘蛛网
 */
public class WebCleaner extends Module {
    public static WebCleaner INSTANCE;

    private final SliderSetting range = add(new SliderSetting("Range", 4.0, 0.0, 6.0));
    private final SliderSetting delay = add(new SliderSetting("Delay", 50.0, 0.0, 200.0));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", true));
    private final BooleanSetting onlyNearPlayer = add(new BooleanSetting("OnlyNearPlayer", false));

    private final Timer timer = new Timer();

    public WebCleaner() {
        super("WebCleaner", Category.Combat);
        this.setChinese("蜘蛛网清除");
        INSTANCE = this;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (Module.nullCheck()) {
            return;
        }
        if (!timer.passedMs((long)delay.getValue())) return;

        BlockPos closestWeb = null;
        double closestDist = Double.MAX_VALUE;

        // 遍历范围寻找蜘蛛网
        int radius = (int) Math.ceil(range.getValue());
        BlockPos playerPos = mc.player.getBlockPos();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = playerPos.add(x, y, z);

                    // 检测蜘蛛网
                    if (mc.world.getBlockState(checkPos).getBlock() == Blocks.COBWEB) {
                        double dist = checkPos.toCenterPos().distanceTo(mc.player.getEyePos());
                        if (dist <= range.getValue() && dist < closestDist) {

                            // 如果开启了只清除玩家附近的网，检查是否有敌人在附近
                            if (onlyNearPlayer.getValue()) {
                                boolean hasEnemy = false;
                                for (var p : mc.world.getPlayers()) {
                                    if (p != mc.player && p.isAlive() && p.distanceTo(mc.player) <= range.getValue()) {
                                        hasEnemy = true;
                                        break;
                                    }
                                }
                                if (!hasEnemy) continue;
                            }

                            closestDist = dist;
                            closestWeb = checkPos;
                        }
                    }
                }
            }
        }

        if (closestWeb != null) {
            breakWeb(closestWeb);
            timer.reset();
        }
    }

    private void breakWeb(BlockPos pos) {
        // 使用序列号发包机制，这样才能真正破坏方块
        // 必须先用 START_DESTROY_BLOCK 开始破坏，然后用 STOP_DESTROY_BLOCK 结束
        // 两者需要使用相同的序列号
        WebCleaner.sendSequencedPacket(id -> new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP, id
        ));
        WebCleaner.sendSequencedPacket(id -> new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.UP, id
        ));

        // 挥动手臂
        mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
    }
}
