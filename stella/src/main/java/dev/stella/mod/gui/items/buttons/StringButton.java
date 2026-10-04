/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.client.util.InputUtil
 *  net.minecraft.client.util.SelectionManager
 *  net.minecraft.util.Formatting
 */
package dev.stella.mod.gui.items.buttons;

import dev.stella.api.utils.math.Timer;
import dev.stella.api.utils.render.ColorUtil;
import dev.stella.api.utils.render.Render2DUtil;
import dev.stella.mod.gui.fonts.FontSelector;
import dev.stella.mod.modules.impl.client.ClickGui;
import dev.stella.mod.modules.impl.client.Fonts;
import dev.stella.mod.modules.settings.impl.StringSetting;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.SelectionManager;
import net.minecraft.util.Formatting;

public class StringButton
extends Button {
    private static final Timer idleTimer = new Timer();
    private static boolean idle;
    private final StringSetting setting;
    public boolean isListening;
    private String currentString = "";
    
    // 字体选择弹出菜单相关
    private boolean showFontMenu = false;
    private List<String> fontList = null;
    private int fontMenuScroll = 0;
    private static final int FONT_MENU_MAX_ITEMS = 10;
    private static final int FONT_ITEM_HEIGHT = 14;

    public StringButton(StringSetting setting) {
        super(setting.getName());
        this.setting = setting;
    }

    public static String removeLastChar(String str) {
        String output = "";
        if (str != null && !str.isEmpty()) {
            output = str.substring(0, str.length() - 1);
        }
        return output;
    }

    public static String getIdleSign() {
        if (idleTimer.passed(500L)) {
            idle = !idle;
            idleTimer.reset();
        }
        if (idle) {
            return "_";
        }
        return "";
    }
    
    /**
     * 检查是否是字体设置（仅限 Font 主字体，Alternate 现在是枚举选择器）
     */
    private boolean isFontSetting() {
        return Fonts.INSTANCE != null &&
               this.setting == Fonts.INSTANCE.font;
    }
    
    /**
     * 获取字体列表
     */
    private List<String> getFontList() {
        if (this.fontList == null) {
            this.fontList = FontSelector.getAvailableFonts();
        }
        return this.fontList;
    }
    
    /**
     * 绘制字体选择菜单
     */
    private void drawFontMenu(DrawContext context, int mouseX, int mouseY) {
        if (!this.showFontMenu) return;

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 1000); // 提高层级，确保菜单在其他元素之上

        List<String> fonts = this.getFontList();
        int menuWidth = 150;
        int menuHeight = Math.min(fonts.size(), FONT_MENU_MAX_ITEMS) * FONT_ITEM_HEIGHT + 4;
        int menuX = (int)this.x;
        int menuY = (int)this.y + this.height + 2;

        // 确保菜单不超出屏幕
        if (menuY + menuHeight > mc.getWindow().getScaledHeight()) {
            menuY = (int)this.y - menuHeight - 2;
        }

        // 绘制背景
        Color bgColor = new Color(20, 20, 20, 230);
        Render2DUtil.rect(context.getMatrices(), menuX, menuY, menuX + menuWidth, menuY + menuHeight, bgColor.getRGB());

        // 绘制字体列表
        int startIdx = Math.max(0, this.fontMenuScroll);
        int endIdx = Math.min(fonts.size(), startIdx + FONT_MENU_MAX_ITEMS);

        for (int i = startIdx; i < endIdx; i++) {
            String fontName = fonts.get(i);
            int itemY = menuY + 2 + (i - startIdx) * FONT_ITEM_HEIGHT;

            // 高亮当前选中的字体
            boolean isSelected = this.setting.getValue().equals(fontName);
            boolean isHovered = mouseX >= menuX && mouseX <= menuX + menuWidth &&
                               mouseY >= itemY && mouseY <= itemY + FONT_ITEM_HEIGHT;

            if (isSelected) {
                Color highlightColor = new Color(40, 60, 100, 200);
                Render2DUtil.rect(context.getMatrices(), menuX + 1, itemY, menuX + menuWidth - 1, itemY + FONT_ITEM_HEIGHT, highlightColor.getRGB());
            } else if (isHovered) {
                Color hoverColor = new Color(50, 50, 50, 150);
                Render2DUtil.rect(context.getMatrices(), menuX + 1, itemY, menuX + menuWidth - 1, itemY + FONT_ITEM_HEIGHT, hoverColor.getRGB());
            }

            // 绘制字体名称
            Color textColor = isSelected ? new Color(100, 180, 255, 255) : new Color(200, 200, 200, 255);
            this.drawString(fontName, (double)(menuX + 5), (double)(itemY + 3), textColor.getRGB());
        }

        context.getMatrices().pop();
    }
    
    /**
     * 处理字体菜单点击
     */
    private boolean handleFontMenuClick(int mouseX, int mouseY, int mouseButton) {
        if (!this.showFontMenu) return false;

        List<String> fonts = this.getFontList();
        int menuWidth = 150;
        int menuHeight = Math.min(fonts.size(), FONT_MENU_MAX_ITEMS) * FONT_ITEM_HEIGHT + 4;
        int menuX = (int)this.x;
        int menuY = (int)this.y + this.height + 2;

        // 确保菜单不超出屏幕
        if (menuY + menuHeight > mc.getWindow().getScaledHeight()) {
            menuY = (int)this.y - menuHeight - 2;
        }

        int startIdx = Math.max(0, this.fontMenuScroll);
        int endIdx = Math.min(fonts.size(), startIdx + FONT_MENU_MAX_ITEMS);

        for (int i = startIdx; i < endIdx; i++) {
            int itemY = menuY + 2 + (i - startIdx) * FONT_ITEM_HEIGHT;
            if (mouseX >= menuX && mouseX <= menuX + menuWidth &&
                mouseY >= itemY && mouseY <= itemY + FONT_ITEM_HEIGHT) {
                this.setting.setValue(fonts.get(i));
                this.showFontMenu = false;
                StringButton.sound();
                return true;
            }
        }

        // 点击菜单外部关闭菜单（扩大检测范围，确保点击屏幕任意空白处都能关闭）
        boolean clickedInside = mouseX >= menuX && mouseX <= menuX + menuWidth &&
                                mouseY >= menuY && mouseY <= menuY + menuHeight;

        if (!clickedInside) {
            this.showFontMenu = false;
            return true; // 点击外部关闭菜单，消耗事件，防止点到下面的东西
        }

        return false;
    }

    @Override
    public void drawScreen(DrawContext context, int mouseX, int mouseY, float partialTicks) {
        boolean hovered = this.isHovering(mouseX, mouseY);
        double baseDelay = this.getColorDelay();
        float w = (float)this.width + 7.0f;
        float h = (float)this.height - 0.5f;
        if (ClickGui.getInstance().colorMode.getValue() == ClickGui.ColorMode.Spectrum && this.getState()) {
            int a = !hovered ? ClickGui.getInstance().alpha.getValueInt() : ClickGui.getInstance().hoverAlpha.getValueInt();
            Render2DUtil.drawLutRect(context.getMatrices(), this.x, this.y, w, h, ClickGui.getInstance().getSpectrumLutId(), ClickGui.getInstance().getSpectrumLutHeight(), a);
        } else {
            Color color = ClickGui.getInstance().getColor(baseDelay);
            Render2DUtil.rect(context.getMatrices(), this.x, this.y, this.x + w, this.y + (float)this.height - 0.5f, this.getState() ? (!hovered ? ColorUtil.injectAlpha(color, ClickGui.getInstance().alpha.getValueInt()).getRGB() : ColorUtil.injectAlpha(color, ClickGui.getInstance().hoverAlpha.getValueInt()).getRGB()) : (!hovered ? defaultColor : hoverColor));
        }
        float textY = this.getCenteredTextY(this.y, (float)this.height - 0.5f);
        if (this.isListening) {
            this.drawString(this.currentString + StringButton.getIdleSign(), (double)(this.x + 2.3f), (double)textY, this.getState() ? enableTextColor : defaultTextColor);
        } else if (this.isHovering(mouseX, mouseY) && InputUtil.isKeyPressed((long)mc.getWindow().getHandle(), (int)340)) {
            this.drawString("Reset Default", (double)(this.x + 2.3f), (double)textY, enableTextColor);
        } else {
            String displayText = this.setting.getName() + ": " + String.valueOf(Formatting.GRAY) + this.setting.getValue();
            // 如果是字体设置，添加提示
            if (this.isFontSetting() && !this.showFontMenu) {
                displayText += " [右键选择]";
            }
            this.drawString(displayText, (double)(this.x + 2.3f), (double)textY, this.getState() ? enableTextColor : defaultTextColor);
        }
        
        // 绘制字体选择菜单
        if (this.showFontMenu && this.isFontSetting()) {
            this.drawFontMenu(context, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        // 处理字体菜单点击
        if (this.isFontSetting() && this.handleFontMenuClick(mouseX, mouseY, mouseButton)) {
            return true;
        }

        // 右键打开字体选择菜单
        if (mouseButton == 1 && this.isHovering(mouseX, mouseY) && this.isFontSetting()) {
            this.showFontMenu = !this.showFontMenu;
            this.fontMenuScroll = 0;
            StringButton.sound();
            return true;
        }

        if (mouseButton == 0 && this.isHovering(mouseX, mouseY) && InputUtil.isKeyPressed((long)mc.getWindow().getHandle(), (int)340)) {
            this.isListening = false;
            this.currentString = "";
            this.setting.setValue(this.setting.getDefaultValue());
            StringButton.sound();
            return true;
        }
        if (mouseButton == 0 && this.isHovering(mouseX, mouseY)) {
            this.onMouseClick();
            return true;
        }
        return false;
    }

    @Override
    public void onKeyTyped(char typedChar, int keyCode) {
        if (this.isListening) {
            this.setString(this.currentString + typedChar);
        }
    }

    @Override
    public void onKeyPressed(int key) {
        // 处理字体菜单的键盘导航
        if (this.showFontMenu && this.isFontSetting()) {
            List<String> fonts = this.getFontList();
            int currentIdx = fonts.indexOf(this.setting.getValue());
            
            switch (key) {
                case 265: { // UP arrow
                    if (currentIdx > 0) {
                        this.setting.setValue(fonts.get(currentIdx - 1));
                        StringButton.sound();
                    }
                    break;
                }
                case 264: { // DOWN arrow
                    if (currentIdx < fonts.size() - 1) {
                        this.setting.setValue(fonts.get(currentIdx + 1));
                        StringButton.sound();
                    }
                    break;
                }
                case 256: // ESC
                case 335: { // ENTER
                    this.showFontMenu = false;
                    break;
                }
            }
            return;
        }
        
        if (this.isListening) {
            switch (key) {
                case 256: {
                    this.isListening = false;
                    break;
                }
                case 257:
                case 335: {
                    this.enterString();
                    break;
                }
                case 86: {
                    if (!InputUtil.isKeyPressed((long)mc.getWindow().getHandle(), (int)341)) break;
                    this.setString(this.currentString + SelectionManager.getClipboard((MinecraftClient)mc));
                    break;
                }
                case 259: {
                    this.setString(StringButton.removeLastChar(this.currentString));
                }
            }
        }
    }

    @Override
    public void update() {
        this.setHidden(!this.setting.isVisible());
    }

    /**
     * 处理鼠标滚轮滚动字体菜单
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        if (!this.showFontMenu || !this.isFontSetting()) return false;
        
        List<String> fonts = this.getFontList();
        int maxScroll = Math.max(0, fonts.size() - FONT_MENU_MAX_ITEMS);
        
        if (verticalAmount > 0) {
            // 向上滚动
            this.fontMenuScroll = Math.max(0, this.fontMenuScroll - 1);
        } else if (verticalAmount < 0) {
            // 向下滚动
            this.fontMenuScroll = Math.min(maxScroll, this.fontMenuScroll + 1);
        }
        
        return this.showFontMenu; // 如果菜单正在显示，消耗滚动事件
    }

    private void enterString() {
        if (this.currentString.isEmpty()) {
            this.setting.setValue(this.setting.getDefaultValue());
        } else {
            this.setting.setValue(this.currentString);
        }
        this.onMouseClick();
    }

    @Override
    public void toggle() {
        this.setString(this.setting.getValue());
        this.isListening = !this.isListening;
    }

    @Override
    public boolean getState() {
        return !this.isListening;
    }

    public void setString(String newString) {
        this.currentString = newString;
    }
}

