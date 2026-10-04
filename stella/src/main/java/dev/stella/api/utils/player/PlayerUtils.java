package dev.stella.api.utils.player;

import dev.stella.api.events.impl.MoveEvent;
import dev.stella.mod.modules.impl.player.MiddleClick;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

public class PlayerUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // 检查是否装备鞘翅
    public static boolean isElytraEquipped() {
        if (mc.player == null) return false;
        ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        return chestStack.getItem() instanceof ElytraItem;
    }

    // 自动装备鞘翅
    public static void equipElytra() {
        if (mc.player == null || !isElytraEquipped()) {
            int slot = InventoryUtil.findItemInventorySlot(Items.ELYTRA);
            if (slot != -1) {
                InventoryUtil.swapArmor(2, slot);
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                mc.player.startFallFlying();
            }
        }
    }

    // 自动卸下鞘翅
    public static void disEquipElytra() {
        if (mc.player == null || isElytraEquipped()) {
            int slot = InventoryUtil.findItemInventorySlot(Items.ELYTRA);
            if (slot != -1) {
                InventoryUtil.swapArmor(2, slot);
            }
        }
    }

    // 检查是否被烟花加速
    public static boolean isBoostedByFirework() {
        if (mc.player == null) return false;
        return !mc.player.getAbilities().flying && !mc.player.isOnGround();
    }

    // 使用烟花
    public static void doFirework() {
        if (mc.player == null || mc.interactionManager == null) return;

        int fireworkHotbar = InventoryUtil.findItemInventorySlotFromZero(Items.FIREWORK_ROCKET);
        if (fireworkHotbar != -1 && fireworkHotbar < 9) {
            switchAndUse(fireworkHotbar);
        } else {
            int fireworkInv = InventoryUtil.findItemInventorySlot(Items.FIREWORK_ROCKET);
            if (fireworkInv == -1) return;

            InventoryUtil.swap(fireworkInv, mc.player.getInventory().selectedSlot);
            use();
            InventoryUtil.swap(fireworkInv, mc.player.getInventory().selectedSlot);
        }
    }

    private static void switchAndUse(int slot) {
        int oldSlot = mc.player.getInventory().selectedSlot;
        InventoryUtil.switchToSlot(slot);
        use();
        InventoryUtil.switchToSlot(oldSlot);
    }

    private static void use() {
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
    }

    // 客户端跳跃
    public static void clientJump() {
        if (mc.player == null) return;
        mc.player.jump();
    }

    // 获取移动朝向
    public static float getMoveYaw(float yaw) {
        if (mc.options.forwardKey.isPressed() && !mc.options.backKey.isPressed()) {
            if (mc.options.leftKey.isPressed() && !mc.options.rightKey.isPressed()) {
                yaw -= 45.0f;
            } else if (mc.options.rightKey.isPressed() && !mc.options.leftKey.isPressed()) {
                yaw += 45.0f;
            }
        } else if (mc.options.backKey.isPressed() && !mc.options.forwardKey.isPressed()) {
            yaw += 180.0f;
            if (mc.options.leftKey.isPressed() && !mc.options.rightKey.isPressed()) {
                yaw += 45.0f;
            } else if (mc.options.rightKey.isPressed() && !mc.options.leftKey.isPressed()) {
                yaw -= 45.0f;
            }
        } else if (mc.options.leftKey.isPressed() && !mc.options.rightKey.isPressed()) {
            yaw -= 90.0f;
        } else if (mc.options.rightKey.isPressed() && !mc.options.leftKey.isPressed()) {
            yaw += 90.0f;
        }
        return MathHelper.wrapDegrees(yaw);
    }

    // 设置速度
    public static void setSpeed(float speed, MoveEvent event) {
        if (mc.player == null) return;

        float forward = mc.player.input.movementForward;
        float strafe = mc.player.input.movementSideways;
        float yaw = mc.player.getYaw();

        if (forward == 0.0f && strafe == 0.0f) {
            event.setX(0.0);
            event.setZ(0.0);
            return;
        }

        double rx = Math.cos(Math.toRadians(yaw + 90.0f));
        double rz = Math.sin(Math.toRadians(yaw + 90.0f));
        event.setX((forward * speed * rx) + (strafe * speed * rz));
        event.setZ((forward * speed * rz) - (strafe * speed * rx));
    }

    // 检查是否正在移动
    public static boolean isMoving() {
        if (mc.player == null) return false;
        return mc.player.input.movementForward != 0.0f || mc.player.input.movementSideways != 0.0f;
    }

    // 设置 Y 轴速度
    public static void setMotionY(double value) {
        if (mc.player != null) {
            mc.player.setVelocity(mc.player.getVelocity().x, value, mc.player.getVelocity().z);
        }
    }

    // ========== 转头功能 ==========

    /**
     * 获取控制模式的水平转头角度（根据移动输入）
     * @param yaw 基础角度
     * @return 计算后的角度
     */
    public static float getControlYaw(float yaw) {
        return getMoveYaw(yaw);
    }

    /**
     * 获取控制模式的垂直转头角度（根据按键状态）
     * @return Pitch 角度
     */
    public static float getControlPitch() {
        if (mc.player == null) return 0.0f;

        boolean isBoosted = isBoostedByFirework();

        if (isBoosted) {
            // 有烟花加速时的角度
            if (mc.options.jumpKey.isPressed() && !mc.options.sneakKey.isPressed()) {
                return isMoving() ? -50.0f : -90.0f;
            } else if (mc.options.sneakKey.isPressed() && !mc.options.jumpKey.isPressed()) {
                return isMoving() ? 50.0f : 90.0f;
            } else {
                return 0.0f;
            }
        } else {
            // 无烟花加速时的角度
            if (mc.options.jumpKey.isPressed() && !mc.options.sneakKey.isPressed()) {
                return -50.0f;
            } else if (mc.options.sneakKey.isPressed() && !mc.options.jumpKey.isPressed()) {
                return 50.0f;
            } else {
                return 0.1f;
            }
        }
    }

    /**
     * 设置旋转（水平+垂直）
     * @param yaw 水平角度
     * @param pitch 垂直角度
     * @param priority 优先级
     */
    public static void setRotation(float yaw, float pitch, int priority) {
        dev.stella.api.utils.eflyRotation.RotationUtils.setRotation(yaw, pitch, priority);
    }

    /**
     * 设置旋转（数组形式）
     * @param angles [yaw, pitch]
     * @param priority 优先级
     */
    public static void setRotation(float[] angles, int priority) {
        if (angles.length >= 2) {
            setRotation(angles[0], angles[1], priority);
        }
    }
}
