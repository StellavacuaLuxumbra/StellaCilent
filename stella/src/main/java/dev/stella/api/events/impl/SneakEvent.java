/*
 * Decompiled with CFR 0.152.
 */
package dev.stella.api.events.impl;

import dev.stella.api.events.Event;

public class SneakEvent
extends Event {
    private static final SneakEvent INSTANCE = new SneakEvent();

    private SneakEvent() {
    }

    public static SneakEvent get() {
        INSTANCE.setCancelled(false);
        return INSTANCE;
    }
}
