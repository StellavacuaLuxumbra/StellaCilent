/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  by.radioegor146.nativeobfuscator.Native
 *  net.minecraft.client.network.AbstractClientPlayerEntity
 *  net.minecraft.entity.Entity
 *  net.minecraft.entity.player.PlayerEntity
 *  net.minecraft.network.packet.Packet
 *  net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket
 *  net.minecraft.world.World
 */
package dev.stella.core.impl;

import dev.stella.stella;
import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.DeathEvent;
import dev.stella.api.events.impl.PacketEvent;
import dev.stella.api.events.impl.TotemEvent;
import dev.stella.api.utils.Wrapper;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.impl.client.ClickGui;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.world.World;

public class PopManager
implements Wrapper {
    public final HashMap<String, Integer> popContainer = new HashMap();
    public final HashMap<String, Long> popTimeContainer = new HashMap(); // Track when pop happened
    private final List<PlayerEntity> deadPlayer = new ArrayList<PlayerEntity>();

    public PopManager() {
        this.init();
    }

    public void init() {
        stella.EVENT_BUS.subscribe(this);
        ClickGui.key = "GOUTOURENNIMASILECAONIMA";
    }

    public int getPop(String s) {
        return this.popContainer.getOrDefault(s, 0);
    }

    public int getPop(PlayerEntity player) {
        return this.getPop(player.getName().getString());
    }

    public long getPopTime(PlayerEntity player) {
        return this.popTimeContainer.getOrDefault(player.getName().getString(), 0L);
    }

    public long getRemainingPopTime(PlayerEntity player) {
        long popTime = this.popTimeContainer.getOrDefault(player.getName().getString(), 0L);
        if (popTime == 0) return 0;
        // Totem pop effect lasts 30 seconds (600 ticks = 30 seconds at 20 ticks/sec)
        long elapsed = System.currentTimeMillis() - popTime;
        long remaining = 30000 - elapsed;
        return Math.max(0, remaining);
    }

    public void onUpdate() {
        if (Module.nullCheck()) {
            return;
        }
        for (AbstractClientPlayerEntity player : stella.THREAD.getPlayers()) {
            if (player == null || !player.isDead()) {
                this.deadPlayer.remove(player);
                continue;
            }
            if (this.deadPlayer.contains(player)) continue;
            stella.EVENT_BUS.post(DeathEvent.get((PlayerEntity)player));
            this.onDeath((PlayerEntity)player);
            this.deadPlayer.add((PlayerEntity)player);
        }
    }

    @EventListener
    public void onPacketReceive(PacketEvent.Receive event) {
        Entity entity;
        EntityStatusS2CPacket packet;
        if (Module.nullCheck()) {
            return;
        }
        Packet<?> packet2 = event.getPacket();
        if (packet2 instanceof EntityStatusS2CPacket && (packet = (EntityStatusS2CPacket)packet2).getStatus() == 35 && (entity = packet.getEntity((World)PopManager.mc.world)) instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity)entity;
            this.onTotemPop(player);
        }
    }

    public void onDeath(PlayerEntity player) {
        this.popContainer.remove(player.getName().getString());
        this.popTimeContainer.remove(player.getName().getString());
    }

    public void onTotemPop(PlayerEntity player) {
        int l_Count = 1;
        if (this.popContainer.containsKey(player.getName().getString())) {
            l_Count = this.popContainer.get(player.getName().getString());
            this.popContainer.put(player.getName().getString(), ++l_Count);
        } else {
            this.popContainer.put(player.getName().getString(), l_Count);
        }
        // Record the time when the pop happened
        this.popTimeContainer.put(player.getName().getString(), System.currentTimeMillis());
        stella.EVENT_BUS.post(TotemEvent.get(player));
    }
}
