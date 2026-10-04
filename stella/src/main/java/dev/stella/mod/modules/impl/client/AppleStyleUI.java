/*
 * Apple Style UI Overlay for Stella-Client
 * Inspired by iOS/macOS design language
 */
package dev.stella.mod.modules.impl.client;

import dev.stella.stella;
import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.Render2DEvent;
import dev.stella.api.utils.Wrapper;
import dev.stella.api.utils.math.Animation;
import dev.stella.api.utils.render.ColorUtil;
import dev.stella.api.utils.render.Render2DUtil;
import dev.stella.api.utils.render.TextUtil;
import dev.stella.core.impl.FontManager;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import dev.stella.mod.modules.settings.impl.ColorSetting;
import dev.stella.mod.modules.settings.impl.EnumSetting;
import dev.stella.mod.modules.settings.impl.SliderSetting;
import java.awt.Color;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

public class AppleStyleUI extends Module {
    
    // UI Settings
    public final BooleanSetting enabled = this.add(new BooleanSetting("Enabled", true));
    public final SliderSetting opacity = this.add(new SliderSetting("Opacity", 90, 0, 255, 5));
    public final SliderSetting blurRadius = this.add(new SliderSetting("BlurRadius", 12.0, 0.0, 30.0, 0.5));
    public final SliderSetting cornerRadius = this.add(new SliderSetting("CornerRadius", 16.0, 0.0, 40.0, 0.5));
    
    // Color Settings
    public final ColorSetting backgroundColor = this.add(new ColorSetting("BackgroundColor", new Color(255, 255, 255, 220)));
    public final ColorSetting textColor = this.add(new ColorSetting("TextColor", new Color(30, 30, 30)));
    public final ColorSetting accentColor = this.add(new ColorSetting("AccentColor", new Color(0, 122, 255)));
    
    // Layout Settings
    public final SliderSetting xPosition = this.add(new SliderSetting("XPosition", 20.0, 0.0, 100.0, 1.0));
    public final SliderSetting yPosition = this.add(new SliderSetting("YPosition", 20.0, 0.0, 100.0, 1.0));
    public final SliderSetting width = this.add(new SliderSetting("Width", 320.0, 200.0, 800.0, 10.0));
    public final SliderSetting height = this.add(new SliderSetting("Height", 240.0, 100.0, 600.0, 10.0));
    
    // Animation Settings
    public final BooleanSetting animate = this.add(new BooleanSetting("Animate", true));
    public final SliderSetting animationSpeed = this.add(new SliderSetting("AnimationSpeed", 0.3, 0.0, 1.0, 0.05));
    
    private final Animation animation = new Animation();
    private boolean isHovered = false;
    
    public AppleStyleUI() {
        super("AppleStyleUI", "为Stella-Client添加类似iOS/macOS的苹果风格UI覆盖层", Module.Category.Client);
        this.setChinese("苹果风格UI");
    }
    
    @EventListener
    public void onRender2D(Render2DEvent event) {
        if (!this.enabled.getValue()) return;
        
        DrawContext context = event.drawContext;
        MatrixStack matrices = context.getMatrices();
        
        // Get screen dimensions
        int screenWidth = Wrapper.mc.getWindow().getScaledWidth();
        int screenHeight = Wrapper.mc.getWindow().getScaledHeight();
        
        // Calculate position (percentage-based)
        float x = (float)screenWidth * (this.xPosition.getValueFloat() / 100.0f);
        float y = (float)screenHeight * (this.yPosition.getValueFloat() / 100.0f);
        float w = this.width.getValueFloat();
        float h = this.height.getValueFloat();
        
        // Apply animation if enabled
        if (this.animate.getValue()) {
            float progress = (float) this.animation.get(1.0, 200L, dev.stella.api.utils.math.Easing.Linear);
            x = x * progress;
            y = y * progress;
        }
        
        // Apply blur effect
        if (this.blurRadius.getValueFloat() > 0.1f) {
            stella.BLUR.applyBlur(this.blurRadius.getValueFloat(), x, y, w, h);
        }
        
        // Draw background with rounded corners
        Color bgColor = this.backgroundColor.getValue();
        int bgAlpha = this.opacity.getValueInt();
        Color finalBgColor = new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), bgAlpha);
        
        Render2DUtil.drawRoundedRect(matrices, x, y, w, h, this.cornerRadius.getValueFloat(), finalBgColor);
        
        // Draw border
        Color borderColor = this.accentColor.getValue();
        Render2DUtil.drawRoundedStroke(matrices, x, y, w, h, this.cornerRadius.getValueFloat(), 
            new Color(borderColor.getRed(), borderColor.getGreen(), borderColor.getBlue(), 100), 2);
        
        // Draw title
        String title = "Apple Style UI";
        boolean customFont = FontManager.isCustomFontEnabled();
        boolean shadow = FontManager.isShadowEnabled();
        
        float titleX = x + 20.0f;
        float titleY = y + 30.0f;
        
        TextUtil.drawString(context, title, titleX, titleY, this.textColor.getValue().getRGB(), customFont, shadow);
        
        // Draw status bar style indicator
        float statusBarX = x + 20.0f;
        float statusBarY = y + 10.0f;
        float statusBarW = 100.0f;
        float statusBarH = 8.0f;
        
        Render2DUtil.drawRoundedRect(matrices, statusBarX, statusBarY, statusBarW, statusBarH, 4.0f, 
            new Color(200, 200, 200, 150));
        
        // Draw sample icons (like iOS app icons)
        float iconSize = 48.0f;
        float iconSpacing = 12.0f;
        
        // First row of icons
        drawIcon(context, x + 60.0f, y + 70.0f, iconSize, "⚙️", this.accentColor.getValue().getRGB());
        drawIcon(context, x + 130.0f, y + 70.0f, iconSize, "📱", this.accentColor.getValue().getRGB());
        drawIcon(context, x + 200.0f, y + 70.0f, iconSize, "🌐", this.accentColor.getValue().getRGB());
        
        // Second row of icons
        drawIcon(context, x + 60.0f, y + 130.0f, iconSize, "🎨", this.accentColor.getValue().getRGB());
        drawIcon(context, x + 130.0f, y + 130.0f, iconSize, "⚡", this.accentColor.getValue().getRGB());
        drawIcon(context, x + 200.0f, y + 130.0f, iconSize, "🔒", this.accentColor.getValue().getRGB());
        
        // Draw footer text
        String footer = "Stella-Client • Apple Style";
        float footerX = x + w - (float)TextUtil.getWidth(footer) - 20.0f;
        float footerY = y + h - 15.0f;
        TextUtil.drawString(context, footer, footerX, footerY, 
            new Color(150, 150, 150).getRGB(), customFont, shadow);
    }
    
    private void drawIcon(DrawContext context, float x, float y, float size, String icon, int color) {
        boolean customFont = FontManager.isCustomFontEnabled();
        boolean shadow = FontManager.isShadowEnabled();
        
        // Draw icon background
        Render2DUtil.drawRoundedRect(context.getMatrices(), x, y, size, size, 12.0f, 
            new Color(240, 240, 240, 200));
        
        // Draw icon text
        float iconX = x + (float)((size - TextUtil.getWidth(icon)) / 2.0);
        float iconY = y + size / 2.0f + 5.0f;
        TextUtil.drawString(context, icon, iconX, iconY, color, customFont, shadow);
    }
    
    @Override
    public void onEnable() {
        // Initialize any resources needed
        super.onEnable();
    }
    
    @Override
    public void onDisable() {
        // Cleanup resources
        super.onDisable();
    }
}
