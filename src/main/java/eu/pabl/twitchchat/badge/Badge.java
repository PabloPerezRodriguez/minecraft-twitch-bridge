package eu.pabl.twitchchat.badge;

import eu.pabl.twitchchat.TwitchChatMod;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.TextureContents;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.IOException;

public class Badge {
    private final String name;
    private final NativeImage image;
    private static final Int2ObjectMap<Badge> badges = new Int2ObjectOpenHashMap<>();

    /**
     * An empty badge with a name set to "" and null image.
     */
    public static final Badge EMPTY = new Badge("", null);

    Badge(String name) throws IOException {
        this.name = name;

        ResourceManager resourceManager = MinecraftClient.getInstance().getResourceManager();
        try {
            image = TextureContents.load(resourceManager, Identifier.of("twitchchat", "textures/badge/" + this.name + ".png")).image();
        } catch (IOException e) {
            throw new IOException("badge texture for '" + this.name + "' badge: " + e);
        }
    }

    Badge(String name, NativeImage image) {
        this.name = name;
        this.image = image;
    }

    /**
     * @return The image of the badge
     */
    public NativeImage image() {
        return image;
    }

    /**
     * @return The name of the badge
     */
    @Override
    public String toString() {
            return name;
        }

    /**
     * Currently loads the hardcoded default badges.
     */
    public static void loadBadges() {
        try {
            badges.put(33, new Badge("broadcaster"));
            badges.put(34, new Badge("moderator"));
            badges.put(35, new Badge("partner"));
            badges.put(36, new Badge("vip"));
        } catch (IOException e) {
            TwitchChatMod.LOGGER.error("Error loading hardcoded badges: " + e);
        }
        TwitchChatMod.LOGGER.info("Loaded " + badges.size() + " default badges!");
    }

    public static IntSet codePoints() {
        return badges.keySet();
    }

    /**
     * Access the Badge for the given code point.
     * @param codePoint The code point to search the badge for.
     * @return The badge for the code point or empty badge.
     */
    public static Badge get(int codePoint) {
        return badges.getOrDefault(codePoint, Badge.EMPTY);
    }

    /**
     * Access the Badge for the given name.
     * @param name The name to search the badge for.
     * @return The badge for the name or empty badge.
     */
    public static Badge get(String name) {
        return badges.values().stream()
                .filter(badge -> badge.toString().equals(name))
                .findFirst()
                .orElse(Badge.EMPTY);
    }

    public static int codePoint(String name) throws IllegalArgumentException {
        for (Int2ObjectMap.Entry<Badge> entry : badges.int2ObjectEntrySet()) {
            if (entry.getValue().name.equals(name)) return entry.getIntKey();
        }
        throw new IllegalArgumentException("badge named '" + name + "' does not exist");
    }

    public static Badge add(int codePoint, String name) throws IOException {
        return add(codePoint, new Badge(name));
    }
    public static Badge add (int codePoint, String name, NativeImage image) {
        return add(codePoint, new Badge(name, image));
    }
    public static Badge add(int codePoint, Badge badge) {
        return badges.put(codePoint, badge);
    }
}
