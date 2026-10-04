/*
 * Decompiled with CFR 0.152.
 */
package dev.stella.mod.modules.impl.movement;

import dev.stella.mod.modules.Module;

public class EntityControl
extends Module {
    public static EntityControl INSTANCE;

    public EntityControl() {
        super("EntityControl", Module.Category.Movement);
        this.setChinese("\u9a91\u884c\u63a7\u5236");
        INSTANCE = this;
    }
}

