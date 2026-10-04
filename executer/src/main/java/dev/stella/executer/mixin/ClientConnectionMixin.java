package dev.stella.executer.mixin;

import dev.stella.executer.modules.ModuleTicker;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public class ClientConnectionMixin {

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void stella$onSend(Packet<?> packet, CallbackInfo ci) {
        ModuleTicker.onSendPacket(packet, ci);
    }

    @Inject(method = "channelRead0", at = @At("HEAD"))
    private void stella$onReceive(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        ModuleTicker.onReceivePacket(packet);
    }
}
