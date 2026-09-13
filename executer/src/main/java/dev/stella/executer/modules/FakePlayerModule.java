package dev.stella.executer.modules;

import dev.stella.executer.gui.Category;
import dev.stella.executer.gui.setting.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.entity.Entity;
import net.minecraft.stat.StatHandler;
import net.minecraft.util.math.MathHelper;

import java.util.UUID;

public class FakePlayerModule extends ModBase {
    private ClientPlayerEntity fakePlayer = null;

    public FakePlayerModule() {
        super("FakePlayer", Category.MISC, 27);
        add(new SliderSetting("Health", 20.0, 1.0, 40.0, 0.5, ""));
        add(new BooleanSetting("CopyInventory", false));
        add(new SliderSetting("Distance", 2.0, 0.5, 5.0, 0.5, "blocks"));
    }

    @Override
    public void onEnable() {
        spawnFakePlayer();
    }

    @Override
    public void onDisable() {
        removeFakePlayer();
    }

    private void spawnFakePlayer() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        removeFakePlayer();

        float yaw = mc.player.getYaw();
        var distSetting = (SliderSetting) getSetting("Distance");
        float dist = distSetting != null ? (float) distSetting.getValue() : 2.0f;

        double spawnX = mc.player.getX() - MathHelper.sin(yaw * 0.017453292F) * dist;
        double spawnZ = mc.player.getZ() + MathHelper.cos(yaw * 0.017453292F) * dist;

        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "FakePlayer");

        ClientPlayNetworkHandler netHandler = mc.getNetworkHandler();
        if (netHandler == null) return;

        fakePlayer = new ClientPlayerEntity(
                mc,
                mc.world,
                netHandler,
                new StatHandler(),
                new ClientRecipeBook(),
                false,
                false
        );

        fakePlayer.refreshPositionAndAngles(
                fakePlayer.getBlockPos().ofFloored(spawnX, mc.player.getY(), spawnZ),
                yaw,
                mc.player.getPitch()
        );

        var healthSetting = (SliderSetting) getSetting("Health");
        float health = healthSetting != null ? (float) healthSetting.getValue() : 20.0f;
        fakePlayer.setHealth(health);

        var copyInvSetting = (BooleanSetting) getSetting("CopyInventory");
        if (copyInvSetting != null && copyInvSetting.getValue()) {
            for (int i = 0; i < 9; i++) {
                fakePlayer.getInventory().setStack(i, mc.player.getInventory().getStack(i).copy());
            }
        }

        mc.world.addEntity(fakePlayer);
    }

    private void removeFakePlayer() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (fakePlayer != null && mc.world != null) {
            mc.world.removeEntity(fakePlayer.getId(), Entity.RemovalReason.DISCARDED);
            fakePlayer = null;
        }
    }

    public ClientPlayerEntity getFakePlayer() {
        return fakePlayer;
    }
}
