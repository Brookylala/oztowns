package net.ozanarchy.towns.events;

import eu.decentsoftware.holograms.api.DHAPI;
import me.clip.placeholderapi.PlaceholderAPI;
import net.ozanarchy.chestlock.ChestLockPlugin;
import net.ozanarchy.chestlock.lock.LockInfo;
import net.ozanarchy.chestlock.lock.LockService;
import net.ozanarchy.towns.economy.EconomyProvider;
import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.util.Utils;
import net.ozanarchy.towns.handlers.ChunkHandler;
import net.ozanarchy.towns.handlers.DatabaseHandler;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static net.ozanarchy.towns.TownsPlugin.config;
import static net.ozanarchy.towns.TownsPlugin.hologramsConfig;
import static net.ozanarchy.towns.TownsPlugin.messagesConfig;

import net.ozanarchy.towns.handlers.PermissionManager;

public class TownEvents implements Listener {
    private final DatabaseHandler db;
    private final PermissionManager permissionManager;
    private final TownsPlugin plugin;
    private final EconomyProvider economy;
    private final ChunkHandler chunkCache;
    private final String prefix = Utils.prefix();
    private final String notEnough = messagesConfig.getString("messages.notenough");
    private final String noPerm = messagesConfig.getString("messages.nopermission");
    private final String incorrectUsage = messagesConfig.getString("messages.incorrectusage");
    private final Double upkeepCost = config.getDouble("towns.addedclaimupkeep");
    private final Map<UUID, BukkitTask> teleportTasks = new HashMap<>();
    private final Map<UUID, BossBar> townBossBars = new HashMap<>();
    private final Map<UUID, Long> lastSpawnSet = new HashMap<>();
    private final Map<UUID, Integer> lastTown = new HashMap<>();

    public TownEvents(DatabaseHandler data, PermissionManager permissionManager, TownsPlugin plugin, EconomyProvider economy, ChunkHandler chunkCache){
        this.db = data;
        this.permissionManager = permissionManager;
        this.plugin = plugin;
        this.economy = economy;
        this.chunkCache = chunkCache;
    }

    // ==========================================
    // SPAWN MANAGEMENT
    // ==========================================

    /**
     * Sets the town spawn location and places a Lodestone block.
     */
    public void setSpawn(Player p) {
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldown = config.getLong("towns.setspawntimer", 120) * 1000;
        long remaining = getRemainingSpawnCooldownSeconds(uuid, now, cooldown);
        if (remaining > 0) {
            p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.pleasewait")).replace("{seconds}", String.valueOf(remaining)));
            return;
        }

        Location requestedLoc = p.getLocation().clone();
        Chunk requestedChunk = requestedLoc.getChunk();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getPlayerTownId(uuid);
            if (townId == null) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notown")))
                );
                return;
            }

            if (!db.isMayor(uuid, townId)) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notmayor")))
                );
                return;
            }

            Integer chunkTownId = chunkCache.getTownId(requestedChunk);
            if (chunkTownId == null || !chunkTownId.equals(townId)) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.setspawnrestricted")))
                );
                return;
            }
            Location oldLoc = db.getTownSpawn(townId);

            Bukkit.getScheduler().runTask(plugin, () -> {
                lastSpawnSet.put(uuid, now);
                // Remove old lodestone if it exists
                if (oldLoc != null) {
                    Block oldBlock = oldLoc.getBlock();
                    unlockChestLock(oldBlock);
                    if (oldBlock.getType() == Material.LODESTONE) {
                        oldBlock.setType(Material.AIR);
                    }
                }

                Location loc = requestedLoc;
                Block targetBlock = loc.getBlock();

                // Enforce maximum allowed Y (player can't set spawn above world build limit)
                if (targetBlock.getY() > 256) {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.spawnheightrestricted")));
                    return;
                }

                // Clear sky check above the target block
                for (int y = targetBlock.getY() + 1; y < loc.getWorld().getMaxHeight(); y++) {
                    Block blockAbove = loc.getWorld().getBlockAt(targetBlock.getX(), y, targetBlock.getZ());
                    if (blockAbove.getType().isOccluding()) {
                        p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.spawnskyrestricted")));
                        return;
                    }
                }

                // Save the spawn as the lodestone block center (so teleports land cleanly above it)
                double saveX = targetBlock.getX() + 0.5;
                double saveY = targetBlock.getY();
                double saveZ = targetBlock.getZ() + 0.5;
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                        db.updateTownSpawn(townId, loc.getWorld().getName(), saveX, saveY, saveZ)
                );

                Block block = targetBlock;
                block.setType(Material.LODESTONE);
                loc.getWorld().save(); // Force save the world to ensure Lodestone placement is persistent
                p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.spawnsetsuccess")));
                updateTownSpawnHologram(townId, loc, p);

                String command = config.getString("town-creation-command");
                if (command != null && !command.isEmpty()) {
                    String finalCommand = command.replace("{player}", p.getName())
                            .replace("{world}", loc.getWorld().getName())
                            .replace("{x}", String.valueOf(loc.getBlockX()))
                            .replace("{y}", String.valueOf(loc.getBlockY()))
                            .replace("{z}", String.valueOf(loc.getBlockZ()));
                    
                    // We need to wait a tick for the block to be placed before locking it
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
                    });
                }
            });
        });
    }

    /**
     * Starts the teleportation process to the town spawn.
     */
    public void spawn(Player p) {
        UUID uuid = p.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getPlayerTownId(uuid);
            if (townId == null) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notown")))
                );
                return;
            }

            Location spawnLoc = db.getTownSpawn(townId);
            if (spawnLoc == null) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notownspawn")))
                );
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                // Clear sky check
                for (int y = spawnLoc.getBlockY() + 1; y < spawnLoc.getWorld().getMaxHeight(); y++) {
                    Block blockAbove = spawnLoc.getWorld().getBlockAt(spawnLoc.getBlockX(), y, spawnLoc.getBlockZ());
                    if (blockAbove.getType().isOccluding()) {
                        p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.spawnskyobstructed")));
                        return;
                    }
                }

                if (teleportTasks.containsKey(uuid)) {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.alreadyteleporting")));
                    return;
                }

                final int delay = config.getInt("spawn-delay", 15); // seconds
                BossBar bossBar = Bukkit.createBossBar(Utils.getColor(messagesConfig.getString("messages.teleportingbar")), BarColor.YELLOW, BarStyle.SOLID);
                bossBar.addPlayer(p);
                bossBar.setProgress(1.0);

                Location startLoc = p.getLocation().clone();

                BukkitTask task = new BukkitRunnable() {
                    int secondsLeft = delay;
                    double progressStep = 1.0 / delay;

                    @Override
                    public void run() {
                        if (!p.isOnline()) {
                            cancelTeleport(uuid, bossBar);
                            return;
                        }

                        if (p.getLocation().distanceSquared(startLoc) > 0.1) {
                            p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.teleportcancelled")));
                            cancelTeleport(uuid, bossBar);
                            return;
                        }

                        secondsLeft--;
                        if (secondsLeft <= 0) {
                            // Teleport player to above the lodestone block center to avoid spawning inside the block
                            Location target = spawnLoc.getBlock().getLocation().add(0.5, 1, 0.5);
                            p.teleport(target);
                            p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.teleported")));
                            cancelTeleport(uuid, bossBar);
                            return;
                        }

                        bossBar.setProgress(Math.max(0.0, bossBar.getProgress() - progressStep));
                        bossBar.setTitle(Utils.getColor(messagesConfig.getString("messages.teleportingbartitle").replace("{seconds}", String.valueOf(secondsLeft))));
                    }
                }.runTaskTimer(plugin, 20L, 20L);

                teleportTasks.put(uuid, task);
            });
        });
    }

    /**
     * Cancels a pending teleport for a player.
     */
    private void cancelTeleport(UUID uuid, BossBar bossBar) {
        if (teleportTasks.containsKey(uuid)) {
            teleportTasks.get(uuid).cancel();
            teleportTasks.remove(uuid);
        }
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    // ==========================================
    // LAND CLAIMING
    // ==========================================

    /**
     * Claims the chunk the player is currently standing in for their town.
     */
    public void claimLand(Player p){
        UUID uuid = p.getUniqueId();
        double cost = config.getInt("towns.claimcost");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getPlayerTownId(uuid);
            if (townId == null) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notown")))
                );
                return;
            }

            // Check if player has permission to claim
            boolean canClaim = db.isTownAdmin(uuid, townId) || permissionManager.getPermissionSync(townId, uuid, "CAN_CLAIM");
            if (!canClaim) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.nopermission")))
                );
                return;
            }

            economy.remove(uuid, cost, success -> {
                if(!success){
                    p.sendMessage(Utils.getColor(prefix + notEnough));
                    return;
                }
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    Chunk chunk = p.getLocation().getChunk();
                    if(db.getChunkClaimed(chunk)){
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.chunkowned")));
                        });
                        economy.add(uuid, cost);
                        return;
                    }

                    if (db.hasClaims(townId) && !db.isAdjacentClaim(townId, chunk)) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.disconnectedclaim")));
                        });
                        economy.add(uuid, cost);
                        return;
                    }
                    
                    for(String worldName : config.getStringList("unclaimable-worlds")){
                        if(p.getWorld().getName().equalsIgnoreCase(worldName)){
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.unclaimableworld")));
                            });
                            economy.add(uuid, cost);
                            return;
                        }
                    }

                    db.increaseUpkeep(townId, upkeepCost);
                    db.saveClaim(chunk, townId);
                    chunkCache.setClaim(chunk, townId);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.chunkclaimed")));
                    });
                });
            });
        });
    }

    /**
     * Unclaims the chunk the player is standing in.
     */
    public void removeChunk(Player p){
        Chunk chunk = p.getLocation().getChunk();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID uuid = p.getUniqueId();
            Integer townId = db.getPlayerTownId(uuid);
            if (townId == null){
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notown")));
                });
                return;
            }

            // Check if player has permission to unclaim
            boolean canUnclaim = db.isTownAdmin(uuid, townId) || permissionManager.getPermissionSync(townId, uuid, "CAN_UNCLAIM");
            if (!canUnclaim) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.nopermission")))
                );
                return;
            }

            Integer claimTown = chunkCache.getTownId(chunk);
            if(claimTown == null){
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.chunknotowned")));
                });
                return;
            }
            if(claimTown != townId){
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.chunkowned")));
                });
                return;
            }

            boolean success = db.unClaimChunk(chunk, townId);
            if(success){
                chunkCache.removeClaim(chunk);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.chunkremoved")));
                });
                db.decreaseUpkeep(townId, config.getDouble("towns.refundedclaimupkeep"));

                // Check if spawn was in this chunk
                Location spawnLoc = db.getTownSpawn(townId);
                if (spawnLoc != null && spawnLoc.getChunk().equals(chunk)) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Block block = spawnLoc.getBlock();
                        unlockChestLock(block);
                        if (block.getType() == Material.LODESTONE) {
                            block.setType(Material.AIR);
                        }
                        removeTownSpawnHologram(townId);
                        db.updateTownSpawn(townId, null, 0, 0, 0);
                        db.resetTownCreationTime(townId);
                        p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.spawnremoved")));
                    });
                }
                return;
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.unclaimfailed")));
                });
                return;
            }
        });
    }

    // ==========================================
    // TOWN LIFECYCLE
    // ==========================================

    /**
     * Handles the creation of a new town.
     */
    public void createTown(Player p, String[] args) {
        if(args.length != 2){
            p.sendMessage(Utils.getColor(prefix + incorrectUsage));
            return;
        }
        String townName = args[1];
        if (!isValidTownName(p, townName)) {
            return;
        }
        UUID uuid = p.getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
           if(db.getPlayerTownId(uuid) != null){
               Bukkit.getScheduler().runTask(plugin, () ->{
                  p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.alreadyinatown")));
               });
               return;
           }

           if(db.townExists(townName)){
               Bukkit.getScheduler().runTask(plugin, () -> {
                  p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.townnametaken")));
               });
               return;
           }

            economy.remove(uuid, config.getDouble("towns.createcost"), success -> {
                if (!success) {
                    p.sendMessage(Utils.getColor(prefix + notEnough));
                    return;
                }

                try {
                    int townId = db.createTown(townName, uuid, null, 0, 0, 0);
                    db.addMember(townId, uuid, "MAYOR");
                    db.createTownBank(townId);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.towncreated").replace("{town}", townName)));
                        p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.towncreatenextsteps")));
                    });
                } catch (SQLException e){
                    e.printStackTrace();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.failedtomaketown")));
                    });
                }
            });
        });

    }

    /**
     * Handles the abandonment (deletion) of a town by its mayor.
     */
    public void abandonTown(UUID uuid){
        Player p = Bukkit.getPlayer(uuid);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getPlayerTownId(uuid);
            if(townId == null){
                if (p != null) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notownexists")))
                    );
                }
                return;
            }
            if(!db.isMayor(uuid, townId)){
                if (p != null) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notmayor")))
                    );
                }
                return;
            }

            abandonTown(townId);

            if (p != null) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.towndeleted")))
                );
            }
        });
    }

    /**
     * Deletes a town and all associated data from the database and world.
     */
    public void abandonTown(int townId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Remove Lodestone if it exists
            Location oldSpawn = db.getTownSpawn(townId);
            if (oldSpawn != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Block block = oldSpawn.getBlock();
                    unlockChestLock(block);
                    if (block.getType() == Material.LODESTONE) {
                        block.setType(Material.AIR);
                    }
                    removeTownSpawnHologram(townId);
                });
            }

            db.deleteClaim(townId);
            plugin.reloadChunkCache();
            db.deleteMembers(townId);
            db.deleteTownBank(townId);
            db.deleteTown(townId);
        });
    }

    /**
     * Sends a message to all members of a specific town.
     */
    public void notifyTown(int townId, String message) {
        String sql = "SELECT uuid FROM town_members WHERE town_id=?";
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
                stmt.setInt(1, townId);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        Bukkit.getScheduler().runTask(plugin, () ->
                                p.sendMessage(Utils.getColor(Utils.prefix() + message))
                        );
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    // ==========================================
    // BOSS BAR MANAGEMENT (Town Entry/Exit)
    // ==========================================

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();

        if (to == null || (from.getBlockX() >> 4 == to.getBlockX() >> 4 && from.getBlockZ() >> 4 == to.getBlockZ() >> 4)) {
            return;
        }

        Player p = e.getPlayer();
        updateTownBossBar(p, to.getChunk());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        BossBar bar = townBossBars.remove(p.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }

        lastTown.remove(p.getUniqueId());
    }

    private void updateTownBossBar(Player p, Chunk chunk) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = chunkCache.getTownId(chunk);
            UUID pUUID = p.getUniqueId();
            Integer previousTown = lastTown.get(pUUID);

            if (Objects.equals(previousTown, townId)) {
                return;
            }

            lastTown.put(pUUID, townId);

            String title;
            BarColor color;
            BarStyle style;

            if (townId != null) {
                String townName = db.getTownName(townId);
                title = Utils.getColor(messagesConfig.getString("messages.townentered").replace("{town}", townName != null ? townName : "Unknown"));
                color = BarColor.valueOf(config.getString("bossbar.town.color", "GREEN"));
                style = BarStyle.valueOf(config.getString("bossbar.town.style", "SOLID"));
            } else {
                title = Utils.getColor(messagesConfig.getString("messages.wilderness"));
                color = BarColor.valueOf(config.getString("bossbar.wilderness.color", "WHITE"));
                style = BarStyle.valueOf(config.getString("bossbar.wilderness.style", "SOLID"));
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                BossBar bar = townBossBars.computeIfAbsent(pUUID, uuid -> {
                    BossBar newBar = Bukkit.createBossBar(title, color, style);
                    newBar.addPlayer(p);
                    return newBar;
                });

                bar.setTitle(title);
                bar.setColor(color);
                bar.setStyle(style);
                bar.setVisible(true);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (townBossBars.get(p.getUniqueId()) == bar) {
                            bar.setVisible(false);
                        }
                    }
                }.runTaskLater(plugin, 100L); // 5 seconds
            });
        });
    }

    private void unlockChestLock(Block block) {
        if (block == null) return;

        if (!(Bukkit.getPluginManager().getPlugin("OminousChestLock") instanceof ChestLockPlugin chestLockPlugin)) {
            return;
        }

        LockService lockService = chestLockPlugin.getLockService();
        if (lockService == null) return;

        LockInfo lockInfo = lockService.getLockInfo(block);
        if (lockInfo != null) {
            lockService.unlock(block, lockInfo.keyName());
        }
    }

    private void updateTownSpawnHologram(int townId, Location spawnBlockLoc, Player contextPlayer) {
        if (!isHologramIntegrationEnabled()) {
            return;
        }

        String id = getTownHologramId(townId);
        String townName = db.getTownName(townId);
        int memberCount = db.getTownMembers(townId).size();
        double balance = db.getTownBalance(townId);
        String mayorName = resolveMayorName(townId, contextPlayer != null ? contextPlayer.getName() : null);

        List<String> lines = renderHologramLines(townName, mayorName, memberCount, balance, contextPlayer);
        if (lines.isEmpty()) {
            return;
        }

        double yOffset = hologramsConfig.getDouble("holograms.y-offset", 2.25D);
        Location hologramLoc = spawnBlockLoc.getBlock().getLocation().add(0.5D, yOffset, 0.5D);

        try {
            DHAPI.removeHologram(id);
            DHAPI.createHologram(id, hologramLoc, true, lines);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Failed to create/update town hologram for town id " + townId + ": " + ex.getMessage());
        }
    }

    private void removeTownSpawnHologram(int townId) {
        if (!isHologramIntegrationEnabled()) {
            return;
        }
        DHAPI.removeHologram(getTownHologramId(townId));
    }

    private boolean isHologramIntegrationEnabled() {
        FileConfiguration cfg = hologramsConfig;
        return cfg != null
                && cfg.getBoolean("holograms.enabled", true)
                && Bukkit.getPluginManager().isPluginEnabled("DecentHolograms");
    }

    private String getTownHologramId(int townId) {
        String prefix = hologramsConfig.getString("holograms.id-prefix", "oztowns_");
        return prefix + "town_spawn_" + townId;
    }

    private String resolveMayorName(int townId, String fallback) {
        List<DatabaseHandler.TownMember> members = db.getTownMembers(townId);
        for (DatabaseHandler.TownMember member : members) {
            if (!"MAYOR".equalsIgnoreCase(member.getRole())) {
                continue;
            }
            OfflinePlayer mayor = Bukkit.getOfflinePlayer(member.getUuid());
            if (mayor.getName() != null && !mayor.getName().isBlank()) {
                return mayor.getName();
            }
        }
        return fallback == null ? "Unknown" : fallback;
    }

    private List<String> renderHologramLines(String townName, String mayorName, int memberCount, double balance, Player contextPlayer) {
        List<String> templateLines = hologramsConfig.getStringList("holograms.string-lines");

        List<String> rendered = new ArrayList<>();
        DecimalFormat money = new DecimalFormat("0.##");

        for (String line : templateLines) {
            String value = line
                    .replace("{townname}", townName == null ? "Unknown" : townName)
                    .replace("{town-name}", townName == null ? "Unknown" : townName)
                    .replace("{mayor-name}", mayorName)
                    .replace("{mayor}", mayorName)
                    .replace("{town-member-count}", String.valueOf(memberCount))
                    .replace("{member-count}", String.valueOf(memberCount))
                    .replace("{balance}", money.format(balance));
            if (contextPlayer != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                value = PlaceholderAPI.setPlaceholders(contextPlayer, value);
            }
            rendered.add(Utils.getColor(value));
        }

        return rendered;
    }

    // ==========================================
    // Town Managment
    // ==========================================

    public void renameTown(Player p, String[] args){
        if(args.length != 2){
            p.sendMessage(Utils.getColor(prefix + incorrectUsage));
            return;
        }

        String newTownName = args[1];
        UUID pUUID = p.getUniqueId();

        if (!isValidTownName(p, newTownName)) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->{
            Integer townId = db.getPlayerTownId(pUUID);

            if(townId == null){
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notown")));
                });
                return;
            }

            if(!db.isMayor(pUUID, townId)){
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notmayor")));
                });
                return;
            }

            if(db.townExists(newTownName)){
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.townnametaken")));
                });
                return;
            }

            db.renameTown(townId, newTownName);
        });
    }

    private long getRemainingSpawnCooldownSeconds(UUID uuid, long nowMillis, long cooldownMillis) {
        Long lastSetAt = lastSpawnSet.get(uuid);
        if (lastSetAt == null) {
            return 0L;
        }
        long elapsedMillis = nowMillis - lastSetAt;
        if (elapsedMillis >= cooldownMillis) {
            return 0L;
        }
        return (cooldownMillis - elapsedMillis) / 1000;
    }

    private boolean isValidTownName(Player player, String townName) {
        if (!townName.matches("^[A-Za-z0-9_]{3,16}$")) {
            player.sendMessage(Utils.getColor(prefix + "Town name must be 3-16 characters (letters, numbers, _)"));
            return false;
        }
        for (String blockedName : config.getStringList("blacklisted-names")) {
            if (townName.equalsIgnoreCase(blockedName)) {
                player.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.nameblacklisted")));
                return false;
            }
        }
        return true;
    }
}

