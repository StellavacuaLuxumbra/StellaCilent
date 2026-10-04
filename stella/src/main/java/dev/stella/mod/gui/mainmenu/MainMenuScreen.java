package dev.stella.mod.gui.mainmenu;

import dev.stella.api.utils.render.Render2DUtil;
import dev.stella.core.impl.FontManager;
import dev.stella.stella;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class MainMenuScreen extends Screen {

    private static final float BUTTON_WIDTH = 150.0f;
    private static final float BUTTON_HEIGHT = 20.0f;
    private static final float BUTTON_SPACING = 5.0f;
    private static final float BUTTON_RADIUS = 3.0f;

    private static final float BUTTON_HOVER_SCALE_MAX = 1.1f;
    private static final float BUTTON_HOVER_SCALE_MIN = 1.0f;
    private static final float BUTTON_HOVER_ANIMATION_SPEED = 0.04f;

    private static final float TITLE_Y_OFFSET = 50.0f;
    private static final float TITLE_FONT_SCALE = 1.8f;

    private static final String NAMESPACE = "stella";

    private final List<MenuButton> buttons = new ArrayList<>();

    private Color buttonNormalColor = new Color(40, 40, 40, 120);
    private Color buttonHoveredColor = new Color(40, 60, 60, 180);

    private Color titleColor = new Color(255, 255, 255, 255);

    private static class MenuConfig {
        public int buttonNormalColor = new Color(40, 40, 40, 120).getRGB();
        public int buttonHoveredColor = new Color(40, 60, 60, 180).getRGB();
    }

    private MenuConfig menuConfig = new MenuConfig();

    public MainMenuScreen() {
        super(Text.translatable("menu.singleplayer"));
        initializeButtons();
        loadSavedSettings();
    }

    private void initializeButtons() {
        buttons.add(new MenuButton("Singleplayer", () -> {
            client.setScreen(new SelectWorldScreen(this));
        }));

        buttons.add(new MenuButton("Multiplayer", () -> {
            client.setScreen(new MultiplayerScreen(this));
        }));

        buttons.add(new MenuButton("Options", () -> {
            client.setScreen(new OptionsScreen(this, client.options));
        }));

        buttons.add(new MenuButton("Account Manager", () -> {
            try {
                Class<?> accountScreenClass = Class.forName("ru.vidtu.ias.screen.AccountScreen");
                Object accountScreen = accountScreenClass.getConstructor(Screen.class).newInstance(this);
                client.setScreen((Screen) accountScreen);
            } catch (Exception e) {
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("Account Manager is not available"), false);
                }
            }
        }));

        buttons.add(new MenuButton("Quit Game", () -> {
            client.stop();
        }));
    }

    private void loadSavedSettings() {
        try {
            if (stella.CONFIG != null) {
                String btnNormalStr = stella.CONFIG.getString("menu_color_button_normal", String.valueOf(buttonNormalColor.getRGB()));
                String btnHoveredStr = stella.CONFIG.getString("menu_color_button_hovered", String.valueOf(buttonHoveredColor.getRGB()));

                try {
                    menuConfig.buttonNormalColor = Integer.parseInt(btnNormalStr);
                    menuConfig.buttonHoveredColor = Integer.parseInt(btnHoveredStr);
                } catch (NumberFormatException e) {
                    // Use defaults
                }
            }
        } catch (Exception e) {
            // Use defaults
        }

        buttonNormalColor = new Color(menuConfig.buttonNormalColor, true);
        buttonHoveredColor = new Color(menuConfig.buttonHoveredColor, true);
    }

    private void saveSettings() {
        menuConfig.buttonNormalColor = buttonNormalColor.getRGB();
        menuConfig.buttonHoveredColor = buttonHoveredColor.getRGB();

        try {
            if (stella.CONFIG != null) {
                // 使用正确的键名格式：模块名_设置名
                stella.CONFIG.setString("MainMenu_buttonNormalColor", String.valueOf(menuConfig.buttonNormalColor));
                stella.CONFIG.setString("MainMenu_buttonHoveredColor", String.valueOf(menuConfig.buttonHoveredColor));
                stella.CONFIG.save(); // 使用正确的保存方法
            }
        } catch (Exception e) {
            System.err.println("Failed to save menu settings: " + e.getMessage());
        }
    }

    @Override
    protected void init() {
        super.init();
    }

    private Identifier getBackgroundTexture() {
        try {
            return Identifier.of("stella", "background/bg.png");
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackgroundWithParallax(context, mouseX, mouseY);

        float rightX = width - BUTTON_WIDTH - 50;
        float startY = 50;
        float spacing = 24;

        renderPersonalizedTitle(context, width / 2.0f, startY);

        for (int i = 0; i < buttons.size(); i++) {
            float buttonY = startY + (i * spacing);
            buttons.get(i).render(context, rightX, buttonY, mouseX, mouseY);
        }

        renderVersionInfo(context);
    }

    private void renderVersionInfo(DrawContext context) {
        int screenWidth = this.width;
        int screenHeight = this.height;

        int leftMargin = 5;
        int bottomMargin = 5;

        String addonName = "Stella Client";
        String versionText = "Version " + stella.VERSION;
        String author = "By Stella";

        int lineHeight = 12;
        int totalHeight = lineHeight * 3;

        int yPos = screenHeight - bottomMargin - totalHeight;

        drawGradientText(context, addonName, leftMargin, yPos,
                new int[]{0xFF6B8EFF, 0xFF9370DB, 0xFFBA55D3});

        if (FontManager.ui != null) {
            FontManager.ui.drawString(context.getMatrices(), versionText,
                    leftMargin, yPos + lineHeight, 0.66f, 0.66f, 0.66f, 1.0f, false);
        }

        drawGradientText(context, author, leftMargin, yPos + lineHeight * 2,
                new int[]{0xFF1E40AF, 0xFF60A5FA, 0xFFBFDBFE});
    }

    private void drawGradientText(DrawContext context, String text, int x, int y, int[] colors) {
        int currentX = x;

        for (int i = 0; i < text.length(); i++) {
            float progress = (float) i / (text.length() - 1);
            int colorIndex = (int) (progress * (colors.length - 1));
            float blend = (progress * (colors.length - 1)) - colorIndex;

            int color1 = colors[Math.min(colorIndex, colors.length - 1)];
            int color2 = colors[Math.min(colorIndex + 1, colors.length - 1)];
            int color = blendColors(color1, color2, blend);

            String charStr = String.valueOf(text.charAt(i));
            if (FontManager.ui != null) {
                FontManager.ui.drawString(context.getMatrices(), charStr, currentX, y,
                        ((color >> 16) & 0xFF) / 255.0f,
                        ((color >> 8) & 0xFF) / 255.0f,
                        (color & 0xFF) / 255.0f,
                        1.0f, false);
                currentX += FontManager.ui.getWidth(charStr);
            }
        }
    }

    private int blendColors(int color1, int color2, float ratio) {
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);

        return (r << 16) | (g << 8) | b;
    }

    private void renderPersonalizedTitle(DrawContext context, float centerX, float buttonsStartY) {
        String titleText;
        if (client != null && client.getSession() != null) {
            String playerName = client.getSession().getUsername();
            if (playerName != null && !playerName.isEmpty()) {
                titleText = "Hi, " + playerName;
            } else {
                titleText = "Welcome Back";
            }
        } else {
            titleText = "Welcome Back";
        }

        MatrixStack matrices = context.getMatrices();
        matrices.push();

        float titleX = centerX;
        float titleY = 20.0f;

        matrices.translate(titleX, titleY, 0);
        matrices.scale(TITLE_FONT_SCALE, TITLE_FONT_SCALE, 1.0f);

        Text title = Text.literal(titleText).styled(style -> style.withBold(true));

        float scaledTextWidth = client.textRenderer.getWidth(title) * TITLE_FONT_SCALE;

        context.drawTextWithShadow(client.textRenderer, title,
                (int) (-scaledTextWidth / (2 * TITLE_FONT_SCALE)),
                0,
                titleColor.getRGB());

        matrices.pop();
    }

    private void renderBackgroundWithParallax(DrawContext context, int mouseX, int mouseY) {
        Identifier backgroundTexture = getBackgroundTexture();

        if (backgroundTexture != null) {
            try {
                AbstractTexture abstractTexture = client.getTextureManager().getTexture(backgroundTexture);
                int texWidth = width;
                int texHeight = height;

                if (abstractTexture instanceof NativeImageBackedTexture) {
                    texWidth = ((NativeImageBackedTexture) abstractTexture).getImage().getWidth();
                    texHeight = ((NativeImageBackedTexture) abstractTexture).getImage().getHeight();
                }

                float offsetX = 0;
                float offsetY = 0;

                context.drawTexture(backgroundTexture,
                        (int) offsetX, (int) offsetY,
                        width, height,
                        0, 0,
                        width, height,
                        texWidth, texHeight);
                return;
            } catch (Exception e) {
                System.err.println("Failed to render background: " + e.getMessage());
            }
        }

        renderFallbackBackground(context);
    }

    private void renderFallbackBackground(DrawContext context) {
        for (int y = 0; y < height; y++) {
            float progress = (float) y / height;
            int color = interpolateColor(new Color(10, 10, 40), new Color(40, 10, 10), progress);
            context.fill(0, y, width, y + 1, color);
        }
    }

    private int interpolateColor(Color start, Color end, float progress) {
        int r = (int) (start.getRed() + (end.getRed() - start.getRed()) * progress);
        int g = (int) (start.getGreen() + (end.getGreen() - start.getGreen()) * progress);
        int b = (int) (start.getBlue() + (end.getBlue() - start.getBlue()) * progress);
        return new Color(r, g, b).getRGB();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            float rightX = width - BUTTON_WIDTH - 50;
            float startY = 50;
            float spacing = 24;

            for (int i = 0; i < buttons.size(); i++) {
                float buttonY = startY + (i * spacing);

                if (isMouseOver(rightX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY)) {
                    buttons.get(i).onClick();
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isMouseOver(double x, double y, double width, double height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private class MenuButton {
        private final String text;
        private final Runnable action;
        private float hoverAnimation = 1.0f;

        public MenuButton(String text, Runnable action) {
            this.text = text;
            this.action = action;
        }

        public void render(DrawContext context, float x, float y, int mouseX, int mouseY) {
            boolean hovered = isMouseOver(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY);

            if (hovered) {
                hoverAnimation = Math.min(BUTTON_HOVER_SCALE_MAX, hoverAnimation + BUTTON_HOVER_ANIMATION_SPEED);
            } else {
                hoverAnimation = Math.max(BUTTON_HOVER_SCALE_MIN, hoverAnimation - BUTTON_HOVER_ANIMATION_SPEED);
            }

            MatrixStack matrices = context.getMatrices();

            matrices.push();

            float centerX = x + BUTTON_WIDTH / 2;
            float centerY = y + BUTTON_HEIGHT / 2;

            matrices.translate(centerX, centerY, 0);
            matrices.scale(hoverAnimation, hoverAnimation, 1.0f);
            matrices.translate(-centerX, -centerY, 0);

            Color buttonColor = hovered ? buttonHoveredColor : buttonNormalColor;
            Render2DUtil.drawRoundedRect(matrices, x, y, BUTTON_WIDTH, BUTTON_HEIGHT, BUTTON_RADIUS, buttonColor);

            if (FontManager.ui != null) {
                FontManager.ui.drawCenteredString(matrices, text, x + BUTTON_WIDTH / 2, y + 6, Color.WHITE.getRGB());
            }

            matrices.pop();
        }

        public void onClick() {
            action.run();
        }
    }
}