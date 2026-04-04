package net.ozanarchy.towns.config;

import org.bukkit.plugin.java.JavaPlugin;

public class TownConfigHandler extends BaseYamlConfigHandler {
    public TownConfigHandler(JavaPlugin plugin) {
        super(plugin, "town-config.yml");
    }
}

