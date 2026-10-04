/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.block.Blocks
 */
package dev.stella.mod.commands.impl;

import dev.stella.stella;
import dev.stella.core.impl.PlayerManager;
import dev.stella.mod.commands.Command;
import dev.stella.mod.gui.windows.WindowsScreen;
import dev.stella.mod.gui.windows.impl.ItemSelectWindow;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Blocks;

public class XrayCommand
extends Command {
    public XrayCommand() {
        super("xray", "[\"\"/name/reset/clear/list] | [add/remove] [name]");
    }

    @Override
    public void runCommand(String[] parameters) {
        if (parameters.length == 0) {
            PlayerManager.screenToOpen = new WindowsScreen(new ItemSelectWindow(stella.XRAY));
            return;
        }
        switch (parameters[0]) {
            case "reset": {
                stella.XRAY.clear();
                stella.XRAY.add(Blocks.DIAMOND_ORE.getTranslationKey());
                stella.XRAY.add(Blocks.DEEPSLATE_DIAMOND_ORE.getTranslationKey());
                stella.XRAY.add(Blocks.GOLD_ORE.getTranslationKey());
                stella.XRAY.add(Blocks.NETHER_GOLD_ORE.getTranslationKey());
                stella.XRAY.add(Blocks.IRON_ORE.getTranslationKey());
                stella.XRAY.add(Blocks.DEEPSLATE_IRON_ORE.getTranslationKey());
                stella.XRAY.add(Blocks.REDSTONE_ORE.getTranslationKey());
                stella.XRAY.add(Blocks.EMERALD_ORE.getTranslationKey());
                stella.XRAY.add(Blocks.DEEPSLATE_EMERALD_ORE.getTranslationKey());
                stella.XRAY.add(Blocks.DEEPSLATE_REDSTONE_ORE.getTranslationKey());
                stella.XRAY.add(Blocks.COAL_ORE.getTranslationKey());
                stella.XRAY.add(Blocks.DEEPSLATE_COAL_ORE.getTranslationKey());
                stella.XRAY.add(Blocks.ANCIENT_DEBRIS.getTranslationKey());
                stella.XRAY.add(Blocks.NETHER_QUARTZ_ORE.getTranslationKey());
                stella.XRAY.add(Blocks.LAPIS_ORE.getTranslationKey());
                stella.XRAY.add(Blocks.DEEPSLATE_LAPIS_ORE.getTranslationKey());
                this.sendChatMessage("\u00a7fBlocks list got reset");
                return;
            }
            case "clear": {
                stella.XRAY.clear();
                this.sendChatMessage("\u00a7fBlocks list got clear");
                return;
            }
            case "list": {
                if (stella.XRAY.getList().isEmpty()) {
                    this.sendChatMessage("\u00a7fBlocks list is empty");
                    return;
                }
                for (String name : stella.XRAY.getList()) {
                    this.sendChatMessage("\u00a7a" + name);
                }
                return;
            }
            case "add": {
                if (parameters.length == 2) {
                    stella.XRAY.add(parameters[1]);
                    this.sendChatMessage("\u00a7f" + parameters[1] + (stella.XRAY.inWhitelist(parameters[1]) ? " \u00a7ahas been added" : " \u00a7chas been removed"));
                    return;
                }
                this.sendUsage();
                return;
            }
            case "remove": {
                if (parameters.length == 2) {
                    stella.XRAY.remove(parameters[1]);
                    this.sendChatMessage("\u00a7f" + parameters[1] + (stella.XRAY.inWhitelist(parameters[1]) ? " \u00a7ahas been added" : " \u00a7chas been removed"));
                    return;
                }
                this.sendUsage();
                return;
            }
        }
        if (parameters.length == 1) {
            this.sendChatMessage("\u00a7f" + parameters[0] + (stella.XRAY.inWhitelist(parameters[0]) ? " \u00a7ais in whitelist" : " \u00a7cisn't in whitelist"));
            return;
        }
        this.sendUsage();
    }

    @Override
    public String[] getAutocorrect(int count, List<String> seperated) {
        if (count == 1) {
            String input = seperated.getLast().toLowerCase();
            ArrayList<String> correct = new ArrayList<String>();
            List<String> list = List.of("add", "remove", "list", "reset", "clear");
            for (String x : list) {
                if (!input.equalsIgnoreCase(stella.getPrefix() + "xray") && !x.toLowerCase().startsWith(input)) continue;
                correct.add(x);
            }
            int numCmds = correct.size();
            String[] commands = new String[numCmds];
            int i = 0;
            for (String x : correct) {
                commands[i++] = x;
            }
            return commands;
        }
        return null;
    }
}
