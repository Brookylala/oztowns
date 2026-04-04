package net.ozanarchy.towns.raid;

import eu.decentsoftware.holograms.api.DHAPI;
import me.clip.placeholderapi.PlaceholderAPI;
import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.bank.repository.TownBankRepository;
import net.ozanarchy.towns.economy.EconomyProvider;
import net.ozanarchy.towns.town.model.TownMember;
import net.ozanarchy.towns.town.tier.TownTier;
import net.ozanarchy.towns.town.tier.TownTierDefinition;
import net.ozanarchy.towns.util.DebugLogger;
import net.ozanarchy.towns.util.Utils;
import net.ozanarchy.towns.util.db.DatabaseHandler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import static net.ozanarchy.towns.TownsPlugin.hologramsConfig;
import static net.ozanarchy.towns.TownsPlugin.messagesConfig;

public class RaidOutcomeHandler {
    private final TownsPlugin plugin;
    private final DatabaseHandler db;
    private final TownBankRepository townBankRepository;
    private final EconomyProvider economy;

    public RaidOutcomeHandler(TownsPlugin plugin, DatabaseHandler db, EconomyProvider economy) {
        this.plugin = plugin;
        this.db = db;
        this.townBankRepository = new TownBankRepository(plugin);
        this.economy = economy;
    }

    public void completeRaid(RaidExecutionContext context, RaidStateBehaviorDefinition behavior) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            double balance = townBankRepository.getTownBalance(context.targetTownId());
            double payout = computePayout(balance, behavior);
            boolean paidOut = payout > 0.0 && townBankRepository.withdrawTownMoney(context.targetTownId(), payout);
            DebugLogger.debug(plugin, "Raid outcome payout computed: townId=" + context.targetTownId()
                    + ", balance=" + balance
                    + ", payout=" + payout
                    + ", paidOut=" + paidOut
                    + ", mode=" + behavior.mode()
                    + ", payoutMode=" + behavior.payoutMode()
                    + ", payoutPercent=" + behavior.payoutPercent());

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin.getRaidConfigManager().playSuccessSound()) {
                    context.targetBlock().getWorld().playSound(
                            context.targetBlock().getLocation(),
                            plugin.getRaidConfigManager().successSound(),
                            plugin.getRaidConfigManager().successVolume(),
                            plugin.getRaidConfigManager().successPitch()
                    );
                }
            });

            int cooldownSeconds = plugin.getRaidConfigManager().raidCooldownSeconds();
            if (cooldownSeconds > 0) {
                long cooldownUntil = System.currentTimeMillis() + (cooldownSeconds * 1000L);
                db.setRaidCooldownUntilMillis(context.targetTownId(), cooldownUntil);
            }
            refreshTownSpawnHologram(context.targetTownId(), context.raider());

            if (context.raiderTownId() != null) {
                if (paidOut) {
                    townBankRepository.depositTownMoney(context.raiderTownId(), payout);
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!plugin.getRaidConfigManager().raidBroadcastEnabled()) {
                        return;
                    }
                    Bukkit.broadcastMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString("messages.raidbroadcast")
                            .replace("{player}", context.raider().getName())
                            .replace("{town}", context.targetTownName() != null ? context.targetTownName() : "Unknown")));
                });
            } else {
                if (paidOut) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        economy.add(context.raiderUuid(), payout);
                        context.raider().sendMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString("messages.raidsuccess")
                                .replace("{balance}", String.valueOf(payout))));
                    });
                } else {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            context.raider().sendMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString("messages.raidsuccessempty")))
                    );
                }
            }

            if (behavior.deleteTownOnSuccess()) {
                deleteTown(context.targetTownId());
                DebugLogger.debug(plugin, "Raid outcome deleted townId=" + context.targetTownId() + " after successful raid.");
            }

            if (behavior.mode() == RaidFlowType.CLEANUP) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        context.raider().sendMessage(Utils.getColor(Utils.prefix() + messagesConfig
                                .getString("raid.cleanup.success", "&aCleanup complete.")))
                );
            }

            Bukkit.getScheduler().runTask(plugin, plugin::reloadChunkCache);
        });
    }

    private double computePayout(double balance, RaidStateBehaviorDefinition behavior) {
        if (balance <= 0) {
            return 0.0;
        }
        if (behavior.payoutMode() == RaidPayoutMode.FULL) {
            return balance;
        }
        double percent = Math.max(0.0, behavior.payoutPercent());
        return Math.min(balance, balance * percent);
    }

    private void deleteTown(int townId) {
        db.deleteClaim(townId);
        db.deleteMembers(townId);
        townBankRepository.deleteTownBank(townId);
        db.deleteTown(townId);
    }

    private void refreshTownSpawnHologram(int townId, Player contextPlayer) {
        if (!isHologramIntegrationEnabled()) {
            return;
        }
        Location spawn = db.getTownSpawn(townId);
        if (spawn == null || spawn.getWorld() == null) {
            return;
        }

        String idPrefix = hologramsConfig.getString("holograms.id-prefix", "oztowns_");
        String hologramId = idPrefix + "town_spawn_" + townId;
        String townName = db.getTownName(townId);
        int memberCount = db.getTownMembers(townId).size();
        double liveBalance = townBankRepository.getTownBalance(townId);
        String mayorName = resolveMayorName(townId, contextPlayer != null ? contextPlayer.getName() : null);
        List<String> lines = renderHologramLines(townId, townName, mayorName, memberCount, liveBalance, contextPlayer);
        if (lines.isEmpty()) {
            return;
        }

        double yOffset = hologramsConfig.getDouble("holograms.y-offset", 2.25D);
        Location hologramLoc = spawn.getBlock().getLocation().add(0.5D, yOffset, 0.5D);
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                DHAPI.removeHologram(hologramId);
                DHAPI.createHologram(hologramId, hologramLoc, true, lines);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Failed to refresh town hologram for town id " + townId + ": " + ex.getMessage());
            }
        });
    }

    private boolean isHologramIntegrationEnabled() {
        FileConfiguration cfg = hologramsConfig;
        return cfg != null
                && cfg.getBoolean("holograms.enabled", true)
                && Bukkit.getPluginManager().isPluginEnabled("DecentHolograms");
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

    private List<String> renderHologramLines(int townId, String townName, String mayorName, int memberCount, double balance, Player contextPlayer) {
        List<String> templateLines = hologramsConfig.getStringList("holograms.string-lines");
        List<String> rendered = new ArrayList<>();
        DecimalFormat money = new DecimalFormat("0.##");
        String tierDisplayName = "Tier 1";
        String tierLevel = "1";
        if (plugin.getTownTierService() != null) {
            TownTier tier = plugin.getTownTierService().getTownTier(townId);
            TownTierDefinition definition = plugin.getTownTierService().getDefinition(tier);
            tierDisplayName = definition.displayName();
            tierLevel = String.valueOf(tier.level());
        }

        for (String line : templateLines) {
            String value = line
                    .replace("{townname}", townName == null ? "Unknown" : townName)
                    .replace("{town-name}", townName == null ? "Unknown" : townName)
                    .replace("{mayor-name}", mayorName)
                    .replace("{mayor}", mayorName)
                    .replace("{town-member-count}", String.valueOf(memberCount))
                    .replace("{member-count}", String.valueOf(memberCount))
                    .replace("{balance}", money.format(balance))
                    .replace("{tier-name}", tierDisplayName)
                    .replace("{tier-level}", tierLevel)
                    .replace("{tier-value}", tierLevel);
            if (contextPlayer != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                value = PlaceholderAPI.setPlaceholders(contextPlayer, value);
            }
            rendered.add(Utils.getColor(value));
        }
        return rendered;
    }
}

