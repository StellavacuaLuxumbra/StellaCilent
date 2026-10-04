package dev.stella.mod.modules.impl.client.hud;

import dev.stella.stella;
import dev.stella.api.utils.render.TextUtil;
import dev.stella.core.impl.FontManager;
import dev.stella.mod.modules.HudModule;
import dev.stella.mod.modules.impl.client.ClickGui;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import java.util.Objects;
import net.minecraft.client.gui.DrawContext;

public class WelcomeHudModule extends HudModule {
    public static WelcomeHudModule INSTANCE;
    private final BooleanSetting showServer = this.add(new BooleanSetting("Server", true));
    private final BooleanSetting showVersion = this.add(new BooleanSetting("Version", true));
    private final BooleanSetting lowerCase = this.add(new BooleanSetting("LowerCase", false));

    public WelcomeHudModule() {
        super("Welcome", "", "欢迎", 2, 50, Corner.LeftTop);
        INSTANCE = this;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (WelcomeHudModule.mc.player == null) {
            this.clearHudBounds();
            return;
        }

        StringBuilder text = new StringBuilder();
        text.append("Welcome, ");
        text.append(WelcomeHudModule.mc.player.getName().getString());
        
        if (this.showServer.getValue()) {
            text.append(" | ");
            if (WelcomeHudModule.mc.isInSingleplayer() || WelcomeHudModule.mc.getCurrentServerEntry() == null) {
                text.append("SinglePlayer");
            } else {
                text.append(WelcomeHudModule.mc.getCurrentServerEntry().address);
            }
        }
        
        if (this.showVersion.getValue()) {
            text.append(" | ");
            text.append(stella.NAME);
            text.append(" ");
            text.append(stella.VERSION);
        }

        String finalText = text.toString();
        if (this.lowerCase.getValue()) {
            finalText = finalText.toLowerCase();
        }

        int w = HudSetting.useFont() ? (int)Math.ceil(FontManager.ui.getWidth(finalText)) : WelcomeHudModule.mc.textRenderer.getWidth(finalText);
        int h;
        if (HudSetting.useFont()) {
            h = (int)Math.ceil(FontManager.ui.getFontHeight());
        } else {
            Objects.requireNonNull(WelcomeHudModule.mc.textRenderer);
            h = 9;
        }

        int x = this.getHudRenderX(w);
        int y = this.getHudRenderY(h);

        int color = this.getHudColor(0.0);
        TextUtil.drawString(context, finalText, x, y, color, HudSetting.useFont(), HudSetting.useShadow());

        this.setHudBounds(x, y, Math.max(1, w), Math.max(1, h));
    }

    private int getHudColor(double delay) {
        ClickGui gui = ClickGui.getInstance();
        return gui == null ? -1 : gui.getColor(delay).getRGB();
    }
}
