package net.ozanarchy.towns.util;

import net.ozanarchy.towns.TownsPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class DebugLogger {
    private DebugLogger() {
    }

    public static boolean isEnabled() {
        FileConfiguration cfg = TownsPlugin.config;
        return cfg != null && cfg.getBoolean("debug.enabled", false);
    }

    public static void debug(JavaPlugin plugin, String message) {
        if (!isEnabled() || plugin == null || message == null) {
            return;
        }
        plugin.getLogger().info("[DEBUG] " + message);
    }
}
