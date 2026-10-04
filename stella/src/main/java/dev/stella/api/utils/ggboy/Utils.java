package dev.stella.api.utils.ggboy;

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.StringUtils;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utils 工具类
 * 功能：提供各种实用工具方法（附魔获取、距离计算、按键名称等）
 */
public class Utils implements IMinecraft {

    /**
     * 将名称转换为标题格式（如 "hello_world" -> "Hello World"）
     */
    public static String nameToTitle(String name) {
        return Arrays.stream(name.split("_"))
                .map(StringUtils::capitalize)
                .collect(Collectors.joining(" "));
    }

    /**
     * 计算两点之间的平方距离
     */
    public static double squaredDistance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dX = x2 - x1;
        double dY = y2 - y1;
        double dZ = z2 - z1;
        return dX * dX + dY * dY + dZ * dZ;
    }

    /**
     * 检查游戏是否可用（客户端、世界、玩家都不为空）
     */
    public static boolean canUpdate() {
        return mc != null && mc.world != null && mc.player != null;
    }

    /**
     * 将 Minecraft Vec3d 复制到 JOML Vector3d
     */
    public static Vector3d set(Vector3d vec, Vec3d v) {
        vec.x = v.x;
        vec.y = v.y;
        vec.z = v.z;
        return vec;
    }

    /**
     * 获取物品的所有附魔
     * @param itemStack 物品栈
     * @param enchantments 附魔映射（输出）
     */
    public static void getEnchantments(ItemStack itemStack, Object2IntMap<RegistryEntry<Enchantment>> enchantments) {
        enchantments.clear();
        if (!itemStack.isEmpty()) {
            var itemEnchantments = EnchantmentHelper.getEnchantments(itemStack);
            // 遍历附魔映射
            for (var entry : itemEnchantments.getEnchantmentEntries()) {
                enchantments.put(entry.getKey(), entry.getIntValue());
            }
        }
    }

    /**
     * 从附魔映射中获取指定附魔的等级
     */
    public static int getEnchantmentLevel(Object2IntMap<RegistryEntry<Enchantment>> itemEnchantments, RegistryEntry<Enchantment> enchantment) {
        return itemEnchantments.getOrDefault(enchantment, 0);
    }

    /**
     * 直接从物品获取指定附魔的等级
     */
    public static int getEnchantmentLevel(ItemStack itemStack, RegistryEntry<Enchantment> enchantment) {
        if (itemStack.isEmpty()) {
            return 0;
        }
        Object2IntArrayMap<RegistryEntry<Enchantment>> itemEnchantments = new Object2IntArrayMap<>();
        Utils.getEnchantments(itemStack, itemEnchantments);
        return Utils.getEnchantmentLevel(itemEnchantments, enchantment);
    }

    /**
     * 获取按键名称（将 GLFW 键码转换为可读字符串）
     */
    public static String getKeyName(int key) {
        return switch (key) {
            case -1 -> "None";
            case 256 -> "Esc";
            case 96 -> "Grave Accent";
            case 161 -> "World 1";
            case 162 -> "World 2";
            case 32 -> "Space";
            case 290 -> "F1";
            case 291 -> "F2";
            case 292 -> "F3";
            case 293 -> "F4";
            case 294 -> "F5";
            case 295 -> "F6";
            case 296 -> "F7";
            case 297 -> "F8";
            case 298 -> "F9";
            case 299 -> "F10";
            case 300 -> "F11";
            case 301 -> "F12";
            case 302 -> "F13";
            case 303 -> "F14";
            case 304 -> "F15";
            case 305 -> "F16";
            case 306 -> "F17";
            case 307 -> "F18";
            case 308 -> "F19";
            case 309 -> "F20";
            case 310 -> "F21";
            case 311 -> "F22";
            case 312 -> "F23";
            case 313 -> "F24";
            case 314 -> "F25";
            default -> {
                String keyName = GLFW.glfwGetKeyName(key, 0);
                if (keyName == null) {
                    yield "None";
                }
                yield StringUtils.capitalize(keyName);
            }
        };
    }

    /**
     * 获取鼠标按钮名称
     */
    public static String getButtonName(int button) {
        return switch (button) {
            case -1 -> "None";
            case 0 -> "Left Click";
            case 1 -> "Right Click";
            case 2 -> "Middle Click";
            default -> "Mouse " + button;
        };
    }
}
