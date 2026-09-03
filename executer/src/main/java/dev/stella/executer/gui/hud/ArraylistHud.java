package dev.stella.executer.gui.hud;

import dev.stella.executer.gui.render.ColorUtil;
import dev.stella.executer.gui.render.Render2DUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import dev.stella.executer.gui.Module;
import dev.stella.executer.gui.ModuleManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ArraylistHud extends HudElement {

    public ArraylistHud() {
        super(0, 10);
    }

    @Override
    public void render(DrawContext ctx, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.textRenderer == null) return;

        List<Module> enabled = new ArrayList<>();
        for (Module m : ModuleManager.getInstance().getModules()) {
            if (m.isEnabled()) enabled.add(m);
        }

        // sort by name width descending
        enabled.sort(Comparator.comparingInt((Module m) -> mc.textRenderer.getWidth(m.getName())).reversed());

        int screenWidth = mc.getWindow().getScaledWidth();
        int yOff = y;

        for (int i = 0; i < enabled.size(); i++) {
            Module mod = enabled.get(i);
            String name = mod.getName();
            int textWidth = mc.textRenderer.getWidth(name);
            int boxWidth = textWidth + 8;
            int boxHeight = 12;

            int bgColor = ColorUtil.injectAlpha(0x1E1E1E, 180);
            int accentColor = ColorUtil.pulseColor(0x0078D4, i, Math.max(1, enabled.size()), 1.0f);

            // background
            int bx = screenWidth - boxWidth;
            Render2DUtil.fillRect(ctx, bx, yOff, boxWidth, boxHeight, bgColor);

            // accent bar
            ctx.fill(bx, yOff, bx + 2, yOff + boxHeight, accentColor);

            // text
            ctx.drawTextWithShadow(mc.textRenderer, name, bx + 5, yOff + 2, 0xFFFFFFFF);

            yOff += boxHeight + 1;
        }
    }

    @Override
    public int getWidth() { return 100; }

    @Override
    public int getHeight() { return 12; }
}
