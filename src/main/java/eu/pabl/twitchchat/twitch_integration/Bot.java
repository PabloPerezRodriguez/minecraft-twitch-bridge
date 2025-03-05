package eu.pabl.twitchchat.twitch_integration;

import com.google.common.collect.ImmutableMap;
import eu.pabl.twitchchat.config.ModConfig;
import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLSocketFactory;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import org.pircbotx.Channel;
import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.cap.EnableCapHandler;
import org.pircbotx.exception.IrcException;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.*;
import eu.pabl.twitchchat.TwitchChatMod;

public class Bot extends ListenerAdapter {
  private final PircBotX ircBot;
  private final String username;
  private String channel;
  private ExecutorService myExecutor;
  private HashMap<String, TextColor> formattingColorCache; // Map of usernames to colors to keep consistency with usernames and colors

  public Bot(String username, String oauthKey, String channel) {
    this.channel = channel.toLowerCase();
    this.username = username.toLowerCase();
    formattingColorCache = new HashMap<>();

    Configuration.Builder builder = new Configuration.Builder()
        .setAutoNickChange(false) //Twitch doesn't support multiple users
        .setOnJoinWhoEnabled(false) //Twitch doesn't support WHO command
        .setEncoding(StandardCharsets.UTF_8) // Use UTF-8 on Windows.
        .setCapEnabled(true)
        .addCapHandler(new EnableCapHandler("twitch.tv/membership")) //Twitch by default doesn't send JOIN, PART, and NAMES unless you request it, see https://dev.twitch.tv/docs/irc/guide/#twitch-irc-capabilities
        .addCapHandler(new EnableCapHandler("twitch.tv/tags"))
        .addCapHandler(new EnableCapHandler("twitch.tv/commands"))

        .addServer("irc.chat.twitch.tv", 6697)
        .setSocketFactory(SSLSocketFactory.getDefault())
        .setName(this.username)
        .setServerPassword(oauthKey);

    if (!channel.equals("")) {
       builder.addAutoJoinChannel("#" + this.channel);
    }

    Configuration config = builder.addListener(this)
        .setAutoSplitMessage(false)
        .buildConfiguration();

    this.ircBot = new PircBotX(config);
    this.myExecutor = Executors.newCachedThreadPool();
  }

  public void start() {
    System.out.println("TWITCH BOT STARTED");
    myExecutor.execute(() -> {
      try {
        ircBot.startBot();
      } catch (IOException | IrcException e) {
        e.printStackTrace();
      }
    });
  }

  public void stop() {
    ircBot.stopBotReconnect();
    ircBot.close();
  }

  public boolean isConnected() {
    return ircBot.isConnected();
  }

  @Override
  public void onMessage(MessageEvent event) throws Exception {
    String message = event.getMessage();
    System.out.println("TWITCH MESSAGE: " + message);
    User user = event.getUser();
    if (user != null) {
      ImmutableMap<String, String> v3Tags = event.getV3Tags();
      if (v3Tags != null) {
        String nick = user.getNick();
        if (!ModConfig.getConfig().getIgnoreList().contains(nick)) {
          String colorTag = v3Tags.get("color");
          TextColor formattingColor;
          
          if (isFormattingColorCached(nick)) {
            formattingColor = getFormattingColor(nick);
          } else {
            if (colorTag.equals("")) {
              formattingColor = CalculateMinecraftColor.getDefaultUserColor(nick);
            } else {
              Color userColor = Color.decode(colorTag);
              formattingColor = TextColor.fromRgb(userColor.getRGB());
            }
            putFormattingColor(nick, formattingColor);
          }

          String[] badges = Arrays.stream(Objects.requireNonNull(v3Tags.getOrDefault("badges", "")).split(","))
                  .map(badge -> badge.split("/")[0])
                  .toArray(String[]::new);

          String formattedTime = TwitchChatMod.formatTMISentTimestamp(v3Tags.get("tmi-sent-ts"));
          TwitchChatMod.addTwitchMessage(formattedTime, nick, message, formattingColor, badges, false);
        }
      } else {
        System.out.println("Message with no v3tags: " + event.getMessage());
      }
    } else {
      System.out.println("NON-USER MESSAGE" + event.getMessage());
    }
  }

  @Override
  public void onUnknown(UnknownEvent event) throws Exception {
    switch (event.getCommand()) {
      case "USERSTATE" -> {
        // Info about our user. More at https://dev.twitch.tv/docs/irc/commands/#userstate
        // Set our correct colour :).
        String colorTag = event.getTags().get("color");
        if (colorTag != null) {
          Color userColor = Color.decode(colorTag);
          TextColor formattingColor = TextColor.fromRgb(userColor.getRGB());

          putFormattingColor(getUsername(), formattingColor);
        }
      }
      default -> {
        System.out.println("UNKNOWN TWITCH EVENT: " + event.toString());
      }
    }
  }

  @Override
  public void onNotice(NoticeEvent event) {
    System.out.println("TWITCH NOTICE: " + event.toString());
    TwitchChatMod.addNotification(Text.literal(event.getNotice()));
  }

  @Override
  public void onKick(KickEvent event) {
    System.out.println("TWITCH KICK: " + event.toString());
    String message = event.getReason();
    TwitchChatMod.addNotification(Text.translatable("text.twitchchat.bot.kicked", message));
  }

  @Override
  public void onDisconnect(DisconnectEvent event) throws Exception {
    super.onDisconnect(event);
    System.out.println("TWITCH DISCONNECT: " + event.toString());
    Exception disconnectException = event.getDisconnectException();
  }

  // Handle /me
  @Override
  public void onAction(ActionEvent event) throws Exception {
    User user = event.getUser();

    if (user != null) {
      String nick = user.getNick();

      if (!ModConfig.getConfig().getIgnoreList().contains(nick.toLowerCase())) {
        String formattedTime = TwitchChatMod.formatTMISentTimestamp(event.getTimestamp());

        TextColor formattingColor;
        if (isFormattingColorCached(nick)) {
          formattingColor = getFormattingColor(nick);
        } else {
          formattingColor = CalculateMinecraftColor.getDefaultUserColor(nick);
          putFormattingColor(nick, formattingColor);
        }

        TwitchChatMod.addTwitchMessage(formattedTime, nick, event.getMessage(), formattingColor, new String[]{}, true);
      }
    } else {
      System.out.println("NON-USER ACTION" + event.getMessage());
    }
  }

  Channel currentChannel;
  @Override
  public void onJoin(JoinEvent event) throws Exception {
    super.onJoin(event);
    Channel channel = event.getChannel();
     if (currentChannel == null || !currentChannel.equals(channel)) {
      TwitchChatMod.addNotification(Text.translatable("text.twitchchat.bot.connected", this.channel));
      currentChannel = channel;
    }
  }

  /**
   * We MUST respond to this or else we will get kicked
   */
  @Override
  public void onPing(PingEvent event) {
    ircBot.sendRaw().rawLineNow(String.format("PONG %s\r\n", event.getPingValue()));
  }

  public void sendMessage(String message) {
    ircBot.sendIRC().message("#" + this.channel, message);
  }

  public String getUsername() {
    return username;
  }

  public void putFormattingColor(String nick, TextColor color) {
    formattingColorCache.put(nick.toLowerCase(), color);
  }
  public TextColor getFormattingColor(String nick) {
    return formattingColorCache.get(nick.toLowerCase());
  }
  public boolean isFormattingColorCached(String nick) {
    return formattingColorCache.containsKey(nick.toLowerCase());
  }

  public void joinChannel(String channel) {
    String oldChannel = this.channel;
    this.channel = channel.toLowerCase();
    if (ircBot.isConnected()) {
      myExecutor.execute(() -> {
        ircBot.sendRaw().rawLine("PART #" + oldChannel); // Leave the channel
        ircBot.sendIRC().joinChannel("#" + this.channel); // Join the new channel
        ircBot.sendCAP().request("twitch.tv/membership", "twitch.tv/tags", "twitch.tv/commands"); // Ask for capabilities
      });
    }
  }
}