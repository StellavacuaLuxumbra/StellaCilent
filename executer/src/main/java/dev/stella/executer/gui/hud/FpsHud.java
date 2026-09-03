package dev.stella.executer.gui.hud;

import dev.stella.executer.gui.render.Render2DUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class FpsHud extends HudElement {

    public FpsHud() {
        super(4, 40);
    }

    @Override
    public void render(DrawContext ctx, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.textRenderer == null) return;

        int fps = mc.getCurrentFps();
        String fpsText = "FPS: " + fps;

        int textWidth = mc.textRenderer.getWidth(fpsText);
        int bgWidth = textWidth + 10;
        int bgHeight = 12;

        int bgColor = 0xB41E1E1E;
        Render2DUtil.fill(ctx, x, y, x + bgWidth, y + bgHeight, bgColor);

        // color by fps
        int fpsColor;
        if (fps >= 60) fpsColor = 0xFF55FF55;
        else if (fps >= 30) fpsColor = 0xFFFFFF55;
        else fpsColor = 0xFFFF5555;

        ctx.drawTextWithShadow(mc.textRenderer, fpsText, x + 5, y + 2, fpsColor);
    }

    @Override
    public int getWidth() { return 60; }

    @Override
    public int getHeight() { return 12; }
}
