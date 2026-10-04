/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.client.gui.Drawable
 *  net.minecraft.client.gui.Element
 *  net.minecraft.client.gui.ScreenRect
 *  net.minecraft.client.gui.screen.DownloadingTerrainScreen
 *  net.minecraft.client.gui.screen.ProgressScreen
 *  net.minecraft.client.gui.screen.Screen
 *  net.minecraft.client.gui.screen.TitleScreen
 *  net.minecraft.client.util.math.MatrixStack
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package dev.stella.asm.mixins;

import dev.stella.stella;
import dev.stella.api.utils.render.Render2DUtil;
import dev.stella.core.impl.LoadingImageManager;
import dev.stella.core.impl.ShaderManager;
import dev.stella.mod.modules.impl.client.ClickGui;
import dev.stella.mod.modules.impl.client.ClientSetting;
import java.awt.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.ladysnake.satin.api.managed.ManagedShaderEffect;

@Mixin(value={Screen.class})
public class MixinScreen {
    @Shadow
    public int width;
    @Shadow
    public int height;
    @Shadow
    protected MinecraftClient client;

    /** Stella: 记录 render HEAD 是否已 push GUI 缩放矩阵，保证 TAIL 必然配对 pop */
    @Unique
    private boolean stella$scalePushed = false;

    @Inject(method={"renderBackground"}, at={@At(value="HEAD")}, cancellable=true)
    public void renderInGameBackgroundHook(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // 注意：此处不再 push GUI 缩放矩阵 —— 缩放由 render HEAD 推入、render TAIL 弹出，
        // 否则每帧多一次 push 且无配对 pop，选项界面会逐帧累积缩放（SunCat 原 bug）
        ci.cancel();
        if (this.client.world == null) {
            boolean isLoading;
            boolean isMainMenu = (Object)this instanceof TitleScreen;
            boolean bl = isLoading = (Object)this instanceof ProgressScreen || (Object)this instanceof DownloadingTerrainScreen;
            if (isMainMenu || isLoading) {
                float x0;
                float i;
                float w = this.width;
                float h = this.height;
                MatrixStack m = context.getMatrices();
                Render2DUtil.verticalGradient(m, 0.0f, 0.0f, w, h, new Color(40, 10, 60, 255), new Color(200, 20, 150, 255));
                
                // 渲染加载图片（仅在加载画面）
                if (isLoading && ClientSetting.INSTANCE != null && ClientSetting.INSTANCE.loadingImage.getValue()) {
                    this.renderLoadingImage(context, w, h);
                }
                
                long now = System.currentTimeMillis();
                float phase = (float)(now % 6000L) / 6000.0f;
                float angle = 0.523599f;
                float dx = (float)Math.tan(angle) * h;
                float base = -h;
                float spacing = 36.0f;
                float shift = phase * spacing * 4.0f;
                Color c1 = new Color(220, 60, 170, 52);
                Color c2 = new Color(160, 40, 130, 36);
                for (i = base; i < w; i += spacing) {
                    x0 = i + shift;
                    Render2DUtil.drawLine(m, x0, 0.0f, x0 + dx, h, c1.getRGB());
                }
                for (i = base + spacing / 2.0f; i < w; i += spacing) {
                    x0 = i + shift * 0.8f;
                    Render2DUtil.drawLine(m, x0, 0.0f, x0 + dx, h, c2.getRGB());
                }
                float hSpacing = 64.0f;
                Color c3 = new Color(255, 255, 255, 18);
                for (float y = 0.0f; y <= h; y += hSpacing) {
                    Render2DUtil.drawLine(m, 0.0f, y, w, y, c3.getRGB());
                }
                if (!stella.SHADER.fullNullCheck()) {
                    ManagedShaderEffect gradient = stella.SHADER.getShader(ShaderManager.Shader.Gradient);
                    gradient.setUniformValue("alpha2", 0.2f);
                    gradient.setUniformValue("rgb", 0.78f, 0.05f, 0.59f);
                    gradient.setUniformValue("rgb1", 0.56f, 0.06f, 0.68f);
                    gradient.setUniformValue("rgb2", 0.93f, 0.12f, 0.63f);
                    gradient.setUniformValue("rgb3", 0.64f, 0.0f, 0.64f);
                    gradient.setUniformValue("step", 180.0f);
                    gradient.setUniformValue("radius", 2.0f);
                    gradient.setUniformValue("quality", 1.0f);
                    gradient.setUniformValue("divider", 150.0f);
                    gradient.setUniformValue("maxSample", 10.0f);
                    gradient.setUniformValue("resolution", w, h);
                    float t = (float)(now % 100000L) / 1000.0f;
                    gradient.setUniformValue("time", t * 300.0f);
                    gradient.render(this.client.getRenderTickCounter().getTickDelta(true));
                }
                return;
            }
            this.renderPanoramaBackground(context, delta);
        }
        if (ClientSetting.INSTANCE.darkening.getValue() && !((Object)this instanceof TitleScreen)) {
            this.renderDarkening(context);
        }
        if (this.client.world != null && ClickGui.getInstance().tint.booleanValue) {
            context.fillGradient(0, 0, this.width, this.height, ClickGui.getInstance().tint.getValue().getRGB(), ClickGui.getInstance().endColor.getValue().getRGB());
        }
    }

    @Inject(method={"render"}, at={@At(value="HEAD")})
    public void renderHook(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // 为OptionsScreen添加GUI缩放支持（push 由 TAIL 通过 stella$scalePushed 配对弹出）
        if ((Object)this instanceof net.minecraft.client.gui.screen.option.OptionsScreen) {
            ClickGui gui = ClickGui.getInstance();
            if (gui != null && gui.guiScale.getValueFloat() != 1.0f) {
                float scale = gui.guiScale.getValueFloat();
                context.getMatrices().push();
                context.getMatrices().scale(scale, scale, 1.0f);
                context.getMatrices().translate(0.0f, 0.0f, 0.0f);
                this.stella$scalePushed = true;
            }
        }
    }

    @Inject(method={"render"}, at={@At(value="TAIL")})
    public void renderTailHook(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // 为OptionsScreen添加GUI缩放支持
        if (this.stella$scalePushed) {
            this.stella$scalePushed = false;
            context.getMatrices().pop();
        }
    }
    
    /**
     * 渲染加载图片
     */
    private void renderLoadingImage(DrawContext context, float w, float h) {
        LoadingImageManager manager = LoadingImageManager.getInstance();

        // 如果未初始化且未在加载中，启动异步加载
        if (!manager.isInitialized() && !manager.isInitializing()) {
            manager.init();
        }

        // 如果还没加载好，直接返回（不阻塞渲染）
        if (!manager.isEnabled()) {
            return;
        }

        manager.update();

        LoadingImageManager.LoadingImage image = manager.getCurrentImage();
        if (image == null) {
            return;
        }

        // 计算适配屏幕的渲染 bounds
        float[] bounds = manager.calculateRenderBounds((int)w, (int)h, image);
        float x = bounds[0];
        float y = bounds[1];
        float renderWidth = bounds[2];
        float renderHeight = bounds[3];

        // 绑定纹理并绘制
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.getTextureManager().bindTexture(image.textureId);

        context.drawTexture(
            image.textureId,
            (int)x,
            (int)y,
            0,
            0,
            (int)renderWidth,
            (int)renderHeight,
            (int)renderWidth,
            (int)renderHeight
        );
    }

    @Shadow
    protected void renderPanoramaBackground(DrawContext context, float delta) {
    }

    @Shadow
    protected void renderDarkening(DrawContext context) {
    }

    @Shadow
    public void close() {
    }

    @Shadow
    public ScreenRect getNavigationFocus() {
        return null;
    }

    @Shadow
    protected <T extends Element & Drawable> T addDrawableChild(T drawableElement) {
        return null;
    }
}
