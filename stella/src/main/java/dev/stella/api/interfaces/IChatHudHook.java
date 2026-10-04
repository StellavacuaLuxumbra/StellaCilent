/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.text.Text
 */
package dev.stella.api.interfaces;

import net.minecraft.text.Text;

public interface IChatHudHook {
    public void stellaClient$addMessage(Text var1, int var2);

    public void stellaClient$addMessage(Text var1);

    public void stellaClient$addMessageOutSync(Text var1, int var2);
}
