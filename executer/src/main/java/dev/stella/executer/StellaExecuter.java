package dev.stella.executer;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.stella.executer.gui.ClickGuiScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.option.KeyBinding;

public class StellaExecuter implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("Stella");
    private static KeyBinding openGuiKey;

    @Override
    public void onInitializeClient() {
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.stella.clickgui",
                GLFW.GLFW_KEY_G,
                "category.stella"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(ClickGuiScreen.getInstance());
                } else if (client.currentScreen instanceof ClickGuiScreen) {
                    client.currentScreen.close();
                }
            }

            if (client.world != null && client.player != null) {
                dev.stella.executer.modules.ModuleTicker.getInstance().tick(client);
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            dev.stella.executer.gui.hud.HudManager.getInstance().renderAll(drawContext, tickCounter.getTickDelta(false));
        });

        ClientSendMessageEvents.ALLOW_CHAT.register((message) -> {
            if (dev.stella.executer.command.CommandManager.getInstance().process(message)) {
                return false;
            }
            return true;
        });

        LOGGER.info("Stella driver initialized (pure Java)");
    }
}
