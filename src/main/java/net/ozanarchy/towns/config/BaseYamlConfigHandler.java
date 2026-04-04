package net.ozanarchy.towns.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;

public abstract class BaseYamlConfigHandler {
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    protected final JavaPlugin plugin;
    protected final String fileName;
    protected final File configFile;
    protected final File backupDir;

    private FileConfiguration config;

    protected BaseYamlConfigHandler(JavaPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName;
        this.configFile = new File(plugin.getDataFolder(), fileName);
        this.backupDir = new File(plugin.getDataFolder(), "backups");
    }

    public synchronized FileConfiguration load() {
        return loadInternal(false);
    }

    public synchronized FileConfiguration reload() {
        return loadInternal(true);
    }

    public synchronized void save() {
        if (config == null) {
            load();
        }
        try {
            config.save(configFile);
            createBackupSnapshot("save");
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save config '" + fileName + "': " + e.getMessage());
        }
    }

    public synchronized FileConfiguration getConfig() {
        if (config == null) {
            load();
        }
        return config;
    }

    private FileConfiguration loadInternal(boolean snapshotAfterLoad) {
        ensureDirectories();

        if (!configFile.exists()) {
            plugin.getLogger().warning("Config '" + fileName + "' is missing. Recreating from defaults.");
            recreateFromDefaults();
        }

        if (!isValidYaml(configFile)) {
            plugin.getLogger().warning("Config '" + fileName + "' appears corrupted. Attempting recovery.");
            backupCorruptedFile();
            if (restoreFromBackup()) {
                plugin.getLogger().warning("Recovered config '" + fileName + "' from backup.");
            } else {
                plugin.getLogger().warning("No usable backup found for '" + fileName + "'. Recreating from defaults.");
                recreateFromDefaults();
            }
        }

        config = loadYaml(configFile);
        if (snapshotAfterLoad) {
            createBackupSnapshot("reload");
        } else {
            ensureBackupExists();
        }
        return config;
    }

    private void ensureDirectories() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }
    }

    private void recreateFromDefaults() {
        try {
            if (configFile.exists()) {
                Files.delete(configFile.toPath());
            }
            if (hasBundledDefault()) {
                plugin.saveResource(fileName, false);
                plugin.getLogger().info("Recreated config '" + fileName + "' from bundled defaults.");
            } else {
                YamlConfiguration empty = new YamlConfiguration();
                empty.save(configFile);
                plugin.getLogger().warning("Created empty config for '" + fileName + "' (no bundled defaults were found).");
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to recreate config '" + fileName + "': " + e.getMessage());
        }
    }

    private boolean hasBundledDefault() {
        try (InputStream in = plugin.getResource(fileName)) {
            return in != null;
        } catch (IOException ignored) {
            return false;
        }
    }

    private boolean isValidYaml(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
            return true;
        } catch (IOException | InvalidConfigurationException e) {
            return false;
        }
    }

    private YamlConfiguration loadYaml(File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().warning("Failed to load config '" + fileName + "': " + e.getMessage());
        }
        return yaml;
    }

    private void backupCorruptedFile() {
        if (!configFile.exists()) {
            return;
        }
        File corruptDir = new File(backupDir, "corrupt");
        if (!corruptDir.exists()) {
            corruptDir.mkdirs();
        }
        File corruptCopy = new File(corruptDir, fileName + "." + timestamp() + ".corrupt.yml");
        copyFile(configFile, corruptCopy, "Stored corrupted config copy for '" + fileName + "' at " + corruptCopy.getName());
    }

    private boolean restoreFromBackup() {
        File latest = latestBackupFile();
        if (latest.exists() && isValidYaml(latest)) {
            copyFile(latest, configFile, "Restored config '" + fileName + "' from latest backup.");
            return isValidYaml(configFile);
        }

        File[] snapshots = backupDir.listFiles((dir, name) ->
                name.startsWith(fileName + ".") && name.endsWith(".bak.yml"));
        if (snapshots == null || snapshots.length == 0) {
            return false;
        }

        Arrays.sort(snapshots, Comparator.comparingLong(File::lastModified).reversed());
        for (File snapshot : snapshots) {
            if (!isValidYaml(snapshot)) {
                continue;
            }
            copyFile(snapshot, configFile, "Restored config '" + fileName + "' from backup " + snapshot.getName() + ".");
            if (isValidYaml(configFile)) {
                return true;
            }
        }
        return false;
    }

    private void ensureBackupExists() {
        File latest = latestBackupFile();
        if (!latest.exists() && configFile.exists()) {
            copyFile(configFile, latest, "Created initial backup for config '" + fileName + "'.");
        }
    }

    private void createBackupSnapshot(String action) {
        if (!configFile.exists()) {
            return;
        }
        File latest = latestBackupFile();
        File snapshot = new File(backupDir, fileName + "." + timestamp() + ".bak.yml");
        copyFile(configFile, latest, "Updated latest backup for config '" + fileName + "' on " + action + ".");
        copyFile(configFile, snapshot, "Created backup snapshot for config '" + fileName + "': " + snapshot.getName());
    }

    private File latestBackupFile() {
        return new File(backupDir, fileName + ".latest.yml");
    }

    private String timestamp() {
        return LocalDateTime.now().format(TS_FORMAT);
    }

    private void copyFile(File from, File to, String logMessage) {
        try {
            if (to.getParentFile() != null && !to.getParentFile().exists()) {
                to.getParentFile().mkdirs();
            }
            Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info(logMessage);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed backup operation for '" + fileName + "': " + e.getMessage());
        }
    }
}



