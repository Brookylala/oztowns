package net.ozanarchy.towns.raid;

import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.economy.EconomyProvider;
import net.ozanarchy.towns.raid.minigame.MinigameResult;
import net.ozanarchy.towns.raid.minigame.RaidMinigameService;
import net.ozanarchy.towns.town.model.TownMember;
import net.ozanarchy.towns.town.tier.TownTier;
import net.ozanarchy.towns.upkeep.UpkeepState;
import net.ozanarchy.towns.upkeep.repository.UpkeepRepository;
import net.ozanarchy.towns.util.DebugLogger;
import net.ozanarchy.towns.util.Utils;
import net.ozanarchy.towns.util.db.DatabaseHandler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.ozanarchy.towns.TownsPlugin.messagesConfig;

public class RaidService {
    private final TownsPlugin plugin;
    private final DatabaseHandler db;
    private final RaidConfigManager raidConfig;
    private final UpkeepRepository upkeepRepository;
    private final RaidOutcomeHandler raidOutcomeHandler;
    private final CleanupRaidHandler cleanupRaidHandler;
    private final RaidMinigameService minigameService;

    private final Map<UUID, RaidSession> activeRaids = new HashMap<>();

    public RaidService(TownsPlugin plugin, DatabaseHandler db, EconomyProvider economy, RaidConfigManager raidConfig) {
        this.plugin = plugin;
        this.db = db;
        this.raidConfig = raidConfig;
        this.upkeepRepository = new UpkeepRepository(plugin);
        this.raidOutcomeHandler = new RaidOutcomeHandler(plugin, db, economy);
        this.cleanupRaidHandler = new CleanupRaidHandler(plugin, raidOutcomeHandler);
        this.minigameService = new RaidMinigameService(plugin, raidConfig);
    }

    public RaidMinigameService minigameService() {
        return minigameService;
    }

    public RaidConfigManager raidConfig() {
        return raidConfig;
    }

    public boolean isRaidLockpick(ItemStack item) {
        RaidItemDefinition def = raidConfig.raidItemDefinition();
        return def.enabled() && RaidItemMatcher.matches(plugin, def, item);
    }

    public void handleRaidEntryAttempt(Player player, org.bukkit.block.Block block, ItemStack usedItem) {
        if (block == null || player == null || block.getType() != Material.LODESTONE) {
            return;
        }

        if (!raidConfig.isRaidingAllowed()) {
            player.sendMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString("messages.raidingdisabled")));
            return;
        }

        if (!raidConfig.isWorldAllowed(block.getWorld().getName())) {
            return;
        }

        if (!isRaidLockpick(usedItem)) {
            return;
        }

        UUID raiderUuid = player.getUniqueId();
        if (activeRaids.containsKey(raiderUuid) || minigameService.hasSession(raiderUuid)) {
            player.sendMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString(
                    "raid.minigame.messages.already-active",
                    "&cYou already have an active raid entry attempt."
            )));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer targetTownId = db.getChunkTownId(block.getChunk());
            if (targetTownId == null) {
                return;
            }

            Location spawnLoc = db.getTownSpawn(targetTownId);
            if (spawnLoc == null || !spawnLoc.getBlock().equals(block)) {
                return;
            }

            long cooldownUntil = db.getRaidCooldownUntilMillis(targetTownId);
            long remainingSeconds = cooldownUntil <= 0L
                    ? 0L
                    : (long) Math.ceil((cooldownUntil - System.currentTimeMillis()) / 1000.0D);
            if (remainingSeconds > 0L) {
                long finalRemainingSeconds = remainingSeconds;
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(Utils.getColor(Utils.prefix() + raidConfig.raidCooldownMessage()
                                .replace("{seconds}", String.valueOf(finalRemainingSeconds))))
                );
                return;
            }

            Integer raiderTownId = db.getPlayerTownId(raiderUuid);
            if (raiderTownId != null && raiderTownId.equals(targetTownId)) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString("raid.general.cannot-raid-own-town")))
                );
                return;
            }

            UpkeepState upkeepState = upkeepRepository.getUpkeepState(targetTownId);
            RaidStateBehaviorDefinition behavior = raidConfig.stateBehavior(upkeepState);

            String targetTownName = db.getTownName(targetTownId);
            String raiderTownName = raiderTownId != null ? db.getTownName(raiderTownId) : null;
            List<UUID> defenderUuids = db.getTownMembers(targetTownId).stream()
                    .map(TownMember::getUuid)
                    .filter(uuid -> !uuid.equals(raiderUuid))
                    .toList();

            RaidExecutionContext context = new RaidExecutionContext(
                    player,
                    raiderUuid,
                    block,
                    targetTownId,
                    targetTownName,
                    raiderTownId,
                    raiderTownName,
                    defenderUuids
            );

            TownTier targetTier = TownTier.TIER_1;
            if (plugin.getTownTierService() != null) {
                targetTier = plugin.getTownTierService().getTownTier(targetTownId);
            }
            RaidMinigameSettings settings = raidConfig.minigameSettings(targetTier);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!consumeOneLockpick(player, usedItem)) {
                    player.sendMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString(
                            "raid.minigame.messages.item-missing",
                            "&cYou must hold the raid lockpick to start this breach."
                    )));
                    return;
                }

                if (behavior.mode() == RaidFlowType.CLEANUP || !settings.enabled()) {
                    startRaidAfterBreach(context, behavior);
                    return;
                }

                minigameService.startSession(player, context, behavior, settings, result -> {
                    if (result == MinigameResult.SUCCESS) {
                        startRaidAfterBreach(context, behavior);
                        return;
                    }
                    if (result == MinigameResult.TIMEOUT) {
                        player.sendMessage(Utils.getColor(Utils.prefix() + settings.timeoutMessage()));
                        return;
                    }
                    if (result == MinigameResult.CANCELLED) {
                        player.sendMessage(Utils.getColor(Utils.prefix() + settings.cancelledMessage()));
                        return;
                    }
                    player.sendMessage(Utils.getColor(Utils.prefix() + settings.failMessage()));
                });
            });
        });
    }

    private boolean consumeOneLockpick(Player player, ItemStack usedItem) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR || hand.getAmount() < 1) {
            return false;
        }
        if (!RaidItemMatcher.matches(plugin, raidConfig.raidItemDefinition(), hand)) {
            return false;
        }
        hand.setAmount(hand.getAmount() - 1);
        player.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);
        player.updateInventory();
        return true;
    }

    private void startRaidAfterBreach(RaidExecutionContext context, RaidStateBehaviorDefinition behavior) {
        DebugLogger.debug(plugin, "Raid breach success: raider=" + context.raider().getName()
                + ", targetTownId=" + context.targetTownId()
                + ", mode=" + behavior.mode());

        if (behavior.mode() == RaidFlowType.CLEANUP) {
            context.raider().sendMessage(Utils.getColor(Utils.prefix() + messagesConfig
                    .getString("raid.cleanup.start", "&6&lCLEANUP RAID: &7Cleanup completes in &e{seconds}&7s.")
                    .replace("{seconds}", String.valueOf(Math.max(1, behavior.timerSeconds())))));
            cleanupRaidHandler.executeCleanupFlow(context, behavior);
            return;
        }
        startNormalRaid(context, behavior);
    }

    private void startNormalRaid(RaidExecutionContext context, RaidStateBehaviorDefinition behavior) {
        int duration = Math.max(1, behavior.timerSeconds());
        UUID raiderUuid = context.raiderUuid();

        BarColor color = raidConfig.raidBarColor();
        BarStyle style = raidConfig.raidBarStyle();

        BossBar raiderBar = null;
        BossBar defenderBar = null;
        BukkitTask tickerTask = null;

        if (behavior.alertsEnabled()) {
            context.raider().sendMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString("messages.raidtimer")
                    .replace("{seconds}", String.valueOf(duration))));

            String title = Utils.getColor(messagesConfig.getString("messages.raidtimerbar")
                    .replace("{seconds}", String.valueOf(duration)));
            String defenderTitle = Utils.getColor(messagesConfig.getString("messages.raiddefenderbar", "&cRaid by &f{player}&c: &f{seconds}s &cremaining")
                    .replace("{player}", context.raider().getName())
                    .replace("{seconds}", String.valueOf(duration)));

            raiderBar = Bukkit.createBossBar(title, color, style);
            defenderBar = Bukkit.createBossBar(defenderTitle, color, style);
            raiderBar.addPlayer(context.raider());
            raiderBar.setProgress(1.0);
            defenderBar.setProgress(1.0);

            for (UUID memberUuid : context.defenderUuids()) {
                Player member = Bukkit.getPlayer(memberUuid);
                if (member != null && member.isOnline()) {
                    defenderBar.addPlayer(member);
                }
            }

            BossBar finalRaiderBar = raiderBar;
            BossBar finalDefenderBar = defenderBar;
            tickerTask = new BukkitRunnable() {
                int secondsLeft = duration;

                @Override
                public void run() {
                    RaidSession session = activeRaids.get(raiderUuid);
                    if (session == null) {
                        if (finalRaiderBar != null) finalRaiderBar.removeAll();
                        if (finalDefenderBar != null) finalDefenderBar.removeAll();
                        cancel();
                        return;
                    }

                    secondsLeft--;
                    if (secondsLeft <= 0) {
                        if (finalRaiderBar != null) finalRaiderBar.removeAll();
                        if (finalDefenderBar != null) finalDefenderBar.removeAll();
                        cancel();
                        return;
                    }

                    if (finalRaiderBar != null) {
                        finalRaiderBar.setTitle(Utils.getColor(messagesConfig.getString("messages.raidtimerbar")
                                .replace("{seconds}", String.valueOf(secondsLeft))));
                        finalRaiderBar.setProgress(secondsLeft / (double) duration);
                    }

                    if (finalDefenderBar != null) {
                        finalDefenderBar.setTitle(Utils.getColor(messagesConfig.getString("messages.raiddefenderbar", "&cRaid by &f{player}&c: &f{seconds}s &cremaining")
                                .replace("{player}", context.raider().getName())
                                .replace("{seconds}", String.valueOf(secondsLeft))));
                        finalDefenderBar.setProgress(secondsLeft / (double) duration);
                    }
                }
            }.runTaskTimer(plugin, 20L, 20L);
        }

        BossBar finalRaiderBar = raiderBar;
        BossBar finalDefenderBar = defenderBar;
        BukkitTask completionTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            RaidSession session = activeRaids.remove(raiderUuid);
            if (session == null) {
                return;
            }
            cleanupBars(session.raiderBar(), session.defenderBar());
            raidOutcomeHandler.completeRaid(context, behavior);
            DebugLogger.debug(plugin, "Raid completed successfully for townId=" + context.targetTownId() + " by " + context.raider().getName());
        }, duration * 20L);

        activeRaids.put(raiderUuid, new RaidSession(completionTask, tickerTask, finalRaiderBar, finalDefenderBar));
    }

    public void failRaid(Player player, boolean notifyPlayer) {
        failRaid(player, notifyPlayer, "messages.raidfailed");
    }

    public void failRaid(Player player, boolean notifyPlayer, String messageKey) {
        UUID uuid = player.getUniqueId();
        RaidSession session = activeRaids.remove(uuid);
        if (session == null) {
            return;
        }

        session.completionTask().cancel();
        if (session.tickerTask() != null) {
            session.tickerTask().cancel();
        }
        cleanupBars(session.raiderBar(), session.defenderBar());

        if (notifyPlayer) {
            String message = messagesConfig.getString(messageKey, "&cRaid failed.");
            player.sendMessage(Utils.getColor(Utils.prefix() + message));
        }
        DebugLogger.debug(plugin, "Raid failed for player=" + player.getName() + ", notifyPlayer=" + notifyPlayer + ", messageKey=" + messageKey);
    }

    private void cleanupBars(BossBar raiderBar, BossBar defenderBar) {
        if (raiderBar != null) {
            raiderBar.removeAll();
        }
        if (defenderBar != null) {
            defenderBar.removeAll();
        }
    }

    private record RaidSession(BukkitTask completionTask, BukkitTask tickerTask, BossBar raiderBar, BossBar defenderBar) {
    }
}







