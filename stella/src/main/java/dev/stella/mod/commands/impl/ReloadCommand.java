/*
 * Decompiled with CFR 0.152.
 */
package dev.stella.mod.commands.impl;

import dev.stella.stella;
import dev.stella.core.impl.ConfigManager;
import dev.stella.mod.commands.Command;
import java.util.List;

public class ReloadCommand
extends Command {
    public ReloadCommand() {
        super("reload", "");
    }

    @Override
    public void runCommand(String[] parameters) {
        this.sendChatMessage("\u00a7fReloading..");
        stella.CONFIG = new ConfigManager();
        stella.CONFIG.load();
        stella.CLEANER.read();
        stella.XRAY.read();
        stella.TRADE.read();
        stella.FRIEND.read();
    }

    @Override
    public String[] getAutocorrect(int count, List<String> seperated) {
        return null;
    }
}
