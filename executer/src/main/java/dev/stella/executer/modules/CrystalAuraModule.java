package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.setting.*;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;

public class CrystalAuraModule extends ModBase {
    public CrystalAuraModule() {
        super("CrystalAura", Category.COMBAT, 14);
        add(new SliderSetting("Range", 5.0, 1.0, 6.0, 0.1, ""));
        add(new SliderSetting("PlaceRange", 5.0, 1.0, 6.0, 0.1, ""));
        add(new SliderSetting("MinDmg", 6.0, 1.0, 20.0, 0.5, ""));
        add(new SliderSetting("MaxSelfDmg", 12.0, 1.0, 20.0, 0.5, ""));
        add(new SliderSetting("Delay", 0.0, 0.0, 500.0, 10.0, "ms"));
        add(new BooleanSetting("AntiSelf", true));
        add(new BooleanSetting("Predict", true));
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}
}
