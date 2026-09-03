package dev.stella.executer.gui.render;

import java.awt.Color;

public class ColorUtil {

    public static int fadeColor(int start, int end, float progress) {
        float r1 = (start >> 16) & 0xFF, g1 = (start >> 8) & 0xFF, b1 = start & 0xFF, a1 = (start >> 24) & 0xFF;
        float r2 = (end >> 16) & 0xFF, g2 = (end >> 8) & 0xFF, b2 = end & 0xFF, a2 = (end >> 24) & 0xFF;
        float r = r1 + (r2 - r1) * progress;
        float g = g1 + (g2 - g1) * progress;
        float b = b1 + (b2 - b1) * progress;
        float a = a1 + (a2 - a1) * progress;
        return ((int) a << 24) | ((int) r << 16) | ((int) g << 8) | (int) b;
    }

    public static int injectAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    public static int injectAlpha(Color color, int alpha) {
        return injectAlpha(color.getRGB(), alpha);
    }

    public static int hslToColor(float h, float s, float l, int a) {
        float c = (1 - Math.abs(2 * l - 1)) * s;
        float x = c * (1 - Math.abs((h * 6) % 2 - 1));
        float m = l - c / 2;
        float r, g, b;
        int sector = (int) (h * 6) % 6;
        switch (sector) {
            case 0: r = c; g = x; b = 0; break;
            case 1: r = x; g = c; b = 0; break;
            case 2: r = 0; g = c; b = x; break;
            case 3: r = 0; g = x; b = c; break;
            case 4: r = x; g = 0; b = c; break;
            default: r = c; g = 0; b = x; break;
        }
        return (a << 24) | (((int) ((r + m) * 255)) << 16) | (((int) ((g + m) * 255)) << 8) | ((int) ((b + m) * 255));
    }

    public static int pulseColor(int color, int index, int count, float speed) {
        float progress = (float) index / count;
        float pulse = (float) (Math.sin(System.currentTimeMillis() * 0.001 * speed + progress * Math.PI * 2) * 0.5 + 0.5);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;
        float factor = 0.7f + pulse * 0.3f;
        return injectAlpha(color, (int) (a * factor));
    }

    public static int rainbow(int delay, float saturation) {
        float hue = (System.currentTimeMillis() % (360 * 10L)) / (360f * 10L) + (float) delay / 100.0f;
        return Color.HSBtoRGB(hue % 1.0f, saturation / 255.0f, 1.0f);
    }
}
