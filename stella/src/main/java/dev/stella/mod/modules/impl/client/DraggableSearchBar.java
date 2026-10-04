package dev.stella.mod.modules.impl.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

/**
 * HUD编辑器中的可拖动搜索框
 * 用于搜索HUD元素
 */
public class DraggableSearchBar {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // 位置和大小
    private int x, y;
    private final int width;
    private final int height;

    // 状态
    private boolean dragging = false;
    private boolean active = false;
    private String query = "";
    private final StringBuilder input = new StringBuilder();

    // 拖动偏移
    private double dragOffsetX, dragOffsetY;

    // 颜色
    private static final int BG_COLOR = 0x80000000;
    private static final int BG_ACTIVE_COLOR = 0xFF333333;
    private static final int HANDLE_COLOR = 0xFF444444;
    private static final int HANDLE_HOVER_COLOR = 0xFF666666;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_INACTIVE_COLOR = 0xFFAAAAAA;
    private static final int TEXT_HINT_COLOR = 0x66FFFFFF;

    public DraggableSearchBar(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * 渲染搜索框
     */
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 更新拖动位置
        if (dragging) {
            x = (int) (mouseX - dragOffsetX);
            y = (int) (mouseY - dragOffsetY);

            // 确保不拖出屏幕
            x = Math.max(0, Math.min(x, mc.getWindow().getScaledWidth() - width));
            y = Math.max(0, Math.min(y, mc.getWindow().getScaledHeight() - height));
        }

        // 绘制背景
        int bgColor = active ? BG_ACTIVE_COLOR : BG_COLOR;
        drawRoundedRect(context, x, y, width, height, bgColor);

        // 绘制拖动把手（左侧）
        boolean hoverHandle = isMouseOverHandle(mouseX, mouseY);
        int handleColor = hoverHandle ? HANDLE_HOVER_COLOR : HANDLE_COLOR;

        // 把手背景
        context.fill(x + 2, y + 2, x + 12, y + height - 2, handleColor);

        // 绘制三个小点表示可拖动
        for (int i = 0; i < 3; i++) {
            int dotY = y + 6 + i * 4;
            context.fill(x + 5, dotY, x + 9, dotY + 2, 0xFF888888);
        }

        // 绘制搜索图标或文本
        String displayText;
        if (active) {
            displayText = "🔍 " + input.toString() + (System.currentTimeMillis() % 1000 < 500 ? "_" : "");
        } else {
            displayText = query.isEmpty() ? "🔍 搜索HUD元素 (Ctrl+F)" : "🔍 " + query;
        }

        int textColor = active ? TEXT_COLOR : TEXT_INACTIVE_COLOR;
        drawString(context, displayText, x + 18, y + 6, textColor);

        // 如果有搜索结果，显示数量
        if (!query.isEmpty()) {
            String countText = getResultCount() + " 个结果";
            int countX = x + width - getStringWidth(countText) - 5;
            drawString(context, countText, countX, y + 6, TEXT_INACTIVE_COLOR);
        }

        // 输入提示
        if (active && input.length() == 0) {
            drawString(context, "输入搜索内容...", x + 18, y + 6, TEXT_HINT_COLOR);
        }
    }

    /**
     * 处理鼠标点击
     */
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;

        // 检查是否点击了拖动把手
        if (isMouseOverHandle(mouseX, mouseY)) {
            dragging = true;
            dragOffsetX = mouseX - x;
            dragOffsetY = mouseY - y;
            return true;
        }

        // 检查是否点击了搜索框主体
        if (isMouseOver(mouseX, mouseY)) {
            active = true;
            return true;
        }

        return false;
    }

    /**
     * 处理鼠标释放
     */
    public void mouseReleased() {
        dragging = false;
    }

    /**
     * 处理键盘输入
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!active) return false;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            active = false;
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_ENTER) {
            active = false;
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (input.length() > 0) {
                input.deleteCharAt(input.length() - 1);
                query = input.toString();
            }
            return true;
        }

        return false;
    }

    /**
     * 处理字符输入
     */
    public boolean charTyped(char chr, int modifiers) {
        if (!active) return false;

        if (chr >= 32 && chr <= 126) {
            input.append(chr);
            query = input.toString();
            return true;
        }

        return false;
    }

    /**
     * 激活搜索框
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * 检查鼠标是否在搜索框上
     */
    public boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width &&
                mouseY >= y && mouseY <= y + height;
    }

    /**
     * 检查鼠标是否在拖动把手上
     */
    private boolean isMouseOverHandle(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + 15 &&
                mouseY >= y && mouseY <= y + height;
    }

    /**
     * 获取当前搜索查询
     */
    public String getQuery() {
        return query;
    }

    /**
     * 清除搜索
     */
    public void clear() {
        query = "";
        input.setLength(0);
        active = false;
    }

    /**
     * 获取搜索结果数量（需要外部设置）
     */
    private String getResultCount() {
        // 这里应该从HUD管理器获取实际数量
        return "0";
    }

    // ==================== 渲染辅助方法 ====================

    private void drawRoundedRect(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + h, color);
        // 简单边框
        context.fill(x, y, x + w, y + 1, 0xFF444444);
        context.fill(x, y + h - 1, x + w, y + h, 0xFF444444);
    }

    private void drawString(DrawContext context, String text, int x, int y, int color) {
        if (mc.textRenderer != null) {
            context.drawText(mc.textRenderer, text, x, y, color, false);
        }
    }

    private int getStringWidth(String text) {
        return mc.textRenderer != null ? mc.textRenderer.getWidth(text) : text.length() * 6;
    }

    // ==================== Getter/Setter ====================

    public int getX() { return x; }
    public int getY() { return y; }
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public boolean isDragging() { return dragging; }
    public boolean isActive() { return active; }
    public boolean hasQuery() { return !query.isEmpty(); }
}