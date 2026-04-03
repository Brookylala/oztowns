package net.ozanarchy.towns.config;

import org.bukkit.plugin.java.JavaPlugin;

public class MainConfigHandler extends BaseYamlConfigHandler {
    public MainConfigHandler(JavaPlugin plugin) {
        super(plugin, "config.yml");
    }
}



