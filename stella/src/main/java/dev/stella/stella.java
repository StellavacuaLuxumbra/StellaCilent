package dev.stella;

import dev.stella.api.events.eventbus.EventBus;
import dev.stella.api.events.impl.InitEvent;
import dev.stella.core.impl.BlurManager;
import dev.stella.core.impl.BreakManager;
import dev.stella.core.impl.CleanerManager;
import dev.stella.core.impl.CommandManager;
import dev.stella.core.impl.ConfigManager;
import dev.stella.core.impl.FPSManager;
import dev.stella.core.impl.FriendManager;
import dev.stella.core.impl.HudItemManager;
import dev.stella.core.impl.HoleManager;
import dev.stella.core.impl.ModuleManager;
import dev.stella.core.impl.PlayerManager;
import dev.stella.core.impl.PopManager;
import dev.stella.core.impl.RotationManager;
import dev.stella.core.impl.ServerManager;
import dev.stella.core.impl.ShaderManager;
import dev.stella.core.impl.ThreadManager;
import dev.stella.core.impl.TimerManager;
import dev.stella.core.impl.TradeManager;
import dev.stella.core.impl.XrayManager;
import dev.stella.mod.modules.impl.client.ClientSetting;
import java.io.File;
import java.lang.invoke.MethodHandles;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;

public class stella implements ModInitializer {
    public static final String NAME = "Stella Client";
    public static final String VERSION = "3.0.0";
    public static final String CONFIG_DIR = "stella";
    public static final EventBus EVENT_BUS = new EventBus();
    public static HoleManager HOLE;
    public static PlayerManager PLAYER;
    public static TradeManager TRADE;
    public static CleanerManager CLEANER;
    public static HudItemManager HUD_ITEM;
    public static XrayManager XRAY;
    public static ModuleManager MODULE;
    public static CommandManager COMMAND;
    public static ConfigManager CONFIG;
    public static RotationManager ROTATION;
    public static BreakManager BREAK;
    public static PopManager POP;
    public static FriendManager FRIEND;
    public static TimerManager TIMER;
    public static ShaderManager SHADER;
    public static BlurManager BLUR;
    public static FPSManager FPS;
    public static ServerManager SERVER;
    public static ThreadManager THREAD;
    public static dev.stella.core.impl.KitManager KIT;
    public static boolean loaded;
    public static long initTime;
    public static String userId;

    public static String getPrefix() {
        return ClientSetting.INSTANCE.prefix.getValue();
    }

    public static void save() {
        CONFIG.save();
        CLEANER.save();
        FRIEND.save();
        XRAY.save();
        TRADE.save();
        HUD_ITEM.save();
        System.out.println("[stella Client] Saved");
    }

    private void register() {
        EVENT_BUS.registerLambdaFactory((lookupInMethod, klass) -> (MethodHandles.Lookup)lookupInMethod.invoke(null, klass, MethodHandles.lookup()));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (loaded) {
                stella.save();
            }
        }));
    }

    public void onInitialize() {
        this.register();
        MODULE = new ModuleManager();

        // StellaBridge / CombatManager 等特性模块的默认开启状态由
        // ConfigManager.defaultEnabledState() 决定（用户保存的关闭状态优先）
        CONFIG = new ConfigManager();
        HOLE = new HoleManager();
        COMMAND = new CommandManager();
        FRIEND = new FriendManager();
        XRAY = new XrayManager();
        CLEANER = new CleanerManager();
        TRADE = new TradeManager();
        HUD_ITEM = new HudItemManager();
        ROTATION = new RotationManager();
        RotationManager.INSTANCE = ROTATION;
        BREAK = new BreakManager();
        PLAYER = new PlayerManager();
        POP = new PopManager();
        TIMER = new TimerManager();
        SHADER = new ShaderManager();
        BLUR = new BlurManager();
        FPS = new FPSManager();
        SERVER = new ServerManager();
        KIT = new dev.stella.core.impl.KitManager();
        
        initTime = System.currentTimeMillis();
        loaded = true;
        EVENT_BUS.post(new InitEvent());
        
        // 同步加载配置（必需，避免模块状态为空）
        CONFIG.load();
        THREAD = new ThreadManager();
        System.out.println("[stella Client] Config loaded in " + (System.currentTimeMillis() - initTime) + "ms");
        
        File folder = new File(MinecraftClient.getInstance().runDirectory.getPath() + File.separator + CONFIG_DIR + File.separator + "cfg");
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    static {
        loaded = false;
    }
}