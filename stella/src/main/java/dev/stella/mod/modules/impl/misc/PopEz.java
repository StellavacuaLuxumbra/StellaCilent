package dev.stella.mod.modules.impl.misc;

import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.TotemEvent;
import dev.stella.api.events.impl.UpdateEvent;
import dev.stella.api.utils.math.Timer;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.impl.BooleanSetting;
import dev.stella.mod.modules.settings.impl.SliderSetting;
import dev.stella.mod.modules.settings.impl.StringSetting;
import dev.stella.stella;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.entity.player.PlayerEntity;

public class PopEz extends Module {
    public static PopEz INSTANCE;

    private final SliderSetting randomChars = this.add(new SliderSetting("RandomChars", 3.0, 0.0, 20.0, 1.0));
    public final BooleanSetting slowSend = this.add(new BooleanSetting("SlowSend", false));
    public final StringSetting customMessage = this.add(new StringSetting("Message", "{player} 被我使用Stella客户端popped {count} totem {suffix}"));
    private final SliderSetting customMessageLength = this.add(new SliderSetting("MsgLength", 0.0, 0.0, 20.0, 1.0));
    
    Random random = new Random();
    Timer timer = new Timer();
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private Map<Integer, Integer> popQueue = new HashMap<Integer, Integer>();

    public PopEz() {
        super("PopEz", Category.Misc);
        this.setChinese("POP嘲讽");
        INSTANCE = this;
    }

    @EventListener
    public void onTotem(TotemEvent event) {
        PlayerEntity player = event.getPlayer();
        if (player != null && !player.equals(mc.player) && !stella.FRIEND.isFriend(player)) {
            int l_Count = 1;
            if (stella.POP.popContainer.containsKey(player.getName().getString())) {
                l_Count = stella.POP.popContainer.get(player.getName().getString());
            }
            if (this.slowSend.getValue()) {
                this.popQueue.put(player.getId(), l_Count);
            } else {
                String message = this.buildMessage(player.getName().getString(), l_Count);
                this.sendMessage(message, player.getId());
            }
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (this.slowSend.getValue() && !this.popQueue.isEmpty() && this.timer.passedS(3.2)) {
            this.timer.reset();
            Map.Entry<Integer, Integer> entry = this.popQueue.entrySet().iterator().next();
            int playerId = entry.getKey();
            int popCount = entry.getValue();
            PlayerEntity player = null;
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p.getId() != playerId) continue;
                player = p;
                break;
            }
            if (player != null) {
                String message = this.buildMessage(player.getName().getString(), popCount);
                this.sendMessage(message, playerId);
                this.popQueue.remove(playerId);
            } else {
                this.popQueue.remove(playerId);
            }
        }
    }

    private String buildMessage(String playerName, int count) {
        String template = this.customMessage.getValue();
        String suffix = count == 1 ? "" : "s";
        
        String message = template
                .replace("{player}", playerName)
                .replace("{count}", String.valueOf(count))
                .replace("{suffix}", suffix);
        
        int randomLength = this.customMessageLength.getValueInt();
        if (randomLength > 0) {
            String randomString = this.generateRandomString(randomLength);
            message = message + " " + randomString;
        }
        
        return message;
    }

    public void sendMessage(String message, int id) {
        if (nullCheck() || mc.player == null || mc.player.networkHandler == null) {
            return;
        }
        int randomLength = this.randomChars.getValueInt();
        if (randomLength > 0) {
            String randomString = this.generateRandomString(randomLength);
            message = message + " " + randomString;
        }
        mc.player.networkHandler.sendChatMessage(message);
    }

    private String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; ++i) {
            int index = this.random.nextInt(CHARACTERS.length());
            sb.append(CHARACTERS.charAt(index));
        }
        return sb.toString();
    }
}
