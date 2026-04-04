package net.ozanarchy.towns.config;

import org.bukkit.plugin.java.JavaPlugin;

public class TiersConfigHandler extends BaseYamlConfigHandler {
    public TiersConfigHandler(JavaPlugin plugin) {
        super(plugin, "tiers.yml");
    }
}

