/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  by.radioegor146.nativeobfuscator.Native
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.client.sound.PositionedSoundInstance
 *  net.minecraft.client.sound.SoundInstance
 *  net.minecraft.client.util.math.MatrixStack
 *  net.minecraft.entity.effect.StatusEffect
 *  net.minecraft.entity.effect.StatusEffectInstance
 *  net.minecraft.registry.entry.RegistryEntry
 *  net.minecraft.sound.SoundEvents
 *  org.lwjgl.glfw.GLFW
 *  org.lwjgl.opengl.GL11
 */
package dev.stella.core.impl;

import dev.stella.stella;
import dev.stella.api.events.impl.Render2DEvent;
import dev.stella.api.events.impl.Render3DEvent;
import dev.stella.api.utils.Wrapper;
import dev.stella.api.utils.path.BaritoneUtil;
import dev.stella.api.utils.render.Render2DUtil;
import dev.stella.api.utils.render.TextUtil;
import dev.stella.mod.Mod;
import dev.stella.mod.gui.clickgui.ClickGuiScreen;
import dev.stella.mod.modules.HudModule;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.impl.client.*;
import dev.stella.mod.modules.impl.combat.*;
import dev.stella.mod.modules.impl.player.*;
import dev.stella.mod.modules.impl.exploit.*;
import dev.stella.mod.modules.impl.misc.*;
import dev.stella.mod.modules.impl.client.hud.*;
import dev.stella.mod.modules.impl.movement.*;
import dev.stella.mod.modules.impl.render.*;
import dev.stella.mod.modules.settings.Setting;
import dev.stella.mod.modules.settings.impl.BindSetting;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import org.lwjgl.opengl.GL11;

public class ModuleManager
        implements Wrapper {
    private final ArrayList<Module> modules = new ArrayList();
    private final ArrayList<ToggleBanner> toggleBanners = new ArrayList();

    public ArrayList<Module> getModules() {
        return this.modules;
    }

    public ModuleManager() {
        this.init();
    }

    public void init() {
        if (BaritoneUtil.loaded) {
            this.addModule(new BaritoneModule());
        }
        this.addModule(new Panic());
        this.addModule(new AutoRegear());
        this.addModule(new AutoTrapDoor());
        // this.addModule(new ModuleLacrymiraAura());
        // this.addModule(new CatCrystal());
        this.addModule(new AutoMace());
        this.addModule(new AntiExplosion());
        this.addModule(new Fonts());
        this.addModule(new NoTerrainScreen());
        this.addModule(new AutoCrystal());
        this.addModule(new PistonCrystal());
        this.addModule(new CombatManager());
        this.addModule(new Ambience());
        this.addModule(new AntiHunger());
        this.addModule(new AntiVoid());
        this.addModule(new AutoWalk());
        this.addModule(new VClip());
        this.addModule(new ExtraTab());
        this.addModule(new AntiWeak());
        this.addModule(new BedCrafter());
        this.addModule(new ChestStealer());
        this.addModule(new AutoGear());
        this.addModule(new Friend());
        this.addModule(new AspectRatio());
        this.addModule(new ChunkESP());
        this.addModule(new Aura());
        this.addModule(new PushCleaner());
        this.addModule(new AutoAnchor());
        this.addModule(new PhaseESP());
        this.addModule(new AutoArmor());
        this.addModule(new Breaker());
        this.addModule(new AutoLog());
        this.addModule(new AutoEZ());
        this.addModule(new SelfTrap());
        this.addModule(new Sorter());
        this.addModule(new AutoMend());
        this.addModule(new AutoPot());
        this.addModule(new AutoPush());
        this.addModule(new Offhand());
        this.addModule(new Nuker());
        this.addModule(new AutoTrap());
        this.addModule(new AutoWeb());
        this.addModule(new AutoDick());
        this.addModule(new Blink());
        this.addModule(new Pull());
        this.addModule(new ChorusControl());
        this.addModule(new BlockStrafe());
        this.addModule(new FastSwim());
        this.addModule(new Blocker());
        this.addModule(new Quiver());
        this.addModule(new BowBomb());
        this.addModule(new BreakESP());
        this.addModule(new Burrow());
        this.addModule(new BurrowAssist());
        this.addModule(new Punctuation());
        this.addModule(new MaceSpoof());
        this.addModule(new CameraClip());
        this.addModule(new ChatAppend());
        this.addModule(new ClickGui());

        this.addModule(new InfiniteTrident());
        this.addModule(new SpearGod());
        this.addModule(new LavaFiller());
        this.addModule(new AntiPhase());
        this.addModule(new Clip());
        this.addModule(new GAntiLag());
        this.addModule(new IRC());
        this.addModule(new ItemCounterHudModule("Items", "\u7269\u54c1", 100, 100));
        this.addModule(new Fov());
        this.addModule(new Criticals());
        this.addModule(new CevBreaker());
        this.addModule(new Crosshair());
        this.addModule(new Chams());
        this.addModule(new AntiPacket());
        this.addModule(new AutoReconnect());
        this.addModule(new ESP());
        this.addModule(new HoleESP());
        this.addModule(new Tracers());
        this.addModule(new MovementSync());
        this.addModule(new EFly());
        this.addModule(new ElytraFly());
        this.addModule(new ChestSwap());
        this.addModule(new FlyOnFirstTime());

        this.addModule(new TpBot());
        this.addModule(new TargetFollow());
        this.addModule(new PacketLogger());
        this.addModule(new TeleportLogger());
        this.addModule(new SkinFlicker());
        this.addModule(new EntityControl());
        this.addModule(new NameTags());
        this.addModule(new ItemNametags());
        this.addModule(new ShulkerViewer());
        this.addModule(new MioFriendSync());
        this.addModule(new PopEz());
        this.addModule(new PingSpoof());
        this.addModule(new FakePlayer());
        this.addModule(new Spammer());
        // this.addModule(new Printer()); // 暫時禁用，Litematica API 方法不匹配
        this.addModule(new WebCleaner());
        this.addModule(new MotionCamera());
        this.addModule(new HighLight());
        this.addModule(new FastFall());
        this.addModule(new FastWeb());
        this.addModule(new Flatten());
        this.addModule(new Fly());
        this.addModule(new LongJump());
        this.addModule(new Yaw());
        this.addModule(new Freecam());
        this.addModule(new FreeLook());
        this.addModule(new TimerModule());
        this.addModule(new Tips());
        this.addModule(new ClientSetting());
        this.addModule(new ColorsModule());
        this.addModule(new HudSetting());
        this.addModule(new TextRadar());
        this.addModule(new ArmorHudModule());
        this.addModule(new WaterMarkHudModule());
        this.addModule(new ArrayListHudModule());
        this.addModule(new CoordsHudModule());
        this.addModule(new InfoHudModule());
        this.addModule(new WelcomeHudModule());
        this.addModule(new DirectionHudModule());
        this.addModule(new BiomeHudModule());
        this.addModule(new FpsHudModule());
        this.addModule(new PacketHudModule());
        this.addModule(new TargetHudModule());
        this.addModule(new DynamicIslandHud());
        this.addModule(new NoResourcePack());
        this.addModule(new RocketExtend());
        this.addModule(new HoleFiller());
        this.addModule(new HoleSnap());
        this.addModule(new LogoutSpots());
        this.addModule(new AutoTool());
        this.addModule(new Trajectories());
        this.addModule(new KillEffect());
        this.addModule(new AutoPearl());
        this.addModule(new AntiEffects());
        this.addModule(new AntiLag());
        this.addModule(new NoFall());
        this.addModule(new NoFallSneak());
        this.addModule(new NoRender());
        this.addModule(new NoSlow());
        this.addModule(new NoSound());
        this.addModule(new AirPlace());
        this.addModule(new MiddleClick());
        this.addModule(new Xray());
        this.addModule(new PacketEat());
        this.addModule(new PacketFly());
        this.addModule(new PacketMine());
        this.addModule(new PacketControl());
        this.addModule(new Phase());
        this.addModule(new PlaceRender());
        this.addModule(new InteractTweaks());
        this.addModule(new PopChams());
        this.addModule(new Replenish());
        this.addModule(new ServerLagger());
        this.addModule(new Scaffold());
        this.addModule(new ShaderModule());
        this.addModule(new AntiCrawl());
        this.addModule(new AntiRegear());
        this.addModule(new SafeWalk());
        this.addModule(new NoJumpDelay());
        this.addModule(new Speed());
        this.addModule(new HighJump());
        this.addModule(new Sprint());
        this.addModule(new Strafe());
        this.addModule(new Step());
        this.addModule(new Surround());
        this.addModule(new SuperVClip());
        this.addModule(new TotemParticle());
        this.addModule(new Velocity());
        this.addModule(new ViewModel());
        this.addModule(new XCarry());
        this.addModule(new Zoom());
        this.addModule(new StellaBridge());
        this.modules.sort(Comparator.comparing(Mod::getName));
    }

    public void onKeyReleased(int eventKey) {
        if (eventKey == -1 || eventKey == 0) {
            return;
        }
        this.handleKeyEvent(eventKey, false);
    }

    public void onKeyPressed(int eventKey) {
        if (eventKey == -1 || eventKey == 0) {
            return;
        }
        this.handleKeyEvent(eventKey, true);
    }

    private void handleKeyEvent(int key, boolean isPressed) {
        for (Module module : this.modules) {
            BindSetting bindSetting = module.getBindSetting();
            if (bindSetting.getValue() == key) {
                if (isPressed && ModuleManager.mc.currentScreen == null) {
                    module.toggle();
                    bindSetting.holding = true;
                } else if (!isPressed && bindSetting.isHoldEnable() && bindSetting.holding) {
                    module.toggle();
                    bindSetting.holding = false;
                }
            }
            for (Setting setting : module.getSettings()) {
                BindSetting bind;
                if (!(setting instanceof BindSetting) || (bind = (BindSetting)setting).getValue() != key) continue;
                bind.setPressed(isPressed);
            }
        }
    }

    public void onLogin() {
        for (Module module : this.modules) {
            if (!module.isOn()) continue;
            module.onLogin();
        }
    }

    public void onLogout() {
        for (Module module : this.modules) {
            if (!module.isOn()) continue;
            module.onLogout();
        }
    }

    public void onRender2D(DrawContext drawContext) {
        boolean skipHudModules = false;
        if (ModuleManager.mc.currentScreen instanceof ClickGuiScreen) {
            ClickGuiScreen screen = (ClickGuiScreen)ModuleManager.mc.currentScreen;
            boolean hudPage = screen.getPage() == ClickGuiScreen.Page.Hud;
            ClickGui gui = ClickGui.getInstance();
            boolean blurHud = gui != null && gui.blur.getValue() && screen.getPage() != ClickGuiScreen.Page.Hud;
            skipHudModules = hudPage || blurHud;
        }
        block5: {
            for (Module module : this.modules) {
                if (!module.isOn()) continue;
                if (skipHudModules && module instanceof HudModule) continue;
                try {
                    module.onRender2D(drawContext, mc.getRenderTickCounter().getTickDelta(true));
                }
                catch (Exception e) {
                    e.printStackTrace();
                    if (!ClientSetting.INSTANCE.debug.getValue()) continue;
                    CommandManager.sendMessage("\u00a74An error has occurred (" + module.getName() + " [onRender2D]) Message: [" + e.getMessage() + "]");
                }
            }
            try {
                stella.EVENT_BUS.post(Render2DEvent.get(drawContext, mc.getRenderTickCounter().getTickDelta(true)));
            }
            catch (Exception e) {
                e.printStackTrace();
                if (!ClientSetting.INSTANCE.debug.getValue()) break block5;
                CommandManager.sendMessage("\u00a74An error has occurred (Render3DEvent) Message: [" + e.getMessage() + "]");
            }
        }
        this.renderToggleBanners(drawContext);
    }

    public void render3D(MatrixStack matrices) {
        block5: {
            GL11.glEnable((int)2848);
            for (Module module : this.modules) {
                if (!module.isOn()) continue;
                try {
                    module.onRender3D(matrices);
                }
                catch (Exception e) {
                    e.printStackTrace();
                    if (!ClientSetting.INSTANCE.debug.getValue()) continue;
                    CommandManager.sendMessage("\u00a74An error has occurred (" + module.getName() + " [onRender3D]) Message: [" + e.getMessage() + "]");
                }
            }
            try {
                stella.EVENT_BUS.post(Render3DEvent.get(matrices, mc.getRenderTickCounter().getTickDelta(true)));
            }
            catch (Exception e) {
                e.printStackTrace();
                if (!ClientSetting.INSTANCE.debug.getValue()) break block5;
                CommandManager.sendMessage("\u00a74An error has occurred (Render3DEvent) Message: [" + e.getMessage() + "]");
            }
        }
        GL11.glDisable((int)2848);
    }

    public void showToggleBanner(Module module, boolean enabled) {
        return;
    }

    private void renderToggleBanners(DrawContext ctx) {
        this.toggleBanners.clear();
    }

    private void renderPotionListLegacy(DrawContext ctx) {
        if (ModuleManager.mc.player == null) {
            return;
        }
        int margin = 14;
        int startX = margin + 2;
        int startY = margin + 92;
        int pillH = 14;
        int pillPad = 6;
        int idx = 0;
        for (StatusEffectInstance se : ModuleManager.mc.player.getStatusEffects()) {
            String name = ((StatusEffect)se.getEffectType().value()).getName().getString();
            int ticks = se.getDuration();
            int totalSec = Math.max(0, ticks / 20);
            int mm = totalSec / 60;
            int ss = totalSec % 60;
            String time = String.format("%d:%02d", mm, ss);
            String text = name + " " + time;
            boolean customFont = FontManager.isCustomFontEnabled();
            int tw = customFont ? (int)FontManager.ui.getWidth(text) : (int)TextUtil.getWidth(text);
            int pillW = tw + pillPad * 2;
            int x = startX;
            int y = startY + idx * (pillH + 4);
            int keyCodec = 180;
            Render2DUtil.drawRoundedRect(ctx.getMatrices(), x, y, pillW, pillH, 4.0f, new Color(255, 255, 255, keyCodec));
            Render2DUtil.drawRoundedStroke(ctx.getMatrices(), x, y, pillW, pillH, 4.0f, new Color(220, 224, 230, 160), 48);
            int tx = x + pillPad;
            double ty = (double)y + (double)((float)pillH - (customFont ? FontManager.ui.getFontHeight() : TextUtil.getHeight())) / 2.0;
            TextUtil.drawString(ctx, text, tx, ty, new Color(30, 30, 30).getRGB(), customFont);
            if (++idx < 5) continue;
            break;
        }
    }

    public void addModule(Module module) {
        this.modules.add(module);
    }

    public Module getModuleByName(String string) {
        for (Module module : this.modules) {
            if (!module.getName().equalsIgnoreCase(string)) continue;
            return module;
        }
        return null;
    }

    private static class ToggleBanner {
        public final String name;
        public final boolean enabled;
        public final long start;

        public ToggleBanner(String name, boolean enabled, long start) {
            this.name = name;
            this.enabled = enabled;
            this.start = start;
        }
    }
}