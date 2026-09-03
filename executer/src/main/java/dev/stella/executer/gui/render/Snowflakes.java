package dev.stella.executer.gui.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Snowflakes {
    private static Snowflakes instance;
    private final List<Snowflake> flakes = new ArrayList<>();
    private final Random random = new Random();
    private boolean enabled = true;

    public Snowflakes() {
        for (int i = 0; i < 60; i++) {
            flakes.add(createFlake());
        }
    }

    private Snowflake createFlake() {
        return new Snowflake(
            random.nextFloat() * 2000,
            random.nextFloat() * 2000,
            0.5f + random.nextFloat() * 2f,
            random.nextFloat() * 360
        );
    }

    public void render(DrawContext ctx) {
        if (!enabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.textRenderer == null) return;

        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        long time = System.currentTimeMillis();

        for (Snowflake f : flakes) {
            f.update(time);
            float x = f.x % w;
            float y = f.y % h;
            if (x < 0) x += w;
            if (y < 0) y += h;

            int alpha = (int) (80 + f.size * 20);
            int color = ColorUtil.injectAlpha(0xFFFFFF, alpha);
            ctx.fill((int) x, (int) y, (int) x + (int)(f.size + 1), (int) y + (int)(f.size + 1), color);
        }
    }

    public void toggle() { enabled = !enabled; }
    public boolean isEnabled() { return enabled; }

    public static Snowflakes getInstance() {
        if (instance == null) instance = new Snowflakes();
        return instance;
    }

    private static class Snowflake {
        float x, y, size, rotation;
        float speedY, speedX, wobbleSpeed, wobbleRange;
        float rotationSpeed;

        Snowflake(float x, float y, float size, float rotation) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.rotation = rotation;
            this.speedY = 0.3f + size * 0.2f;
            this.speedX = 0;
            this.wobbleSpeed = 0.5f + (float) Math.random() * 1.5f;
            this.wobbleRange = 0.3f + (float) Math.random() * 0.7f;
            this.rotationSpeed = 0.5f + (float) Math.random() * 2f;
        }

        void update(long time) {
            y += speedY;
            x += (float) Math.sin(time * 0.001 * wobbleSpeed) * wobbleRange;
            rotation += rotationSpeed * 0.02f;
        }
    }
}
