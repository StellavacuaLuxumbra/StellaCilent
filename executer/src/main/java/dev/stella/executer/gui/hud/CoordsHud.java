package dev.stella.executer.gui.hud;

import dev.stella.executer.gui.render.Render2DUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class CoordsHud extends HudElement {

    public CoordsHud() {
        super(4, 4);
    }

    @Override
    public void render(DrawContext ctx, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.textRenderer == null) return;

        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        String coords = String.format("XYZ: %.1f / %.1f / %.1f", px, py, pz);
        String block = String.format("Block: %d / %d / %d", (int) px, (int) py, (int) pz);
        String facing = mc.player.getHorizontalFacing().getName().toUpperCase();

        // nether/overworld conversion
        String dimInfo = "";
        if (mc.world != null) {
            if (mc.world.getRegistryKey().getValue().getPath().equals("the_nether")) {
                dimInfo = String.format("Overworld: %.1f / %.1f / %.1f", px * 8, py, pz * 8);
            } else if (mc.world.getRegistryKey().getValue().getPath().equals("overworld")) {
                dimInfo = String.format("Nether: %.1f / %.1f / %.1f", px / 8, py, pz / 8);
            }
        }

        int textWidth = mc.textRenderer.getWidth(coords);
        int textWidth2 = mc.textRenderer.getWidth(block);
        int textWidth3 = mc.textRenderer.getWidth("Facing: " + facing);
        int textWidth4 = dimInfo.isEmpty() ? 0 : mc.textRenderer.getWidth(dimInfo);
        int maxW = Math.max(Math.max(textWidth, textWidth2), Math.max(textWidth3, textWidth4));
        int bgWidth = maxW + 10;
        int bgHeight = dimInfo.isEmpty() ? 30 : 40;

        int bgColor = 0xB41E1E1E;
        Render2DUtil.fill(ctx, x, y, x + bgWidth, y + bgHeight, bgColor);

        ctx.drawTextWithShadow(mc.textRenderer, coords, x + 5, y + 3, 0xFFDCDCDC);
        ctx.drawTextWithShadow(mc.textRenderer, block, x + 5, y + 13, 0xFFAAAAAA);
        ctx.drawTextWithShadow(mc.textRenderer, "Facing: " + facing, x + 5, y + 23, 0xFF888888);
        if (!dimInfo.isEmpty()) {
            ctx.drawTextWithShadow(mc.textRenderer, dimInfo, x + 5, y + 33, 0xFF6688AA);
        }
    }

    @Override
    public int getWidth() { return 120; }

    @Override
    public int getHeight() { return 40; }
}
