package eu.pabl.twitchchat;

import eu.pabl.twitchchat.badge.Badge;
import eu.pabl.twitchchat.badge.BadgeFont;
import eu.pabl.twitchchat.commands.TwitchBaseCommand;
import eu.pabl.twitchchat.config.ModConfig;
import eu.pabl.twitchchat.twitch_integration.Bot;
import java.text.SimpleDateFormat;
import java.util.Date;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TwitchChatMod implements ModInitializer {
  public final static Logger LOGGER = LoggerFactory.getLogger(TwitchChatMod.class);
  public static Bot bot;

  @Override
  public void onInitialize() {
    ModConfig.getConfig().load();

    // Register commands
    ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
        new TwitchBaseCommand().registerCommands(dispatcher));
  }

  public static void addTwitchMessage(String time, String username, String message, TextColor textColor, String[] badges, boolean isMeMessage) {
    MutableText timestampText = Text.literal(time);
    MutableText usernameText = Text.literal(username).styled(style -> style.withColor(textColor));
    MutableText badgesText = Text.literal("");
    for (String badgeName : badges) {
      Badge badge = Badge.get(badgeName);
      if (badge.image() == null) continue;
      badgesText.append(Text.literal(Character.toString((char) Badge.codePoint(badgeName))).styled(style -> style.withFont(BadgeFont.IDENTIFIER)));
    }
    MutableText messageBodyText;

    if (!isMeMessage) {
      messageBodyText = Text.literal(": " + message);
    } else {
      // '/me' messages have the same color as the username in the Twitch website.
      // And thus I set the color of the message to be the same as the username.
      // They also don't have a colon after the username.
      messageBodyText = Text.literal(" " + message).styled(style -> style.withColor(textColor));

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
          MinecraftClient.getInstance().player.sendMessage(Text.literal(plainTextMessage), false);
        }
      } catch (NullPointerException e) {
        System.err.println("TWITCH BOT FAILED TO BROADCAST MESSAGE: " + e.getMessage());
      }
    } else {
      MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(
          timestampText
          .append(badgesText)
          .append(usernameText)
          .append(messageBodyText));
    }
  }

  private static String sanitiseMessage(String message) {
    return message.replaceAll("§", "");
  }

  public static void addNotification(MutableText message) {
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
