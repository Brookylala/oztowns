package net.ozanarchy.towns.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class MessageService {
    private static final List<String> MESSAGE_FILES = Arrays.asList(
            "general.yml",
            "town.yml",
            "raid.yml",
            "bank.yml",
            "upkeep.yml",
            "admin.yml",
            "help.yml"
    );

    private final JavaPlugin plugin;
    private final File messagesDirectory;

    private YamlConfiguration mergedConfig = new YamlConfiguration();

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.messagesDirectory = new File(plugin.getDataFolder(), "messages");
    }

    public FileConfiguration load() {
        ensureDefaults();
        this.mergedConfig = new YamlConfiguration();

        List<File> files = new ArrayList<>();
        for (String fileName : MESSAGE_FILES) {
            files.add(new File(messagesDirectory, fileName));
        }
        files.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        for (File file : files) {
            if (!file.exists()) {
                continue;
            }
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            mergeInto(mergedConfig, cfg);
        }

        applyAliases();
        return mergedConfig;
    }

    public FileConfiguration reload() {
        return load();
    }

    public String get(String key) {
        String value = mergedConfig.getString(key);
        if (value == null && key.contains("-")) {
            value = mergedConfig.getString(key.replace('-', '.'));
        }
        return value;
    }

    public String get(String key, String fallback) {
        String value = get(key);
        return value == null ? fallback : value;
    }

    public List<String> getList(String key) {
        List<String> values = mergedConfig.getStringList(key);
        if (values.isEmpty() && key.contains("-")) {
            return mergedConfig.getStringList(key.replace('-', '.'));
        }
        return values;
    }

    public String format(String key, Map<String, String> placeholders) {
        String message = get(key);
        if (message == null) {
            return null;
        }
        if (placeholders == null || placeholders.isEmpty()) {
            return message;
        }
        String formatted = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            formatted = formatted.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return formatted;
    }

    public FileConfiguration asCompatibilityConfig() {
        return mergedConfig;
    }

    private void ensureDefaults() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        if (!messagesDirectory.exists()) {
            messagesDirectory.mkdirs();
        }

        for (String fileName : MESSAGE_FILES) {
            File target = new File(messagesDirectory, fileName);
            if (target.exists()) {
                continue;
            }
            plugin.saveResource("messages/" + fileName, false);
        }
    }

    private void mergeInto(YamlConfiguration target, FileConfiguration source) {
        for (String key : source.getKeys(true)) {
            Object value = source.get(key);
            if (value instanceof ConfigurationSection) {
                continue;
            }
            target.set(key, value);
        }
    }

    private void applyAliases() {
        Map<String, String> aliases = Map.ofEntries(
                Map.entry("prefix", "general.prefix"),
                Map.entry("adminprefix", "admin.prefix"),
                Map.entry("messages.bankamountpending", "bank.chat-input.pending"),
                Map.entry("messages.bankamounttimeout", "bank.chat-input.timeout"),
                Map.entry("messages.nopermission", "general.error.no-permission"),
                Map.entry("messages.databaseerror", "general.error.database"),
                Map.entry("messages.playernotfound", "general.error.player-not-found"),
                Map.entry("messages.incorrectusage", "general.error.incorrect-usage"),
                Map.entry("messages.notenough", "general.error.not-enough-money"),
                Map.entry("messages.invalidamount", "general.error.invalid-amount"),
                Map.entry("messages.notown", "town.membership.not-in-town"),
                Map.entry("messages.notownexists", "town.membership.town-not-found"),
                Map.entry("messages.alreadyinatown", "town.membership.already-in-town"),
                Map.entry("messages.playeralreadyintown", "town.membership.player-already-in-town"),
                Map.entry("messages.playernotinyourtown", "town.membership.player-not-in-your-town"),
                Map.entry("messages.joinedtown", "town.membership.joined"),
                Map.entry("messages.lefttown", "town.membership.left"),
                Map.entry("messages.leavefailed", "town.membership.leave-failed"),
                Map.entry("messages.kickedfromtown", "town.membership.kicked"),
                Map.entry("messages.notmayor", "town.roles.not-mayor"),
                Map.entry("messages.nottownadmin", "town.roles.not-town-admin"),
                Map.entry("messages.mayorcantleave", "town.roles.mayor-cannot-leave"),
                Map.entry("messages.cantremovemayor", "town.roles.cannot-remove-mayor"),
                Map.entry("messages.cantpromote", "town.roles.cannot-promote"),
                Map.entry("messages.cantdemote", "town.roles.cannot-demote"),
                Map.entry("messages.cantdemoteself", "town.roles.cannot-demote-self"),
                Map.entry("messages.promotedmember", "town.roles.promote-success-member"),
                Map.entry("messages.promotedtarget", "town.roles.promote-success-target"),
                Map.entry("messages.promotefailed", "town.roles.promote-failed"),
                Map.entry("messages.demotedmember", "town.roles.demote-success-member"),
                Map.entry("messages.demotedtarget", "town.roles.demote-success-target"),
                Map.entry("messages.demotefailed", "town.roles.demote-failed"),
                Map.entry("messages.townnametaken", "town.create.name-taken"),
                Map.entry("messages.nameblacklisted", "town.create.name-blacklisted"),
                Map.entry("messages.towncreated", "town.create.success"),
                Map.entry("messages.towncreatenextsteps", "town.create.next-steps"),
                Map.entry("messages.failedtomaketown", "town.create.failed"),
                Map.entry("messages.abandonconfirm", "town.delete.confirm"),
                Map.entry("messages.towndeleted", "town.delete.success"),
                Map.entry("messages.chunkclaimed", "town.claim.success"),
                Map.entry("messages.chunkremoved", "town.claim.removed"),
                Map.entry("messages.chunkowned", "town.claim.owned"),
                Map.entry("messages.chunknotowned", "town.claim.unowned"),
                Map.entry("messages.disconnectedclaim", "town.claim.disconnected"),
                Map.entry("messages.unclaimableworld", "town.claim.world-blocked"),
                Map.entry("messages.unclaimfailed", "town.claim.unclaim-failed"),
                Map.entry("messages.spawnsetsuccess", "town.spawn.set-success"),
                Map.entry("messages.notownspawn", "town.spawn.not-set"),
                Map.entry("messages.setspawnrestricted", "town.spawn.set-restricted"),
                Map.entry("messages.spawnremoved", "town.spawn.removed"),
                Map.entry("messages.pleasewait", "town.spawn.cooldown"),
                Map.entry("messages.spawnheightrestricted", "town.spawn.height-restricted"),
                Map.entry("messages.spawnskyrestricted", "town.spawn.sky-restricted"),
                Map.entry("messages.spawnskyobstructed", "town.spawn.sky-obstructed"),
                Map.entry("messages.alreadyteleporting", "town.spawn.teleport.already"),
                Map.entry("messages.teleportcancelled", "town.spawn.teleport.cancelled"),
                Map.entry("messages.teleported", "town.spawn.teleport.success"),
                Map.entry("messages.teleportingbar", "town.spawn.teleport.bar"),
                Map.entry("messages.teleportingbartitle", "town.spawn.teleport.bar-title"),
                Map.entry("messages.inviteusage", "town.invite.usage"),
                Map.entry("messages.invitesent", "town.invite.sent"),
                Map.entry("messages.invitereceived", "town.invite.received"),
                Map.entry("messages.inviteactions", "town.invite.actions"),
                Map.entry("messages.nopendinginvite", "town.invite.no-pending"),
                Map.entry("messages.inviteinvalid", "town.invite.invalid"),
                Map.entry("messages.inviteacceptedtarget", "town.invite.accepted-target"),
                Map.entry("messages.inviteacceptedrequester", "town.invite.accepted-requester"),
                Map.entry("messages.invitedeniedtarget", "town.invite.denied-target"),
                Map.entry("messages.invitedeniedrequester", "town.invite.denied-requester"),
                Map.entry("messages.inviteexpiredtarget", "town.invite.expired-target"),
                Map.entry("messages.inviteexpiredrequester", "town.invite.expired-requester"),
                Map.entry("messages.addmemberfailed", "town.member.added-failed"),
                Map.entry("messages.removedmember", "town.member.removed"),
                Map.entry("messages.removememberfailed", "town.member.remove-failed"),
                Map.entry("messages.newmayor", "town.mayor.new"),
                Map.entry("messages.newtownmayor", "town.mayor.transfer-target"),
                Map.entry("messages.mayortransfered", "town.mayor.transfer-success"),
                Map.entry("messages.setmayorfail", "town.mayor.set-failed"),
                Map.entry("messages.visualizerenabled", "town.visualizer.enabled"),
                Map.entry("messages.visualizerdisabled", "town.visualizer.disabled"),
                Map.entry("messages.invalidparticle", "town.visualizer.invalid-particle"),
                Map.entry("messages.missinginteractperm", "town.protection.missing-interact-perm"),
                Map.entry("messages.missingcontainerperm", "town.protection.missing-container-perm"),
                Map.entry("messages.cannotbreaktownlodestone", "town.protection.cannot-break-lodestone"),
                Map.entry("messages.upkeepsuccess", "upkeep.payment.success"),
                Map.entry("messages.upkeepoverdue", "upkeep.payment.overdue"),
                Map.entry("messages.raidingdisabled", "raid.general.disabled"),
                Map.entry("messages.raidfailed", "raid.fail.death"),
                Map.entry("messages.raidfaileddamage", "raid.fail.damage"),
                Map.entry("messages.raidsuccess", "raid.success.with-loot"),
                Map.entry("messages.raidsuccessempty", "raid.success.empty"),
                Map.entry("messages.raidbroadcast", "raid.broadcast.success"),
                Map.entry("messages.raidtimer", "raid.timer.chat"),
                Map.entry("messages.raidtimerbar", "raid.timer.bossbar"),
                Map.entry("messages.raiddefenderbar", "raid.timer.defender-bossbar"),
                Map.entry("messages.bankbalance", "bank.balance.info"),
                Map.entry("messages.depositwealth", "bank.deposit.success"),
                Map.entry("messages.withdrawwealth", "bank.withdraw.success"),
                Map.entry("messages.notenoughbankbalance", "bank.withdraw.insufficient-balance"),
                Map.entry("messages.helpmenu", "help.town.title"),
                Map.entry("messages.bankhelpmenu", "help.bank.title"),
                Map.entry("messages.wilderness", "general.ui.wilderness"),
                Map.entry("messages.townentered", "general.ui.town-entered"),
                Map.entry("messages.townleft", "general.ui.town-left"),
                Map.entry("bankhelp", "help.bank.commands"),
                Map.entry("adminmessages.help", "help.admin.commands"),
                Map.entry("adminmessages.mustbeplayer", "admin.general.must-be-player"),
                Map.entry("adminmessages.incorrectusage", "admin.general.incorrect-usage"),
                Map.entry("adminmessages.reloaded", "admin.general.reloaded"),
                Map.entry("adminmessages.townnotfound", "admin.town.not-found"),
                Map.entry("adminmessages.towndeleted", "admin.town.deleted"),
                Map.entry("adminmessages.spawnnotfound", "admin.town.spawn-not-found"),
                Map.entry("adminmessages.spawnremoved", "admin.town.spawn-removed"),
                Map.entry("adminmessages.spawnsuccess", "admin.town.spawn-success"),
                Map.entry("adminmessages.teleported", "admin.town.teleported"),
                Map.entry("adminmessages.playernotfound", "admin.member.player-not-found"),
                Map.entry("adminmessages.notintown", "admin.member.not-in-town"),
                Map.entry("adminmessages.alreadyintown", "admin.member.already-in-town"),
                Map.entry("adminmessages.addedmember", "admin.member.added"),
                Map.entry("adminmessages.removedmember", "admin.member.removed"),
                Map.entry("adminmessages.cannotremovemayor", "admin.member.cannot-remove-mayor"),
                Map.entry("adminmessages.setmayor", "admin.member.set-mayor"),
                Map.entry("adminmessages.lockpickgiven", "admin.raid.lockpick-given"),
                Map.entry("adminmessages.lockpickreceived", "admin.raid.lockpick-received")
        );

        for (Map.Entry<String, String> alias : aliases.entrySet()) {
            String legacyKey = alias.getKey();
            String modernKey = alias.getValue();

            if (!mergedConfig.contains(legacyKey) && mergedConfig.contains(modernKey)) {
                mergedConfig.set(legacyKey, mergedConfig.get(modernKey));
            }
            if (!mergedConfig.contains(modernKey) && mergedConfig.contains(legacyKey)) {
                mergedConfig.set(modernKey, mergedConfig.get(legacyKey));
            }
        }

        for (String page : List.of("1", "2")) {
            String oldPath = "help." + page;
            String newPath = "help.town.pages." + page;
            if (!mergedConfig.contains(oldPath) && mergedConfig.contains(newPath)) {
                mergedConfig.set(oldPath, mergedConfig.get(newPath));
            }
            if (!mergedConfig.contains(newPath) && mergedConfig.contains(oldPath)) {
                mergedConfig.set(newPath, mergedConfig.get(oldPath));
            }
        }
    }
}






