package dev.stella.api;

import dev.stella.mod.commands.Command;
import dev.stella.mod.modules.HudModule;
import dev.stella.mod.modules.Module;

import java.util.List;

public interface IAddon {
    String getName();

    void onInitialize();

    void onShutdown();

    Package getPackage();

    List<Module> getModules();

    List<Command> getCommands();

    List<HudModule> getHudElements();

    Object getKitManager();
}
