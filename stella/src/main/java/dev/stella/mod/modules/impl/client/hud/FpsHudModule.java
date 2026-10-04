package dev.stella.mod.modules.impl.client.hud;

import dev.stella.stella;
import dev.stella.api.utils.render.TextUtil;
import dev.stella.core.impl.FontManager;
import dev.stella.mod.modules.HudModule;
import dev.stella.mod.modules.impl.client.ClickGui;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import java.util.Objects;
import net.minecraft.client.gui.DrawContext;

public class FpsHudModule extends HudModule {
    public static FpsHudModule INSTANCE;
    private final BooleanSetting lowerCase = this.add(new BooleanSetting("LowerCase", false));

    public FpsHudModule() {
        super("FPS", "", "帧数", 2, 30, Corner.RightTop);
        INSTANCE = this;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        String text = "FPS: " + stella.FPS.getFps();
        
        if (this.lowerCase.getValue()) {
            text = text.toLowerCase();
        }

        int w = HudSetting.useFont() ? (int)Math.ceil(FontManager.ui.getWidth(text)) : FpsHudModule.mc.textRenderer.getWidth(text);
        int h;
        if (HudSetting.useFont()) {
            h = (int)Math.ceil(FontManager.ui.getFontHeight());
        } else {
            Objects.requireNonNull(FpsHudModule.mc.textRenderer);
            h = 9;
        }

        int x = this.getHudRenderX(w);
        int y = this.getHudRenderY(h);

        int color = this.getHudColor(0.0);
        TextUtil.drawString(context, text, x, y, color, HudSetting.useFont(), HudSetting.useShadow());

        this.setHudBounds(x, y, Math.max(1, w), Math.max(1, h));
    }

    private int getHudColor(double delay) {
        ClickGui gui = ClickGui.getInstance();
        return gui == null ? -1 : gui.getColor(delay).getRGB();
    }
}
