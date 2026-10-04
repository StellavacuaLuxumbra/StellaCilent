package dev.stella.mod.modules.impl.client.hud;

import dev.stella.stella;
import dev.stella.api.utils.render.ColorUtil;
import dev.stella.api.utils.render.TextUtil;
import dev.stella.core.impl.FontManager;
import dev.stella.mod.modules.HudModule;
import dev.stella.mod.modules.impl.client.ClickGui;
import dev.stella.mod.modules.impl.misc.FakePlayer;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import java.awt.Color;
import java.text.DecimalFormat;
import java.util.Objects;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

public class TargetHudModule extends HudModule {
    public static TargetHudModule INSTANCE;
    private final DecimalFormat df = new DecimalFormat("0.0");
    private final BooleanSetting health = this.add(new BooleanSetting("Health", true));
    private final BooleanSetting armor = this.add(new BooleanSetting("Armor", true));
    private final BooleanSetting distance = this.add(new BooleanSetting("Distance", true));
    private final BooleanSetting lowerCase = this.add(new BooleanSetting("LowerCase", false));

    public TargetHudModule() {
        super("TargetHUD", "", "目标 HUD", 500, 300, Corner.RightBottom);
        INSTANCE = this;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (TargetHudModule.mc.player == null || TargetHudModule.mc.world == null) {
            this.clearHudBounds();
            return;
        }

        PlayerEntity target = this.findTarget();
        if (target == null) {
            this.clearHudBounds();
            return;
        }

        StringBuilder text = new StringBuilder();
        text.append(target.getName().getString());
        
        if (this.health.getValue()) {
            float healthValue = target.getHealth() + target.getAbsorptionAmount();
            text.append(" | HP: ");
            text.append(this.df.format(healthValue));
        }
        
        if (this.armor.getValue()) {
            int armorValue = this.getArmorValue(target);
            text.append(" | Armor: ");
            text.append(armorValue);
        }
        
        if (this.distance.getValue()) {
            float dist = TargetHudModule.mc.player.distanceTo(target);
            text.append(" | Dist: ");
            text.append(this.df.format(dist));
            text.append("m");
        }

        String finalText = text.toString();
        if (this.lowerCase.getValue()) {
            finalText = finalText.toLowerCase();
        }

        int w = HudSetting.useFont() ? (int)Math.ceil(FontManager.ui.getWidth(finalText)) : TargetHudModule.mc.textRenderer.getWidth(finalText);
        int h;
        if (HudSetting.useFont()) {
            h = (int)Math.ceil(FontManager.ui.getFontHeight());
        } else {
            Objects.requireNonNull(TargetHudModule.mc.textRenderer);
            h = 9;
        }

        int x = this.getHudRenderX(w);
        int y = this.getHudRenderY(h);

        int color = this.getHudColor(0.0);
        TextUtil.drawString(context, finalText, x, y, color, HudSetting.useFont(), HudSetting.useShadow());

        this.setHudBounds(x, y, Math.max(1, w), Math.max(1, h));
    }

    private PlayerEntity findTarget() {
        PlayerEntity closestTarget = null;
        double closestDistance = Double.MAX_VALUE;

        for (Entity entity : TargetHudModule.mc.world.getEntities()) {
            // 检查真实玩家
            if (entity instanceof PlayerEntity && entity != TargetHudModule.mc.player && entity.isAlive()) {
                PlayerEntity player = (PlayerEntity) entity;
                double distance = TargetHudModule.mc.player.distanceTo(player);
                
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestTarget = player;
                }
            }
            
            // 检查FakePlayer
            if (FakePlayer.fakePlayer != null && entity == FakePlayer.fakePlayer && entity != TargetHudModule.mc.player && !FakePlayer.fakePlayer.isRemoved()) {
                double distance = TargetHudModule.mc.player.distanceTo(entity);
                
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestTarget = (PlayerEntity) entity;
                }
            }
        }

        return closestTarget;
    }

    private int getArmorValue(LivingEntity entity) {
        int armor = 0;
        for (var stack : entity.getArmorItems()) {
            if (!stack.isEmpty()) {
                armor += stack.getMaxDamage() - stack.getDamage();
            }
        }
        return armor;
    }

    private int getHudColor(double delay) {
        ClickGui gui = ClickGui.getInstance();
        return gui == null ? -1 : gui.getColor(delay).getRGB();
    }
}
