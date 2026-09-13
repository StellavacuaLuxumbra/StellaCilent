package dev.stella.executer.gui;

import dev.stella.executer.gui.setting.*;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    private static ModuleManager instance;
    private final List<Module> modules = new ArrayList<>();

    private ModuleManager() {
        // Combat
        Module killAura = new Module("KillAura", Category.COMBAT, 1);
        killAura.add(new SliderSetting("Range", 4.0, 1.0, 6.0, 0.1, ""));
        killAura.add(new SliderSetting("CPS", 10.0, 1.0, 20.0, 1.0, ""));
        killAura.add(new EnumSetting<>("Mode", KillAuraMode.SINGLE));
        killAura.add(new BooleanSetting("Players Only", true));
        modules.add(killAura);

        // Movement
        Module speed = new Module("Speed", Category.MOVEMENT, 3);
        speed.add(new SliderSetting("Speed", 1.5, 0.5, 5.0, 0.1, "x"));
        speed.add(new EnumSetting<>("Mode", SpeedMode.VANILLA));
        modules.add(speed);

        Module fly = new Module("Fly", Category.MOVEMENT, 4);
        fly.add(new SliderSetting("Speed", 2.0, 0.5, 10.0, 0.1, "x"));
        fly.add(new EnumSetting<>("Mode", FlyMode.VANILLA));
        modules.add(fly);

        Module scaffold = new Module("Scaffold", Category.MOVEMENT, 5);
        scaffold.add(new BooleanSetting("Rotate", true));
        scaffold.add(new BooleanSetting("Swing", true));
        modules.add(scaffold);

        Module sprint = new Module("Sprint", Category.MOVEMENT, 11);
        sprint.add(new EnumSetting<>("Mode", SprintMode.LEGIT));
        modules.add(sprint);

        // Render
        Module esp = new Module("ESP", Category.RENDER, 0);
        esp.add(new SliderSetting("Range", 16.0, 1.0, 64.0, 0.5, "blocks"));
        esp.add(new EnumSetting<>("Mode", EspMode.BOX));
        esp.add(new ColorSetting("Color", 0x00FF00));
        modules.add(esp);

        Module fullbright = new Module("Fullbright", Category.RENDER, 2);
        fullbright.add(new SliderSetting("Brightness", 1.0, 0.0, 1.0, 0.1, ""));
        modules.add(fullbright);

        Module itemEsp = new Module("ItemESP", Category.RENDER, 9);
        itemEsp.add(new SliderSetting("Range", 16.0, 1.0, 64.0, 0.5, "blocks"));
        itemEsp.add(new ColorSetting("Color", 0xFFFF00));
        modules.add(itemEsp);

        Module tracers = new Module("Tracers", Category.RENDER, 10);
        tracers.add(new SliderSetting("Range", 64.0, 1.0, 128.0, 1.0, "blocks"));
        tracers.add(new ColorSetting("Color", 0xFF0000));
        modules.add(tracers);

        // Player
        Module noFall = new Module("NoFall", Category.PLAYER, 6);
        noFall.add(new EnumSetting<>("Mode", NoFallMode.SPOOF));
        modules.add(noFall);

        Module autoTotem = new Module("AutoTotem", Category.PLAYER, 7);
        autoTotem.add(new SliderSetting("Health", 8.0, 1.0, 20.0, 0.5, ""));
        modules.add(autoTotem);

        Module chestStealer = new Module("ChestStealer", Category.PLAYER, 8);
        chestStealer.add(new SliderSetting("Delay", 100.0, 0.0, 500.0, 10.0, "ms"));
        chestStealer.add(new BooleanSetting("Auto Armor", true));
        modules.add(chestStealer);

        // Misc
        // (add misc modules here as needed)

        // === New modules (SunCat parity) ===

        // Combat
        modules.add(new dev.stella.executer.modules.VelocityModule());
        modules.add(new dev.stella.executer.modules.CriticalsModule());

        // Movement
        modules.add(new dev.stella.executer.modules.StepModule());

        // Player
        modules.add(new dev.stella.executer.modules.FastFallModule());
        modules.add(new dev.stella.executer.modules.FreecamModule());
        modules.add(new dev.stella.executer.modules.TimerModule());

        // Render
        modules.add(new dev.stella.executer.modules.NameTagsModule());
        modules.add(new dev.stella.executer.modules.HoleEspModule());
        modules.add(new dev.stella.executer.modules.NoRenderModule());
        modules.add(new dev.stella.executer.modules.CrosshairModule());

        // Combat - NCP Bypass
        modules.add(new dev.stella.executer.modules.CrystalAuraModule());
        modules.add(new dev.stella.executer.modules.SelfTrapModule());
        modules.add(new dev.stella.executer.modules.SurroundModule());

        // Misc
        modules.add(new dev.stella.executer.modules.AutoLogModule());
    }

    public static ModuleManager getInstance() {
        if (instance == null) instance = new ModuleManager();
        return instance;
    }

    public List<Module> getModules() {
        return modules;
    }

    public List<Module> getModulesByCategory(Category cat) {
        List<Module> result = new ArrayList<>();
        for (Module m : modules) {
            if (m.getCategory() == cat) result.add(m);
        }
        return result;
    }

    public Module getModule(String name) {
        for (Module m : modules) {
            if (m.getName().equalsIgnoreCase(name)) return m;
        }
        return null;
    }

    public Module getModuleById(int id) {
        for (Module m : modules) {
            if (m.getModuleId() == id) return m;
        }
        return null;
    }

    // enum definitions
    public enum KillAuraMode { SINGLE, SWITCH }
    public enum SpeedMode { VANILLA, PACKET }
    public enum FlyMode { VANILLA, PACKET, CREATIVE }
    public enum SprintMode { LEGIT, RAGE }
    public enum EspMode { BOX, OUTLINE, TWO_D }
    public enum NoFallMode { SPOOF, BUNDLE }
    public enum SelfTrapMode { HEAD, FULL }
}
