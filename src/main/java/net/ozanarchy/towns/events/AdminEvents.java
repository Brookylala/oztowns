package net.ozanarchy.towns.events;

import eu.decentsoftware.holograms.api.DHAPI;
import net.ozanarchy.chestlock.ChestLockPlugin;
import net.ozanarchy.chestlock.lock.LockInfo;
import net.ozanarchy.chestlock.lock.LockService;
import me.clip.placeholderapi.PlaceholderAPI;
import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.handlers.DatabaseHandler;
import net.ozanarchy.towns.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static net.ozanarchy.towns.TownsPlugin.hologramsConfig;
import static net.ozanarchy.towns.TownsPlugin.messagesConfig;

public class AdminEvents {
    private final TownsPlugin plugin;
    private final DatabaseHandler db;
    private final String prefix = Utils.adminPrefix();

    public AdminEvents(DatabaseHandler db, TownsPlugin plugin) {
        this.db = db;
        this.plugin = plugin;
    }

    // ==========================================
    // ADMIN UTILITIES
    // ==========================================

    public void reload(CommandSender sender) {
        plugin.reloadAllConfigs();
        sender.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.reloaded")));
    }

    // ==========================================
    // TOWN ADMIN ACTIONS
    // ==========================================

    public void deleteTown(CommandSender sender, String townName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getTownIdByName(townName);
            if (townId == null) {
                sendSync(sender, prefix + messagesConfig.getString("adminmessages.townnotfound"));
                return;
            }

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
            db.deleteMembers(townId);
            db.deleteTownBank(townId);
            db.deleteTown(townId);

            sendSync(sender, prefix + messagesConfig.getString("adminmessages.towndeleted").replace("{town}", townName));
        });
    }

    public void setSpawn(Player player, String townName) {
        Location loc = player.getLocation();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getTownIdByName(townName);
            if (townId == null) {
                player.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.townnotfound")));
                return;
            }

            Location oldLoc = db.getTownSpawn(townId);
            if (oldLoc != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Block oldBlock = oldLoc.getBlock();
                    unlockChestLock(oldBlock);
                    if (oldBlock.getType() == Material.LODESTONE) {
                        oldBlock.setType(Material.AIR);
                    }
                });
            }

            double saveX = loc.getBlockX() + 0.5;
            double saveY = loc.getBlockY();
            double saveZ = loc.getBlockZ() + 0.5;

            db.updateTownSpawn(townId, loc.getWorld().getName(), saveX, saveY, saveZ);

            Bukkit.getScheduler().runTask(plugin, () -> {
                Block block = loc.getBlock();
                block.setType(Material.LODESTONE);
                loc.getWorld().save();
                updateTownSpawnHologram(townId, loc, player);
                player.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.spawnsuccess").replace("{town}", townName)));
            });
        });
    }

    public void removeSpawn(CommandSender sender, String townName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getTownIdByName(townName);
            if (townId == null) {
                sendSync(sender, prefix + messagesConfig.getString("adminmessages.townnotfound"));
                return;
            }

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

            db.updateTownSpawn(townId, null, 0, 0, 0);
            db.resetTownCreationTime(townId);

            sendSync(sender, prefix + messagesConfig.getString("adminmessages.spawnremoved").replace("{town}", townName));
        });
    }

    public void addMember(CommandSender sender, String townName, String playerName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getTownIdByName(townName);
            if (townId == null) {
                sendSync(sender, prefix + messagesConfig.getString("adminmessages.townnotfound"));
                return;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
            if (!target.isOnline() && !target.hasPlayedBefore()) {
                sendSync(sender, prefix + messagesConfig.getString("adminmessages.playernotfound"));
                return;
            }

            UUID targetId = target.getUniqueId();
            Integer targetTown = db.getPlayerTownId(targetId);
            if (targetTown != null) {
                sendSync(sender, prefix + messagesConfig.getString("adminmessages.alreadyintown"));
                return;
            }

            boolean success = db.addMember(townId, targetId, "MEMBER");
            if (!success) {
                sendSync(sender, prefix + messagesConfig.getString("messages.addmemberfailed"));
                return;
            }

            sendSync(sender, prefix + messagesConfig.getString("adminmessages.addedmember")
                    .replace("{player}", target.getName() == null ? playerName : target.getName())
                    .replace("{town}", townName));

            if (target.isOnline()) {
                Player onlineTarget = target.getPlayer();
                if (onlineTarget != null) {
                    onlineTarget.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.joinedtown")));
                }
            }
        });
    }

    public void removeMember(CommandSender sender, String townName, String playerName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getTownIdByName(townName);
            if (townId == null) {
                sendSync(sender, prefix + messagesConfig.getString("adminmessages.townnotfound"));
                return;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
            if (!target.isOnline() && !target.hasPlayedBefore()) {
                sendSync(sender, prefix + messagesConfig.getString("adminmessages.playernotfound"));
                return;
            }

            UUID targetId = target.getUniqueId();
            Integer targetTown = db.getPlayerTownId(targetId);
            if (targetTown == null || !targetTown.equals(townId)) {
                sendSync(sender, prefix + messagesConfig.getString("adminmessages.notintown"));
                return;
            }
            if(db.isMayor(targetId, townId)){
                sendSync(sender, prefix + messagesConfig.getString("adminmessages.cannotremovemayor"));
                return;
            }
            boolean success = db.removeMember(targetId, townId);
            if (!success) {
                sendSync(sender, prefix + messagesConfig.getString("messages.removememberfailed"));
                return;
            }

            sendSync(sender, prefix + messagesConfig.getString("adminmessages.removedmember")
                    .replace("{player}", target.getName() == null ? playerName : target.getName())
                    .replace("{town}", townName));

            if (target.isOnline()) {
                Player onlineTarget = target.getPlayer();
                if (onlineTarget != null) {
                    onlineTarget.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.kickedfromtown")));
                }
            }
        });
    }

    public void setMayor(CommandSender sender, String townName, String playerName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getTownIdByName(townName);
            if (townId == null) {
                sendSync(sender, prefix + messagesConfig.getString("adminmessages.townnotfound"));
                return;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
            if (!target.isOnline() && !target.hasPlayedBefore()) {
                sendSync(sender, prefix + messagesConfig.getString("adminmessages.playernotfound"));
                return;
            }

            UUID targetId = target.getUniqueId();
            Integer targetTown = db.getPlayerTownId(targetId);
            if (targetTown == null || !targetTown.equals(townId)) {
                sendSync(sender, prefix + messagesConfig.getString("adminmessages.notintown"));
                return;
            }

            boolean success = db.setMayor(targetId, townId);
            if (!success) {
                sendSync(sender, prefix + messagesConfig.getString("messages.setmayorfail"));
                return;
            }

            sendSync(sender, prefix + messagesConfig.getString("adminmessages.setmayor")
                    .replace("{player}", target.getName() == null ? playerName : target.getName())
                    .replace("{town}", townName));

            if (target.isOnline()) {
                Player onlineTarget = target.getPlayer();
                if (onlineTarget != null) {
                    onlineTarget.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.newmayor")));
                }
            }
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

    public void spawnTeleport(Player p, String townName){
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getTownIdByName(townName);
            if (townId == null) {
                p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.townnotfound")));
                return;
            }

            Location spawn = db.getTownSpawn(townId);

            if(spawn == null){
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.spawnnotfound")));
                });
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () ->{
                p.teleport(spawn);
                p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.teleported")
                    .replace("{town}", townName)));
            });
        });
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
        String idPrefix = hologramsConfig.getString("holograms.id-prefix", "oztowns_");
        return idPrefix + "town_spawn_" + townId;
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

    private void sendSync(CommandSender sender, String message) {
        Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(Utils.getColor(message)));
    }
}
