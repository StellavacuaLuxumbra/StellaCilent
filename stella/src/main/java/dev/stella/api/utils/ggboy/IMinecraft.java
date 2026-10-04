package dev.stella.api.utils.ggboy;

import net.minecraft.client.MinecraftClient;

/**
 * IMinecraft 接口
 * 功能：提供静态 mc 字段访问 Minecraft 客户端实例
 */
public interface IMinecraft {
    /**
     * Minecraft 客户端实例
     */
    MinecraftClient mc = MinecraftClient.getInstance();
}
