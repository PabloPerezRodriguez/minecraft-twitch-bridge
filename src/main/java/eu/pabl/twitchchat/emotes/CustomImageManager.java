package eu.pabl.twitchchat.emotes;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.pabl.twitchchat.TwitchChatMod;
import eu.pabl.twitchchat.config.ModConfig;
import eu.pabl.twitchchat.emotes.twitch_api.TwitchAPIBadge;
import eu.pabl.twitchchat.emotes.twitch_api.TwitchAPIBadgeSet;
import eu.pabl.twitchchat.emotes.twitch_api.TwitchAPIEmote;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CustomImageManager {
  public static final Identifier CUSTOM_IMAGE_FONT_IDENTIFIER = Identifier.of(TwitchChatMod.MOD_ID, "emote_font");

  // I've found this is a pretty good scale factor for 24x24px Twitch emotes.
  public static final float CUSTOM_IMAGE_SCALE_FACTOR = 0.3f;
  public static final String TMI_CLIENT_ID = "q6batx0epp608isickayubi39itsckt";

  private final CustomImageFont customImageFont;
  private final CustomImageFontStorage customImageFontStorage;
  private static final CustomImageManager instance = new CustomImageManager();

  // A map of the badge set name and the badges it contains.
  private final HashMap<String, Integer> badgeNameToCodepointHashMap;
  private final HashMap<String, Integer> emoteNameToCodepointHashMap;
  private int currentCodepoint;

  private CustomImageManager() {
    this.emoteNameToCodepointHashMap = new HashMap<>();
    this.badgeNameToCodepointHashMap = new HashMap<>();
    this.currentCodepoint = 1;

    /// The order is important here. Emote font storage depends on the emote font.
    this.customImageFont = new CustomImageFont();
    this.customImageFontStorage = new CustomImageFontStorage(this.getCustomImageFont());
  }
  public static CustomImageManager getInstance() {
    return instance;
  }

  /* These handle emoji downloading, they accept any of the possible urls' return formats.
     Possible urls: - /chat/emotes?broadcaster_id=
                    - /chat/emotes/global
                    - /char/emotes/set?emote_set_id
     And add the emotes to the CustomImageFont and the HashMap emoteName -> codepoint.
   */
  public void downloadEmotePack(String urlStr) {
    Thread t = new Thread(() -> {
      try {
        HttpClient client = HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .followRedirects(HttpClient.Redirect.NORMAL)
          .connectTimeout(Duration.ofSeconds(20))
          .build();
        HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(urlStr))
          .timeout(Duration.ofMinutes(2))
          .header("Authorization", "Bearer " + ModConfig.getConfig().getOauthKey().replace("oauth:", ""))
          .header("Client-Id", TMI_CLIENT_ID)
          .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
          TwitchChatMod.LOGGER.warn("Couldn't load emotes from url {}, status code {}", urlStr, res.statusCode());
          return;
        }

        JsonObject jsonObject = (JsonObject) JsonParser.parseString(res.body());
        Gson gson = new Gson();
        jsonObject.getAsJsonArray("data").asList().stream()
          .parallel()
          .map(emote -> gson.fromJson(emote, TwitchAPIEmote.class))
          .forEach(twitchEmote -> {
            try {
              downloadEmote(twitchEmote);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          });

        TwitchChatMod.LOGGER.info("Loaded emotes from url {}", urlStr);
      } catch (IOException | InterruptedException e) {
        throw new RuntimeException(e);
      };
    });
    t.setDaemon(true);
    t.start();
  }
  public void downloadEmote(TwitchAPIEmote twitchEmote) throws IOException {
    // I've we've already downloaded the emote, do not download it again.
    if (this.emoteNameToCodepointHashMap.containsKey(twitchEmote.name())) {
      return;
    }

    String url1x = twitchEmote.images().get("url_1x");
    URL url = new URL(url1x);
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();

    if (connection.getResponseCode() != 200) {
      TwitchChatMod.LOGGER.warn("Couldn't load emote 1x {} from url {}: status code {}",
        twitchEmote.name(), url1x, connection.getResponseCode());
      return;
    }

    NativeImage image = NativeImage.read(url.openStream());
    int codepoint = getAndAdvanceCurrentCodepoint();
    // advance is the amount the text is moved forward after the character
    int advance = (int) (image.getWidth()* CUSTOM_IMAGE_SCALE_FACTOR) + 1; // the +1 is to account for the shadow, which is a pixel in length
    // TODO: It would be really cool to be able to add or remove the +1 depending on if we're rendering a shadow or
    //       not. This could be done through a mixin in TextRenderer.Drawer#accept.
    // ascent is the height of the glyph relative to something
    int ascent = (int) (image.getHeight()* CUSTOM_IMAGE_SCALE_FACTOR);
    // both advance and ascent seem to correlate pretty well with its scale factor
    this.getCustomImageFont().addGlyph(codepoint,
      new CustomImageFont.CustomImageGlyph(CUSTOM_IMAGE_SCALE_FACTOR, image, 0, 0, image.getWidth(), image.getHeight(), advance, ascent,
        "emotes/" + twitchEmote.id()));
    this.emoteNameToCodepointHashMap.put(twitchEmote.name(), codepoint);

    TwitchChatMod.LOGGER.debug("Loaded emote {}", twitchEmote.name());
  }

  public void downloadBadges(String urlStr) {
    Thread t = new Thread(() -> {
      try {
        HttpClient client = HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .followRedirects(HttpClient.Redirect.NORMAL)
          .connectTimeout(Duration.ofSeconds(20))
          .build();
        HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(urlStr))
          .timeout(Duration.ofMinutes(2))
          .header("Authorization", "Bearer " + ModConfig.getConfig().getOauthKey().replace("oauth:", ""))
          .header("Client-Id", TMI_CLIENT_ID)
          .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
          TwitchChatMod.LOGGER.warn("Couldn't load badgess from url {}, status code {}", urlStr, res.statusCode());
          return;
        }

        JsonObject jsonObject = (JsonObject) JsonParser.parseString(res.body());
        Gson gson = new Gson();

        jsonObject.getAsJsonArray("data")
          .asList()
          .stream()
          .parallel()
          .map(badgeSet -> gson.fromJson(badgeSet, TwitchAPIBadgeSet.class))
          .forEach(badgeSet -> {
            try {
              downloadBadgeSet(badgeSet);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          });

        TwitchChatMod.LOGGER.info("Loaded badges from url {}", urlStr);
      } catch (IOException | InterruptedException e) {
        throw new RuntimeException(e);
      };
    });
    t.setDaemon(true);
    t.start();
  }
  private void downloadBadgeSet(TwitchAPIBadgeSet badgeSet) throws IOException {
    for (var badge : badgeSet.versions()) {
      downloadBadge(badgeSet.set_id(), badge);
    }
  }
  private void downloadBadge(String badgeSetId, TwitchAPIBadge badge) throws IOException {
    // I've we've already downloaded the emote, do not download it again.
    String id = badgeSetId + "/" + badge.id();
    if (this.badgeNameToCodepointHashMap.containsKey(id))
      return;

    String url1x = badge.image_url_1x();
    URL url = new URL(url1x);
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();

    if (connection.getResponseCode() != 200) {
      TwitchChatMod.LOGGER.warn("Couldn't load badge 1x {} from url {}: status code {}",
        badge.id(), url1x, connection.getResponseCode());
      return;
    }

    NativeImage image = NativeImage.read(url.openStream());
    int codepoint = getAndAdvanceCurrentCodepoint();
    int advance = (int) (image.getWidth()* CUSTOM_IMAGE_SCALE_FACTOR) + 1;
    int ascent = (int) (image.getHeight()* CUSTOM_IMAGE_SCALE_FACTOR);
    this.getCustomImageFont().addGlyph(codepoint,
      new CustomImageFont.CustomImageGlyph(CUSTOM_IMAGE_SCALE_FACTOR, image, 0, 0, image.getWidth(),
        image.getHeight(), advance, ascent, "badges/" + id));
    this.badgeNameToCodepointHashMap.put(id, codepoint);

    TwitchChatMod.LOGGER.debug("Loaded badge {}", id);
  }

  private int getAndAdvanceCurrentCodepoint() {
    int prevCodepoint = currentCodepoint;
    currentCodepoint++;
    // Skip the space (' ') codepoint, because the TextRenderer does weird stuff with the space character
    // (like it doesn't get obfuscated and stuff).
    if (currentCodepoint == 32) currentCodepoint++;
    return prevCodepoint;
  }

  public Integer getEmoteCodepoint(String emoteName) {
    return this.emoteNameToCodepointHashMap.get(emoteName);
  }
  public Integer getBadgeCodepoint(String badgeIdentifier) {
    return this.badgeNameToCodepointHashMap.get(badgeIdentifier);
  }
  public CustomImageFont getCustomImageFont() {
    return this.customImageFont;
  }
  public CustomImageFontStorage getCustomImageFontStorage() {
    return this.customImageFontStorage;
  }

}
