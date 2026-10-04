package dev.stella.mod.modules.impl.client.hud;

import dev.stella.stella;
import dev.stella.api.utils.render.TextUtil;
import dev.stella.core.impl.FontManager;
import dev.stella.mod.modules.HudModule;
import dev.stella.mod.modules.impl.client.ClickGui;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import dev.stella.mod.modules.settings.impl.SliderSetting;
import dev.stella.mod.modules.settings.impl.StringSetting;
import java.util.Objects;
import net.minecraft.client.gui.DrawContext;

public class WaterMarkHudModule extends HudModule {
    public static WaterMarkHudModule INSTANCE;
    public final StringSetting title = this.add(new StringSetting("Title", "%hackname% %version%"));
    public final BooleanSetting animate = this.add(new BooleanSetting("Animate", false));
    public final SliderSetting animSpeed = this.add(new SliderSetting("AnimSpeed", 150, 50, 500, 10, this.animate::getValue));
    public final StringSetting animText = this.add(new StringSetting("AnimText", "Stella", this.animate::getValue));

    private int animIndex = 1;
    private long lastAnimTime = System.currentTimeMillis();
    private boolean animForward = true;
    private String cachedText = "";

    public WaterMarkHudModule() {
        super("WaterMark", "水印", 1, 1);
        INSTANCE = this;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        String text;

        // Handle animation - type out letters one by one then delete
        if (this.animate.getValue()) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - this.lastAnimTime > this.animSpeed.getValue()) {
                this.lastAnimTime = currentTime;

                String fullText = this.animText.getValue();
                if (this.animForward) {
                    this.animIndex++;
                    // When reaching the end, start going backwards
                    if (this.animIndex >= fullText.length()) {
                        this.animForward = false;
                    }
                } else {
                    this.animIndex--;
                    // When going back to start, start going forwards
                    if (this.animIndex <= 1) {
                        this.animForward = true;
                    }
                }
            }

            String animSubstring = this.animText.getValue().substring(0, this.animIndex);
            text = animSubstring.isEmpty() ? " " : animSubstring;
        } else {
            text = this.title.getValue().replaceAll("%version%", stella.VERSION).replaceAll("%hackname%", stella.NAME);
        }

        int w = HudSetting.useFont() ? (int)Math.ceil(FontManager.ui.getWidth(text)) : WaterMarkHudModule.mc.textRenderer.getWidth(text);
        int h;
        if (HudSetting.useFont()) {
            h = (int)Math.ceil(FontManager.ui.getFontHeight());
        } else {
            Objects.requireNonNull(WaterMarkHudModule.mc.textRenderer);
            h = 9;
        }

        int x = this.getHudRenderX(w);
        int y = this.getHudRenderY(h);

        int color = this.getHudColor(0.0);
        TextUtil.drawString(context, text, x, y, color, HudSetting.useFont(), HudSetting.useShadow());

        this.setHudBounds(x, y, Math.max(1, w), Math.max(1, h));
    }

    private int getHudColor(double delay) {
        ClickGui gui = ClickGui.getInstance();
        return gui == null ? -1 : gui.getColor(delay).getRGB();
    }
}