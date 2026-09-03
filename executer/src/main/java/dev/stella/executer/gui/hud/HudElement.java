package dev.stella.executer.gui.hud;

import net.minecraft.client.gui.DrawContext;

public abstract class HudElement {
    protected int x, y;
    protected boolean visible = true;

    public HudElement(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public abstract void render(DrawContext ctx, float delta);
    public abstract int getWidth();
    public abstract int getHeight();

    public void setPosition(int x, int y) { this.x = x; this.y = y; }
    public int getX() { return x; }
    public int getY() { return y; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean v) { this.visible = v; }
    public void toggle() { visible = !visible; }
}
