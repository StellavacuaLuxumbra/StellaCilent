package dev.stella.mod.gui.clickgui.impl;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;

final class ClickGuiScreen extends Screen {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static List<AbstractCategory> windows;
    public static boolean anyHovered;

    private boolean firstOpen;
    private float scrollY, closeAnimation, prevYaw, prevPitch, closeDirectionX, closeDirectionY;
    public static boolean close = false, imageDirection;

    public static String currentDescription = "";
    public EaseOutBack imageAnimation = new EaseOutBack(6);

    // ClickGuiFrame 相关字段
    private ClickGuiFrame frame;
    private float totalOffsetX, totalOffsetY;

    // 配置字段（需要从配置系统获取）
    private static boolean blurEnabled = true;
    private static boolean descriptionsEnabled = true;
    private static boolean tipsEnabled = true;
    private static boolean closeAnimationEnabled = true;
    private static int moduleWidth = 100;
    private static Image image = Image.None;
    private static ScrollMode scrollMode = ScrollMode.New;

    public ClickGuiScreen() {
        super(Text.of("ClickGUI"));
        windows = Lists.newArrayList();
        firstOpen = true;
        this.setInstance();
    }

    private static ClickGuiScreen INSTANCE = new ClickGuiScreen();

    public static ClickGuiScreen getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ClickGuiScreen();
        }

        imageDirection = true;

        return INSTANCE;
    }

    public static ClickGuiScreen getClickGui() {
        windows.forEach(AbstractCategory::init);
        return ClickGuiScreen.getInstance();
    }

    private void setInstance() {
        INSTANCE = this;
    }

    @Override
    protected void init() {
        // 初始化 ClickGuiFrame
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();
        float scale = 1.0f;
        float slideY = 0f;
        float pageOffsetX = 0f;
        int pageW = 100;
        int panelX = 0;
        int panelY = 0;
        int panelW = screenW;
        int panelH = screenH;
        totalOffsetX = 0f;
        totalOffsetY = 0f;

        frame = new ClickGuiFrame(scale, slideY, pageOffsetX, pageW, panelX, panelY, panelW, panelH, totalOffsetX, totalOffsetY, screenW, screenH);

        if (firstOpen) {
            float offset = 0;
            int windowHeight = 18;

            int halfWidth = screenW / 2;
            int halfWidthCats = (int) ((((float) Category.values().length - 1) / 2f) * (moduleWidth + 4f));

            for (final Category category : Category.values()) {
                if (category == Category.HUD) continue;
                CategoryWindow window = new CategoryWindow(category, getModulesByCategory(category), (halfWidth - halfWidthCats) + offset, 20, 100, windowHeight);
                window.setOpen(true);
                windows.add(window);
                offset += moduleWidth + 2;
                if (offset > screenW)
                    offset = 0;
            }
            firstOpen = false;
        } else {
            if (!windows.isEmpty() && (windows.getFirst().getX() < 0 || windows.getFirst().getY() < 0)) {
                float offset = 0;

                int halfWidth = screenW / 2;
                int halfWidthCats = (int) (3 * (moduleWidth + 4f));

                for (AbstractCategory w : windows) {
                    w.setX((halfWidth - halfWidthCats) + offset);
                    w.setY(20);
                    offset += moduleWidth + 2;
                    if (offset > screenW)
                        offset = 0;
                }
            }
        }
        windows.forEach(AbstractCategory::init);
    }

    // 模拟获取模块的方法
    private List<AbstractModule> getModulesByCategory(Category category) {
        // 这里应该从你的模块系统中获取
        return Lists.newArrayList();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void tick() {
        windows.forEach(AbstractCategory::tick);
        imageAnimation.update(imageDirection);

        if (close) {
            if (mc.player != null) {
                if (mc.player.getPitch() > prevPitch)
                    closeDirectionY = (prevPitch - mc.player.getPitch()) * 300;

                if (mc.player.getPitch() < prevPitch)
                    closeDirectionY = (prevPitch - mc.player.getPitch()) * 300;

                if (mc.player.getYaw() > prevYaw)
                    closeDirectionX = (prevYaw - mc.player.getYaw()) * 300;

                if (mc.player.getYaw() < prevYaw)
                    closeDirectionX = (prevYaw - mc.player.getYaw()) * 300;
            }

            if (closeDirectionX < 1 && closeDirectionY < 1 && closeAnimation > 2)
                closeDirectionY = -3000;

            closeAnimation++;
            if (closeAnimation > 6) {
                close = false;
                windows.forEach(AbstractCategory::restorePos);
                close();
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (blurEnabled)
            applyBlur();

        anyHovered = false;

        // 使用 frame 转换鼠标坐标
        float unitMouseX = frame.unitMouseX(mouseX);
        float unitMouseY = frame.unitMouseY(mouseY);

        if (image != Image.None) {
            RenderSystem.setShaderTexture(0, image.getIdentifier());

            Render2DEngine.renderTexture(context.getMatrices(),
                    mc.getWindow().getScaledWidth() - image.fileWidth * imageAnimation.getAnimationd(),
                    mc.getWindow().getScaledHeight() - image.fileHeight,
                    image.fileWidth,
                    image.fileHeight,
                    0, 0,
                    image.fileWidth, image.fileHeight, image.fileWidth, image.fileHeight);
        }

        if (closeAnimation <= 6) {
            windows.forEach(w -> {
                w.setX((float) (w.getX() + closeDirectionX * AnimationUtility.deltaTime()));
                w.setY((float) (w.getY() + closeDirectionY * AnimationUtility.deltaTime()));
            });
        }

        if (mc.player == null || mc.world == null)
            renderBackground(context, mouseX, mouseY, delta);

        if (scrollMode == ScrollMode.Old) {
            for (AbstractCategory window : windows) {
                if (InputUtil.isKeyPressed(mc.getWindow().getHandle(), GLFW.GLFW_KEY_DOWN))
                    window.setY(window.getY() + 2);
                if (InputUtil.isKeyPressed(mc.getWindow().getHandle(), GLFW.GLFW_KEY_UP))
                    window.setY(window.getY() - 2);
                if (InputUtil.isKeyPressed(mc.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT))
                    window.setX(window.getX() + 2);
                if (InputUtil.isKeyPressed(mc.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT))
                    window.setX(window.getX() - 2);
                if (scrollY != 0)
                    window.setY(window.getY() + scrollY);
            }
        } else {
            for (AbstractCategory window : windows) {
                if (scrollY != 0)
                    window.setModuleOffset(scrollY, (int) unitMouseX, (int) unitMouseY);
            }
        }

        scrollY = 0;
        windows.forEach(w -> w.render(context, (int) unitMouseX, (int) unitMouseY, delta));

        if (!Objects.equals(currentDescription, "") && descriptionsEnabled) {
            Render2DEngine.drawHudBase(context, mouseX + 7, mouseY + 5,
                    FontRenderers.sf_medium.getStringWidth(currentDescription) + 6, 11, 1f, false);
            FontRenderers.sf_medium.drawString(context, currentDescription,
                    mouseX + 10, mouseY + 8, getColor(0));
            currentDescription = "";
        }

        if (tipsEnabled && !close) {
            String tips = "Left Mouse Click to enable module\n" +
                    "Right Mouse Click to open module settings\n" +
                    "Middle Mouse Click to bind module\n" +
                    "Ctrl + F to start searching\n" +
                    "Drag n Drop config there to load\n" +
                    "Shift + Left Mouse Click to change module visibility\n" +
                    "Middle Mouse Click on slider to enter value from keyboard\n" +
                    "Delete + Left Mouse Click on module to reset";
            FontRenderers.sf_medium.drawString(context, tips,
                    5, mc.getWindow().getScaledHeight() - 80, getColor(0));
        }

        if (!anyHovered && !ClickGuiScreen.anyHovered) {
            if (GLFW.glfwGetPlatform() != GLFW.GLFW_PLATFORM_WAYLAND) {
                GLFW.glfwSetCursor(mc.getWindow().getHandle(),
                        GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR));
            }
        }
    }

    private void applyBlur() {
        // 模糊效果实现
    }

    private int getColor(int index) {
        // 颜色获取逻辑
        return 0xFFFFFFFF;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollY += (int) (verticalAmount * 5D);
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float unitMouseX = frame.unitMouseX((int) mouseX);
        float unitMouseY = frame.unitMouseY((int) mouseY);

        windows.forEach(w -> {
            w.mouseClicked((int) unitMouseX, (int) unitMouseY, button);
            windows.forEach(w1 -> {
                if (w.dragging && w != w1) w1.dragging = false;
            });
        });
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        float unitMouseX = frame.unitMouseX((int) mouseX);
        float unitMouseY = frame.unitMouseY((int) mouseY);

        windows.forEach(w -> w.mouseReleased((int) unitMouseX, (int) unitMouseY, button));
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char key, int modifier) {
        windows.forEach(w -> w.charTyped(key, modifier));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        windows.forEach(w -> w.keyTyped(keyCode));

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (mc.player == null || !closeAnimationEnabled) {
                imageDirection = false;
                imageAnimation.reset();
                super.keyPressed(keyCode, scanCode, modifiers);
                return true;
            }

            if (close)
                return true;

            imageDirection = false;

            windows.forEach(AbstractCategory::savePos);

            closeDirectionX = 0;
            closeDirectionY = 0;

            close = true;
            mc.mouse.lockCursor();

            closeAnimation = 0;
            if (mc.player != null) {
                prevYaw = mc.player.getYaw();
                prevPitch = mc.player.getPitch();
            }
            return true;
        }

        return false;
    }

    // ==================== 内部类定义 ====================

    public static final class ClickGuiFrame {
        public final float scale;
        public final float slideY;
        public final float pageOffsetX;
        public final int pageW;
        public final int panelX;
        public final int panelY;
        public final int panelW;
        public final int panelH;
        public final float totalOffsetX;
        public final float totalOffsetY;
        public final int screenW;
        public final int screenH;

        public ClickGuiFrame(float scale, float slideY, float pageOffsetX, int pageW, int panelX, int panelY, int panelW, int panelH, float totalOffsetX, float totalOffsetY, int screenW, int screenH) {
            this.scale = scale;
            this.slideY = slideY;
            this.pageOffsetX = pageOffsetX;
            this.pageW = pageW;
            this.panelX = panelX;
            this.panelY = panelY;
            this.panelW = panelW;
            this.panelH = panelH;
            this.totalOffsetX = totalOffsetX;
            this.totalOffsetY = totalOffsetY;
            this.screenW = screenW;
            this.screenH = screenH;
        }

        public float unitMouseX(int mouseX) {
            return this.scale == 0.0f ? (float)mouseX : (float)mouseX / this.scale;
        }

        public float unitMouseY(int mouseY) {
            return this.scale == 0.0f ? (float)mouseY : ((float)mouseY - this.slideY) / this.scale;
        }

        public float pageUnitW() {
            return this.scale == 0.0f ? (float)this.pageW : (float)this.pageW / this.scale;
        }

        public float baseX(Page page) {
            return this.pageOffsetX + (float)page.ordinal() * this.pageUnitW();
        }
    }

    public enum Page {
        MAIN, SETTINGS, COLOR, BIND
    }

    public enum Image {
        None(null, 0, 0),
        Background1("clickgui/bg1", 256, 256),
        Background2("clickgui/bg2", 256, 256);

        private final String path;
        public final int fileWidth;
        public final int fileHeight;
        private Identifier identifier;

        Image(String path, int width, int height) {
            this.path = path;
            this.fileWidth = width;
            this.fileHeight = height;
        }

        public Identifier getIdentifier() {
            if (identifier == null && path != null) {
                // 使用 Identifier.of 静态方法（1.21+）
                identifier = Identifier.of("idhammai", path);
            }
            return identifier;
        }
    }

    public enum ScrollMode {
        Old, New
    }

    public enum Category {
        COMBAT("Combat"),
        MOVEMENT("Movement"),
        RENDER("Render"),
        PLAYER("Player"),
        WORLD("World"),
        MISC("Misc"),
        CLIENT("Client"),
        HUD("HUD");

        public final String name;

        Category(String name) {
            this.name = name;
        }
    }

    public static class EaseOutBack {
        private float value;
        private final int speed;

        public EaseOutBack(int speed) {
            this.speed = speed;
            this.value = 0;
        }

        public void update(boolean direction) {
            // 动画更新逻辑
            if (direction) {
                value = Math.min(1, value + 0.1f);
            } else {
                value = Math.max(0, value - 0.1f);
            }
        }

        public float getAnimationd() {
            return value;
        }

        public void reset() {
            value = 0;
        }
    }

    public static class AnimationUtility {
        public static float deltaTime() {
            return 0.016f; // 模拟 60 FPS 的 delta time
        }
    }

    public static class Render2DEngine {
        public static void renderTexture(MatrixStack matrices,
                                         float x, float y, float width, float height,
                                         float u, float v, float regionWidth, float regionHeight,
                                         float textureWidth, float textureHeight) {
            // 纹理渲染实现
            // 这里应该使用 DrawContext 的 drawTexture 方法
            // 但由于没有 DrawContext 参数，这只是一个占位
        }

        public static void drawHudBase(DrawContext context,
                                       int x, int y, int width, int height, float alpha, boolean shadow) {
            // HUD 背景绘制实现 - 使用 DrawContext
            int color = ((int)(alpha * 255) << 24) | 0x000000;
            context.fill(x, y, x + width, y + height, color);
        }
    }

    public static class FontRenderers {
        public static class sf_medium {
            public static int getStringWidth(String text) {
                return text.length() * 6;
            }

            public static void drawString(DrawContext context,
                                          String text, int x, int y, int color) {
                // 字体渲染实现 - 使用 DrawContext
                context.drawText(mc.textRenderer, text, x, y, color, false);
            }
        }
    }

    // ==================== 抽象基类 ====================

    public static abstract class AbstractCategory {
        public boolean dragging;

        public abstract void init();
        public abstract void tick();
        public abstract void render(DrawContext context, int mouseX, int mouseY, float delta);
        public abstract void mouseClicked(int mouseX, int mouseY, int button);
        public abstract void mouseReleased(int mouseX, int mouseY, int button);
        public abstract void keyTyped(int keyCode);
        public abstract void charTyped(char key, int modifier);
        public abstract void setModuleOffset(float offset, int mouseX, int mouseY);
        public abstract void savePos();
        public abstract void restorePos();

        public abstract float getX();
        public abstract float getY();
        public abstract void setX(float x);
        public abstract void setY(float y);
        public abstract void setOpen(boolean open);
    }

    public static class CategoryWindow extends AbstractCategory {
        private final Category category;
        private final List<AbstractModule> modules;
        private float x, y;
        private final int width, height;
        private boolean open;

        public CategoryWindow(Category category, List<AbstractModule> modules, float x, float y, int width, int height) {
            this.category = category;
            this.modules = modules;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        @Override
        public void init() {}

        @Override
        public void tick() {}

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {}

        @Override
        public void mouseClicked(int mouseX, int mouseY, int button) {}

        @Override
        public void mouseReleased(int mouseX, int mouseY, int button) {}

        @Override
        public void keyTyped(int keyCode) {}

        @Override
        public void charTyped(char key, int modifier) {}

        @Override
        public void setModuleOffset(float offset, int mouseX, int mouseY) {}

        @Override
        public void savePos() {}

        @Override
        public void restorePos() {}

        @Override
        public float getX() { return x; }

        @Override
        public float getY() { return y; }

        @Override
        public void setX(float x) { this.x = x; }

        @Override
        public void setY(float y) { this.y = y; }

        @Override
        public void setOpen(boolean open) { this.open = open; }
    }

    public static abstract class AbstractModule {
        // 模块基类
    }
}