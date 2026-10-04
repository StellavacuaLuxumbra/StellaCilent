package dev.stella.mod.modules.impl.misc;

import dev.stella.stella;
import dev.stella.api.events.eventbus.EventListener;
import dev.stella.api.events.impl.PacketEvent;
import dev.stella.mod.modules.Module;
import dev.stella.mod.modules.settings.impl.StringSetting;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MioFriendSync extends Module {
   public static MioFriendSync INSTANCE;
   public final StringSetting prefix = this.add(new StringSetting("Prefix", "."));
   private static final Pattern FRIEND_ADD_PATTERN = Pattern.compile("已将(.+?)添加为好友|Added (.+?) to friends");

   public MioFriendSync() {
      super("MioFriendSync", Category.Misc);
      this.setChinese("MIO好友同步");
      INSTANCE = this;
   }

   @Override
   public void onEnable() {
      if (mc.player != null) {
         this.sendMessage("§a[MioFriendSync] 已启用，开始同步MIO好友");
      }

   }

   @EventListener
   public void onPacketReceive(PacketEvent.Receive event) {
      if (mc.player != null) {
         if (event.getPacket() instanceof GameMessageS2CPacket packet) {
            String var6 = packet.content().getString();
            Matcher matcher = FRIEND_ADD_PATTERN.matcher(var6);
            if (matcher.find()) {
               String playerName = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
               if (playerName != null && !playerName.isEmpty() && !stella.FRIEND.isFriend(playerName)) {
                  this.sendAddFriendCommand(playerName);
               }
            }
         }
      }
   }

   private void sendAddFriendCommand(String playerName) {
      if (mc.player != null) {
         boolean isChineseName = this.containsChinese(playerName);
         String command;
         if (isChineseName) {
            command = this.prefix.getValue() + "add \"" + playerName + "\"";
         } else {
            command = this.prefix.getValue() + "add " + playerName;
         }

         mc.player.networkHandler.sendChatMessage(command);
      }
   }

   private boolean containsChinese(String str) {
      if (str != null && !str.isEmpty()) {
         for (char c : str.toCharArray()) {
            if (this.isChinese(c)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean isChinese(char c) {
      Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
      return ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
         || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
         || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
         || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
         || ub == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
         || ub == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
         || ub == Character.UnicodeBlock.GENERAL_PUNCTUATION;
   }
}
