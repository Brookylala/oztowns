package net.ozanarchy.towns;

import net.ozanarchy.towns.commands.TownBankCommands;
import net.ozanarchy.towns.commands.TownMessageCommand;
import net.ozanarchy.towns.commands.TownsCommand;
import net.ozanarchy.towns.commands.AdminCommands;
import net.ozanarchy.towns.economy.EconomyProvider;
import net.ozanarchy.towns.events.AdminEvents;
import net.ozanarchy.towns.events.MemberEvents;
import net.ozanarchy.towns.events.ProtectionListener;
import net.ozanarchy.towns.events.TownEvents;
import net.ozanarchy.towns.handlers.ChunkHandler;
import net.ozanarchy.towns.handlers.DatabaseHandler;
import net.ozanarchy.towns.handlers.PermissionManager;
import net.ozanarchy.towns.util.TownsPlaceholder;
import net.ozanarchy.towns.gui.MainGui;
import net.ozanarchy.towns.gui.BankGui;
import net.ozanarchy.towns.gui.MembersGui;
import net.ozanarchy.towns.gui.MemberPermissionMenu;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.Map;

public final class TownsPlugin extends JavaPlugin {
    public enum StorageType {
        MYSQL,
        SQLITE
    }

    private Connection connection;
    private StorageType storageType = StorageType.MYSQL;
    private String host;
    private String database;
    private String username;
    private String password;
    private int port;
    private String sqliteFile;
    public static FileConfiguration config;
    public static FileConfiguration guiConfig;
    public static FileConfiguration messagesConfig;
    public static FileConfiguration hologramsConfig;
    public static FileConfiguration protectionConfig;
    private EconomyProvider economy;
    private final ChunkHandler chunkCache = new ChunkHandler();
    private PermissionManager permissionManager;

    @Override
    public void onEnable() {
        initializeConfigs();
        economy = setupEconomyProvider();
        if (economy == null) {
            getLogger().severe("No valid economy provider available for current config. Disabling.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        DatabaseHandler db = new DatabaseHandler(this);
        permissionManager = new PermissionManager(this);
        TownEvents townEvents = new TownEvents(db, permissionManager, this, economy, chunkCache);
        MemberEvents memberEvents = new MemberEvents(db, permissionManager, this, economy, chunkCache);
        AdminEvents adminEvents = new AdminEvents(db, this);

        MainGui mainGui = new MainGui(this);
        BankGui bankGui = new BankGui(this);
        MembersGui membersGui = new MembersGui(this, db, permissionManager);
        registerCommands(db, townEvents, memberEvents, adminEvents, mainGui, bankGui, membersGui);
        registerEvents(db, townEvents, memberEvents, mainGui, bankGui, membersGui);

        setupDatabase();
        createTables();

        registerPlaceholders(db);
        scheduleChunkCacheReload();
        scheduleUpkeepHandler(db, townEvents);
        scheduleSpawnReminderAndDeletion(db, townEvents);
    }

    private void initializeConfigs() {
        config = getConfig();
        config.options().copyDefaults(true);
        saveDefaultConfig();
        guiConfig = loadYamlConfig("gui.yml");
        messagesConfig = loadYamlConfig("messages.yml");
        hologramsConfig = loadYamlConfig("holograms.yml");
        protectionConfig = loadYamlConfig("protection-config.yml");
        applyCacheSettings();
    }

    private void registerCommands(DatabaseHandler db, TownEvents townEvents, MemberEvents memberEvents, AdminEvents adminEvents,
                                  MainGui mainGui, BankGui bankGui, MembersGui membersGui) {
        TownsCommand townsCommand = new TownsCommand(db, townEvents, memberEvents, mainGui, membersGui);
        getCommand("towns").setExecutor(townsCommand);
        getCommand("towns").setTabCompleter(townsCommand);

        TownBankCommands townBankCommands = new TownBankCommands(memberEvents, bankGui);
        getCommand("townbank").setExecutor(townBankCommands);
        getCommand("townbank").setTabCompleter(townBankCommands);

        AdminCommands adminCommands = new AdminCommands(adminEvents);
        getCommand("townadmin").setExecutor(adminCommands);
        getCommand("townadmin").setTabCompleter(adminCommands);

        if (config.getBoolean("townmessages")) {
            getCommand("tm").setExecutor(new TownMessageCommand(this, db));
        }
    }

    private void registerEvents(DatabaseHandler db, TownEvents townEvents, MemberEvents memberEvents,
                                MainGui mainGui, BankGui bankGui, MembersGui membersGui) {
        getServer().getPluginManager().registerEvents(townEvents, this);
        getServer().getPluginManager().registerEvents(memberEvents, this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this, db, chunkCache, permissionManager), this);
        registerLockPickListenerIfAvailable(db);
        getServer().getPluginManager().registerEvents(mainGui, this);
        getServer().getPluginManager().registerEvents(bankGui, this);
        getServer().getPluginManager().registerEvents(membersGui, this);
        getServer().getPluginManager().registerEvents(new MemberPermissionMenu(this, db, permissionManager, null), this);
    }

    private void registerLockPickListenerIfAvailable(DatabaseHandler db) {
        if (!getServer().getPluginManager().isPluginEnabled("OminousChestLock")) {
            getLogger().info("OminousChestLock not found; raid lockpick listener not registered.");
            return;
        }
        try {
            Class<?> clazz = Class.forName("net.ozanarchy.towns.events.LockPickListener");
            Listener listener = (Listener) clazz
                    .getConstructor(TownsPlugin.class, DatabaseHandler.class, EconomyProvider.class)
                    .newInstance(this, db, economy);
            getServer().getPluginManager().registerEvents(listener, this);
            getLogger().info("OminousChestLock detected; raid lockpick listener registered.");
        } catch (Throwable t) {
            getLogger().warning("Failed to register LockPickListener; OminousChestLock integration disabled.");
            getLogger().warning(t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private void registerPlaceholders(DatabaseHandler db) {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new TownsPlaceholder(this, db).register();
            getLogger().info("PlaceholderAPI Enabled");
        }
    }

    private EconomyProvider setupEconomyProvider() {
        String selected = config.getString("economy-plugin", "ozanarchy-economy");
        String normalized = selected == null ? "ozanarchy-economy" : selected.trim().toLowerCase();
        String providerClass = switch (normalized) {
            case "vault" -> "net.ozanarchy.towns.economy.VaultEconomyProvider";
            case "ozanarchy-economy", "ozanarchy" -> "net.ozanarchy.towns.economy.OzanarchyEconomyProvider";
            default -> null;
        };

        if (providerClass == null) {
            getLogger().severe("Invalid economy-plugin value: " + selected + ". Use 'ozanarchy-economy' or 'vault'.");
            return null;
        }

        try {
            Class<?> clazz = Class.forName(providerClass);
            Object instance = clazz.getMethod("create", org.bukkit.plugin.java.JavaPlugin.class).invoke(null, this);
            if (instance instanceof EconomyProvider provider) {
                getLogger().info("Using economy provider: " + provider.getName());
                return provider;
            }
            getLogger().severe("Economy provider create() returned null for: " + providerClass);
            return null;
        } catch (Throwable t) {
            getLogger().severe("Failed to initialize economy provider '" + selected + "': " + t.getMessage());
            return null;
        }
    }

    private void scheduleChunkCacheReload() {
        reloadChunkCache();
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::reloadChunkCache, 300L, 300L);
    }

    private void scheduleUpkeepHandler(DatabaseHandler db, TownEvents townEvents) {
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            long unpaidDeleteAfterMinutes = Math.max(0L, config.getLong("upkeep.unpaid-delete-after-minutes", 1440L));
            try (ResultSet rs = db.getTownsNeedingUpkeep()) {
                while (rs.next()) {
                    int townId = rs.getInt("town_id");
                    double cost = rs.getDouble("upkeep_cost");
                    long elapsedSeconds = 0L;
                    Timestamp lastUpkeep = rs.getTimestamp("last_upkeep");
                    if (lastUpkeep != null) {
                        elapsedSeconds = Math.max(0L, (System.currentTimeMillis() - lastUpkeep.getTime()) / 1000L);
                    }
                    long upkeepIntervalSeconds = Math.max(1L, rs.getLong("upkeep_interval"));
                    if (elapsedSeconds < upkeepIntervalSeconds) {
                        continue;
                    }
                    boolean paid = db.withdrawTownMoney(townId, cost);
                    if (paid) {
                        db.updateLastUpkeep(townId);
                        townEvents.notifyTown(townId, messagesConfig.getString("messages.upkeepsuccess").replace("{cost}", String.valueOf(cost)));
                    } else {
                        long overdueSeconds = Math.max(0L, elapsedSeconds - upkeepIntervalSeconds);

                        if (unpaidDeleteAfterMinutes == 0L || overdueSeconds >= unpaidDeleteAfterMinutes * 60L) {
                            String townName = db.getTownName(townId);
                            townEvents.abandonTown(townId);
                            getLogger().info("Town " + (townName != null ? townName : ("#" + townId))
                                    + " deleted for unpaid upkeep after "
                                    + (overdueSeconds / 60L)
                                    + " overdue minutes (limit: "
                                    + unpaidDeleteAfterMinutes
                                    + ").");
                        } else {
                            townEvents.notifyTown(townId, messagesConfig.getString("messages.upkeepoverdue"));
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, 0L, 20 * 60 * 15);
    }

    private void scheduleSpawnReminderAndDeletion(DatabaseHandler db, TownEvents townEvents) {
        long maxAgeMinutes = config.getLong("spawn-reminder.max-age-minutes", 60);
        long intervalMinutes = config.getLong("spawn-reminder.reminder-interval-minutes", 5);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            try (ResultSet rs = db.getTownsWithoutSpawn()) {
                while (rs.next()) {
                    int townId = rs.getInt("id");
                    String townName = rs.getString("name");
                    long ageSeconds = db.getTownAgeInSeconds(townId);
                    long ageMinutes = ageSeconds / 60;

                    if (ageMinutes >= maxAgeMinutes) {
                        Bukkit.getScheduler().runTask(this, () -> {
                            townEvents.abandonTown(townId);
                            getLogger().info("Town " + townName + " deleted for not setting spawn within " + maxAgeMinutes + " minutes.");
                        });
                    } else if (ageMinutes > 0 && ageMinutes % intervalMinutes == 0 && ageSeconds % 60 < 20) {
                        String msg = messagesConfig.getString("messages.setspawnreminder")
                                .replace("{minutes}", String.valueOf(maxAgeMinutes - ageMinutes));
                        townEvents.notifyTown(townId, msg);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, 20 * 30L, 20 * 60L);
    }

    public void setupDatabase() {
        String storage = config.getString("storage.type", "mysql");
        storageType = "sqlite".equalsIgnoreCase(storage) ? StorageType.SQLITE : StorageType.MYSQL;
        try {
            connectDatabase();
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private synchronized void connectDatabase() throws SQLException, ClassNotFoundException {
        if (connection != null && !connection.isClosed()) return;

        if (storageType == StorageType.SQLITE) {
            sqliteFile = config.getString("sqlite.file", "towns.db");
            Class.forName("org.sqlite.JDBC");
            File dbFile = new File(getDataFolder(), sqliteFile);
            dbFile.getParentFile().mkdirs();
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            setConnection(DriverManager.getConnection(url));
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
            }
            getLogger().info("SQLite connected: " + dbFile.getAbsolutePath());
            return;
        }

        host = config.getString("mysql.host");
        port = config.getInt("mysql.port");
        username = config.getString("mysql.username");
        password = config.getString("mysql.password");
        database = config.getString("mysql.database");
        Class.forName("com.mysql.cj.jdbc.Driver");
        String url = "jdbc:mysql://" + this.host + ":" + this.port + "/" + this.database
                + "?useSSL=false&allowPublicKeyRetrieval=true&tcpKeepAlive=true&connectTimeout=5000&socketTimeout=10000";
        setConnection(DriverManager.getConnection(url, this.username, this.password));
        getLogger().info("MySQL connected successfully");
    }

    /**
     * Creates the necessary MySQL tables if they don't already exist.
     */
    public void createTables() {
        try (Statement stmt = getConnection().createStatement()) {
            // Towns table: Stores basic town information
            String towns = storageType == StorageType.SQLITE
                    ? "CREATE TABLE IF NOT EXISTS towns (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT UNIQUE," +
                    "mayor_uuid TEXT," +
                    "world TEXT," +
                    "spawn_x REAL," +
                    "spawn_y REAL," +
                    "spawn_z REAL," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")"
                    : "CREATE TABLE IF NOT EXISTS towns (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "name VARCHAR(32) UNIQUE," +
                    "mayor_uuid VARCHAR(36)," +
                    "world VARCHAR(32)," +
                    "spawn_x DOUBLE," +
                    "spawn_y DOUBLE," +
                    "spawn_z DOUBLE," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")";
            stmt.executeUpdate(towns);

            // Claims table: Stores chunk ownership information
            String claims = storageType == StorageType.SQLITE
                    ? "CREATE TABLE IF NOT EXISTS claims (" +
                    "world TEXT," +
                    "chunkx INTEGER," +
                    "chunkz INTEGER," +
                    "town_id INTEGER," +
                    "PRIMARY KEY (world, chunkx, chunkz)," +
                    "FOREIGN KEY (town_id) REFERENCES towns(id) ON DELETE CASCADE" +
                    ")"
                    : "CREATE TABLE IF NOT EXISTS claims (" +
                    "world VARCHAR(32)," +
                    "chunkx INT," +
                    "chunkz INT," +
                    "town_id INT," +
                    "PRIMARY KEY (world, chunkx, chunkz)," +
                    "FOREIGN KEY (town_id) REFERENCES towns(id) ON DELETE CASCADE" +
                    ")";
            stmt.executeUpdate(claims);

            // Town Members table: Stores players and their roles within towns
            String members = storageType == StorageType.SQLITE
                    ? "CREATE TABLE IF NOT EXISTS town_members (" +
                    "town_id INTEGER NOT NULL," +
                    "uuid TEXT NOT NULL," +
                    "role TEXT NOT NULL CHECK(role IN ('MAYOR','OFFICER','MEMBER'))," +
                    "PRIMARY KEY (uuid)" +
                    ")"
                    : "CREATE TABLE IF NOT EXISTS town_members (" +
                    "town_id INT NOT NULL," +
                    "uuid VARCHAR(36) NOT NULL," +
                    "role ENUM('MAYOR', 'OFFICER', 'MEMBER') NOT NULL," +
                    "PRIMARY KEY (uuid)," +
                    "INDEX (town_id)" +
                    ")";
            stmt.executeUpdate(members);
            if (storageType == StorageType.SQLITE) {
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_town_members_town_id ON town_members(town_id)");
            }

            // Town Bank table: Stores economic data for each town
            String townBank = storageType == StorageType.SQLITE
                    ? "CREATE TABLE IF NOT EXISTS town_bank (" +
                    "town_id INTEGER NOT NULL," +
                    "balance REAL NOT NULL DEFAULT 0," +
                    "last_upkeep TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "upkeep_interval INTEGER NOT NULL DEFAULT 86400," +
                    "upkeep_cost REAL NOT NULL DEFAULT 0.0," +
                    "FOREIGN KEY (town_id) REFERENCES towns(id) ON DELETE CASCADE" +
                    ")"
                    : "CREATE TABLE IF NOT EXISTS town_bank (" +
                    "town_id INT NOT NULL," +
                    "balance DOUBLE NOT NULL DEFAULT 0," +
                    "last_upkeep TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "upkeep_interval INT NOT NULL DEFAULT 86400," +
                    "upkeep_cost DOUBLE NOT NULL DEFAULT 0.0," +
                    "FOREIGN KEY (town_id) REFERENCES towns(id) ON DELETE CASCADE" +
                    ")";
            stmt.executeUpdate(townBank);

            // Member Permissions table: Stores individual player permissions within towns
            String memberPermissions = storageType == StorageType.SQLITE
                    ? "CREATE TABLE IF NOT EXISTS town_member_permissions (" +
                    "town_id INTEGER NOT NULL," +
                    "player_uuid TEXT NOT NULL," +
                    "permission_node TEXT NOT NULL," +
                    "value INTEGER NOT NULL DEFAULT 0," +
                    "PRIMARY KEY (town_id, player_uuid, permission_node)," +
                    "FOREIGN KEY (town_id) REFERENCES towns(id) ON DELETE CASCADE" +
                    ")"
                    : "CREATE TABLE IF NOT EXISTS town_member_permissions (" +
                    "town_id INT NOT NULL," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "permission_node VARCHAR(50) NOT NULL," +
                    "value BOOLEAN NOT NULL DEFAULT FALSE," +
                    "PRIMARY KEY (town_id, player_uuid, permission_node)," +
                    "FOREIGN KEY (town_id) REFERENCES towns(id) ON DELETE CASCADE" +
                    ")";
            stmt.executeUpdate(memberPermissions);

            // Database migrations: ensure legacy tables have the required columns
            try {
                stmt.executeUpdate("ALTER TABLE towns ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
            } catch (SQLException ignored) {}

            getLogger().info("Tables checked/created successfully.");
        } catch (SQLException e) {
            getLogger().severe("Error creating tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed() || !connection.isValid(2)) {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException ignored) {
                    }
                }
                connection = null;
                connectDatabase();
            }
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException("Failed to establish MySQL connection", e);
        }
        return connection;
    }

    public StorageType getStorageType() {
        return storageType;
    }

    public PermissionManager getPermissionManager() {
        return permissionManager;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    // Chunk Reloader
    public void reloadChunkCache() {
        DatabaseHandler db = new DatabaseHandler(this);
        Map<String, Integer> claims = db.loadAllClaims();
        chunkCache.setAll(claims);
    }

    private FileConfiguration loadYamlConfig(String fileName) {
        File configFile = new File(getDataFolder(), fileName);
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            saveResource(fileName, false);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
        return yaml;
    }

    public void reloadAllConfigs() {
        reloadConfig();
        config = getConfig();
        guiConfig = loadYamlConfig("gui.yml");
        messagesConfig = loadYamlConfig("messages.yml");
        hologramsConfig = loadYamlConfig("holograms.yml");
        protectionConfig = loadYamlConfig("protection-config.yml");
        applyCacheSettings();
    }

    private void applyCacheSettings() {
        long ttlSeconds = config.getLong("cache.ttl-seconds", 15L);
        DatabaseHandler.setCacheTtlSeconds(ttlSeconds);
        PermissionManager.setCacheTtlSeconds(ttlSeconds);
    }

    @Override
    public void onDisable() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }
}
