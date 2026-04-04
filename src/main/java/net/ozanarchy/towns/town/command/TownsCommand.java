package net.ozanarchy.towns.town.command;

import net.ozanarchy.towns.util.Utils;
import net.ozanarchy.towns.town.listener.MemberEvents;
import net.ozanarchy.towns.town.listener.TownEvents;
import net.ozanarchy.towns.town.gui.MainGui;
import net.ozanarchy.towns.town.gui.MembersGui;
import net.ozanarchy.towns.util.db.DatabaseHandler;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static net.ozanarchy.towns.TownsPlugin.townConfig;
import static net.ozanarchy.towns.TownsPlugin.messagesConfig;

public class TownsCommand implements CommandExecutor, TabCompleter {
    private final DatabaseHandler db;
    private final TownEvents events;
    private final MemberEvents mEvents;
    private final MainGui gui;
    private final MembersGui membersGui;
    private final String prefix = Utils.prefix();

    public TownsCommand(DatabaseHandler db, TownEvents events, MemberEvents mEvents, MainGui gui, MembersGui membersGui) {
        this.db = db;
        this.events = events;
        this.mEvents = mEvents;
        this.gui = gui;
        this.membersGui = membersGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Town commands are player-only because they depend on player location and UUID.
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (!p.hasPermission("oztowns.commands")) {
            sendNoPermission(p);
            return true;
        }

        if (db == null) {
            p.sendMessage(Utils.getColor(prefix + "&cDatabase Error, Please seek an Admin."));
            return true;
        }

        if (args.length < 1){
            // Open the main menu when no subcommand is provided.
            gui.openGui(p);
            return true;
        }

        // Route each subcommand to the matching event/GUI handler.
        switch (args[0].toLowerCase()){
            case "gui" -> {
                gui.openGui(p);
                return true;
            }
            case "help", "commands" -> {
                if (!hasPermission(p, "oztowns.commands.help")) {
                    return true;
                }
                helpCommand(p, args);
                return true;
            }
            case "claim" -> {
                if (!hasPermission(p, "oztowns.commands.claim")) {
                    return true;
                }
                events.claimLand(p);
                return true;
            }
            case "create" -> {
                if (!hasPermission(p, "oztowns.commands.create")) {
                    return true;
                }
                events.createTown(p, args);
                return true;
            }
            case "abandon" -> {
                if (!hasPermission(p, "oztowns.commands.abandon")) {
                    return true;
                }
                if(args.length <2 || !args[1].toLowerCase().equals("confirm")){
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.abandonconfirm")));
                    return true;
                }
                events.abandonTown(p.getUniqueId());
                return true;
            }
            case "unclaim" -> {
                if (!hasPermission(p, "oztowns.commands.unclaim")) {
                    return true;
                }
                events.removeChunk(p);
                return true;
            }
            case "setspawn" -> {
                if (!hasPermission(p, "oztowns.commands.setspawn")) {
                    return true;
                }
                events.setSpawn(p);
                return true;
            }
            case "spawn" -> {
                if (!hasPermission(p, "oztowns.commands.spawn")) {
                    return true;
                }
                events.spawn(p);
                return true;
            }
            case "add", "invite" -> {
                if (!hasPermission(p, "oztowns.commands.add")) {
                    return true;
                }
                mEvents.addMember(p, args);
                return true;
            }
            case "accept" -> {
                if (!hasPermission(p, "oztowns.commands.add")) {
                    return true;
                }
                mEvents.acceptInvite(p);
                return true;
            }
            case "deny" -> {
                if (!hasPermission(p, "oztowns.commands.add")) {
                    return true;
                }
                mEvents.denyInvite(p);
                return true;
            }
            case "remove" -> {
                if (!hasPermission(p, "oztowns.commands.remove")) {
                    return true;
                }
                mEvents.removeMember(p, args);
                return true;
            }
            case "promote" -> {
                if (!hasPermission(p, "oztowns.commands.promote")) {
                    return true;
                }
                mEvents.promotePlayer(p, args);
                return true;
            }
            case "demote" -> {
                if (!hasPermission(p, "oztowns.commands.demote")) {
                    return true;
                }
                mEvents.demotePlayer(p, args);
                return true;
            }
            case "leave" -> {
                if (!hasPermission(p, "oztowns.commands.leave")) {
                    return true;
                }
                mEvents.leaveTown(p);
                return true;
            }
            case "members" -> {
                if (!hasPermission(p, "oztowns.commands.members")) {
                    return true;
                }
                membersGui.openGui(p);
                return true;
            }
            case "visualizer", "chunks" -> {
                if (!hasPermission(p, "oztowns.commands.visualizer")) {
                    return true;
                }
                boolean visualizerEnabled = townConfig.getBoolean("visualizer.enabled", true);
                if(visualizerEnabled){
                    mEvents.chunkVisualizer(p);
                    return true;
                } else {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.visualizerdisabled")));
                    return true;
                }
            }
            case "setmayor" -> {
                if (!hasPermission(p, "oztowns.commands.transfer")) {
                    return true;
                }
                mEvents.transferMayor(p, args);
                return true;
            }
            case "rename" -> {
                if (!hasPermission(p, "oztowns.commands.rename")) {
                    return true;
                }
                events.renameTown(p, args);
                return true;
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return List.of();
        }

        if (args.length == 1) {
            // First-argument suggestions for /towns.
            List<String> subCommands = Arrays.asList(
                    "help", "commands", "claim", "create", "abandon", "unclaim",
                    "setspawn", "spawn", "add", "remove", "promote", "demote",
                    "leave", "members", "visualizer", "chunks", "gui", "accept", "deny", "invite", "setmayor"
            );
            return subCommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .sorted()
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "add", "remove", "promote", "demote", "invite", "setmayor" -> {
                    // Suggest online player names for rank/member actions.
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .sorted()
                            .collect(Collectors.toList());
                }
                case "abandon" -> {
                    if ("confirm".startsWith(args[1].toLowerCase())) {
                        return List.of("confirm");
                    }
                }
                case "help", "commands" -> {
                    return List.of("1", "2", "3").stream()
                            .filter(page -> page.startsWith(args[1]))
                            .collect(Collectors.toList());
                }
            }
        }

        return List.of();
    }

    private void helpCommand(Player p, String[] args){
        final int maxPage = 3;
        String page = "1";
        if (args.length >= 2) {
            page = args[1];
            if (!page.equals("1") && !page.equals("2") && !page.equals("3")) {
                p.sendMessage(Utils.getColor(prefix + "&cInvalid help page. Use &f/towns help [1-" + maxPage + "]&c."));
                return;
            }
        }

        p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.helpmenu", "&e&lTown Commands")
                + " &7(Page " + page + "/" + maxPage + ")"));
        List<String> lines = messagesConfig.getStringList("help.town.pages." + page);
        if (lines.isEmpty()) {
            lines = messagesConfig.getStringList("help." + page);
        }
        for (String line : lines){
            p.sendMessage(Utils.getColor(line));
        }
        p.sendMessage(Utils.getColor("&7Use &f/towns help <1-" + maxPage + "> &7to view other pages."));
    }

    private boolean hasPermission(Player player, String node) {
        if (player.hasPermission(node)) {
            return true;
        }
        sendNoPermission(player);
        return false;
    }

    private void sendNoPermission(Player player) {
        player.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.nopermission")));
    }
}








