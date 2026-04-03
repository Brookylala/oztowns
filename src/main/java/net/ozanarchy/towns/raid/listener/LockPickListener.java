package net.ozanarchy.towns.raid.listener;

import net.ozanarchy.towns.town.model.TownMember;
import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.bank.repository.TownBankRepository;
import net.ozanarchy.towns.economy.EconomyProvider;
import net.ozanarchy.towns.integration.ominous.OminousChestLockCompat;
import net.ozanarchy.towns.raid.RaidConfigManager;
import net.ozanarchy.towns.raid.RaidItemMatcher;
import net.ozanarchy.towns.util.Utils;
import net.ozanarchy.towns.util.db.DatabaseHandler;

import eu.decentsoftware.holograms.api.DHAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.ozanarchy.towns.TownsPlugin.hologramsConfig;
import static net.ozanarchy.towns.TownsPlugin.messagesConfig;

public class LockPickListener implements Listener {
    private final TownsPlugin plugin;
    private final DatabaseHandler db;
    private final TownBankRepository townBankRepository;
    private final EconomyProvider economy;
    private final RaidConfigManager raidConfig;
    private final Map<UUID, BukkitTask> activeRaids = new HashMap<>();
    private final Map<UUID, BossBar> raidBossBars = new HashMap<>();
    private final Map<UUID, BossBar> defenderRaidBossBars = new HashMap<>();

    public LockPickListener(TownsPlugin plugin, DatabaseHandler db, EconomyProvider economy, RaidConfigManager raidConfig) {
        this.plugin = plugin;
        this.db = db;
        this.townBankRepository = new TownBankRepository(plugin);
        this.economy = economy;
        this.raidConfig = raidConfig;
    }

    /**
     * Handles successful lockpicking of a town Lodestone (spawn point).
     * This event triggers a town raid, rewarding the raider and taking over the town after a delay.
     */
    public boolean registerLockPickHook() {
        return OminousChestLockCompat.registerLockPickSuccessListener(
                plugin,
                this,
                (listener, event) -> onLockPickSuccess(event)
        );
    }

    private void onLockPickSuccess(Event event) {
        Block block = OminousChestLockCompat.getEventBlock(event);
        Player player = OminousChestLockCompat.getEventPlayer(event);
        if (block == null || player == null) return;
        int raidDuration = raidConfig.raidDurationSeconds();

        // Raids only target Lodestones
        if (block.getType() != Material.LODESTONE) return;

        if (!raidConfig.isRaidingAllowed()) {
            OminousChestLockCompat.setCancelled(event, true);
            player.sendMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString("messages.raidingdisabled")));
            return;
        }

        if (!raidConfig.isWorldAllowed(block.getWorld().getName())) {
            OminousChestLockCompat.setCancelled(event, true);
            return;
        }

        ItemStack usedItem = OminousChestLockCompat.getEventItem(event, player);
        if (!RaidItemMatcher.matches(plugin, raidConfig.raidItemDefinition(), usedItem)) {
            OminousChestLockCompat.setCancelled(event, true);
            return;
        }

        UUID raiderUuid = player.getUniqueId();

        if (activeRaids.containsKey(raiderUuid)) {
            OminousChestLockCompat.setCancelled(event, true);
            return;
        }

        // Keep the lock in place while the raid timer is active.
        OminousChestLockCompat.setCancelled(event, true);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getChunkTownId(block.getChunk());
            if (townId == null) {
                return;
            }

            // Verify if the clicked Lodestone is indeed the town's spawn
            Location spawnLoc = db.getTownSpawn(townId);
            if (spawnLoc == null || !spawnLoc.getBlock().equals(block)) {
                return;
            }

            // This is a town Lodestone! Check if the player is in a town
            Integer raiderTownId = db.getPlayerTownId(raiderUuid);

            String townName = db.getTownName(townId);
            String raiderTownName = raiderTownId != null ? db.getTownName(raiderTownId) : null;
            List<UUID> raidedTownMemberUuids = db.getTownMembers(townId).stream()
                    .map(TownMember::getUuid)
                    .filter(uuid -> !uuid.equals(raiderUuid))
                    .toList();

            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString("messages.raidtimer").replace("{seconds}", String.valueOf(raidDuration))));
                String title = Utils.getColor(messagesConfig.getString("messages.raidtimerbar").replace("{seconds}", String.valueOf(raidDuration)));
                String defenderTitle = Utils.getColor(messagesConfig.getString("messages.raiddefenderbar", "&cRaid by &f{player}&c: &f{seconds}s &cremaining")
                        .replace("{player}", player.getName())
                        .replace("{seconds}", String.valueOf(raidDuration)));
                BarColor color = raidConfig.raidBarColor();
                BarStyle style = raidConfig.raidBarStyle();
                BossBar bossBar = Bukkit.createBossBar(title, color, style);
                BossBar defenderBossBar = Bukkit.createBossBar(defenderTitle, color, style);
                bossBar.addPlayer(player);
                bossBar.setProgress(1.0);
                defenderBossBar.setProgress(1.0);

                for (UUID memberUuid : raidedTownMemberUuids) {
                    Player member = Bukkit.getPlayer(memberUuid);
                    if (member != null && member.isOnline()) {
                        defenderBossBar.addPlayer(member);
                    }
                }

                raidBossBars.put(raiderUuid, bossBar);
                defenderRaidBossBars.put(raiderUuid, defenderBossBar);

                new BukkitRunnable() {
                    int secondsLeft = raidDuration;
                    @Override
                    public void run() {
                        if (!activeRaids.containsKey(raiderUuid)) {
                            bossBar.removeAll();
                            defenderBossBar.removeAll();
                            raidBossBars.remove(raiderUuid);
                            defenderRaidBossBars.remove(raiderUuid);
                            this.cancel();
                            return;
                        }
                        secondsLeft--;
                        if (secondsLeft <= 0) {
                            bossBar.removeAll();
                            defenderBossBar.removeAll();
                            raidBossBars.remove(raiderUuid);
                            defenderRaidBossBars.remove(raiderUuid);
                            this.cancel();
                            return;
                        }
                        bossBar.setTitle(Utils.getColor(messagesConfig.getString("messages.raidtimerbar").replace("{seconds}", String.valueOf(secondsLeft))));
                        bossBar.setProgress(secondsLeft / (double) raidDuration);
                        defenderBossBar.setTitle(Utils.getColor(messagesConfig.getString("messages.raiddefenderbar", "&cRaid by &f{player}&c: &f{seconds}s &cremaining")
                                .replace("{player}", player.getName())
                                .replace("{seconds}", String.valueOf(secondsLeft))));
                        defenderBossBar.setProgress(secondsLeft / (double) raidDuration);
                    }
                }.runTaskTimer(plugin, 20L, 20L);
            });

            BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                activeRaids.remove(raiderUuid);
                BossBar raiderBar = raidBossBars.remove(raiderUuid);
                if (raiderBar != null) raiderBar.removeAll();
                BossBar defenderBar = defenderRaidBossBars.remove(raiderUuid);
                if (defenderBar != null) defenderBar.removeAll();
                completeTakeover(player, townId, townName, raiderTownId, raiderTownName, block);
            }, 20L * raidDuration);

            activeRaids.put(raiderUuid, task);
        });
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent e) {
        if (!raidConfig.failOnDamage()) return;
        if (!(e.getEntity() instanceof Player player)) return;
        failRaid(player);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (!raidConfig.failOnDeath()) return;
        failRaid(e.getEntity());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        if (!raidConfig.failOnDisconnect()) return;
        failRaid(e.getPlayer(), false);
    }

    private void failRaid(Player player) {
        failRaid(player, true);
    }

    private void failRaid(Player player, boolean notifyPlayer) {
        UUID uuid = player.getUniqueId();
        BukkitTask task = activeRaids.remove(uuid);
        if (task == null) return;

        task.cancel();
        BossBar bar = raidBossBars.remove(uuid);
        if (bar != null) bar.removeAll();
        BossBar defenderBar = defenderRaidBossBars.remove(uuid);
        if (defenderBar != null) defenderBar.removeAll();

        if (notifyPlayer) {
            player.sendMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString("messages.raidfailed")));
        }
    }

    private void completeTakeover(Player player, int raidedTownId, String raidedTownName, Integer raiderTownId, String raiderTownName, Block block) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            double balance = townBankRepository.getTownBalance(raidedTownId);

            Bukkit.getScheduler().runTask(plugin, () -> {
                block.setType(Material.AIR);
                block.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, block.getLocation().add(0.5, 0.5, 0.5), 50, 0.3, 0.3, 0.3, 0.1, Material.LODESTONE.createBlockData());
                if (raidConfig.playSuccessSound()) {
                    block.getWorld().playSound(block.getLocation(), raidConfig.successSound(), raidConfig.successVolume(), raidConfig.successPitch());
                }
            });

            db.updateTownSpawn(raidedTownId, null, 0, 0, 0);
            removeTownSpawnHologram(raidedTownId);
            
            if(raiderTownId != null){
                townBankRepository.transferBankBalance(raidedTownId, raiderTownId);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!raidConfig.raidBroadcastEnabled()) return;
                    Bukkit.broadcastMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString("messages.raidbroadcast")
                        .replace("{player}", player.getName())
                        .replace("{town}", (raidedTownName != null ? raidedTownName : "Unknown"))));
                });
            } else {
                if (balance > 0) {
                    townBankRepository.withdrawTownMoney(raidedTownId, balance);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        economy.add(player.getUniqueId(), balance);
                        player.sendMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString("messages.raidsuccess").replace("{balance}", String.valueOf(balance))));
                    });   
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString("messages.raidsuccessempty")));
                    });
                }
            }
            
            db.deleteClaim(raidedTownId);
            db.deleteMembers(raidedTownId);
            townBankRepository.deleteTownBank(raidedTownId);
            db.deleteTown(raidedTownId);
               
            // Reload chunk cache on next tick
            Bukkit.getScheduler().runTask(plugin, plugin::reloadChunkCache);
        });
    }

    private void removeTownSpawnHologram(int townId) {
        if (hologramsConfig == null
                || !hologramsConfig.getBoolean("holograms.enabled", true)
                || !Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
            return;
        }
        String idPrefix = hologramsConfig.getString("holograms.id-prefix", "oztowns_");
        DHAPI.removeHologram(idPrefix + "town_spawn_" + townId);
    }
}










