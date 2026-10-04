package dev.stella.mod.gui.clickgui.impl;

import net.minecraft.client.gui.DrawContext;
import dev.stella.mod.modules.Module;
import dev.stella.mod.gui.clickgui.ClickGuiFrame;

public class AbstractCategory {
    private String name;
    public float animationY;
    protected float prevTargetX;

    protected float x, y, width, height, sx, sy;
    private float prevX, prevY;
    protected boolean hovered;
    public boolean dragging;
    public float moduleOffset;

    private boolean open;

    public AbstractCategory(String name, float x, float y, float width, float height) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.open = false;
    }

    public void init() {
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        hovered = isHovered(mouseX, mouseY);
        animationY = (float) interpolate(y, animationY, 0.05);

        if (this.dragging) {
            prevTargetX = x;
            this.x = this.prevX + mouseX;
            this.y = this.prevY + mouseY;
        } else {
            prevTargetX = x;
        }
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta, ClickGuiFrame frame) {
        if (frame == null) {
            render(context, mouseX, mouseY, delta);
            return;
        }

        // 转换鼠标坐标
        float unitMouseX = frame.unitMouseX(mouseX);
        float unitMouseY = frame.unitMouseY(mouseY);

        // 保存原始位置
        float originalX = this.x;
        float originalY = this.y;

        // 应用frame的偏移和缩放
        float renderX = originalX + frame.totalOffsetX / frame.scale;
        float renderY = originalY + frame.totalOffsetY / frame.scale;

        // 临时设置渲染位置
        this.x = renderX;
        this.y = renderY;

        // 检查悬停（使用转换后的鼠标坐标）
        hovered = isHovered((int)unitMouseX, (int)unitMouseY);
        animationY = (float) interpolate(y, animationY, 0.05);

        if (this.dragging) {
            prevTargetX = x;
            this.x = this.prevX + unitMouseX;
            this.y = this.prevY + unitMouseY;
        } else {
            prevTargetX = x;
        }

        // 恢复原始位置
        this.x = originalX;
        this.y = originalY;
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (isHovered(mouseX, mouseY) && button == 0) {
            this.dragging = true;
            this.prevX = this.x - mouseX;
            this.prevY = this.y - mouseY;
        }
    }

    public void mouseClicked(int mouseX, int mouseY, int button, ClickGuiFrame frame) {
        if (frame == null) {
            mouseClicked(mouseX, mouseY, button);
            return;
        }

        float unitMouseX = frame.unitMouseX(mouseX);
        float unitMouseY = frame.unitMouseY(mouseY);

        if (isHovered((int)unitMouseX, (int)unitMouseY) && button == 0) {
            this.dragging = true;
            this.prevX = this.x - unitMouseX;
            this.prevY = this.y - unitMouseY;
        }
    }

    public void mouseReleased(int mouseX, int mouseY, int button) {
        if (button == 0)
            this.dragging = false;
    }

    public void mouseReleased(int mouseX, int mouseY, int button, ClickGuiFrame frame) {
        if (button == 0)
            this.dragging = false;
    }

    public boolean keyTyped(int keyCode) {
        return true;
    }

    public void charTyped(char key, int modifier) {
    }

    public void onClose() {
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public String getName() {
        return name;
    }

    public boolean isOpen() {
        return open;
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public void setModuleOffset(float v, int mx, int my) {
        if (isHovered(mx, my)) {
            moduleOffset = moduleOffset + v;
        }
    }

    public void setModuleOffset(float v, int mx, int my, ClickGuiFrame frame) {
        if (frame == null) {
            setModuleOffset(v, mx, my);
            return;
        }

        float unitMouseX = frame.unitMouseX(mx);
        float unitMouseY = frame.unitMouseY(my);

        if (isHovered((int)unitMouseX, (int)unitMouseY)) {
            moduleOffset = moduleOffset + v;
        }
    }

    public void tick() {
    }

    public void hudClicked(Module module) {
    }

    public void savePos() {
        sx = x;
        sy = y;
    }

    public void restorePos() {
        x = sx;
        y = sy;
    }

    // 工具方法：检查鼠标悬停
    private boolean isHovered(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width &&
                mouseY >= y && mouseY <= y + height;
    }

    // 工具方法：插值计算
    public static double interpolate(double current, double old, double scale) {
        return old + (current - old) * scale;
    }
}