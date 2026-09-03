package dev.stella.executer.command;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public interface Command {
    String getName();
    String getDescription();
    String getUsage();
    void execute(String[] args, MinecraftClient mc);
}
