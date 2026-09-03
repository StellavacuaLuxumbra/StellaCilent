package dev.stella.executer.gui.hud;

import dev.stella.executer.gui.render.ColorUtil;
import dev.stella.executer.gui.render.Render2DUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class WaterMarkHud extends HudElement {

    public WaterMarkHud() {
        super(4, 4);
    }

    @Override
    public void render(DrawContext ctx, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.textRenderer == null) return;

        String text = "Stella Client";
        int textWidth = mc.textRenderer.getWidth(text);
        int bgWidth = textWidth + 14;
        int bgHeight = 14;

        int bgColor = 0xD20078D4;
        Render2DUtil.drawRoundedRect(ctx, x, y, bgWidth, bgHeight, 4, bgColor);

        ctx.drawTextWithShadow(mc.textRenderer, text, x + 7, y + 3, 0xFFFFFFFF);
    }

    @Override
    public int getWidth() { return 100; }

    @Override
    public int getHeight() { return 14; }
}
