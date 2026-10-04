package dev.stella.mod.modules.impl.client.hud;

import dev.stella.api.utils.render.TextUtil;
import dev.stella.core.impl.FontManager;
import dev.stella.mod.modules.HudModule;
import dev.stella.mod.modules.impl.client.ClickGui;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import java.util.Objects;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

public class BiomeHudModule extends HudModule {
    public static BiomeHudModule INSTANCE;
    private final BooleanSetting showNamespace = this.add(new BooleanSetting("Namespace", false));
    private final BooleanSetting lowerCase = this.add(new BooleanSetting("LowerCase", false));

    public BiomeHudModule() {
        super("Biome", "", "生物群系", 2, 90, Corner.LeftTop);
        INSTANCE = this;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (BiomeHudModule.mc.player == null || BiomeHudModule.mc.world == null) {
            this.clearHudBounds();
            return;
        }

        String biomeName = this.getBiomeName();
        if (biomeName == null || biomeName.isEmpty()) {
            this.clearHudBounds();
            return;
        }

        String text = "Biome: " + biomeName;
        if (this.lowerCase.getValue()) {
            text = text.toLowerCase();
        }

        int w = HudSetting.useFont() ? (int)Math.ceil(FontManager.ui.getWidth(text)) : BiomeHudModule.mc.textRenderer.getWidth(text);
        int h;
        if (HudSetting.useFont()) {
            h = (int)Math.ceil(FontManager.ui.getFontHeight());
        } else {
            Objects.requireNonNull(BiomeHudModule.mc.textRenderer);
            h = 9;
        }

        int x = this.getHudRenderX(w);
        int y = this.getHudRenderY(h);

        int color = this.getHudColor(0.0);
        TextUtil.drawString(context, text, x, y, color, HudSetting.useFont(), HudSetting.useShadow());

        this.setHudBounds(x, y, Math.max(1, w), Math.max(1, h));
    }

    private String getBiomeName() {
        BlockPos pos = BlockPos.ofFloored(BiomeHudModule.mc.player.getPos());
        
        RegistryKey<Biome> biomeKey = BiomeHudModule.mc.world.getBiome(pos).getKey().orElse(null);

        if (biomeKey == null) {
            return "Unknown";
        }

        Identifier biomeId = biomeKey.getValue();
        if (this.showNamespace.getValue()) {
            return biomeId.toString();
        }
        return biomeId.getPath();
    }

    private int getHudColor(double delay) {
        ClickGui gui = ClickGui.getInstance();
        return gui == null ? -1 : gui.getColor(delay).getRGB();
    }
}
