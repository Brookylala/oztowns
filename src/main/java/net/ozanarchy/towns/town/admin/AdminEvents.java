package net.ozanarchy.towns.town.admin;

import net.ozanarchy.towns.town.model.TownMember;
import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.bank.repository.TownBankRepository;
import net.ozanarchy.towns.util.Utils;
import net.ozanarchy.towns.util.db.DatabaseHandler;

import eu.decentsoftware.holograms.api.DHAPI;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static net.ozanarchy.towns.TownsPlugin.hologramsConfig;
import static net.ozanarchy.towns.TownsPlugin.messagesConfig;
import static net.ozanarchy.towns.TownsPlugin.townConfig;

public class AdminEvents {
    private final TownsPlugin plugin;
    private final DatabaseHandler db;
    private final TownBankRepository townBankRepository;
    private final String prefix = Utils.adminPrefix();

    public AdminEvents(DatabaseHandler db, TownsPlugin plugin) {
        this.db = db;
        this.plugin = plugin;
        this.townBankRepository = new TownBankRepository(plugin);
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

            // Always attempt hologram cleanup for this town id, even if spawn is already null.
            Bukkit.getScheduler().runTask(plugin, () -> removeTownSpawnHologram(townId));

            Location oldSpawn = db.getTownSpawn(townId);
            if (oldSpawn != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Block block = oldSpawn.getBlock();                    if (block.getType() == Material.LODESTONE) {
                        block.setType(Material.AIR);
                    }
                    removeTownSpawnHologram(townId);
                });
            }

            db.deleteClaim(townId);
            db.deleteMembers(townId);
            townBankRepository.deleteTownBank(townId);
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
                    Block oldBlock = oldLoc.getBlock();                    if (oldBlock.getType() == Material.LODESTONE) {
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
                    Block block = oldSpawn.getBlock();                    if (block.getType() == Material.LODESTONE) {
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

            int maxMembers = townConfig.getInt("limits.max-members", -1);
            if (maxMembers >= 0 && db.getTownMembers(townId).size() >= maxMembers) {
                sendSync(sender, prefix + messagesConfig.getString(
                        "town.member.max-members-reached",
                        "&cThis town is full. Maximum members: &f{max}&c."
                ).replace("{max}", String.valueOf(maxMembers)));
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
            refreshTownSpawnHologram(townId, sender instanceof Player p ? p : null);

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
            refreshTownSpawnHologram(townId, sender instanceof Player p ? p : null);

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
            refreshTownSpawnHologram(townId, sender instanceof Player p ? p : null);

            if (target.isOnline()) {
                Player onlineTarget = target.getPlayer();
                if (onlineTarget != null) {
                    onlineTarget.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.newmayor")));
                }
            }
        });
    }
    public void giveRaidLockpick(CommandSender sender, String playerName, int amount) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null || !target.isOnline()) {
            sendSync(sender, prefix + messagesConfig.getString("adminmessages.playernotfound"));
            return;
        }

        ItemStack stack = plugin.getRaidConfigManager().createRaidLockpickItem(Math.max(1, amount));
        if (stack == null) {
            sendSync(sender, prefix + "&cFailed to create OzTowns raid lockpick item. Check raid.item settings.");
            return;
        }
        int itemAmount = stack.getAmount();

        var leftovers = target.getInventory().addItem(stack);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
        }

        String givenMessage = messagesConfig.getString("adminmessages.lockpickgiven",
                "&aGave &e{amount}x &araid lockpick to &f{player}&a.");
        String receivedMessage = messagesConfig.getString("adminmessages.lockpickreceived",
                "&aYou received &e{amount}x &araid lockpick.");

        sendSync(sender, prefix + givenMessage
                .replace("{player}", target.getName())
                .replace("{amount}", String.valueOf(itemAmount)));
        target.sendMessage(Utils.getColor(prefix + receivedMessage
                .replace("{amount}", String.valueOf(itemAmount))));
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
        double balance = townBankRepository.getTownBalance(townId);
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
        List<TownMember> members = db.getTownMembers(townId);
        for (TownMember member : members) {
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

    private void refreshTownSpawnHologram(int townId, Player contextPlayer) {
        if (!isHologramIntegrationEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Location spawn = db.getTownSpawn(townId);
            if (spawn == null || spawn.getWorld() == null) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> updateTownSpawnHologram(townId, spawn, contextPlayer));
        });
    }
}












