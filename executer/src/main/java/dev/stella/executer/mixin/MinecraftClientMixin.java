package dev.stella.executer.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(method = "run", at = @At("HEAD"))
    private void onRun(CallbackInfo ci) {
        // Client started
    }

    @Inject(method = "disconnect", at = @At("HEAD"))
    private void onDisconnect(CallbackInfo ci) {
        // Client disconnecting from server
    }

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void onSetScreen(net.minecraft.client.gui.screen.Screen screen, CallbackInfo ci) {
        // Screen change — useful for ChestStealer auto-open detection
    }
}
