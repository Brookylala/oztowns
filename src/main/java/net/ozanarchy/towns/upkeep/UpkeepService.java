package net.ozanarchy.towns.upkeep;

import eu.decentsoftware.holograms.api.DHAPI;
import me.clip.placeholderapi.PlaceholderAPI;
import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.town.TownNotifier;
import net.ozanarchy.towns.town.listener.TownEvents;
import net.ozanarchy.towns.town.model.TownMember;
import net.ozanarchy.towns.town.tier.TownTier;
import net.ozanarchy.towns.town.tier.TownTierDefinition;
import net.ozanarchy.towns.town.tier.TownTierService;
import net.ozanarchy.towns.upkeep.repository.UpkeepRepository;
import net.ozanarchy.towns.util.DebugLogger;
import net.ozanarchy.towns.util.Utils;
import net.ozanarchy.towns.util.db.DatabaseHandler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static net.ozanarchy.towns.TownsPlugin.hologramsConfig;

public class UpkeepService {
    private final TownsPlugin plugin;
    private final DatabaseHandler databaseHandler;
    private final UpkeepRepository upkeepRepository;
    private final UpkeepConfigManager config;
    private final UpkeepCalculator calculator;
    private final UpkeepActivityService activityService;
    private final UpkeepDecayService decayService;
    private final TownNotifier townNotifier;
    private final TownTierService tierService;

    public UpkeepService(TownsPlugin plugin, DatabaseHandler databaseHandler, TownEvents townEvents, TownNotifier townNotifier) {
        this.plugin = plugin;
        this.databaseHandler = databaseHandler;
        this.upkeepRepository = new UpkeepRepository(plugin);
        this.config = new UpkeepConfigManager();
        this.calculator = new UpkeepCalculator(config);
        this.activityService = new UpkeepActivityService(config, databaseHandler);
        this.decayService = new UpkeepDecayService(plugin, config, upkeepRepository, databaseHandler, townEvents);
        this.townNotifier = townNotifier;
        this.tierService = plugin.getTownTierService();
    }

    public void processCycle() {
        if (!config.enabled()) {
            DebugLogger.debug(plugin, "Upkeep cycle skipped: upkeep.enabled=false");
            return;
        }

        List<UpkeepTownSnapshot> snapshots = upkeepRepository.loadSnapshots();
        DebugLogger.debug(plugin, "Upkeep cycle started. Snapshot count=" + snapshots.size());
        long nowMs = System.currentTimeMillis();
        long intervalMs = config.paymentIntervalMinutes() * 60_000L;

        for (UpkeepTownSnapshot snapshot : snapshots) {
            int townId = snapshot.townId();

            UpkeepActivityService.ActivityResult activity = activityService.evaluateActivity(townId);
            if (activity.newestSeenTimestamp() > 0) {
                upkeepRepository.setLastActivity(townId, new Timestamp(activity.newestSeenTimestamp()));
            }
            long inactiveMinutes = calculateInactiveMinutes(nowMs, activity.newestSeenTimestamp(), snapshot.lastActivityAt());

            boolean paymentDue = isPaymentDue(snapshot.lastPaymentAt(), nowMs, intervalMs);
            int unpaidCycles = snapshot.unpaidCycles();
            UpkeepState previous = snapshot.state();

            if (paymentDue) {
                if (config.autoPayFromBank()) {
                    double due = calculator.calculatePaymentDue(snapshot);
                    boolean paid = databaseHandler.withdrawTownMoney(townId, due);
                    if (paid) {
                        upkeepRepository.markPaymentSuccess(townId);
                        unpaidCycles = 0;
                        townNotifier.notifyTown(townId, plugin.getMessageService()
                                .format("upkeep.payment.success", Map.of("cost", String.valueOf(due))));
                        refreshTownSpawnHologram(townId, null);
                        applyTierProgressOnPayment(townId, activity.active());
                        DebugLogger.debug(plugin, "Upkeep paid: townId=" + townId + ", amount=" + due);
                    } else {
                        unpaidCycles = upkeepRepository.incrementUnpaidCycles(townId);
                        townNotifier.notifyTown(townId, plugin.getMessageService().get("upkeep.payment.overdue"));
                        applyTierDecayOnUnpaid(townId);
                        DebugLogger.debug(plugin, "Upkeep overdue: townId=" + townId + ", unpaidCycles=" + unpaidCycles);
                    }
                } else {
                    unpaidCycles = upkeepRepository.incrementUnpaidCycles(townId);
                    applyTierDecayOnUnpaid(townId);
                    DebugLogger.debug(plugin, "Upkeep not auto-paid: townId=" + townId + ", unpaidCycles=" + unpaidCycles);
                }
            }

            if (!activity.active()) {
                applyTierDecayOnInactivity(townId);
            }

            UpkeepState resolved = resolveState(unpaidCycles, inactiveMinutes);
            upkeepRepository.setUpkeepState(townId, resolved);
            if (resolved != previous) {
                DebugLogger.debug(plugin, "Upkeep state changed: townId=" + townId + ", " + previous + " -> " + resolved);
            }
            decayService.applyDecay(townId, resolved);
            maybeDowngradeTier(townId);
        }
    }

    private boolean isPaymentDue(Timestamp lastPayment, long nowMs, long intervalMs) {
        if (lastPayment == null) {
            return true;
        }
        return nowMs - lastPayment.getTime() >= intervalMs;
    }

    private long calculateInactiveMinutes(long nowMs, long newestActivityMs, Timestamp persistedActivity) {
        long reference = newestActivityMs;
        if (reference <= 0 && persistedActivity != null) {
            reference = persistedActivity.getTime();
        }
        if (reference <= 0) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, (nowMs - reference) / 60_000L);
    }

    private UpkeepState resolveState(int unpaidCycles, long inactiveMinutes) {
        if (unpaidCycles >= config.decayingUnpaidCycles() || inactiveMinutes >= config.decayingInactiveMinutes()) {
            return UpkeepState.DECAYING;
        }
        if (unpaidCycles >= config.abandonedUnpaidCycles() || inactiveMinutes >= config.abandonedInactiveMinutes()) {
            return UpkeepState.ABANDONED;
        }
        if (unpaidCycles >= config.neglectedUnpaidCycles() || inactiveMinutes >= config.neglectedInactiveMinutes()) {
            return UpkeepState.NEGLECTED;
        }
        if (unpaidCycles >= config.overdueUnpaidCycles()) {
            return UpkeepState.OVERDUE;
        }
        return UpkeepState.ACTIVE;
    }

    private void applyTierProgressOnPayment(int townId, boolean active) {
        if (tierService == null || !config.tierIntegrationEnabled()) {
            return;
        }
        if (config.requireActivityForProgress() && !active) {
            return;
        }
        tierService.increaseTierProgress(townId, config.progressPerSuccessfulPayment());
    }

    private void applyTierDecayOnUnpaid(int townId) {
        if (tierService == null || !config.tierIntegrationEnabled() || !config.loseProgressWhenUnpaid()) {
            return;
        }
        tierService.decreaseTierProgress(townId, config.progressLossPerUnpaidCycle());
    }

    private void applyTierDecayOnInactivity(int townId) {
        if (tierService == null || !config.tierIntegrationEnabled() || !config.loseProgressWhenInactive()) {
            return;
        }
        tierService.decreaseTierProgress(townId, config.progressLossPerInactiveCycle());
    }

    private void maybeDowngradeTier(int townId) {
        if (tierService == null || !config.tierIntegrationEnabled()) {
            return;
        }
        if (!config.downgradeEnabled() || !config.downgradeWhenProgressZero()) {
            return;
        }
        if (tierService.getTierProgress(townId) > 0.0) {
            return;
        }
        if (!tierService.canDowngradeTier(townId)) {
            return;
        }

        TownTier current = tierService.getTownTier(townId);
        TownTier target = TownTier.fromLevel(current.level() - 1, current);
        if (target == current) {
            return;
        }
        tierService.setTownTier(townId, target);
        if (config.resetProgressOnDowngrade()) {
            tierService.setTierProgress(townId, 0.0);
        }
    }

    private void refreshTownSpawnHologram(int townId, Player contextPlayer) {
        if (!isHologramIntegrationEnabled()) {
            return;
        }
        Location spawn = databaseHandler.getTownSpawn(townId);
        if (spawn == null || spawn.getWorld() == null) {
            return;
        }

        String idPrefix = hologramsConfig.getString("holograms.id-prefix", "oztowns_");
        String hologramId = idPrefix + "town_spawn_" + townId;
        String townName = databaseHandler.getTownName(townId);
        int memberCount = databaseHandler.getTownMembers(townId).size();
        double balance = databaseHandler.getTownBalance(townId);
        String mayorName = resolveMayorName(townId, contextPlayer != null ? contextPlayer.getName() : null);
        List<String> lines = renderHologramLines(townId, townName, mayorName, memberCount, balance, contextPlayer);
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
        List<TownMember> members = databaseHandler.getTownMembers(townId);
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

    public void schedule() {
        long intervalTicks = 20L * 60L * 5L;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::processCycle, 20L, intervalTicks);
    }
}
