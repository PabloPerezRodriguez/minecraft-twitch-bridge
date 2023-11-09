package eu.pabl.twitchchat;

import eu.pabl.twitchchat.commands.TwitchBaseCommand;
import eu.pabl.twitchchat.config.ModConfig;
import eu.pabl.twitchchat.emotes.CustomImageManager;
import eu.pabl.twitchchat.emotes.twitch_api.TwitchAPIEmoteTagElement;
import eu.pabl.twitchchat.twitch_integration.Bot;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.IntStream;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TwitchChatMod implements ModInitializer {
  public static final String MOD_ID = "twitchchat";
  public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

  public static Bot bot;

  @Override
  public void onInitialize() {
    ModConfig.getConfig().load();

    // Register commands
    ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
        new TwitchBaseCommand().registerCommands(dispatcher));

//    EmoteManager.getInstance().downloadEmote();
    CustomImageManager.getInstance().downloadEmotePack("https://api.twitch.tv/helix/chat/emotes/global");
    CustomImageManager.getInstance().downloadBadges("https://api.twitch.tv/helix/chat/badges/global");
//    MinecraftClient.getInstance().textRenderer
  }

  public static MutableText getBadgedUsername(String username, TextColor usernameColour, String[] userBadges) {
    MutableText badgedUsername = Text.empty();
    if (userBadges != null) {
      for (String badge : userBadges) {
        Integer codepoint = CustomImageManager.getInstance().getBadgeCodepoint(badge);
        badgedUsername.append(Text
          .literal(Character.toString(codepoint))
          .styled(style -> style.withFont(CustomImageManager.CUSTOM_IMAGE_FONT_IDENTIFIER))
        );
      }
    }
    badgedUsername.append(Text.literal(username).styled(style -> style.withColor(usernameColour)));
    return badgedUsername;
  }
  public static MutableText getEmotedMessage(String plainMessage, List<TwitchAPIEmoteTagElement> emotes) {
    MutableText emotedMessage = MutableText.of(TextContent.EMPTY);
    int currentPos = 0;

    // The emotes count in offsets by codepoints. So... we're doing the same.
    if (emotes != null) {
      for (var emote : emotes) {
        if (currentPos != emote.startPosition()) {
          emotedMessage.append(Text.of(substringCodepoints(plainMessage, currentPos, emote.startPosition())));
        }
        Integer codepoint = CustomImageManager.getInstance().getEmoteCodepointFromId(emote.emoteID());

        emotedMessage.append(
          Text.literal(Character.toString(codepoint))
            .styled(style -> style.withFont(CustomImageManager.CUSTOM_IMAGE_FONT_IDENTIFIER))
        );

        // The end position is the exact end position of the emote, so we add one.
        currentPos = emote.endPosition() + 1;
      }
    }

    if (currentPos != plainMessage.length()) {
      emotedMessage.append(Text.of(substringCodepoints(plainMessage, currentPos)));
    }
    return emotedMessage;
  }

  private static String substringCodepoints(String str, int idx, int len) {
    int start = str.offsetByCodePoints(0, idx);
    int end = str.offsetByCodePoints(start, len-idx);
    return str.substring(start, end);
  }
  private static String substringCodepoints(String str, int idx) {
    int start = str.offsetByCodePoints(0, idx);
    return str.substring(start);
  }

  public static void addTwitchMessage(String time, String username, String message, List<TwitchAPIEmoteTagElement> emotes, TextColor usernameColour, String[] userBadges, boolean isMeMessage) {
    MutableText timestampText = Text.literal(time);
    MutableText usernameText = getBadgedUsername(username, usernameColour, userBadges);
    MutableText emotedMessage = getEmotedMessage(message, emotes);
    MutableText messageBodyText;

    if (!isMeMessage) {
      messageBodyText = Text.literal(": ").append(emotedMessage);
    } else {
      // '/me' messages have the same color as the username in the Twitch website.
      // And thus I set the color of the message to be the same as the username.
      // They also don't have a colon after the username.
      messageBodyText = Text.literal(" ").append(emotedMessage).styled(style -> style.withColor(usernameColour));

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
//      MinecraftClient.getInstance().getF
//      Text.of("").getWithStyle(Style.EMPTY.withFont(Font))
      MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(
          timestampText
          .append(usernameText)
          .append(messageBodyText));
      MinecraftClient.getInstance().getNarratorManager()
              .narrateChatMessage(Text.of(usernameText + "" + messageBodyText));
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
