package dev.stella.mod.commands.impl;

import dev.stella.core.impl.KitManager;
import dev.stella.mod.commands.Command;

import java.util.List;

public class KitCommand extends Command {

    public KitCommand() {
        super("kit", " <save/load/del/list> <name>");
    }

    @Override
    public void runCommand(String[] args) {
        if (args.length == 0) {
            sendUsage();
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list":
                listKits();
                break;
            case "save":
                if (args.length < 2) {
                    sendUsage();
                    return;
                }
                saveKit(args[1]);
                break;
            case "load":
                if (args.length < 2) {
                    sendUsage();
                    return;
                }
                loadKit(args[1]);
                break;
            case "del":
            case "delete":
                if (args.length < 2) {
                    sendUsage();
                    return;
                }
                deleteKit(args[1]);
                break;
            default:
                sendUsage();
                break;
        }
    }

    @Override
    public String[] getAutocorrect(int count, List<String> seperated) {
        // count == 1: .kit <subcommand>，正在输入子命令
        if (count == 1) {
            String input = seperated.getLast().toLowerCase();
            List<String> subCommands = List.of("save", "load", "del", "list");
            java.util.ArrayList<String> matches = new java.util.ArrayList<>();
            for (String cmd : subCommands) {
                if (input.isEmpty() || cmd.toLowerCase().startsWith(input)) {
                    matches.add(cmd);
                }
            }
            return matches.toArray(new String[0]);
        }
        // count == 2: .kit load <kitname> / .kit del <kitname>
        if (count == 2 && seperated.size() >= 2) {
            String subCommand = seperated.get(1).toLowerCase();
            if (subCommand.equals("load") || subCommand.equals("del") || subCommand.equals("delete")) {
                String input = seperated.getLast().toLowerCase();
                String[] kits = KitManager.INSTANCE.getKitNames();
                java.util.ArrayList<String> matches = new java.util.ArrayList<>();
                for (String kit : kits) {
                    if (input.isEmpty() || kit.toLowerCase().startsWith(input)) {
                        matches.add(kit);
                    }
                }
                return matches.toArray(new String[0]);
            }
        }
        return new String[0];
    }

    private void listKits() {
        String[] kits = KitManager.INSTANCE.getKitNames();
        String selected = KitManager.INSTANCE.getSelectedKit();
        
        if (kits.length == 0) {
            sendChatMessage("No kits found! Use .kit save <name> to create one.");
            return;
        }
        
        sendChatMessage("Available kits (" + kits.length + "):");
        for (String kit : kits) {
            String marker = kit.equals(selected) ? " §a[Selected]" : "";
            sendChatMessage("  §7-> §f" + kit + marker);
        }
    }

    private void saveKit(String name) {
        if (mc.player == null) {
            sendChatMessage("§cCannot save kit: not in game");
            return;
        }
        
        if (KitManager.INSTANCE.kitExists(name)) {
            sendChatMessage("§cKit '" + name + "' already exists! Use a different name.");
            return;
        }
        
        if (KitManager.INSTANCE.saveKit(name)) {
            sendChatMessage("§aKit '" + name + "' saved successfully!");
        } else {
            sendChatMessage("§cFailed to save kit '" + name + "'");
        }
    }

    private void loadKit(String name) {
        if (!KitManager.INSTANCE.kitExists(name)) {
            sendChatMessage("§cKit '" + name + "' not found!");
            return;
        }
        
        KitManager.INSTANCE.setSelectedKit(name);
        sendChatMessage("§aKit '" + name + "' selected!");
        
        // 触发 AutoGear 装备
        dev.stella.mod.modules.impl.misc.AutoGear autoGear = 
            dev.stella.mod.modules.impl.misc.AutoGear.INSTANCE;
        if (autoGear != null && autoGear.isOn()) {
            autoGear.equipKit();
        }
    }

    private void deleteKit(String name) {
        if (!KitManager.INSTANCE.kitExists(name)) {
            sendChatMessage("§cKit '" + name + "' not found!");
            return;
        }
        
        if (KitManager.INSTANCE.deleteKit(name)) {
            sendChatMessage("§aKit '" + name + "' deleted!");
        } else {
            sendChatMessage("§cFailed to delete kit '" + name + "'");
        }
    }
}
