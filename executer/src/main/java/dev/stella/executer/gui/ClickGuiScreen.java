package dev.stella.executer.gui;

import dev.stella.executer.gui.render.Animation;
import dev.stella.executer.gui.render.ColorUtil;
import dev.stella.executer.gui.render.Render2DUtil;
import dev.stella.executer.gui.setting.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class ClickGuiScreen extends Screen {
    private static ClickGuiScreen INSTANCE;
    private final ArrayList<Component> components = new ArrayList<>();
    private final Animation openAnim = new Animation(0f);
    private String searchText = "";
    private boolean searchFocused = false;
    private long searchCursorBlinkTime = 0;
    private boolean searchCursorVisible = false;

    // SunCat-matching colors
    public static final int DEFAULT_COLOR = 0xDD1E1E1E;
    public static final int HOVER_COLOR = 0xDC323232;
    public static final int ACTIVE_ALPHA = 180;
    public static final int HOVER_ALPHA = 220;
    public static final int TOP_ALPHA = 210;
    public static final int BG_ALPHA = 236;
    public static final int ENABLE_TEXT_COLOR = 0xFFFFFFFF;
    public static final int DEFAULT_TEXT_COLOR = 0xFF888888;
    public static final int HEADER_HEIGHT = 22; // categoryHeight(17) + 5

    private enum Page { Module }
    private Page page = Page.Module;

    public static ClickGuiScreen getInstance() {
        if (INSTANCE == null) INSTANCE = new ClickGuiScreen();
        return INSTANCE;
    }

    private ClickGuiScreen() {
        super(Text.of("Stella ClickGUI"));
    }

    @Override
    protected void init() {
        components.clear();
        Category[] cats = {Category.COMBAT, Category.MOVEMENT, Category.RENDER, Category.PLAYER, Category.MISC};
        int moduleBtnWidth = 93;
        int categoryWidth = 95;
        int layoutWidth = Math.max(categoryWidth, moduleBtnWidth);
        int spacing = layoutWidth + 1;
        int count = cats.length;
        int totalWidth = count * layoutWidth + (count - 1);
        int startX = Math.round(((float) width - (float) totalWidth) / 2.0f);
        int startY = Math.round((float) height / 6.0f);
        int offsetX = Math.round(((float) layoutWidth - (float) moduleBtnWidth) / 2.0f);

        int x = startX - spacing;
        for (Category cat : cats) {
            x += spacing;
            Component comp = new Component(cat.getDisplayName(), cat, x + offsetX, startY, moduleBtnWidth);
            components.add(comp);
        }
    }

    public static int getActiveColor(double delay) {
        return ColorUtil.rainbow((int) delay, 210);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float alphaValue = openAnim.get(1f, 400L, Animation.Easing.BACK_IN_OUT);
        float scale = 0.92f + 0.08f * alphaValue;
        float slideY = (1.0f - alphaValue) * 20.0f;

        ItemCtx.context = ctx;

        // background
        ctx.fill(0, 0, width, height, 0x801E1E1E);
        dev.stella.executer.gui.render.Snowflakes.getInstance().render(ctx);

        // compute panel bounds
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Component c : components) {
            minX = Math.min(minX, c.getX());
            minY = Math.min(minY, c.getY());
            maxX = Math.max(maxX, c.getX() + c.getWidth());
            maxY = Math.max(maxY, c.getY() + c.getHeight());
        }
        int margin = 16;
        int panelX = Math.max(8, minX - margin);
        int panelY = Math.max(6, minY - margin);
        int panelW = Math.min(width - panelX - 8, maxX - minX + margin * 2);
        int panelH = Math.min(height - panelY - 6, maxY - minY + margin * 2 + 24);

        ctx.getMatrices().push();
        ctx.getMatrices().translate(0.0f, slideY, 0.0f);
        ctx.getMatrices().scale(scale, scale, 1.0f);

        // render search bar
        renderSearchBar(ctx, mouseX, mouseY);

        // render all components
        for (Component comp : components) {
            comp.drawScreen(ctx, mouseX, mouseY, delta);
        }
        ctx.getMatrices().pop();
    }

    private void renderSearchBar(DrawContext ctx, int mouseX, int mouseY) {
        if (searchText == null) searchText = "";
        long now = System.currentTimeMillis();
        if (now - searchCursorBlinkTime > 500) {
            searchCursorVisible = !searchCursorVisible;
            searchCursorBlinkTime = now;
        }
        int sw = width;
        int sbW = Math.min(200, sw - 40);
        int sbH = 16;
        int sbX = (sw - sbW) / 2;
        int sbY = 8;
        boolean hovered = mouseX >= sbX && mouseX <= sbX + sbW && mouseY >= sbY && mouseY <= sbY + sbH;

        Render2DUtil.drawRoundedRect(ctx, sbX, sbY, sbW, sbH, 4, 0xC81E1E1E);
        int borderColor = searchFocused ? 0xD20078D4 : 0x96505050;
        Render2DUtil.drawRoundedStroke(ctx, sbX, sbY, sbW, sbH, 4, borderColor, 12);

        if (searchText.isEmpty() && !searchFocused) {
            ctx.drawTextWithShadow(textRenderer, "Search...", sbX + 6, sbY + 4, 0x96969696);
        } else {
            ctx.drawTextWithShadow(textRenderer, searchText, sbX + 6, sbY + 4, 0xFFFFFFFF);
            if (searchFocused && searchCursorVisible) {
                int tw = textRenderer.getWidth(searchText);
                ctx.fill(sbX + 6 + tw + 1, sbY + 3, sbX + 6 + tw + 2, sbY + 13, 0xFFFFFFFF);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int sw = width;
            int sbW = Math.min(200, sw - 40);
            int sbX = (sw - sbW) / 2;
            searchFocused = mouseX >= sbX && mouseX <= sbX + sbW && mouseY >= 8 && mouseY <= 24;
        }
        for (Component c : components) {
            if (c.mouseClicked((int) mouseX, (int) mouseY, button)) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (Component c : components) {
            c.mouseReleased((int) mouseX, (int) mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (Component c : components) {
            if (c.isMouseOver((int) mouseX, (int) mouseY)) {
                c.mouseScrolled(mouseX, mouseY, verticalAmount);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { searchFocused = false; return true; }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchText.isEmpty()) { searchText = searchText.substring(0, searchText.length() - 1); return true; }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { searchFocused = false; return true; }
            return true;
        }
        for (Component c : components) {
            c.onKeyPressed(keyCode);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchFocused) {
            if (searchText.length() < 50 && chr >= 32 && chr < 127) searchText += chr;
            return true;
        }
        for (Component c : components) {
            c.onKeyTyped(chr, modifiers);
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean shouldPause() { return false; }

    // ========== Search filter ==========
    public String getSearchText() { return searchText; }

    // ========== Static context holder ==========
    static class ItemCtx {
        static DrawContext context;
    }

    // ============================================================
    // Component = category column panel (header + module list)
    // ============================================================
    private static class Component {
        final String name;
        final Category category;
        int x, y;
        int x2, y2;
        final int width;
        int height = HEADER_HEIGHT;
        boolean drag = false;
        boolean open = true;
        int scrollOffset = 0;
        private float animX, animY;
        final ArrayList<ModuleButton> items = new ArrayList<>();

        Component(String name, Category category, int x, int y, int moduleBtnWidth) {
            this.name = name;
            this.category = category;
            this.x = x;
            this.y = y;
            this.animX = x;
            this.animY = y;
            this.width = moduleBtnWidth;
            setupItems();
        }

        void setupItems() {
            items.clear();
            List<Module> catModules = ModuleManager.getInstance().getModulesByCategory(category);
            for (Module m : catModules) {
                items.add(new ModuleButton(m, width));
            }
        }

        int getX() { return (int) animX; }
        int getY() { return (int) animY; }
        int getWidth() { return width; }
        int getHeight() {
            float h = HEADER_HEIGHT;
            for (ModuleButton item : items) {
                h += item.getTotalHeight();
            }
            return (int) h;
        }

        void drawScreen(DrawContext ctx, int mouseX, int mouseY, float delta) {
            // drag
            if (drag) {
                x = x2 + mouseX;
                y = y2 + mouseY;
                animX = x;
                animY = y;
            }

            String searchFilter = ClickGuiScreen.getInstance().getSearchText();
            int filteredCount = 0;
            for (ModuleButton item : items) {
                if (searchFilter.isEmpty() || item.module.getName().toLowerCase().contains(searchFilter.toLowerCase())) {
                    filteredCount++;
                }
            }

            // header
            int categoryWidth = Math.max(95, width);
            float headerX = x + ((float) width - (float) categoryWidth) / 2.0f;
            int headerH = height - 5;
            int topColor = ColorUtil.injectAlpha(0x0078D4, TOP_ALPHA);
            Render2DUtil.fill(ctx, (int) headerX, y, (int) (headerX + categoryWidth), y + headerH, topColor);

            // header text
            int rainbowColor = dev.stella.executer.gui.render.Spectrum.getInstance().getColor(0f, 255);
            ctx.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, name,
                    (int) (headerX + categoryWidth / 2), y + (headerH - 8) / 2, rainbowColor);

            if (open && filteredCount > 0) {
                // content background
                int contentStartY = y + headerH;
                float contentHeight = 0;
                for (ModuleButton item : items) {
                    if (!searchFilter.isEmpty() && !item.module.getName().toLowerCase().contains(searchFilter.toLowerCase())) continue;
                    contentHeight += item.getTotalHeight();
                }
                if (contentHeight > 0) {
                    int bgColor = ColorUtil.injectAlpha(0x1E1E1E, BG_ALPHA);
                    Render2DUtil.fill(ctx, x, contentStartY, x + width, (int) (contentStartY + contentHeight), bgColor);
                }

                // scissor
                MinecraftClient mc = MinecraftClient.getInstance();
                float sf = (float) mc.getWindow().getScaleFactor();
                int scissorY = (int) (mc.getWindow().getFramebufferHeight() - (contentStartY + (int) contentHeight) * sf);
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor((int) (x * sf), Math.max(0, scissorY), (int) (width * sf), (int) (contentHeight * sf));

                float yOff = contentStartY - scrollOffset;
                for (ModuleButton item : items) {
                    if (!searchFilter.isEmpty() && !item.module.getName().toLowerCase().contains(searchFilter.toLowerCase())) continue;
                    item.setLocation(x + 2, (int) yOff);
                    item.drawScreen(ctx, mouseX, mouseY);
                    yOff += item.getTotalHeight();
                }
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
        }

        boolean mouseClicked(int mouseX, int mouseY, int button) {
            int categoryWidth = Math.max(95, width);
            float headerX = x + ((float) width - (float) categoryWidth) / 2.0f;
            int headerH = height - 5;
            boolean onHeader = mouseX >= headerX && mouseX <= headerX + categoryWidth && mouseY >= y && mouseY <= y + headerH;

            if (onHeader) {
                if (button == 0) {
                    x2 = x - mouseX;
                    y2 = y - mouseY;
                    drag = true;
                    return true;
                }
                if (button == 1) {
                    open = !open;
                    return true;
                }
            }

            if (!open) return false;

            String searchFilter = ClickGuiScreen.getInstance().getSearchText();
            for (ModuleButton item : items) {
                if (!searchFilter.isEmpty() && !item.module.getName().toLowerCase().contains(searchFilter.toLowerCase())) continue;
                if (item.mouseClicked(mouseX, mouseY, button)) return true;
            }
            return false;
        }

        void mouseReleased(int mouseX, int mouseY, int button) {
            if (button == 0) drag = false;
            for (ModuleButton item : items) item.mouseReleased(mouseX, mouseY, button);
        }

        void mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
            scrollOffset = Math.max(0, scrollOffset - (int) (verticalAmount * 5));
        }

        void onKeyPressed(int key) {
            for (ModuleButton item : items) item.onKeyPressed(key);
        }

        void onKeyTyped(char chr, int key) {
            for (ModuleButton item : items) item.onKeyTyped(chr, key);
        }

        boolean isMouseOver(int mouseX, int mouseY) {
            int totalH = getHeight();
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + totalH;
        }
    }

    // ============================================================
    // ModuleButton - module toggle + settings expander
    // ============================================================
    private static class ModuleButton {
        Module module;
        int x, y;
        int width;
        boolean subOpen = false;
        final int btnHeight = 13;
        private final Animation hoverAnim = new Animation(0f);
        private final Animation expandAnim = new Animation(0f);
        private final ArrayList<SettingControl> settingControls = new ArrayList<>();

        ModuleButton(Module module, int width) {
            this.module = module;
            this.width = width;
            initSettings();
        }

        void initSettings() {
            settingControls.clear();
            for (Setting s : module.getSettings()) {
                settingControls.add(new SettingControl(s, width - 8));
            }
        }

        void setLocation(float x, float y) {
            this.x = (int) x;
            this.y = (int) y;
        }

        int getButtonHeight() { return btnHeight; }

        float getSettingsHeight() {
            float h = 0;
            for (SettingControl sc : settingControls) h += sc.getHeight() + 2.0f;
            return h;
        }

        float getTotalHeight() {
            float settingsH = subOpen ? getSettingsHeight() : 0;
            return (btnHeight + 1.5f) + settingsH;
        }

        void drawScreen(DrawContext ctx, int mouseX, int mouseY) {
            boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + btnHeight;
            boolean pressed = module.isEnabled();
            float hp = hoverAnim.get(hovered ? 1f : 0f, 100L, Animation.Easing.CUBIC_IN_OUT);
            float ep = expandAnim.get(subOpen ? 1f : 0f, 200L, Animation.Easing.CUBIC_IN_OUT);

            // module button background
            float h = btnHeight - 0.5f;
            if (pressed) {
                int accentA = (int) (ACTIVE_ALPHA + (HOVER_ALPHA - ACTIVE_ALPHA) * hp);
                int accentColor = ColorUtil.injectAlpha(0x0078D4, accentA);
                Render2DUtil.fill(ctx, x, y, x + width, (int) (y + h), accentColor);
            } else {
                int bgColor = hovered ? HOVER_COLOR : DEFAULT_COLOR;
                Render2DUtil.fill(ctx, x, y, x + width, (int) (y + h), bgColor);
            }

            // module name
            int textColor = pressed ? ENABLE_TEXT_COLOR : DEFAULT_TEXT_COLOR;
            ctx.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, module.getName(), x + 2, y + 3, textColor);

            // expand indicator (+/-)
            float settingsH = getSettingsHeight();
            if (settingsH > 0) {
                float expandProgress = settingsH > 0 ? ep : 0;
                String icon = subOpen ? "-" : "+";
                int gearColor = 0xFF888888;
                ctx.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, icon,
                        x + width - 8, y + 3, gearColor);
            }

            // settings
            if (subOpen && ep > 0.01f) {
                float visibleH = getSettingsHeight() * ep;
                float settingsY = y + btnHeight + 2;

                // scissor for settings
                MinecraftClient mc = MinecraftClient.getInstance();
                float sf = (float) mc.getWindow().getScaleFactor();
                int scX1 = (int) (x * sf);
                int scY1 = (int) (mc.getWindow().getFramebufferHeight() - (settingsY + visibleH) * sf);
                int scW = (int) (width * sf);
                int scH = (int) (visibleH * sf);
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(scX1, Math.max(0, scY1), scW, Math.max(0, scH));

                for (SettingControl sc : settingControls) {
                    sc.setLocation(x + 1, settingsY);
                    sc.drawScreen(ctx, mouseX, mouseY);
                    settingsY += sc.getHeight() + 2.0f;
                }

                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
        }

        boolean mouseClicked(int mouseX, int mouseY, int button) {
            // header click
            if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + btnHeight) {
                if (button == 0) {
                    module.toggle();
                    dev.stella.executer.StellaExecuter.sendModuleToggleToCpp(module.getModuleId(), module.isEnabled());
                    return true;
                }
                if (button == 1) {
                    subOpen = !subOpen;
                    return true;
                }
            }
            // settings clicks
            if (subOpen) {
                for (SettingControl sc : settingControls) {
                    if (sc.mouseClicked(mouseX, mouseY, button)) return true;
                }
            }
            return false;
        }

        void mouseReleased(int mouseX, int mouseY, int button) {
            for (SettingControl sc : settingControls) sc.mouseReleased(mouseX, mouseY, button);
        }

        void onKeyPressed(int key) {
            for (SettingControl sc : settingControls) sc.onKeyPressed(key);
        }

        void onKeyTyped(char chr, int key) {
            for (SettingControl sc : settingControls) sc.onKeyTyped(chr, key);
        }
    }

    // ============================================================
    // SettingControl - individual setting rendering/interaction
    // ============================================================
    private static class SettingControl {
        final Setting setting;
        int x, y;
        int width;
        int height;
        private boolean draggingSlider = false;

        SettingControl(Setting setting, int width) {
            this.setting = setting;
            this.width = width;
            this.height = 13;
        }

        void setLocation(float x, float y) {
            this.x = (int) x;
            this.y = (int) y;
        }

        int getHeight() { return height; }

        void drawScreen(DrawContext ctx, int mouseX, int mouseY) {
            MinecraftClient mc = MinecraftClient.getInstance();
            boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;

            if (setting instanceof BooleanSetting bs) {
                int bg = hovered ? HOVER_COLOR : DEFAULT_COLOR;
                Render2DUtil.fill(ctx, x, y, x + width, y + height, bg);
                ctx.drawTextWithShadow(mc.textRenderer, bs.getName(), x + 2, y + 3, DEFAULT_TEXT_COLOR);
                // toggle box
                int boxX = x + width - 10;
                int boxY = y + 2;
                Render2DUtil.fill(ctx, boxX, boxY, boxX + 8, boxY + 8, 0xFF323232);
                if (bs.getValue()) {
                    Render2DUtil.fill(ctx, boxX + 1, boxY + 1, boxX + 7, boxY + 7, 0xFF0078D4);
                }
            } else if (setting instanceof SliderSetting ss) {
                int bg = hovered ? HOVER_COLOR : DEFAULT_COLOR;
                Render2DUtil.fill(ctx, x, y, x + width, y + height, bg);
                ctx.drawTextWithShadow(mc.textRenderer, ss.getName(), x + 2, y + 3, DEFAULT_TEXT_COLOR);
                // slider bar
                int barX = x + 2;
                int barY = y + height - 4;
                int barW = width - 4;
                Render2DUtil.fill(ctx, barX, barY, barX + barW, barY + 2, 0xFF323232);
                double ratio = (ss.getValue() - ss.getMinValue()) / (ss.getMaxValue() - ss.getMinValue());
                int fillW = (int) (barW * ratio);
                Render2DUtil.fill(ctx, barX, barY, barX + fillW, barY + 2, 0xFF0078D4);
                // value text
                String val = ss.getDisplayValue();
                ctx.drawTextWithShadow(mc.textRenderer, val, x + width - mc.textRenderer.getWidth(val) - 2, y + 3, 0xFFAAAAAA);
            } else if (setting instanceof EnumSetting<?> es) {
                int bg = hovered ? HOVER_COLOR : DEFAULT_COLOR;
                Render2DUtil.fill(ctx, x, y, x + width, y + height, bg);
                ctx.drawTextWithShadow(mc.textRenderer, es.getName(), x + 2, y + 3, DEFAULT_TEXT_COLOR);
                String val = es.getValue().name();
                ctx.drawTextWithShadow(mc.textRenderer, val, x + width - mc.textRenderer.getWidth(val) - 2, y + 3, 0xFF0078D4);
            } else if (setting instanceof ColorSetting cs) {
                int bg = hovered ? HOVER_COLOR : DEFAULT_COLOR;
                Render2DUtil.fill(ctx, x, y, x + width, y + height, bg);
                ctx.drawTextWithShadow(mc.textRenderer, cs.getName(), x + 2, y + 3, DEFAULT_TEXT_COLOR);
                int c = cs.getColor();
                Render2DUtil.fill(ctx, x + width - 12, y + 2, x + width - 4, y + 10, c);
            }
        }

        boolean mouseClicked(int mouseX, int mouseY, int button) {
            if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) return false;

            if (setting instanceof BooleanSetting bs && button == 0) {
                bs.setValue(!bs.getValue());
                return true;
            }
            if (setting instanceof SliderSetting ss && button == 0) {
                draggingSlider = true;
                updateSlider(ss, mouseX);
                return true;
            }
            if (setting instanceof EnumSetting<?> es && button == 0) {
                es.increment();
                return true;
            }
            return false;
        }

        void mouseReleased(int mouseX, int mouseY, int button) {
            draggingSlider = false;
        }

        void onKeyPressed(int key) {}
        void onKeyTyped(char chr, int key) {}

        private void updateSlider(SliderSetting ss, int mouseX) {
            int barX = x + 2;
            int barW = width - 4;
            double ratio = Math.max(0, Math.min(1, (mouseX - barX) / (double) barW));
            double val = ss.getMinValue() + ratio * (ss.getMaxValue() - ss.getMinValue());
            val = Math.round(val / ss.getIncrement()) * ss.getIncrement();
            ss.setValue(val);
        }
    }
}
