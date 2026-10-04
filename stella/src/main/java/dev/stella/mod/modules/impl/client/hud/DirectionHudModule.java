package dev.stella.mod.modules.impl.client.hud;

import dev.stella.api.utils.render.TextUtil;
import dev.stella.core.impl.FontManager;
import dev.stella.mod.modules.HudModule;
import dev.stella.mod.modules.impl.client.ClickGui;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import java.util.Objects;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

public class DirectionHudModule extends HudModule {
    public static DirectionHudModule INSTANCE;
    private final BooleanSetting showCoords = this.add(new BooleanSetting("Coords", true));
    private final BooleanSetting lowerCase = this.add(new BooleanSetting("LowerCase", false));

    public DirectionHudModule() {
        super("Direction", "", "方向", 2, 70, Corner.LeftTop);
        INSTANCE = this;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (DirectionHudModule.mc.player == null || DirectionHudModule.mc.world == null) {
            this.clearHudBounds();
            return;
        }

        String direction = this.getDirection();
        String text = direction;
        
        if (this.showCoords.getValue()) {
            int x = DirectionHudModule.mc.player.getBlockX();
            int z = DirectionHudModule.mc.player.getBlockZ();
            text = direction + " | " + x + ", " + z;
        }

        if (this.lowerCase.getValue()) {
            text = text.toLowerCase();
        }

        int w = HudSetting.useFont() ? (int)Math.ceil(FontManager.ui.getWidth(text)) : DirectionHudModule.mc.textRenderer.getWidth(text);
        int h;
        if (HudSetting.useFont()) {
            h = (int)Math.ceil(FontManager.ui.getFontHeight());
        } else {
            Objects.requireNonNull(DirectionHudModule.mc.textRenderer);
            h = 9;
        }

        int x = this.getHudRenderX(w);
        int y = this.getHudRenderY(h);

        int color = this.getHudColor(0.0);
        TextUtil.drawString(context, text, x, y, color, HudSetting.useFont(), HudSetting.useShadow());

        this.setHudBounds(x, y, Math.max(1, w), Math.max(1, h));
    }

    private String getDirection() {
        float yaw = DirectionHudModule.mc.player.getYaw();
        yaw = yaw < 0.0f ? yaw + 360.0f : yaw;
        yaw = yaw % 360.0f;
        
        int segment = (int)((double)(yaw + 22.5f) / 45.0) % 8;
        String[] directions = new String[]{"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        
        return directions[segment];
    }

    private int getHudColor(double delay) {
        ClickGui gui = ClickGui.getInstance();
        return gui == null ? -1 : gui.getColor(delay).getRGB();
    }
}
