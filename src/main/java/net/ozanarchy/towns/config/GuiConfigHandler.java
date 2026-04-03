package net.ozanarchy.towns.config;

import org.bukkit.plugin.java.JavaPlugin;

public class GuiConfigHandler extends BaseYamlConfigHandler {
    public GuiConfigHandler(JavaPlugin plugin) {
        super(plugin, "gui.yml");
    }
}



