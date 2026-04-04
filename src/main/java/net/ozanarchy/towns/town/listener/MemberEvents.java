package net.ozanarchy.towns.town.listener;

import net.ozanarchy.towns.town.model.TownMember;
import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.bank.repository.TownBankRepository;
import net.ozanarchy.towns.economy.EconomyProvider;
import net.ozanarchy.towns.town.claim.ChunkHandler;
import net.ozanarchy.towns.town.permission.PermissionManager;
import net.ozanarchy.towns.town.tier.TownTier;
import net.ozanarchy.towns.town.tier.TownTierDefinition;
import net.ozanarchy.towns.upkeep.repository.UpkeepRepository;
import net.ozanarchy.towns.util.ChunkVisuals;
import net.ozanarchy.towns.util.Utils;
import net.ozanarchy.towns.util.db.DatabaseHandler;

import eu.decentsoftware.holograms.api.DHAPI;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.ozanarchy.towns.TownsPlugin.hologramsConfig;
import static net.ozanarchy.towns.TownsPlugin.townConfig;
import static net.ozanarchy.towns.TownsPlugin.upkeepConfig;
import static net.ozanarchy.towns.TownsPlugin.messagesConfig;

public class MemberEvents implements Listener {
    private static final long INVITE_TIMEOUT_SECONDS = 30L;

    private static class TownInvite {
        private final UUID inviterUuid;
        private final int townId;
        private final long expiresAt;
        private final BukkitTask timeoutTask;

        private TownInvite(UUID inviterUuid, int townId, long expiresAt, BukkitTask timeoutTask) {
            this.inviterUuid = inviterUuid;
            this.townId = townId;
            this.expiresAt = expiresAt;
            this.timeoutTask = timeoutTask;
        }
    }

    private final DatabaseHandler db;
    private final PermissionManager permissionManager;
    private final TownsPlugin plugin;
    private final EconomyProvider economy;
    private final TownBankRepository townBankRepository;
    private final UpkeepRepository upkeepRepository;
    private final ChunkHandler chunkCache;
    private final Map<UUID, TownInvite> pendingInvites = new ConcurrentHashMap<>();
    private final String prefix = Utils.prefix();
    private final String incorrectUsage = messagesConfig.getString("messages.incorrectusage");

    public MemberEvents(DatabaseHandler data, PermissionManager permissionManager, TownsPlugin plugin, EconomyProvider economy, ChunkHandler chunkCache){
        this.db = data;
        this.permissionManager = permissionManager;
        this.plugin = plugin;
        this.economy = economy;
        this.townBankRepository = new TownBankRepository(plugin);
        this.upkeepRepository = new UpkeepRepository(plugin);
        this.chunkCache = chunkCache;
    }

    // ==========================================
    // MEMBER ADD / REMOVE
    // ==========================================

    /**
     * Sends a town invite to a player.
     */
    public void addMember(Player requester, String[] args){
        if(args.length < 2){
            requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.inviteusage", incorrectUsage)));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null){
            requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.playernotfound")));
            return;
        }

        UUID requesterUUID = requester.getUniqueId();
        UUID targetUUID = target.getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
           Integer townId = db.getPlayerTownId(requesterUUID);

           if (townId == null){
               Bukkit.getScheduler().runTask(plugin, () -> {
                   requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notown")));
               });
               return;
           }

           // Check if player has permission to invite
           boolean canInvite = db.isTownAdmin(requesterUUID, townId) || permissionManager.getPermissionSync(townId, requesterUUID, "CAN_INVITE");
           if (!canInvite) {
               Bukkit.getScheduler().runTask(plugin, () -> {
                   requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.nopermission")));
               });
               return;
           }

           if (db.getPlayerTownId(targetUUID) != null){
               Bukkit.getScheduler().runTask(plugin, () -> {
                   requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.playeralreadyintown")));
               });
               return;
           }

           if (isTownAtMemberCap(townId)) {
               Bukkit.getScheduler().runTask(plugin, () -> {
                   requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString(
                           "town.member.max-members-reached",
                           "&cYour town is full. Maximum members: &f{max}&c."
                   ).replace("{max}", String.valueOf(maxTownMembers()))));
               });
               return;
           }

           String townName = db.getTownName(townId);
           Bukkit.getScheduler().runTask(plugin, () -> sendInvite(requester, target, townId, townName));
        });
    }

    /**
     * Accepts a pending town invite.
     */
    public void acceptInvite(Player target) {
        UUID targetUUID = target.getUniqueId();
        TownInvite invite = pendingInvites.remove(targetUUID);

        if (invite == null || invite.expiresAt < System.currentTimeMillis()) {
            target.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.nopendinginvite")));
            return;
        }

        invite.timeoutTask.cancel();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (db.getPlayerTownId(targetUUID) != null) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        target.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.playeralreadyintown"))));
                return;
            }

            Integer inviterTown = db.getPlayerTownId(invite.inviterUuid);
            if (inviterTown == null || inviterTown != invite.townId || !db.isTownAdmin(invite.inviterUuid, invite.townId)) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        target.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.inviteinvalid"))));
                return;
            }

            if (isTownAtMemberCap(invite.townId)) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        target.sendMessage(Utils.getColor(prefix + messagesConfig.getString(
                                "town.member.max-members-reached",
                                "&cThis town is full. Maximum members: &f{max}&c."
                        ).replace("{max}", String.valueOf(maxTownMembers())))));
                return;
            }

            boolean success = db.addMember(invite.townId, targetUUID, "MEMBER");
            String townName = db.getTownName(invite.townId);

            if (success){
                upkeepRepository.increaseUpkeep(inviterTown, upkeepConfig.getDouble("payment.costs.member.added", 5.0));
                refreshTownSpawnHologram(invite.townId, target);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player inviter = Bukkit.getPlayer(invite.inviterUuid);
                        target.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.inviteacceptedtarget")
                            .replace("{town}", townName != null ? townName : "Unknown")));
                        if (inviter != null) {
                            inviter.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.inviteacceptedrequester")
                                .replace("{player}", target.getName())));
                        }
                });
            }
        });
    }

    /**
     * Denies a pending town invite.
     */
    public void denyInvite(Player target) {
        UUID targetUUID = target.getUniqueId();
        TownInvite invite = pendingInvites.remove(targetUUID);

        if (invite == null || invite.expiresAt < System.currentTimeMillis()) {
            target.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.nopendinginvite")));
            return;
        }

        invite.timeoutTask.cancel();

        target.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.invitedeniedtarget")));
        Player inviter = Bukkit.getPlayer(invite.inviterUuid);
        if (inviter != null) {
            inviter.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.invitedeniedrequester")
                    .replace("{player}", target.getName())));
        }
    }

    /**
     * Removes a player from the town (kick).
     */
    public void removeMember(Player requester, String[] args){
        if(args.length <2){
            requester.sendMessage(Utils.getColor(prefix + incorrectUsage));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null){
            requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.playernotfound")));
            return;
        }

        UUID requesterUUID = requester.getUniqueId();
        UUID targetUUID = target.getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
           Integer townId = db.getPlayerTownId(requesterUUID);

           if (townId == null){
               Bukkit.getScheduler().runTask(plugin, () -> {
                   requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notown")));
               });
               return;
           }
           if (!db.isTownAdmin(requesterUUID, townId)) {
               Bukkit.getScheduler().runTask(plugin, () -> {
                   requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.nottownadmin")));
               });
               return;
           }
           Integer targetTown = db.getPlayerTownId(targetUUID);
           if (targetTown == null || !targetTown.equals(townId)){
               Bukkit.getScheduler().runTask(plugin, () -> {
                   requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.playernotinyourtown")));
               });
               return;
           }
           if(db.isMayor(targetUUID, townId)) {
               Bukkit.getScheduler().runTask(plugin, () -> {
                   requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.cantremovemayor")));
               });
               return;
           }

           boolean success = db.removeMember(targetUUID, townId);
           if (success) {
               refreshTownSpawnHologram(townId, requester);
           }
           Bukkit.getScheduler().runTask(plugin, () -> {
              if(success){
                  requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.removedmember").replace("{player}", target.getName())));
                  target.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.kickedfromtown")));
                  return;
              } else {
                  requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.removememberfailed")));
              }
           });
           upkeepRepository.decreaseUpkeep(townId, upkeepConfig.getDouble("payment.costs.member.refunded", 5.0));
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        UUID targetUUID = e.getPlayer().getUniqueId();
        TownInvite invite = pendingInvites.remove(targetUUID);
        if (invite != null) {
            invite.timeoutTask.cancel();
        }
    }

    /**
     * Allows a player to leave their current town.
     */
    public void leaveTown(Player p){
        UUID uuid = p.getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->{
           Integer townId = db.getPlayerTownId(uuid);
            if (townId == null){
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notown")));
                });
                return;
            }
            if(db.isMayor(uuid, townId)) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.mayorcantleave")));
                });
                return;
            }

            boolean success = db.removeMember(uuid, townId);
            if (success) {
                refreshTownSpawnHologram(townId, p);
            }

            Bukkit.getScheduler().runTask(plugin, () ->{
               if (success) {
                   p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.lefttown")));
               } else {
                   p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.leavefailed")));
                   return;
               }
            });
            upkeepRepository.decreaseUpkeep(townId, upkeepConfig.getDouble("payment.costs.member.refunded", 5.0));
        });
    }

    // ==========================================
    // PLAYER RANKS
    // ==========================================

    /**
     * Promotes a member to Officer.
     */
    public void promotePlayer(Player requester, String[] args){
        if(args.length <2){
            requester.sendMessage(Utils.getColor(prefix + incorrectUsage));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.playernotfound")));
            return;
        }

        UUID requesterUUID = requester.getUniqueId();
        UUID targetUUID = target.getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
           Integer townId = db.getPlayerTownId(requesterUUID);
            if (townId == null){
                Bukkit.getScheduler().runTask(plugin, () -> {
                    requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notown")));
                });
                return;
            }
            if (!db.isMayor(requesterUUID, townId)) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notmayor")));
                });
                return;
            }
            Integer targetTown = db.getPlayerTownId(targetUUID);
            if (targetTown == null || !targetTown.equals(townId)){
                Bukkit.getScheduler().runTask(plugin, () -> {
                    requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.playernotinyourtown")));
                });
                return;
            }
            if(!db.isMemberRank(targetUUID, townId)){
                Bukkit.getScheduler().runTask(plugin, () ->{
                    requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.cantpromote")));
                });
                return;
            }
            boolean success = db.setRole(targetUUID, townId, "OFFICER");
            if (success) {
                refreshTownSpawnHologram(townId, requester);
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if(success){
                    requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.promotedmember").replace("{player}", target.getName())));
                    target.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.promotedtarget")));
                } else {
                    requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.promotefailed")));
                }
            });
        });
    }

    /**
     * Demotes an Officer to Member.
     */
    public void demotePlayer(Player requester, String[] args){
        if(args.length <2){
            requester.sendMessage(Utils.getColor(prefix + incorrectUsage));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.playernotfound")));
            return;
        }

        UUID requesterUUID = requester.getUniqueId();
        UUID targetUUID = target.getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getPlayerTownId(requesterUUID);
            if (townId == null){
                Bukkit.getScheduler().runTask(plugin, () -> {
                    requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notown")));
                });
                return;
            }
            if (!db.isMayor(requesterUUID, townId)) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notmayor")));
                });
                return;
            }
            if (requesterUUID.equals(targetUUID)) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.cantdemoteself")))
                );
                return;
            }
            Integer targetTown = db.getPlayerTownId(targetUUID);
            if (targetTown == null || !targetTown.equals(townId)){
                Bukkit.getScheduler().runTask(plugin, () -> {
                    requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.playernotinyourtown")));
                });
                return;
            }
            if(db.isMemberRank(targetUUID, townId)){
                Bukkit.getScheduler().runTask(plugin, () ->{
                    requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.cantdemote")));
                });
                return;
            }
            boolean success = db.setRole(targetUUID, townId, "MEMBER");
            if (success) {
                refreshTownSpawnHologram(townId, requester);
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if(success){
                    requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.demotedmember").replace("{player}", target.getName())));
                    target.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.demotedtarget")));
                } else {
                    requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.demotefailed")));
                }
            });
        });
    }

    public void transferMayor(Player p, String[] args) {
        if(args.length < 2){
            p.sendMessage(Utils.getColor(prefix + incorrectUsage));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()){
            p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.playernotfound")));
            return;
        }
        
        UUID targetUUID = target.getUniqueId();
        UUID requesterUUID = p.getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getPlayerTownId(requesterUUID);
            Integer targetTownId = db.getPlayerTownId(targetUUID);
            if (townId == null || targetTownId == null) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notown"))));
                return;
            }
            String townName = db.getTownName(townId);
            if(!targetTownId.equals(townId)){
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.playernotinyourtown")));
                });
                return;
            }
            if(!db.isMayor(requesterUUID, townId)){
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notmayor")));
                });
                return;
            }
            if (requesterUUID.equals(targetUUID)) {
                Bukkit.getScheduler().runTask(plugin, () ->
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.cantpromote")))
                );
                return;
            }
            Boolean setMayor = db.setMayor(targetUUID, townId);
            if(setMayor){
                refreshTownSpawnHologram(townId, p);
                Bukkit.getScheduler().runTask(plugin, () ->{
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.mayortransfered").replace("{town}", townName).replace("{player}", target.getName())));
                    target.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.newtownmayor").replace("{town}", townName)));
                });
                return;
            } else {
                Bukkit.getScheduler().runTask(plugin, () ->{
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.databaseerror")));
                });
                return;
            }

        });
    }

    // ==========================================
    // TOWN BANK
    // ==========================================

    /**
     * Deposits money into the town bank.
     */
    public void giveTownMoney(Player p, String[] args){
        Double amount = parsePositiveAmount(p, args);
        if (amount == null) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getPlayerTownId(p.getUniqueId());
            if(townId == null){
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notown")));
                });
                return;
            }
            economy.remove(p.getUniqueId(), amount, success ->{
                if(success){
                    double acceptedAmount = amount;
                    if (plugin.getTownTierPerkService() != null) {
                        double currentBalance = townBankRepository.getTownBalance(townId);
                        acceptedAmount = plugin.getTownTierPerkService().allowedDepositAmount(townId, currentBalance, amount);
                    }
                    if (acceptedAmount <= 0.0) {
                        economy.add(p.getUniqueId(), amount);
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            p.sendMessage(Utils.getColor(prefix + messagesConfig.getString(
                                    "town.bank.cap-reached",
                                    "&cYour town bank is at its tier cap and cannot accept more deposits."
                            )));
                        });
                        return;
                    }

                    townBankRepository.depositTownMoney(townId, acceptedAmount);
                    refreshTownSpawnHologram(townId, p);

                    double overflow = amount - acceptedAmount;
                    if (overflow > 0.0) {
                        economy.add(p.getUniqueId(), overflow);
                    }
                    double finalAcceptedAmount = acceptedAmount;
                    double finalOverflow = overflow;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.depositwealth").replace("{amount}", String.valueOf(finalAcceptedAmount))));
                        if (finalOverflow > 0.0) {
                            p.sendMessage(Utils.getColor(prefix + messagesConfig.getString(
                                    "town.bank.cap-partial",
                                    "&eTown bank cap reached. Refunded: &f{amount}"
                            ).replace("{amount}", String.valueOf(finalOverflow))));
                        }
                    });
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notenough")));
                    });
                }
            });
        });
    }

    /**
     * Withdraws money from the town bank (Officers and Mayors only).
     */
    public void withdrawTownMoney(Player p, String[] args){
        Double amount = parsePositiveAmount(p, args);
        if (amount == null) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getPlayerTownId(p.getUniqueId());
            if(townId == null){
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notown")));
                });
                return;
            }

            boolean canWithdraw = db.isTownAdmin(p.getUniqueId(), townId) || permissionManager.getPermissionSync(townId, p.getUniqueId(), "CAN_WITHDRAW");
            if(canWithdraw){
                economy.add(p.getUniqueId(), amount);
                boolean success = townBankRepository.withdrawTownMoney(townId, amount);
                if (success) {
                    refreshTownSpawnHologram(townId, p);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.withdrawwealth").replace("{amount}", String.valueOf(amount))));
                    });
                } else {
                    economy.remove(p.getUniqueId(), amount, (ignored) -> {}); // Refund failed withdrawal
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notenoughbankbalance")));
                    });
                }
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.nopermission")));
                });
            }
        });
    }

    /**
     * Shows the current balance of the town bank.
     */
    public void townBalance(Player p){
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getPlayerTownId(p.getUniqueId());
            if (townId == null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notown")));
                });
                return;
            }
            double bal = townBankRepository.getTownBalance(townId);
            Bukkit.getScheduler().runTask(plugin, () ->{
               p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.bankbalance").replace("{balance}", String.valueOf(bal))));
            });
        });
    }

    // ==========================================
    // VISUALS
    // ==========================================

    /**
     * Enables a temporary particle visualizer to show chunk boundaries and ownership.
     */
    public void chunkVisualizer(Player p){
        int duration = townConfig.getInt("visualizer.duration", 30);
        int timer = duration * 2;
        String ownParticle = townConfig.getString("visualizer.own", "SOUL_FIRE_FLAME");
        String wildParticle = townConfig.getString("visualizer.wild", "COMPOSTER");
        String enemyParticle = townConfig.getString("visualizer.enemy", "FLAME");

        Particle own, wild, enemy;
        try {
            own = Particle.valueOf(ownParticle.toUpperCase());
            wild = Particle.valueOf(wildParticle.toUpperCase());
            enemy = Particle.valueOf(enemyParticle.toUpperCase());

            // Check if any particle requires data but we aren't equipped to handle it beyond DUST
            validateParticle(own);
            validateParticle(wild);
            validateParticle(enemy);

        } catch (IllegalArgumentException e) {
            p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.invalidparticle")));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer playerTown = db.getPlayerTownId(p.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.visualizerenabled").replace("{duration}", String.valueOf(duration))));
                new BukkitRunnable() {
                    int ticks = 0;
                    @Override
                    public void run() {
                        if (ticks++ >= timer || !p.isOnline()) {
                            cancel();
                            return;
                        }

                        Chunk center = p.getLocation().getChunk();

                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                Chunk c = center.getWorld().getChunkAt(
                                        center.getX() + dx,
                                        center.getZ() + dz
                                );

                                Integer chunkTown = chunkCache.getTownId(c);

                                if (chunkTown == null) {
                                    ChunkVisuals.showChunk(p, c, wild);
                                } else if (chunkTown.equals(playerTown)) {
                                    ChunkVisuals.showChunk(p, c, own);
                                } else {
                                    ChunkVisuals.showChunk(p, c, enemy);
                                }
                            }
                        }
                    }
                }.runTaskTimer(plugin, 0L, 10L);
            });
        });
    }

    /**
     * Validates if a particle can be used in the visualizer.
     */
    private void validateParticle(Particle particle) {
        if (particle == null) return;
        Class<?> dataClass = particle.getDataType();
        if (dataClass != Void.class && dataClass != Particle.DustOptions.class) {
            throw new IllegalArgumentException("Unsupported particle data type: " + dataClass.getName());
        }
    }

    private void sendInvite(Player requester, Player target, int townId, String townName) {
        TownInvite previous = pendingInvites.remove(target.getUniqueId());
        if (previous != null) {
            previous.timeoutTask.cancel();
        }

        long expiresAt = System.currentTimeMillis() + (INVITE_TIMEOUT_SECONDS * 1000L);
        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            TownInvite current = pendingInvites.get(target.getUniqueId());
            if (current == null || current.expiresAt != expiresAt) {
                return;
            }

            pendingInvites.remove(target.getUniqueId());
            if (target.isOnline()) {
                target.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.inviteexpiredtarget")));
            }
            if (requester.isOnline()) {
                requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.inviteexpiredrequester")
                        .replace("{player}", target.getName())));
            }
        }, INVITE_TIMEOUT_SECONDS * 20L);

        pendingInvites.put(target.getUniqueId(), new TownInvite(requester.getUniqueId(), townId, expiresAt, timeoutTask));

        requester.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.invitesent")
                .replace("{player}", target.getName())
                .replace("{seconds}", String.valueOf(INVITE_TIMEOUT_SECONDS))));

        target.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.invitereceived")
                .replace("{player}", requester.getName())
                .replace("{town}", townName != null ? townName : "Unknown")
                .replace("{seconds}", String.valueOf(INVITE_TIMEOUT_SECONDS))));
        target.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.inviteactions")));
    }

    private void refreshTownSpawnHologram(int townId, Player contextPlayer) {
        if (hologramsConfig == null
                || !hologramsConfig.getBoolean("holograms.enabled", true)
                || !Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
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
        double balance = townBankRepository.getTownBalance(townId);
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

    private Double parsePositiveAmount(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Utils.getColor(prefix + incorrectUsage));
            return null;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException ignored) {
            player.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.invalidamount")));
            return null;
        }
        if (amount < 1) {
            player.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.invalidamount")));
            return null;
        }
        return amount;
    }

    private int maxTownMembers() {
        return townConfig.getInt("limits.max-members", -1);
    }

    private boolean isTownAtMemberCap(int townId) {
        int maxMembers = maxTownMembers();
        return maxMembers >= 0 && db.getTownMembers(townId).size() >= maxMembers;
    }
}









