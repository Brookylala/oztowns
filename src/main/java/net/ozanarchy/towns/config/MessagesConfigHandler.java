package net.ozanarchy.towns.config;

import org.bukkit.plugin.java.JavaPlugin;

public class MessagesConfigHandler extends BaseYamlConfigHandler {
    public MessagesConfigHandler(JavaPlugin plugin) {
        super(plugin, "messages.yml");
    }
}



