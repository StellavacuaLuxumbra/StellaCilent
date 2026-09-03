package dev.stella.executer.gui.hud;

import dev.stella.executer.gui.render.ColorUtil;
import dev.stella.executer.gui.render.Render2DUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

public class TargetHud extends HudElement {
    private LivingEntity target;

    public TargetHud() {
        super(0, 0);
    }

    @Override
    public void render(DrawContext ctx, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.textRenderer == null) return;

        // find nearest target
        target = findTarget(mc);

        int bgW = 120;
        int bgH = 32;
        int bgX = mc.getWindow().getScaledWidth() - bgW - 4;
        int bgY = 40;

        Render2DUtil.fill(ctx, bgX, bgY, bgX + bgW, bgY + bgH, 0xB41E1E1E);

        if (target != null && target.isAlive()) {
            String name = target.getName().getString();
            float health = target.getHealth();
            float maxHealth = target.getMaxHealth();
            int armor = target.getArmor();

            ctx.drawTextWithShadow(mc.textRenderer, name, bgX + 4, bgY + 3, 0xFFFFFFFF);

            // health bar
            int barW = bgW - 8;
            float healthPct = Math.min(1.0f, health / maxHealth);
            int barColor = healthPct > 0.5f ? 0xFF55FF55 : healthPct > 0.25f ? 0xFFFFFF55 : 0xFFFF5555;
            ctx.fill(bgX + 4, bgY + 13, bgX + 4 + barW, bgY + 16, 0xFF333333);
            ctx.fill(bgX + 4, bgY + 13, bgX + 4 + (int)(barW * healthPct), bgY + 16, barColor);

            String info = String.format("HP: %.0f/%.0f  Armor: %d", health, maxHealth, armor);
            ctx.drawTextWithShadow(mc.textRenderer, info, bgX + 4, bgY + 20, 0xFFAAAAAA);
        } else {
            ctx.drawCenteredTextWithShadow(mc.textRenderer, "No Target", bgX + bgW / 2, bgY + 12, 0xFF888888);
        }
    }

    private LivingEntity findTarget(MinecraftClient mc) {
        double bestDist = 6.0; // max range
        LivingEntity best = null;
        Box box = mc.player.getBoundingBox().expand(6, 6, 6);
        for (LivingEntity e : mc.world.getEntitiesByClass(LivingEntity.class, box,
                entity -> entity != mc.player && entity.isAlive() && !entity.isRemoved())) {
            double dist = mc.player.distanceTo(e);
            if (dist < bestDist) {
                bestDist = dist;
                best = e;
            }
        }
        return best;
    }

    @Override
    public int getWidth() { return 120; }

    @Override
    public int getHeight() { return 32; }
}
