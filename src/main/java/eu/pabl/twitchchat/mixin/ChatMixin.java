package eu.pabl.twitchchat.mixin;

import eu.pabl.twitchchat.TwitchChatMod;
import eu.pabl.twitchchat.config.ModConfig;
import eu.pabl.twitchchat.twitch_integration.Bot;

import java.util.Date;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class ChatMixin {
	@Inject(at = @At("HEAD"), method = "sendMessage(Ljava/lang/String;Z)Z", cancellable = true)
	private void sendMessage(String text, boolean addToHistory, CallbackInfoReturnable<Boolean> info) {
      ModConfig config = ModConfig.getConfig();


      String prefix = config.getPrefix();

      // Allow users to write /twitch commands (such as disabling and enabling the mod) when their prefix is "".
      if (prefix.equals("") && text.startsWith("/twitch")) {
        return; // Don't cancel the message, return execution to the real method
      }

      // If the message is a twitch message
      if (text.startsWith(prefix)) {
        Bot bot = TwitchChatMod.bot;
        if (bot != null && bot.isConnected()) {
          String textWithoutPrefix = text.substring(text.indexOf(prefix) + prefix.length());
          bot.sendMessage(textWithoutPrefix); // Send the message to the Twitch IRC Chat

          Date currentTime = new Date();
          String formattedTime = TwitchChatMod.formatDateTwitch(currentTime);

          String username = bot.getUsername();
          TextColor userColor = bot.getOrComputeUserColour(username);

          boolean isMeMessage = textWithoutPrefix.startsWith("/me");

          // Add the message to the Minecraft Chat
//          TwitchChatMod.addTwitchMessage(formattedTime, username, isMeMessage ? textWithoutPrefix.substring(4) : textWithoutPrefix, emotes, userColor, bot.getUserBadges(), isMeMessage);
          MinecraftClient.getInstance().inGameHud.getChatHud().addToMessageHistory(text);
          info.setReturnValue(true);
        } else {
          TwitchChatMod.addNotification(Text.translatable("text.twitchchat.chat.integration_disabled"));
        }
      }
	}
}
