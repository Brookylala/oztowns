package net.ozanarchy.towns.config;

import net.ozanarchy.towns.util.DebugLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GuiConfigHandler {
    private static final List<String> GUI_FILES = Arrays.asList(
            "main.yml",
            "bank.yml",
            "members.yml",
            "permissions.yml"
    );

    private final JavaPlugin plugin;
    private final File guiDirectory;
    private YamlConfiguration mergedConfig = new YamlConfiguration();

    public GuiConfigHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.guiDirectory = new File(plugin.getDataFolder(), "gui");
    }

    public FileConfiguration load() {
        ensureDefaults();
        this.mergedConfig = new YamlConfiguration();

        List<File> files = new ArrayList<>();
        for (String fileName : GUI_FILES) {
            files.add(new File(guiDirectory, fileName));
        }
        files.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        for (File file : files) {
            if (!file.exists()) {
                DebugLogger.debug(plugin, "GUI config file missing during merge: " + file.getName());
                continue;
            }
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            mergeInto(mergedConfig, cfg);
            DebugLogger.debug(plugin, "Merged GUI config file: " + file.getName());
        }
        return mergedConfig;
    }

    public FileConfiguration reload() {
        return load();
    }

    private void ensureDefaults() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        if (!guiDirectory.exists()) {
            guiDirectory.mkdirs();
        }

        for (String fileName : GUI_FILES) {
            File target = new File(guiDirectory, fileName);
            if (target.exists()) {
                continue;
            }
            plugin.saveResource("gui/" + fileName, false);
        }
    }

    private void mergeInto(YamlConfiguration target, FileConfiguration source) {
        for (String key : source.getKeys(true)) {
            Object value = source.get(key);
            if (value instanceof ConfigurationSection) {
                continue;
            }
            target.set(key, value);
        }
    }
}



