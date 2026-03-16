package net.ozanarchy.towns.util;

import org.bukkit.ChatColor;

import static net.ozanarchy.towns.TownsPlugin.messagesConfig;

public class Utils {
    public static String getColor(String message) {
        if (message == null)
            return "";

        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static String prefix(){
        return messagesConfig.getString("prefix");
    }

    public static String adminPrefix(){
        return messagesConfig.getString("adminprefix");
    }

}
