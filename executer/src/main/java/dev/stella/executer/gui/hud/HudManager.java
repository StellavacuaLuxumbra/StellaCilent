package dev.stella.executer.gui.hud;

import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public class HudManager {
    private static HudManager instance;
    private final List<HudElement> elements = new ArrayList<>();

    private HudManager() {
        elements.add(new WaterMarkHud());
        elements.add(new FpsHud());
        elements.add(new CoordsHud());
        elements.add(new ArraylistHud());
        elements.add(new TargetHud());
    }

    public static HudManager getInstance() {
        if (instance == null) instance = new HudManager();
        return instance;
    }

    public List<HudElement> getElements() { return elements; }

    public void renderAll(DrawContext ctx, float delta) {
        for (HudElement e : elements) {
            if (e.isVisible()) e.render(ctx, delta);
        }
    }

    public HudElement getElement(String name) {
        for (HudElement e : elements) {
            if (e.getClass().getSimpleName().equalsIgnoreCase(name)) return e;
        }
        return null;
    }
}
