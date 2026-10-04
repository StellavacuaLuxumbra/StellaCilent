/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.util.Formatting
 */
package dev.stella.mod.gui.items.buttons;

import dev.stella.api.utils.render.ColorUtil;
import dev.stella.api.utils.render.Render2DUtil;
import dev.stella.mod.gui.clickgui.ClickGuiScreen;
import dev.stella.mod.modules.impl.client.ClickGui;
import dev.stella.mod.modules.settings.impl.EnumSetting;
import java.awt.Color;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Formatting;

public class EnumButton
extends Button {
    public final EnumSetting<?> setting;
    private boolean open;

    public EnumButton(EnumSetting<?> setting) {
        super(setting.getName());
        this.setting = setting;
    }

    @Override
    public void drawScreen(DrawContext context, int mouseX, int mouseY, float partialTicks) {
        boolean hovered = this.isHovering(mouseX, mouseY);
        double baseDelay = this.getColorDelay();
        float w = (float)this.width + 7.0f;
        float h = (float)this.height - 0.5f;
        if (ClickGui.getInstance().colorMode.getValue() == ClickGui.ColorMode.Spectrum && this.getState()) {
            int a = hovered ? ClickGui.getInstance().hoverAlpha.getValueInt() : ClickGui.getInstance().alpha.getValueInt();
            Render2DUtil.drawLutRect(context.getMatrices(), this.x, this.y, w, h, ClickGui.getInstance().getSpectrumLutId(), ClickGui.getInstance().getSpectrumLutHeight(), a);
        } else {
            Color color = ClickGui.getInstance().getColor(baseDelay);
            Render2DUtil.rect(context.getMatrices(), this.x, this.y, this.x + w, this.y + (float)this.height - 0.5f, this.getState() ? (!hovered ? ColorUtil.injectAlpha(color, ClickGui.getInstance().alpha.getValueInt()).getRGB() : ColorUtil.injectAlpha(color, ClickGui.getInstance().hoverAlpha.getValueInt()).getRGB()) : (!hovered ? defaultColor : hoverColor));
        }
        float textY = this.getCenteredTextY(this.y, (float)this.height - 0.5f);
        if (this.isHovering(mouseX, mouseY) && InputUtil.isKeyPressed((long)mc.getWindow().getHandle(), (int)340)) {
            this.drawString(this.setting.getName().equalsIgnoreCase("Page") ? "Reset Page" : "Reset Default", (double)(this.x + 2.3f), (double)textY, enableTextColor);
        } else {
            this.drawString(this.setting.getName() + " " + String.valueOf(Formatting.GRAY) + (((Enum)this.setting.getValue()).name().equalsIgnoreCase("ABC") ? "ABC" : ((Enum)this.setting.getValue()).name()), (double)(this.x + 2.3f), (double)textY, this.getState() ? enableTextColor : defaultTextColor);
        }
        Enum[] values = (Enum[])((Enum)this.setting.getValue()).getDeclaringClass().getEnumConstants();
        double fullHeight = 11.0 * (double)values.length;
        double baseHeight = (double)super.getHeight();
        double visibleHeight = this.getVisibleHeight();
        double openProgress = fullHeight <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, (visibleHeight - baseHeight) / fullHeight));
        if (openProgress > 0.01) {
            double y = (double)this.y + (double)this.height + 1.0;
            double step = 11.0 * openProgress;
            int alpha = (int)Math.round(255.0 * openProgress);
            for (Enum e : values) {
                String s = e.name();
                float optionY = this.getCenteredTextY((float)y, 11.0f);
                int baseColor = ((Enum)this.setting.getValue()).name().equals(s) ? enableTextColor : defaultTextColor;
                this.drawString(s, (double)((float)this.width / 2.0f - (float)this.getWidth(s) / 2.0f + 2.0f + this.x), (double)optionY, ColorUtil.injectAlpha(baseColor, alpha));
                y += step;
            }
        }
    }

    @Override
    public void update() {
        this.setHidden(!this.setting.isVisible());
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && this.isHovering(mouseX, mouseY) && InputUtil.isKeyPressed((long)mc.getWindow().getHandle(), (int)340)) {
            if (this.setting.getName().equalsIgnoreCase("Page")) {
                boolean resetPage = false;
                for (dev.stella.mod.gui.items.Component component : ClickGuiScreen.getInstance().getComponents()) {
                    for (ModuleButton moduleButton : component.getItems()) {
                        if (!moduleButton.getModule().getSettings().contains(this.setting)) continue;
                        moduleButton.resetPageSettingsToDefault(this.setting);
                        resetPage = true;
                        break;
                    }
                    if (!resetPage) continue;
                    break;
                }
                EnumButton.sound();
                return true;
            }
            Enum defaultValue = (Enum)this.setting.getDefaultValue();
            this.setting.setEnumValue(defaultValue.name());
            EnumButton.sound();
            return true;
        }
        if (mouseButton == 1 && this.isHovering(mouseX, mouseY)) {
            this.open = !this.open;
            EnumButton.sound();
            return true;
        }
        if (mouseButton == 0 && this.open) {
            int y = (int)this.y;
            for (Enum o : (Enum[])((Enum)this.setting.getValue()).getDeclaringClass().getEnumConstants()) {
                if ((float)mouseX > this.x && (float)mouseX < this.x + (float)this.width && mouseY >= y + this.height + 1 && mouseY < y + this.height + 11 + 1) {
                    this.setting.setEnumValue(String.valueOf(o));
                    EnumButton.sound();
                    return true;
                }
                y += 11;
            }
        }
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public int getHeight() {
        return super.getHeight() + (this.open ? 11 * ((Enum[])((Enum)this.setting.getValue()).getDeclaringClass().getEnumConstants()).length : 0);
    }

    @Override
    public void toggle() {
        this.setting.increaseEnum();
    }

    @Override
    public boolean getState() {
        return true;
    }
}

