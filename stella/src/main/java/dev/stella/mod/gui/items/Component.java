/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.DrawContext
 */
package dev.stella.mod.gui.items;

import dev.stella.api.utils.math.Animation;
import dev.stella.api.utils.math.Easing;
import dev.stella.api.utils.render.ColorUtil;
import dev.stella.api.utils.render.Render2DUtil;
import dev.stella.core.impl.FontManager;
import dev.stella.mod.Mod;
import dev.stella.mod.gui.clickgui.ClickGuiScreen;
import dev.stella.mod.gui.items.buttons.Button;
import dev.stella.mod.gui.items.buttons.ModuleButton;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.impl.client.ClickGui;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;

public class Component
extends Mod {
    private final List<ModuleButton> items = new ArrayList<ModuleButton>();
    private final Module.Category category;
    public boolean drag;
    protected DrawContext context;
    private int x;
    private int y;
    private float animX;
    private float animY;
    private float mouseMoveOffsetX;
    private float mouseMoveOffsetY;
    private float pageOffsetX;
    private final Animation xAnimation = new Animation();
    private final Animation yAnimation = new Animation();
    private final Animation openAnimation = new Animation();
    private int x2;
    private int y2;
    private int width;
    private int height;
    private boolean open;
    private boolean hidden = false;

    public Component(String name, Module.Category category, int x, int y, boolean open) {
        super(name);
        this.category = category;
        this.setX(x);
        this.setY(y);
        this.animX = this.x;
        this.animY = this.y;
        if (ClickGui.getInstance() != null) {
            this.setWidth(ClickGui.getInstance().moduleButtonWidth.getValueInt());
            this.setHeight(ClickGui.getInstance().categoryHeight.getValueInt() + 5);
        } else {
            this.setWidth(93);
            this.setHeight(18);
        }
        this.open = open;
        this.setupItems();
    }

    public void setupItems() {
    }

    private void drag(int mouseX, int mouseY) {
        if (!this.drag) {
            return;
        }
        this.x = this.x2 + mouseX;
        this.y = this.y2 + mouseY;
        this.animX = this.x;
        this.animY = this.y;
    }

    private void updatePosition() {
        if (!ClickGui.getInstance().scrollAnim.getValue() || this.drag) {
            this.animX = this.x;
            this.animY = this.y;
            this.xAnimation.from = this.x;
            this.xAnimation.to = this.x;
            this.yAnimation.from = this.y;
            this.yAnimation.to = this.y;
            return;
        }
        // 优化：性能模式使用更短的动画时间
        boolean perfMode = ClickGui.getInstance().performanceMode.isOpen() && ClickGui.getInstance().simplifiedAnimations.getValue();
        int length = perfMode ? Math.max(1, ClickGui.getInstance().scrollAnimLength.getValueInt() / 2) : Math.max(1, ClickGui.getInstance().scrollAnimLength.getValueInt());
        Easing easing = perfMode ? Easing.Linear : ClickGui.getInstance().scrollAnimEasing.getValue();
        this.animX = (float)this.xAnimation.get((double)this.x, (long)length, easing);
        this.animY = (float)this.yAnimation.get((double)this.y, (long)length, easing);
    }

    protected double getColorDelay() {
        return (double)this.getY() / 10.0;
    }

    public void drawScreen(DrawContext context, int mouseX, int mouseY, float partialTicks) {
        this.context = context;
        this.drag(mouseX, mouseY);
        this.updatePosition();
        int x = this.getX();
        int y = this.getY();
        float targetItemHeight = this.getTotalItemHeight() - 2.0f;
        boolean perfMode = ClickGui.getInstance().performanceMode.isOpen() && ClickGui.getInstance().simplifiedAnimations.getValue();
        long openTime = perfMode ? 100L : 200L;
        Easing openEasing = perfMode ? Easing.Linear : Easing.CubicInOut;
        double openProgress = this.openAnimation.get(this.open ? 1.0 : 0.0, openTime, openEasing);
        float totalItemHeight = (float)(targetItemHeight * openProgress);
        int categoryWidth = ClickGui.getInstance().categoryWidth.getValueInt();
        float headerX = (float)x + ((float)this.width - (float)categoryWidth) / 2.0f;
        float headerY = (float)y;
        float headerW = (float)categoryWidth;
        float headerH = (float)this.height - 5.0f;
        double headerBaseDelay = this.getColorDelay();
        int topAlpha = ClickGui.getInstance().topAlpha.getValueInt();
        
        // 液态玻璃效果
        boolean isLiquid = ClickGui.getInstance().backgroundStyle.getValue() == ClickGui.BackgroundStyle.Liquid;
        boolean isLiquidGlass = ClickGui.getInstance().liquidGlass.getValue();
        float radius = 8.0f;

        if (isLiquid) {
            // 毛玻璃效果 - 类似图片中的质感
            int glassAlpha = isLiquidGlass ? ClickGui.getInstance().glassAlpha.getValueInt() : 100;
            int highlight = isLiquidGlass ? ClickGui.getInstance().glassHighlight.getValueInt() : 60;

            // 主背景（深色半透明基底）
            Color bgColor = new Color(15, 20, 25, Math.min(255, glassAlpha));
            Render2DUtil.drawRoundedRect(context.getMatrices(), headerX, headerY, headerW, headerH, radius, bgColor);

            // 顶部高光渐变 - 模拟光线照射效果（更柔和）
            int highlightAlpha = Math.min(255, highlight * 2 / 3);
            Color highlightTop = new Color(255, 255, 255, highlightAlpha);
            Color highlightBottom = new Color(255, 255, 255, 0);
            Render2DUtil.drawGradientRoundedRect(context.getMatrices(), headerX + 3, headerY + 2, headerW - 6, headerH / 3, radius / 2, highlightTop, highlightBottom);

            // 底部微光 - 增加层次感
            int bottomAlpha = Math.min(255, glassAlpha + 15);
            Color bottomGrad1 = new Color(25, 35, 45, bottomAlpha);
            Color bottomGrad2 = new Color(15, 20, 25, glassAlpha);
            Render2DUtil.drawGradientRoundedRect(context.getMatrices(), headerX, headerY + headerH / 2, headerW, headerH / 2, radius / 2, bottomGrad1, bottomGrad2);

            // 边缘高光 - 更细腻的光泽
            int edgeAlpha = Math.min(120, highlight / 2);
            Color edgeTop = new Color(255, 255, 255, edgeAlpha);
            Color edgeSide = new Color(255, 255, 255, edgeAlpha / 2);
            Render2DUtil.drawRoundedStroke(context.getMatrices(), headerX, headerY, headerW, headerH, radius, new Color(120, 140, 160, 70), 16);
        } else {
            if (ClickGui.getInstance().colorMode.getValue() == ClickGui.ColorMode.Spectrum) {
                Render2DUtil.drawLutRect(context.getMatrices(), headerX, headerY, headerW, headerH, ClickGui.getInstance().getSpectrumLutId(), ClickGui.getInstance().getSpectrumLutHeight(), topAlpha);
            } else {
                Color topColor = ColorUtil.injectAlpha(ClickGui.getInstance().getColor(headerBaseDelay), topAlpha);
                Render2DUtil.drawRect(context.getMatrices(), headerX, headerY, headerW, headerH, topColor);
            }
            if (ClickGui.getInstance().backgroundStyle.getValue() != ClickGui.BackgroundStyle.Transparent) {
                Render2DUtil.drawRectWithOutline(context.getMatrices(), headerX, headerY, headerW, headerH, new Color(0, 0, 0, 0), new Color(ClickGui.getInstance().hoverColor.getValue().getRGB()));
            }
        }
        if (openProgress > 0.01) {
            if (ClickGui.getInstance().backGround.booleanValue) {
                Render2DUtil.drawRect(context.getMatrices(), x, (float)y + (float)this.height - 5.0f, this.width, (float)(y + this.height) + totalItemHeight - ((float)y + (float)this.height - 5.0f), ColorUtil.injectAlpha(ClickGui.getInstance().backGround.getValue(), ClickGui.getInstance().backgroundAlpha.getValueInt()));
                Render2DUtil.drawRectWithOutline(context.getMatrices(), x, (float)y + (float)this.height - 5.0f, this.width, (float)(y + this.height) + totalItemHeight - ((float)y + (float)this.height - 5.0f), new Color(0, 0, 0, 0), new Color(ClickGui.getInstance().hoverColor.getValue().getRGB()));
            }
            if (ClickGui.getInstance().line.getValue()) {
                float yTop = (float)y + (float)this.height - 5.0f;
                float yBottom = (float)(y + this.height) + totalItemHeight;
                double delayPerPixel = 0.25;
                float leftX = (float)x + 0.2f;
                float rightX = (float)(x + this.width);
                int alpha = ClickGui.getInstance().topAlpha.getValueInt();
                if (ClickGui.getInstance().colorMode.getValue() == ClickGui.ColorMode.Spectrum) {
                    float lineW = 1.0f;
                    Render2DUtil.drawLutRect(context.getMatrices(), leftX, yTop, lineW, yBottom - yTop, ClickGui.getInstance().getSpectrumLutId(), ClickGui.getInstance().getSpectrumLutHeight(), alpha);
                    Render2DUtil.drawLutRect(context.getMatrices(), rightX - lineW, yTop, lineW, yBottom - yTop, ClickGui.getInstance().getSpectrumLutId(), ClickGui.getInstance().getSpectrumLutHeight(), alpha);
                } else {
                    float segment = 2.0f;
                    for (float yy = yTop; yy < yBottom; yy += segment) {
                        float yy2 = Math.min(yy + segment, yBottom);
                        int c = ColorUtil.injectAlpha(ClickGui.getInstance().getColor((double)yy * delayPerPixel).getRGB(), alpha);
                        Render2DUtil.drawLine(context.getMatrices(), leftX, yy2, leftX, yy, c);
                        Render2DUtil.drawLine(context.getMatrices(), rightX, yy2, rightX, yy, c);
                    }
                }
                int bottomColor = ColorUtil.injectAlpha(ClickGui.getInstance().getColor((double)yBottom * delayPerPixel).getRGB(), alpha);
                Render2DUtil.drawLine(context.getMatrices(), x, yBottom, x + this.width, yBottom, bottomColor);
            }
        }
        float barHeight = headerH;
        float iconY = headerY + (barHeight - FontManager.icon.getFontHeight()) / 2.0f;
        FontManager.icon.drawString(context.getMatrices(), this.category.getIcon(), (double)(headerX + 6.0f), (double)iconY, Button.enableTextColor);
        float nameFontHeight = FontManager.isCustomFontEnabled() ? FontManager.ui.getFontHeight() : 9.0f;
        float nameY = headerY + (barHeight - nameFontHeight) / 2.0f + (float)ClickGui.getInstance().titleOffset.getValueInt();
        this.drawString(this.getName(), (double)(headerX + 20.0f), (double)nameY, Button.enableTextColor);
        if (openProgress > 0.01) {
            int panelX1 = x - 1;
            int panelY1 = (int)((float)y + (float)this.height - 6.0f);
            int panelX2 = x + this.width + 1;
            int panelY2 = (int)Math.round((double)(y + this.height) + (double)totalItemHeight) + 1;
            context.enableScissor(panelX1, panelY1, panelX2, panelY2);
            float yOff = (float)(this.getY() + this.getHeight()) - 3.0f;
            for (ModuleButton item : this.getItems()) {
                if (item.isHidden()) continue;
                item.setLocation((float)x + 2.0f, yOff);
                item.setWidth(this.getWidth() - 4);
                if (item.itemHeight > 0.0 || item.subOpen) {
                    int scissorX1 = (int)item.x - 1;
                    int scissorY1 = (int)item.y - 1;
                    int scissorX2 = (int)(item.x + (float)item.getWidth() + 1.0f);
                    int scissorY2 = (int)((double)(yOff + (float)item.getButtonHeight() + 1.5f) + item.itemHeight) + 1;
                    context.enableScissor(scissorX1, scissorY1, scissorX2, scissorY2);
                    item.drawScreen(context, mouseX, mouseY, partialTicks);
                    context.disableScissor();
                } else {
                    item.drawScreen(context, mouseX, mouseY, partialTicks);
                }
                yOff += (float)item.getButtonHeight() + 1.5f + (float)item.itemHeight;
            }
            context.disableScissor();
        }
    }

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && this.isHovering(mouseX, mouseY)) {
            if (ClickGui.getInstance().mouseMove.getValue()) {
                this.setX(this.getX());
                this.setY(this.getY());
                this.animX = this.x;
                this.animY = this.y;
                this.xAnimation.from = this.x;
                this.xAnimation.to = this.x;
                this.yAnimation.from = this.y;
                this.yAnimation.to = this.y;
            }
            this.x2 = this.getX() - mouseX;
            this.y2 = this.getY() - mouseY;
            ClickGuiScreen.getInstance().getComponents().forEach(component -> {
                if (component.drag) {
                    component.drag = false;
                }
            });
            this.drag = true;
            return true;
        }
        if (mouseButton == 1 && this.isHovering(mouseX, mouseY)) {
            this.open = !this.open;
            Item.sound();
            return true;
        }
        if (!this.open) {
            return false;
        }
        for (Item item : this.getItems()) {
            if (item.isHidden()) continue;
            if (item.mouseClicked(mouseX, mouseY, mouseButton)) {
                return true;
            }
        }
        return false;
    }

    public void mouseReleased(int mouseX, int mouseY, int releaseButton) {
        if (releaseButton == 0) {
            this.drag = false;
        }
        if (!this.open) {
            return;
        }
        this.getItems().forEach(item -> item.mouseReleased(mouseX, mouseY, releaseButton));
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        if (!this.open) {
            return false;
        }
        for (Item item : this.getItems()) {
            if (item.isHidden()) continue;
            if (item.mouseScrolled(mouseX, mouseY, verticalAmount)) {
                return true;
            }
        }
        return false;
    }

    public void onKeyTyped(char typedChar, int keyCode) {
        if (!this.open) {
            return;
        }
        this.getItems().forEach(item -> item.onKeyTyped(typedChar, keyCode));
    }

    public void onKeyPressed(int key) {
        if (!this.open) {
            return;
        }
        this.getItems().forEach(item -> item.onKeyPressed(key));
    }

    public void addButton(ModuleButton button) {
        this.items.add(button);
    }

    public int getX() {
        return (int)(this.animX + this.mouseMoveOffsetX + this.pageOffsetX);
    }

    public int getTargetX() {
        return this.x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return (int)(this.animY + this.mouseMoveOffsetY);
    }

    public int getTargetY() {
        return this.y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getWidth() {
        return this.width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return this.height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public boolean isHidden() {
        return this.hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public boolean isOpen() {
        return this.open;
    }

    public final List<ModuleButton> getItems() {
        return this.items;
    }

    public void setMouseMoveOffset(float x, float y) {
        this.mouseMoveOffsetX = x;
        this.mouseMoveOffsetY = y;
    }

    public void setPageOffsetX(float x) {
        this.pageOffsetX = x;
    }

    public boolean isHovering(int mouseX, int mouseY) {
        int categoryWidth = ClickGui.getInstance().categoryWidth.getValueInt();
        int hx = Math.round((float)this.getX() + ((float)this.getWidth() - (float)categoryWidth) / 2.0f);
        int hy = this.getY();
        int hw = categoryWidth;
        int hh = this.getHeight() - 5;
        return mouseX >= hx && mouseX <= hx + hw && mouseY >= hy && mouseY <= hy + hh;
    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        if (this.isHovering(mouseX, mouseY)) {
            return true;
        }
        if (!this.open) {
            return false;
        }
        float height = this.getTotalItemHeight() - 2.0f;
        int y = this.getY() + this.getHeight() - 5;
        return mouseX >= this.getX() && mouseX <= this.getX() + this.getWidth() && (float)mouseY >= (float)y && (float)mouseY <= (float)y + height;
    }

    private float getTotalItemHeight() {
        float height = 0.0f;
        for (ModuleButton item : this.getItems()) {
            item.update();
            double openProgress = item.animation.get(item.subOpen ? 1.0 : 0.0, 200L, Easing.CubicInOut);
            item.itemHeight = item.getVisibleItemHeight() * openProgress;
            height += (float)item.getButtonHeight() + 1.5f + (float)item.itemHeight;
        }
        return height;
    }

    protected void drawString(String text, double x, double y, Color color) {
        this.drawString(text, x, y, color.hashCode());
    }

    protected void drawString(String text, double x, double y, int color) {
        boolean shadow = FontManager.isShadowEnabled();
        if (FontManager.isCustomFontEnabled()) {
            FontManager.ui.drawString(this.context.getMatrices(), text, (double)((int)x), (double)((int)y), color, shadow);
        } else {
            this.context.drawText(Component.mc.textRenderer, text, (int)x, (int)y, color, shadow);
        }
    }
}

