package dev.stella.executer.gui.render;

import net.minecraft.client.gui.DrawContext;

public class Spectrum {
    private static Spectrum instance;
    private boolean enabled = true;
    private float speed = 1.0f;
    private float saturation = 210f;

    public Spectrum() {}

    public int getColor(float offset) {
        float hue = (System.currentTimeMillis() * 0.0001f * speed + offset) % 1.0f;
        return ColorUtil.hslToColor(hue, saturation / 255f, 0.55f, 255);
    }

    public int getColor(float offset, int alpha) {
        float hue = (System.currentTimeMillis() * 0.0001f * speed + offset) % 1.0f;
        return ColorUtil.hslToColor(hue, saturation / 255f, 0.55f, alpha);
    }

    public void renderGradient(DrawContext ctx, int x, int y, int width, int height, float offset) {
        int colorA = getColor(offset);
        int colorB = getColor(offset + 0.3f);
        Render2DUtil.horizontalGradient(ctx, x, y, x + width, y + height, colorA, colorB);
    }

    public void renderRect(DrawContext ctx, int x, int y, int width, int height, float offset) {
        int color = getColor(offset);
        Render2DUtil.fillRect(ctx, x, y, width, height, color);
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean e) { enabled = e; }
    public float getSpeed() { return speed; }
    public void setSpeed(float s) { speed = s; }
    public float getSaturation() { return saturation; }
    public void setSaturation(float s) { saturation = s; }

    public static Spectrum getInstance() {
        if (instance == null) instance = new Spectrum();
        return instance;
    }
}
