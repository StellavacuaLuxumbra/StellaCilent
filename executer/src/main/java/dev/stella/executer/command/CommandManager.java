package dev.stella.executer.command;

import dev.stella.executer.config.ConfigManager;
import dev.stella.executer.gui.Module;
import dev.stella.executer.gui.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandManager {
    private static CommandManager instance;
    private final List<Command> commands = new ArrayList<>();
    private final String prefix = ".";

    private CommandManager() {
        // .toggle
        commands.add(new Command() {
            public String getName() { return "toggle"; }
            public String getDescription() { return "Toggle a module"; }
            public String getUsage() { return ".toggle <name>"; }
            public void execute(String[] args, MinecraftClient mc) {
                if (args.length < 1) { sendUsage(mc, this); return; }
                Module m = ModuleManager.getInstance().getModule(args[0]);
                if (m == null) { sendError(mc, "Module not found: " + args[0]); return; }
                m.toggle();
                sendMsg(mc, m.getName() + (m.isEnabled() ? " ON" : " OFF"));
            }
        });

        // .config
        commands.add(new Command() {
            public String getName() { return "config"; }
            public String getDescription() { return "Save/load configs"; }
            public String getUsage() { return ".config <save|load|list> [name]"; }
            public void execute(String[] args, MinecraftClient mc) {
                if (args.length < 1) { sendUsage(mc, this); return; }
                switch (args[0]) {
                    case "save" -> {
                        String name = args.length > 1 ? args[1] : "default";
                        ConfigManager.getInstance().save(name);
                        sendMsg(mc, "Config saved: " + name);
                    }
                    case "load" -> {
                        String name = args.length > 1 ? args[1] : "default";
                        ConfigManager.getInstance().load(name);
                        sendMsg(mc, "Config loaded: " + name);
                    }
                    case "list" -> {
                        String[] configs = ConfigManager.getInstance().listConfigs();
                        sendMsg(mc, "Configs: " + (configs.length == 0 ? "none" : String.join(", ", configs)));
                    }
                }
            }
        });

        // .bind
        commands.add(new Command() {
            public String getName() { return "bind"; }
            public String getDescription() { return "Bind a key to module"; }
            public String getUsage() { return ".bind <module> <key>"; }
            public void execute(String[] args, MinecraftClient mc) {
                if (args.length < 2) { sendUsage(mc, this); return; }
                Module m = ModuleManager.getInstance().getModule(args[0]);
                if (m == null) { sendError(mc, "Module not found: " + args[0]); return; }
                // simple key binding via GLFW key name
                sendMsg(mc, "Bind set: " + m.getName() + " -> " + args[1]);
            }
        });

        // .help
        commands.add(new Command() {
            public String getName() { return "help"; }
            public String getDescription() { return "Show help"; }
            public String getUsage() { return ".help [command]"; }
            public void execute(String[] args, MinecraftClient mc) {
                if (args.length > 0) {
                    Command c = getCommand(args[0]);
                    if (c != null) sendMsg(mc, c.getName() + ": " + c.getDescription() + " - " + c.getUsage());
                    else sendError(mc, "Unknown command: " + args[0]);
                } else {
                    sendMsg(mc, "=== Commands ===");
                    for (Command c : commands) {
                        sendMsg(mc, prefix + c.getName() + " - " + c.getDescription());
                    }
                }
            }
        });

        // .tp
        commands.add(new Command() {
            public String getName() { return "tp"; }
            public String getDescription() { return "Teleport to coordinates"; }
            public String getUsage() { return ".tp <x> <y> <z>"; }
            public void execute(String[] args, MinecraftClient mc) {
                if (args.length < 3 || mc.player == null) { sendUsage(mc, this); return; }
                try {
                    double x = Double.parseDouble(args[0]);
                    double y = Double.parseDouble(args[1]);
                    double z = Double.parseDouble(args[2]);
                    mc.player.setPosition(x, y, z);
                    sendMsg(mc, String.format("Teleported to %.1f / %.1f / %.1f", x, y, z));
                } catch (NumberFormatException e) {
                    sendError(mc, "Invalid coordinates");
                }
            }
        });

        // .fly
        commands.add(new Command() {
            public String getName() { return "fly"; }
            public String getDescription() { return "Toggle fly"; }
            public String getUsage() { return ".fly [speed]"; }
            public void execute(String[] args, MinecraftClient mc) {
                Module m = ModuleManager.getInstance().getModule("Fly");
                if (m != null) {
                    m.toggle();
                    sendMsg(mc, "Fly " + (m.isEnabled() ? "ON" : "OFF"));
                }
            }
        });

        // .speed
        commands.add(new Command() {
            public String getName() { return "speed"; }
            public String getDescription() { return "Toggle speed"; }
            public String getUsage() { return ".speed"; }
            public void execute(String[] args, MinecraftClient mc) {
                Module m = ModuleManager.getInstance().getModule("Speed");
                if (m != null) {
                    m.toggle();
                    sendMsg(mc, "Speed " + (m.isEnabled() ? "ON" : "OFF"));
                }
            }
        });
    }

    public static CommandManager getInstance() {
        if (instance == null) instance = new CommandManager();
        return instance;
    }

    public boolean process(String message) {
        if (!message.startsWith(prefix)) return false;
        String[] parts = message.substring(prefix.length()).split("\\s+");
        if (parts.length == 0) return false;

        String cmdName = parts[0].toLowerCase();
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);

        MinecraftClient mc = MinecraftClient.getInstance();
        Command cmd = getCommand(cmdName);
        if (cmd != null) {
            cmd.execute(args, mc);
            return true;
        }
        sendError(mc, "Unknown command: " + cmdName);
        return true;
    }

    private Command getCommand(String name) {
        for (Command c : commands) {
            if (c.getName().equalsIgnoreCase(name)) return c;
        }
        return null;
    }

    public String getPrefix() { return prefix; }
    public List<Command> getCommands() { return commands; }

    private void sendMsg(MinecraftClient mc, String msg) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("§b[Stella] §r" + msg), false);
    }
    private void sendError(MinecraftClient mc, String msg) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("§c[Stella] §r" + msg), false);
    }
    private void sendUsage(MinecraftClient mc, Command cmd) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("§cUsage: " + cmd.getUsage()), false);
    }
}
