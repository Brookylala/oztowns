package net.ozanarchy.towns.config;

import org.bukkit.plugin.java.JavaPlugin;

public class RaidConfigFileHandler extends BaseYamlConfigHandler {
    public RaidConfigFileHandler(JavaPlugin plugin) {
        super(plugin, "raid-config.yml");
    }
}



