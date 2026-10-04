/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.item.Items
 */
package dev.stella.mod.commands.impl;

import dev.stella.stella;
import dev.stella.stella;
import dev.stella.core.impl.PlayerManager;
import dev.stella.mod.commands.Command;
import dev.stella.mod.gui.windows.WindowsScreen;
import dev.stella.mod.gui.windows.impl.ItemSelectWindow;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.Items;

public class CleanerCommand
extends Command {
    public CleanerCommand() {
        super("cleaner", "[\"\"/name/reset/clear/list] | [add/remove] [name]");
    }

    @Override
    public void runCommand(String[] parameters) {
        if (parameters.length == 0) {
            PlayerManager.screenToOpen = new WindowsScreen(new ItemSelectWindow(stella.CLEANER));
            return;
        }
        switch (parameters[0]) {
            case "reset": {
                stella.CLEANER.clear();
                stella.CLEANER.add(Items.NETHERITE_SWORD.getTranslationKey());
                stella.CLEANER.add(Items.NETHERITE_PICKAXE.getTranslationKey());
                stella.CLEANER.add(Items.NETHERITE_HELMET.getTranslationKey());
                stella.CLEANER.add(Items.NETHERITE_CHESTPLATE.getTranslationKey());
                stella.CLEANER.add(Items.NETHERITE_LEGGINGS.getTranslationKey());
                stella.CLEANER.add(Items.NETHERITE_BOOTS.getTranslationKey());
                stella.CLEANER.add(Items.OBSIDIAN.getTranslationKey());
                stella.CLEANER.add(Items.ENDER_CHEST.getTranslationKey());
                stella.CLEANER.add(Items.ENDER_PEARL.getTranslationKey());
                stella.CLEANER.add(Items.ENCHANTED_GOLDEN_APPLE.getTranslationKey());
                stella.CLEANER.add(Items.EXPERIENCE_BOTTLE.getTranslationKey());
                stella.CLEANER.add(Items.COBWEB.getTranslationKey());
                stella.CLEANER.add(Items.POTION.getTranslationKey());
                stella.CLEANER.add(Items.SPLASH_POTION.getTranslationKey());
                stella.CLEANER.add(Items.TOTEM_OF_UNDYING.getTranslationKey());
                stella.CLEANER.add(Items.END_CRYSTAL.getTranslationKey());
                stella.CLEANER.add(Items.ELYTRA.getTranslationKey());
                stella.CLEANER.add(Items.FLINT_AND_STEEL.getTranslationKey());
                stella.CLEANER.add(Items.PISTON.getTranslationKey());
                stella.CLEANER.add(Items.STICKY_PISTON.getTranslationKey());
                stella.CLEANER.add(Items.REDSTONE_BLOCK.getTranslationKey());
                stella.CLEANER.add(Items.GLOWSTONE.getTranslationKey());
                stella.CLEANER.add(Items.RESPAWN_ANCHOR.getTranslationKey());
                stella.CLEANER.add(Items.ANVIL.getTranslationKey());
                this.sendChatMessage("\u00a7fItems list got reset");
                return;
            }
            case "clear": {
                stella.CLEANER.getList().clear();
                this.sendChatMessage("\u00a7fItems list got clear");
                return;
            }
            case "list": {
                if (stella.CLEANER.getList().isEmpty()) {
                    this.sendChatMessage("\u00a7fItems list is empty");
                    return;
                }
                for (String name : stella.CLEANER.getList()) {
                    this.sendChatMessage("\u00a7a" + name);
                }
                return;
            }
            case "add": {
                if (parameters.length == 2) {
                    stella.CLEANER.add(parameters[1]);
                    this.sendChatMessage("\u00a7f" + parameters[1] + (stella.CLEANER.inList(parameters[1]) ? " \u00a7ahas been added" : " \u00a7chas been removed"));
                    return;
                }
                this.sendUsage();
                return;
            }
            case "remove": {
                if (parameters.length == 2) {
                    stella.CLEANER.remove(parameters[1]);
                    this.sendChatMessage("\u00a7f" + parameters[1] + (stella.CLEANER.inList(parameters[1]) ? " \u00a7ahas been added" : " \u00a7chas been removed"));
                    return;
                }
                this.sendUsage();
                return;
            }
        }
        if (parameters.length == 1) {
            this.sendChatMessage("\u00a7f" + parameters[0] + (stella.CLEANER.inList(parameters[0]) ? " \u00a7ais in whitelist" : " \u00a7cisn't in whitelist"));
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
                if (!input.equalsIgnoreCase(stella.getPrefix() + "cleaner") && !x.toLowerCase().startsWith(input)) continue;
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

