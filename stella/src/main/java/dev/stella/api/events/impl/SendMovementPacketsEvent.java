/*
 * Decompiled with CFR 0.152.
 */
package dev.stella.api.events.impl;

import dev.stella.api.events.Event;
import dev.stella.api.utils.Wrapper;

public class SendMovementPacketsEvent extends Event {
    public static final SendMovementPacketsEvent instance = new SendMovementPacketsEvent();
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private boolean onGround;
    private boolean modified = false;

    private SendMovementPacketsEvent() {
    }

    public static SendMovementPacketsEvent get(double x, double y, double z, float yaw, float pitch) {
        SendMovementPacketsEvent.instance.x = x;
        SendMovementPacketsEvent.instance.y = y;
        SendMovementPacketsEvent.instance.z = z;
        SendMovementPacketsEvent.instance.yaw = yaw;
        SendMovementPacketsEvent.instance.pitch = pitch;
        SendMovementPacketsEvent.instance.onGround = Wrapper.mc.player.isOnGround();
        SendMovementPacketsEvent.instance.modified = false;
        SendMovementPacketsEvent.instance.setCancelled(false);
        return instance;
    }

    public double getX() {
        return this.x;
    }

    public void setX(double x) {
        this.x = x;
        this.modified = true;
    }

    public double getY() {
        return this.y;
    }

    public void setY(double y) {
        this.y = y;
        this.modified = true;
    }

    public double getZ() {
        return this.z;
    }

    public void setZ(double z) {
        this.z = z;
        this.modified = true;
    }

    public float getYaw() {
        return this.yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
        this.modified = true;
    }

    public float getPitch() {
        return this.pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
        this.modified = true;
    }

    public boolean isOnGround() {
        return this.onGround;
    }

    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
        this.modified = true;
    }

    public boolean isModified() {
        return this.modified;
    }
}

