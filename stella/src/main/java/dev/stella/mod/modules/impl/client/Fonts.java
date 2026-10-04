/*
 * Decompiled with CFR 0.152.
 */
package dev.stella.mod.modules.impl.client;

import dev.stella.core.impl.FontManager;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import dev.stella.mod.modules.settings.impl.EnumSetting;
import dev.stella.mod.modules.settings.impl.SliderSetting;
import dev.stella.mod.modules.settings.impl.StringSetting;

public class Fonts
extends Module {
    public static Fonts INSTANCE;
    public final StringSetting font = this.add(new StringSetting("Font", "default").injectTask(this::refresh));
    public final EnumSetting<AlternateFont> alternate = this.add(new EnumSetting<AlternateFont>("Alternate", AlternateFont.MSYH).injectTask(this::refresh));
    public final EnumSetting<Style> style = this.add(new EnumSetting<Style>("Style", Style.PLAIN).injectTask(this::refresh));
    public final SliderSetting size = this.add(new SliderSetting("Size", 8.0, 1.0, 15.0, 1.0).injectTask(this::refresh));
    public final SliderSetting shift = this.add(new SliderSetting("Shift", 0.0, -10.0, 10.0, 1.0).injectTask(this::refresh));
    public final SliderSetting translate = this.add(new SliderSetting("Translate", 0.0, -10.0, 10.0, 1.0).injectTask(this::refresh));
    public final BooleanSetting shadow = this.add(new BooleanSetting("Shadow", true));

    public Fonts() {
        super("Fonts", Module.Category.Client);
        this.setChinese("字体");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        this.refresh();
    }

    @Override
    public void onDisable() {
        FontManager.init();
    }

    public void refresh() {
        if (!this.isOn()) {
            return;
        }

        try {
            String fontName = this.font.getValue();
            String altName = this.alternate.getValue().getFontName();
            int styleVal = this.style.getValue().get();
            float sizeVal = this.size.getValueInt();

            boolean hasAlternate = altName != null && !altName.equals("null");

            // 先尝试从资源目录加载（如 songti, msyh 等内置字体）
            boolean isResourceFont = isResourceFontAvailable(fontName);

            if (isResourceFont) {
                // 从资源目录加载主字体
                if (hasAlternate) {
                    FontManager.ui = FontManager.assets(sizeVal, fontName, styleVal, altName);
                    FontManager.small = FontManager.assets(6.0f, fontName, styleVal, altName);
                } else {
                    FontManager.ui = FontManager.assets(sizeVal, fontName, styleVal);
                    FontManager.small = FontManager.assets(6.0f, fontName, styleVal);
                }
            } else {
                // 从系统字体目录加载
                if (hasAlternate) {
                    FontManager.ui = FontManager.create((int)sizeVal, fontName, styleVal, altName);
                    FontManager.small = FontManager.create(6, fontName, styleVal, altName);
                } else {
                    FontManager.ui = FontManager.create((int)sizeVal, fontName, styleVal);
                    FontManager.small = FontManager.create(6, fontName, styleVal);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            // 出错时回退到默认字体
            try {
                FontManager.ui = FontManager.assets(this.size.getValueInt(), "default", this.style.getValue().get());
                FontManager.small = FontManager.assets(6.0f, "default", this.style.getValue().get());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * 检查字体是否在资源目录中可用
     */
    private boolean isResourceFontAvailable(String fontName) {
        try {
            ClassLoader cl = FontManager.class.getClassLoader();
            return cl.getResourceAsStream("assets/stella/font/" + fontName + ".ttf") != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static enum Style {
        PLAIN{

            @Override
            public int get() {
                return 0;
            }
        }
        ,
        BOLD{

            @Override
            public int get() {
                return 1;
            }
        }
        ,
        ITALIC{

            @Override
            public int get() {
                return 2;
            }
        };


        public abstract int get();
    }

    /**
     * 备用字体选择器
     * 用于显示中文字符
     */
    public static enum AlternateFont {
        NONE("null", "无"),
        MSYH("msyh", "微软雅黑"),
        SONGTI("songti", "Songti"),
        SIMHEI("simhei", "黑体"),
        KAITI("kaiti", "楷体");

        private final String fontName;
        private final String displayName;

        AlternateFont(String fontName, String displayName) {
            this.fontName = fontName;
            this.displayName = displayName;
        }

        public String getFontName() {
            return fontName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}

