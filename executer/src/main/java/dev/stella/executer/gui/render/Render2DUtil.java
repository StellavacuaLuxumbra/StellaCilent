package dev.stella.executer.gui.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

public class Render2DUtil {

    public static void fill(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        ctx.fill(x1, y1, x2, y2, color);
    }

    public static void fillRect(DrawContext ctx, float x, float y, float w, float h, int color) {
        ctx.fill((int) x, (int) y, (int) (x + w), (int) (y + h), color);
    }

    public static void drawRoundedRect(DrawContext ctx, float x, float y, float w, float h, float radius, int color) {
        float r = Math.min(radius, Math.min(w, h) / 2);
        int a = (color >> 24) & 0xFF;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;

        ctx.fill((int)(x + r), (int) y, (int)(x + w - r), (int)(y + h), color);
        ctx.fill((int) x, (int)(y + r), (int)(x + r), (int)(y + h - r), color);
        ctx.fill((int)(x + w - r), (int)(y + r), (int)(x + w), (int)(y + h - r), color);

        Matrix4f matrix = ctx.getMatrices().peek().getPositionMatrix();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);

        for (int i = 0; i <= 48; i++) {
            float angle = (float) Math.PI + (float) i / 48 * (float) Math.PI / 2;
            buf.vertex(matrix, x + r + (float) Math.cos(angle) * r, y + r + (float) Math.sin(angle) * r, 0).color(red, green, blue, a);
        }
        for (int i = 0; i <= 48; i++) {
            float angle = (float) (Math.PI * 1.5) + (float) i / 48 * (float) Math.PI / 2;
            buf.vertex(matrix, x + w - r + (float) Math.cos(angle) * r, y + r + (float) Math.sin(angle) * r, 0).color(red, green, blue, a);
        }
        for (int i = 0; i <= 48; i++) {
            float angle = (float) i / 48 * (float) Math.PI / 2;
            buf.vertex(matrix, x + w - r + (float) Math.cos(angle) * r, y + h - r + (float) Math.sin(angle) * r, 0).color(red, green, blue, a);
        }
        for (int i = 0; i <= 48; i++) {
            float angle = (float) (Math.PI / 2) + (float) i / 48 * (float) Math.PI / 2;
            buf.vertex(matrix, x + r + (float) Math.cos(angle) * r, y + h - r + (float) Math.sin(angle) * r, 0).color(red, green, blue, a);
        }

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    public static void drawRoundedStroke(DrawContext ctx, float x, float y, float w, float h, float radius, int color, int segments) {
        float r = Math.min(radius, Math.min(w, h) / 2);
        int a = (color >> 24) & 0xFF;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;

        Matrix4f matrix = ctx.getMatrices().peek().getPositionMatrix();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);

        for (int i = 0; i <= segments; i++) {
            float angle = (float) Math.PI + (float) i / segments * (float) Math.PI / 2;
            buf.vertex(matrix, x + r + (float) Math.cos(angle) * r, y + r + (float) Math.sin(angle) * r, 0).color(red, green, blue, a);
        }
        for (int i = 0; i <= segments; i++) {
            float angle = (float) (Math.PI * 1.5) + (float) i / segments * (float) Math.PI / 2;
            buf.vertex(matrix, x + w - r + (float) Math.cos(angle) * r, y + r + (float) Math.sin(angle) * r, 0).color(red, green, blue, a);
        }
        for (int i = 0; i <= segments; i++) {
            float angle = (float) i / segments * (float) Math.PI / 2;
            buf.vertex(matrix, x + w - r + (float) Math.cos(angle) * r, y + h - r + (float) Math.sin(angle) * r, 0).color(red, green, blue, a);
        }
        for (int i = 0; i <= segments; i++) {
            float angle = (float) (Math.PI / 2) + (float) i / segments * (float) Math.PI / 2;
            buf.vertex(matrix, x + r + (float) Math.cos(angle) * r, y + h - r + (float) Math.sin(angle) * r, 0).color(red, green, blue, a);
        }
        float angle0 = (float) Math.PI;
        buf.vertex(matrix, x + r + (float) Math.cos(angle0) * r, y + r + (float) Math.sin(angle0) * r, 0).color(red, green, blue, a);

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    public static void horizontalGradient(DrawContext ctx, int x1, int y1, int x2, int y2, int startColor, int endColor) {
        ctx.fillGradient(x1, y1, x2, y2, startColor, endColor);
    }

    public static void verticalGradient(DrawContext ctx, int x1, int y1, int x2, int y2, int startColor, int endColor) {
        ctx.fillGradient(x1, y1, x2, y2, startColor, endColor);
    }

    public static void drawCircle(DrawContext ctx, float cx, float cy, float radius, int color, int segments) {
        int a = (color >> 24) & 0xFF;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;

        Matrix4f matrix = ctx.getMatrices().peek().getPositionMatrix();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);

        buf.vertex(matrix, cx, cy, 0).color(red, green, blue, a);
        for (int i = 0; i <= segments; i++) {
            float angle = (float) i / segments * (float) (Math.PI * 2);
            buf.vertex(matrix, cx + (float) Math.cos(angle) * radius, cy + (float) Math.sin(angle) * radius, 0).color(red, green, blue, a);
        }

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    public static void drawPill(DrawContext ctx, float x, float y, float w, float h, int color) {
        float r = h / 2;
        int a = (color >> 24) & 0xFF;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;

        ctx.fill((int)(x + r), (int) y, (int)(x + w - r), (int)(y + h), color);

        Matrix4f matrix = ctx.getMatrices().peek().getPositionMatrix();
        Tessellator tess = Tessellator.getInstance();

        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        buf.vertex(matrix, x + r, y + r, 0).color(red, green, blue, a);
        for (int i = 0; i <= 64; i++) {
            float angle = (float) i / 64 * (float) (Math.PI * 2);
            buf.vertex(matrix, x + r + (float) Math.cos(angle) * r, y + r + (float) Math.sin(angle) * r, 0).color(red, green, blue, a);
        }
        BufferRenderer.drawWithGlobalProgram(buf.end());

        buf = tess.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        buf.vertex(matrix, x + w - r, y + r, 0).color(red, green, blue, a);
        for (int i = 0; i <= 64; i++) {
            float angle = (float) i / 64 * (float) (Math.PI * 2);
            buf.vertex(matrix, x + w - r + (float) Math.cos(angle) * r, y + r + (float) Math.sin(angle) * r, 0).color(red, green, blue, a);
        }
        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    public static void drawGlow(DrawContext ctx, float x, float y, float w, float h, int color) {
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;

        Matrix4f matrix = ctx.getMatrices().peek().getPositionMatrix();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);

        buf.vertex(matrix, x, y, 0).color(red, green, blue, 20);
        buf.vertex(matrix, x, y - 10, 0).color(red, green, blue, 0);
        buf.vertex(matrix, x + w, y, 0).color(red, green, blue, 20);
        buf.vertex(matrix, x + w, y - 10, 0).color(red, green, blue, 0);
        buf.vertex(matrix, x + w, y, 0).color(red, green, blue, 20);
        buf.vertex(matrix, x + w + 10, y, 0).color(red, green, blue, 0);
        buf.vertex(matrix, x + w, y + h, 0).color(red, green, blue, 20);
        buf.vertex(matrix, x + w + 10, y + h, 0).color(red, green, blue, 0);
        buf.vertex(matrix, x + w, y + h, 0).color(red, green, blue, 20);
        buf.vertex(matrix, x + w, y + h + 10, 0).color(red, green, blue, 0);
        buf.vertex(matrix, x, y + h, 0).color(red, green, blue, 20);
        buf.vertex(matrix, x, y + h + 10, 0).color(red, green, blue, 0);
        buf.vertex(matrix, x, y + h, 0).color(red, green, blue, 20);
        buf.vertex(matrix, x - 10, y + h, 0).color(red, green, blue, 0);
        buf.vertex(matrix, x, y, 0).color(red, green, blue, 20);
        buf.vertex(matrix, x - 10, y, 0).color(red, green, blue, 0);

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    public static void drawLine(DrawContext ctx, float x1, float y1, float x2, float y2, int color, float width) {
        int a = (color >> 24) & 0xFF;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;

        Matrix4f matrix = ctx.getMatrices().peek().getPositionMatrix();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        buf.vertex(matrix, x1, y1, 0).color(red, green, blue, a);
        buf.vertex(matrix, x2, y2, 0).color(red, green, blue, a);

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    public static boolean isHovered(double mouseX, double mouseY, double x, double y, double w, double h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
}
