/*
 * Decompiled with CFR 0.152.
 */
package dev.stella.api.interfaces;

import dev.stella.api.utils.math.FadeUtils;

public interface IChatHudLineHook {
    public int stellaClient$getMessageId();

    public void stellaClient$setMessageId(int var1);

    public boolean stellaClient$getSync();

    public void stellaClient$setSync(boolean var1);

    public FadeUtils stellaClient$getFade();

    public void stellaClient$setFade(FadeUtils var1);
}

