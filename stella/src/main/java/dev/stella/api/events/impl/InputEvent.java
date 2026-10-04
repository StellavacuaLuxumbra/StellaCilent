/*
 * Decompiled with CFR 0.152.
 */
package dev.stella.api.events.impl;

import dev.stella.api.events.Event;
import net.minecraft.client.input.Input;

public class InputEvent
extends Event {
    private static InputEvent INSTANCE;
    private final Input input;

    private InputEvent(Input input) {
        this.input = input;
    }

    public static InputEvent get(Input input) {
        INSTANCE = new InputEvent(input);
        INSTANCE.setCancelled(false);
        return INSTANCE;
    }

    public Input getInput() {
        return this.input;
    }
}
