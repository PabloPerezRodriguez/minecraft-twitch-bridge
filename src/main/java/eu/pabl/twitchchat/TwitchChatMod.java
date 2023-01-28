package eu.pabl.twitchchat;

import eu.pabl.twitchchat.commands.TwitchBaseCommand;
import eu.pabl.twitchchat.config.ModConfig;
import eu.pabl.twitchchat.twitch_integration.Bot;
import java.text.SimpleDateFormat;
import java.util.Date;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;

public class TwitchChatMod implements ModInitializer {
  public static Bot bot;

  @Override
  public void onInitialize() {
    ModConfig.getConfig().load();

    // Initialize the bot
    ModConfig config = ModConfig.getConfig();
    TwitchChatMod.bot = new Bot(config.getUsername(), config.getOauthKey(), config.getChannel());

    // Register commands
    ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
        new TwitchBaseCommand().registerCommands(dispatcher));
  }

  public static void addTwitchMessage(String time, String username, String message, Formatting textColor, boolean isMeMessage) {
    MutableText timestampText = Text.literal(time);
    MutableText usernameText = Text.literal(username).formatted(textColor);
    MutableText messageBodyText;

    if (!isMeMessage) {
      messageBodyText = Text.literal(": " + message);
    } else {
      // '/me' messages have the same color as the username in the Twitch website.
      // And thus I set the color of the message to be the same as the username.
      // They also don't have a colon after the username.
      messageBodyText = Text.literal(" " + message).formatted(textColor);

      // In Minecraft, a '/me' message is marked with a star before the name, like so:
      //
      // <Player> This is a normal message
      // * Player this is a '/me' message
      //
      // The star is always white (that's why I don't format it).
      usernameText = Text.literal("* ").append(usernameText);
    }

    if (ModConfig.getConfig().isBroadcastEnabled()) {
      try {
        String plainTextMessage = ModConfig.getConfig().getBroadcastPrefix() + username + ": " + message;
        plainTextMessage = sanitiseMessage(plainTextMessage);
        if (MinecraftClient.getInstance().player != null) {
          MinecraftClient.getInstance().player.sendMessage(Text.literal(plainTextMessage));
        }
      } catch (NullPointerException e) {
        System.err.println("TWITCH BOT FAILED TO BROADCAST MESSAGE: " + e.getMessage());
      }
    } else {
      // This is just what ChatHud#addMessage(String) does, but with a custom MessageIndicator.
      // TODO: Actually add the custom MessageIndicator. Currently using notSecure to check it's
      //       working and differentiate it from the normal messages.
      MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(
          timestampText.append(usernameText).append(messageBodyText),
          (MessageSignatureData)null,
          MessageIndicator.notSecure()
      );
    }
  }

  private static String sanitiseMessage(String message) {
    return message.replaceAll("§", "");
  }

  public static void addNotification(MutableText message) {
    System.out.println("TWITCH BOT: " + message.getString());
    MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(message.formatted(Formatting.DARK_GRAY));
  }

  public static String formatTMISentTimestamp(String tmiSentTS) {
    return formatTMISentTimestamp(Long.parseLong(tmiSentTS));
  }
  public static String formatTMISentTimestamp(long tmiSentTS) {
    Date date = new Date(tmiSentTS);
    return formatDateTwitch(date);
  }
  public static String formatDateTwitch(Date date) {
    SimpleDateFormat sf = new SimpleDateFormat(ModConfig.getConfig().getDateFormat());
    return sf.format(date);
  }
}
