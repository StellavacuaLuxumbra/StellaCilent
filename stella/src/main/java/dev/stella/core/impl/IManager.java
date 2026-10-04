package dev.stella.core.impl;

import net.minecraft.client.MinecraftClient;

public interface IManager {
    MinecraftClient mc = MinecraftClient.getInstance();
    
    void init();
    void shutdown();
}
