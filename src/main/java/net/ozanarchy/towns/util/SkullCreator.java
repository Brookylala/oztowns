package net.ozanarchy.towns.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.UUID;

/**
 * A utility class for creating custom player heads using the official Bukkit API.
 * This implementation avoids NMS and is compatible with modern Minecraft versions (1.18+).
 */
public class SkullCreator {

    /**
     * Creates a player skull item from a Base64 texture string.
     *
     * @param base64 The Base64 encoded texture string.
     * @return The player skull ItemStack.
     */
    public static ItemStack itemFromBase64(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return new ItemStack(Material.PLAYER_HEAD);
        }

        String urlString = getUrlFromBase64(base64);
        if (urlString == null) {
            return new ItemStack(Material.PLAYER_HEAD);
        }

        return itemFromUrl(urlString);
    }

    /**
     * Creates a player skull item from a Mojang texture URL.
     *
     * @param urlString The Mojang texture URL.
     * @return The player skull ItemStack.
     */
    public static ItemStack itemFromUrl(String urlString) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) return item;

        try {
            URL url = new URL(urlString);
            // Create a profile with a random UUID
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(url);
            profile.setTextures(textures);
            
            meta.setOwnerProfile(profile);
            item.setItemMeta(meta);
        } catch (MalformedURLException e) {
            // Fallback to basic head if URL is invalid
        }

        return item;
    }

    /**
     * Creates a player skull item from an OfflinePlayer (works for online and offline players).
     *
     * @param player The OfflinePlayer to use for the skull.
     * @return The player skull ItemStack.
     */
    public static ItemStack itemFromPlayer(OfflinePlayer player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) return item;

        meta.setOwningPlayer(player);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Extracts the texture URL from a Base64 string.
     * Base64 is expected to be a JSON object: {"textures":{"SKIN":{"url":"..."}}}
     */
    private static String getUrlFromBase64(String base64) {
        try {
            String decoded = new String(Base64.getDecoder().decode(base64));
            // Simple parsing to find the URL in the JSON string
            int index = decoded.indexOf("\"url\":\"");
            if (index == -1) return null;
            
            int start = index + 7;
            int end = decoded.indexOf("\"", start);
            if (end == -1) return null;
            
            return decoded.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }
}
