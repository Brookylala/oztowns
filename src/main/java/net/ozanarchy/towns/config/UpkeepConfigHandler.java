package net.ozanarchy.towns.config;

import org.bukkit.plugin.java.JavaPlugin;

public class UpkeepConfigHandler extends BaseYamlConfigHandler {
    public UpkeepConfigHandler(JavaPlugin plugin) {
        super(plugin, "upkeep.yml");
    }
}

