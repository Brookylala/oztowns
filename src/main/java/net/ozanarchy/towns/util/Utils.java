package net.ozanarchy.towns.util;

import net.ozanarchy.towns.TownsPlugin;
import org.bukkit.ChatColor;

import static net.ozanarchy.towns.TownsPlugin.messagesConfig;

public class Utils {
    public static String getColor(String message) {
        if (message == null)
            return "";

        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static String prefix(){
        TownsPlugin plugin = TownsPlugin.getPlugin(TownsPlugin.class);
        if (plugin != null && plugin.getMessageService() != null) {
            String value = plugin.getMessageService().get("general.prefix");
            if (value != null) {
                return value;
            }
        }
        return messagesConfig.getString("prefix", "&f&l[&c&lOz&4&lTowns&f&l] ");
    }

    public static String adminPrefix(){
        TownsPlugin plugin = TownsPlugin.getPlugin(TownsPlugin.class);
        if (plugin != null && plugin.getMessageService() != null) {
            String value = plugin.getMessageService().get("admin.prefix");
            if (value != null) {
                return value;
            }
        }
        return messagesConfig.getString("adminprefix", "&f[&cTownsAdmin&f] ");
    }

}



