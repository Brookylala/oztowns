package net.ozanarchy.towns.events;

import eu.decentsoftware.holograms.api.DHAPI;
import net.ozanarchy.chestlock.events.LockPickSuccessEvent;
import net.ozanarchy.towns.economy.EconomyProvider;
import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.handlers.DatabaseHandler;
import net.ozanarchy.towns.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.ozanarchy.towns.TownsPlugin.hologramsConfig;
import static net.ozanarchy.towns.TownsPlugin.messagesConfig;

public class LockPickListener implements Listener {
    private final TownsPlugin plugin;
    private final DatabaseHandler db;
    private final EconomyProvider economy;
    private final Map<UUID, BukkitTask> activeRaids = new HashMap<>();
    private final Map<UUID, BossBar> raidBossBars = new HashMap<>();
    private final Map<UUID, BossBar> defenderRaidBossBars = new HashMap<>();

    public LockPickListener(TownsPlugin plugin, DatabaseHandler db, EconomyProvider economy) {
        this.plugin = plugin;
        this.db = db;
        this.economy = economy;
    }

    /**
     * Handles successful lockpicking of a town Lodestone (spawn point).
     * This event triggers a town raid, rewarding the raider and taking over the town after a delay.
     */
    @EventHandler
    public void onLockPick(LockPickSuccessEvent e) {
        Block block = e.getBlock();
        // Raids only target Lodestones
        if (block.getType() != Material.LODESTONE) return;

        if (!TownsPlugin.config.getBoolean("raiding-allowed", true)) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString("messages.raidingdisabled")));
            return;
        }

        Player player = e.getPlayer();
        UUID raiderUuid = player.getUniqueId();

        if (activeRaids.containsKey(raiderUuid)) {
            e.setCancelled(true);
            return;
        }

        // Keep the lock in place while the raid timer is active.
        e.setCancelled(true);

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
                    .map(DatabaseHandler.TownMember::getUuid)
                    .filter(uuid -> !uuid.equals(raiderUuid))
                    .toList();

            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString("messages.raidtimer").replace("{seconds}", "60")));
                String title = Utils.getColor(messagesConfig.getString("messages.raidtimerbar").replace("{seconds}", "60"));
                String defenderTitle = Utils.getColor(messagesConfig
                        .getString("messages.raiddefenderbar", "&cRaid by &f{player}&c: &f{seconds}s &cremaining")
                        .replace("{player}", player.getName())
                        .replace("{seconds}", "60"));
                BarColor color = BarColor.valueOf(TownsPlugin.config.getString("bossbar.raid.color", "RED"));
                BarStyle style = BarStyle.valueOf(TownsPlugin.config.getString("bossbar.raid.style", "SEGMENTED_10"));
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
                    int secondsLeft = 60;
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
                        bossBar.setProgress(secondsLeft / 60.0);
                        defenderBossBar.setTitle(Utils.getColor(messagesConfig
                                .getString("messages.raiddefenderbar", "&cRaid by &f{player}&c: &f{seconds}s &cremaining")
                                .replace("{player}", player.getName())
                                .replace("{seconds}", String.valueOf(secondsLeft))));
                        defenderBossBar.setProgress(secondsLeft / 60.0);
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
            }, 20 * 60L);

            activeRaids.put(raiderUuid, task);
        });
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        failRaid(player);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        failRaid(e.getEntity());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
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
            double balance = db.getTownBalance(raidedTownId);

            Bukkit.getScheduler().runTask(plugin, () -> {
                block.setType(Material.AIR);
                block.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, block.getLocation().add(0.5, 0.5, 0.5), 50, 0.3, 0.3, 0.3, 0.1, Material.LODESTONE.createBlockData());
                block.getWorld().playSound(block.getLocation(), org.bukkit.Sound.BLOCK_STONE_BREAK, 1.0f, 1.0f);
            });

            db.updateTownSpawn(raidedTownId, null, 0, 0, 0);
            removeTownSpawnHologram(raidedTownId);
            
            if(raiderTownId != null){
                db.transferBankBalance(raidedTownId, raiderTownId);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Bukkit.broadcastMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString("messages.raidbroadcast")
                        .replace("{player}", player.getName())
                        .replace("{town}", (raidedTownName != null ? raidedTownName : "Unknown"))));
                });
            } else {
                if (balance > 0) {
                    db.withdrawTownMoney(raidedTownId, balance);
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
            db.deleteTownBank(raidedTownId);
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
