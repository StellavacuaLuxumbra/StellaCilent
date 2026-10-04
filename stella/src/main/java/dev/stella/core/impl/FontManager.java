/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.util.math.MatrixStack
 */
package dev.stella.core.impl;

import dev.stella.mod.gui.fonts.FontRenderer;
import dev.stella.mod.modules.impl.client.Fonts;
import java.awt.Font;
import java.awt.FontFormatException;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.Objects;
import net.minecraft.client.util.math.MatrixStack;

public class FontManager {
    public static FontRenderer ui;
    public static FontRenderer small;
    public static FontRenderer icon;
    private static volatile boolean loading = false;
    private static volatile boolean loaded = false;
    private static final Object lock = new Object();

    public static boolean isCustomFontEnabled() {
        return Fonts.INSTANCE != null && Fonts.INSTANCE.isOn();
    }

    public static boolean isShadowEnabled() {
        return Fonts.INSTANCE == null || Fonts.INSTANCE.shadow.getValue();
    }

    public static void init() {
        if (loaded || loading) {
            return;
        }
        
        // 首先创建临时字体避免空指针
        try {
            ui = new FontRenderer(new Font("Verdana", Font.PLAIN, 8), 8);
            small = new FontRenderer(new Font("Verdana", Font.PLAIN, 6), 6);
            icon = new FontRenderer(new Font("Verdana", Font.PLAIN, 8), 8);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // 然后在后台加载真实字体
        synchronized (lock) {
            if (loading) return;
            loading = true;
        }
        
        Thread loadThread = new Thread(() -> {
            try {
                FontRenderer newUi = FontManager.assets(8.0f, "default", 0);
                FontRenderer newSmall = FontManager.assets(6.0f, "default", 0);
                FontRenderer newIcon = FontManager.assetsWithoutOffset(8.0f, "icon", 0);
                
                synchronized (lock) {
                    ui = newUi;
                    small = newSmall;
                    icon = newIcon;
                    loaded = true;
                }
                System.out.println("[FontManager] Fonts loaded asynchronously");
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("[FontManager] Using fallback fonts");
            } finally {
                synchronized (lock) {
                    loading = false;
                }
            }
        }, "Stella-Font-Loader");
        loadThread.setDaemon(true);
        loadThread.start();
    }

    public static void initSync() {
        if (loaded) {
            return;
        }
        synchronized (lock) {
            if (loaded) return;
            loading = true;
        }
        
        try {
            ui = FontManager.assets(8.0f, "default", 0);
            small = FontManager.assets(6.0f, "default", 0);
            icon = FontManager.assetsWithoutOffset(8.0f, "icon", 0);
            loaded = true;
        } catch (Exception e) {
            e.printStackTrace();
            try {
                ui = new FontRenderer(new Font("Verdana", Font.PLAIN, 8), 8);
                small = new FontRenderer(new Font("Verdana", Font.PLAIN, 6), 6);
                icon = new FontRenderer(new Font("Verdana", Font.PLAIN, 8), 8);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } finally {
            synchronized (lock) {
                loading = false;
            }
        }
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static FontRenderer assets(float size, String font, int style, String alternate) throws IOException, FontFormatException {
        ClassLoader classLoader = FontManager.class.getClassLoader();
        InputStream primary = classLoader.getResourceAsStream("assets/stella/font/" + font + ".ttf");
        InputStream fallback = classLoader.getResourceAsStream("assets/minecraft/font/font.ttf");
        InputStream stream = primary != null ? primary : fallback;
        return new FontRenderer(Font.createFont(0, Objects.requireNonNull(stream)).deriveFont(style, size), FontManager.getFont(alternate, style, (int)size), size){

            @Override
            public void drawString(MatrixStack stack, String s, float x, float y, float r, float g, float elementCodec, float keyCodec, boolean shadow) {
                float dx = 0.0f;
                float dy = 0.0f;
                if (FontManager.isCustomFontEnabled()) {
                    dx = (float)Fonts.INSTANCE.translate.getValueInt();
                    dy = (float)Fonts.INSTANCE.shift.getValueInt();
                }
                super.drawString(stack, s, x + dx, y + dy, r, g, elementCodec, keyCodec, shadow);
            }
        };
    }

    public static FontRenderer assetsWithoutOffset(float size, String name, int style) throws IOException, FontFormatException {
        ClassLoader classLoader = FontManager.class.getClassLoader();
        InputStream primary = classLoader.getResourceAsStream("assets/stella/icon/" + name + ".ttf");
        InputStream fallback = classLoader.getResourceAsStream("assets/minecraft/font/font.ttf");
        InputStream stream = primary != null ? primary : fallback;
        return new FontRenderer(Font.createFont(0, Objects.requireNonNull(stream)).deriveFont(style, size), size);
    }

    public static FontRenderer assets(float size, String name, int style) throws IOException, FontFormatException {
        ClassLoader classLoader = FontManager.class.getClassLoader();
        InputStream primary = classLoader.getResourceAsStream("assets/stella/font/" + name + ".ttf");
        InputStream fallback = classLoader.getResourceAsStream("assets/minecraft/font/font.ttf");
        InputStream stream = primary != null ? primary : fallback;
        return new FontRenderer(Font.createFont(0, Objects.requireNonNull(stream)).deriveFont(style, size), size){

            @Override
            public void drawString(MatrixStack stack, String s, float x, float y, float r, float g, float elementCodec, float keyCodec, boolean shadow) {
                float dx = 0.0f;
                float dy = 0.0f;
                if (FontManager.isCustomFontEnabled()) {
                    dx = (float)Fonts.INSTANCE.translate.getValueInt();
                    dy = (float)Fonts.INSTANCE.shift.getValueInt();
                }
                super.drawString(stack, s, x + dx, y + dy, r, g, elementCodec, keyCodec, shadow);
            }
        };
    }

    public static FontRenderer create(int size, String font, int style, String alternate) {
        return new FontRenderer(FontManager.getFont(font, style, size), FontManager.getFont(alternate, style, size), size){

            @Override
            public void drawString(MatrixStack stack, String s, float x, float y, float r, float g, float elementCodec, float keyCodec, boolean shadow) {
                float dx = 0.0f;
                float dy = 0.0f;
                if (FontManager.isCustomFontEnabled()) {
                    dx = (float)Fonts.INSTANCE.translate.getValueInt();
                    dy = (float)Fonts.INSTANCE.shift.getValueInt();
                }
                super.drawString(stack, s, x + dx, y + dy, r, g, elementCodec, keyCodec, shadow);
            }
        };
    }

    public static FontRenderer create(int size, String font, int style) {
        return new FontRenderer(FontManager.getFont(font, style, size), size){

            @Override
            public void drawString(MatrixStack stack, String s, float x, float y, float r, float g, float elementCodec, float keyCodec, boolean shadow) {
                float dx = 0.0f;
                float dy = 0.0f;
                if (FontManager.isCustomFontEnabled()) {
                    dx = (float)Fonts.INSTANCE.translate.getValueInt();
                    dy = (float)Fonts.INSTANCE.shift.getValueInt();
                }
                super.drawString(stack, s, x + dx, y + dy, r, g, elementCodec, keyCodec, shadow);
            }
        };
    }

    private static Font getFont(String font, int style, int size) {
        File fontDir = new File("C:\\Windows\\Fonts");
        try {
            for (File file : fontDir.listFiles()) {
                if (!file.getName().replace(".ttf", "").replace(".ttc", "").replace(".otf", "").equalsIgnoreCase(font)) continue;
                try {
                    return Font.createFont(0, file).deriveFont(style, size);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
            for (File file : fontDir.listFiles()) {
                if (!file.getName().startsWith(font)) continue;
                try {
                    return Font.createFont(0, file).deriveFont(style, size);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return new Font(null, style, size);
    }
}

