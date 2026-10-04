package dev.stella.mod.gui.items.buttons;

import dev.stella.api.utils.render.ColorUtil;
import dev.stella.api.utils.render.Render2DUtil;
import dev.stella.mod.gui.clickgui.ClickGuiScreen;
import dev.stella.mod.gui.items.Component;
import dev.stella.mod.gui.items.Item;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.impl.client.ClickGui;
import dev.stella.mod.modules.settings.impl.ModuleListSetting;
import dev.stella.stella;
import net.minecraft.client.gui.DrawContext;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class ModuleListButton extends Button {
    private final ModuleListSetting setting;
    private boolean open = false;
    private Component parentComponent;

    public ModuleListButton(ModuleListSetting setting) {
        super(setting.getName());
        this.setting = setting;
    }

    private void findParentComponent() {
        if (parentComponent != null) return;
        for (Component component : ClickGuiScreen.getInstance().getComponents()) {
            for (Item item : component.getItems()) {
                if (item == this) {
                    parentComponent = component;
                    return;
                }
            }
        }
    }

    @Override
    public void drawScreen(DrawContext context, int mouseX, int mouseY, float partialTicks) {
        findParentComponent();

        boolean hovered = this.isHovering(mouseX, mouseY);
        float h = (float)this.height - 0.5f;
        float textY = this.getCenteredTextY(this.y, h);

        // Draw background
        Render2DUtil.rect(context.getMatrices(), this.x, this.y, this.x + (float)this.width, this.y + h, !hovered ? defaultColor : hoverColor);

        // Draw name
        this.drawString((this.open ? "+" : "-") + " " + this.getName(), (double)(this.x + 2.3f), (double)textY, defaultTextColor);

        if (this.open && stella.MODULE != null) {
            int y = (int) (this.y + this.height);
            int moduleHeight = 14;
            List<Module> modules = new ArrayList<>(stella.MODULE.getModules());

            for (Module module : modules) {
                boolean isModuleHovered = ModuleListButton.mouseOver(this.x, y, this.x + (float)this.width, y + moduleHeight, mouseX, mouseY);
                boolean isSelected = this.setting.contains(module);
                boolean isEnabled = module.isOn();

                // Background for module item
                Color bgColor = isModuleHovered ? new Color(hoverColor) : new Color(defaultColor);
                Render2DUtil.rect(context.getMatrices(), this.x, y, this.x + (float)this.width, y + moduleHeight, bgColor.getRGB());

                // State indicators: [H] for Hidden, [ON/OFF] for enabled
                String stateText = (isSelected ? "H " : "  ") + (isEnabled ? "[开]" : "[关]");
                int stateColor = isEnabled ? Color.GREEN.getRGB() : Color.RED.getRGB();

                // Draw state text
                this.drawString(stateText, (double)(this.x + 2.3f), (double)(y + 2), stateColor);
                // Draw module name
                this.drawString(module.getDisplayName(), (double)(this.x + (float)this.width - 2.3f - mc.textRenderer.getWidth(module.getDisplayName())), (double)(y + 2), defaultTextColor);

                y += moduleHeight;
            }
            
            // Dynamically update height so the Component knows to render more space
            if (parentComponent != null) {
                int totalHeight = this.height + modules.size() * moduleHeight;
                parentComponent.setHeight(totalHeight);
            }
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (this.isHovering(mouseX, mouseY)) {
            if (mouseButton == 1) {
                this.open = !this.open;
                ModuleListButton.sound();
                return true;
            }
        }

        if (this.open && stella.MODULE != null) {
            int y = (int) (this.y + this.height);
            int moduleHeight = 14;
            List<Module> modules = new ArrayList<>(stella.MODULE.getModules());

            for (Module module : modules) {
                if (ModuleListButton.mouseOver(this.x, y, this.x + (float)this.width, y + moduleHeight, mouseX, mouseY)) {
                    if (mouseButton == 0) {
                        this.setting.toggle(module);
                        ModuleListButton.sound();
                    } else if (mouseButton == 1) {
                        if (module.isOn()) {
                            module.disable();
                        } else {
                            module.enable();
                        }
                        ModuleListButton.sound();
                    }
                    return true;
                }
                y += moduleHeight;
            }
        }
        return false;
    }

    public static boolean mouseOver(float x1, float y1, float x2, float y2, int mx, int my) {
        return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
    }
}
