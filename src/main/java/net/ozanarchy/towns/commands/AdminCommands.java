package net.ozanarchy.towns.commands;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import net.ozanarchy.towns.events.AdminEvents;
import net.ozanarchy.towns.util.Utils;
import static net.ozanarchy.towns.TownsPlugin.messagesConfig;

public class AdminCommands implements CommandExecutor, TabCompleter {
    private final AdminEvents adminEvents;
    private final String prefix = Utils.adminPrefix();

    public AdminCommands(AdminEvents adminEvents) {
        this.adminEvents = adminEvents;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("help", "reload", "delete", "setspawn", "removespawn", "add", "remove", "spawn");
            return subCommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .sorted()
                    .collect(Collectors.toList());
        }

        return List.of();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command arg1, String arg2, String[] args) {
        if (sender instanceof Player p) {
            if (!p.hasPermission("oztowns.admin")) {
                p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.nopermission")));
                return true;
            }
        }

        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help" -> {
                sendHelp(sender);
                return true;
            }
            case "reload" -> {
                adminEvents.reload(sender);
                return true;
            }
            case "delete" -> {
                if (args.length < 2) {
                    sender.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.incorrectusage")));
                    return true;
                }
                adminEvents.deleteTown(sender, args[1]);
                return true;
            }
            case "setspawn" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.mustbeplayer")));
                    return true;
                }
                if (args.length < 2) {
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.incorrectusage")));
                    return true;
                }
                adminEvents.setSpawn(p, args[1]);
                return true;
            }
            case "removespawn" -> {
                if (args.length < 2) {
                    sender.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.incorrectusage")));
                    return true;
                }
                adminEvents.removeSpawn(sender, args[1]);
                return true;
            }
            case "add" -> {
                if (args.length < 3) {
                    sender.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.incorrectusage")));
                    return true;
                }
                adminEvents.addMember(sender, args[1], args[2]);
                return true;
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.incorrectusage")));
                    return true;
                }
                adminEvents.removeMember(sender, args[1], args[2]);
                return true;
            }
            case "setmayor" -> {
                if (args.length < 3) {
                    sender.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.incorrectusage")));
                    return true;
                }
                adminEvents.setMayor(sender, args[1], args[2]);
                return true;
            }
            case "spawn" -> {
                if (args.length < 2){
                    sender.sendMessage(Utils.getColor(prefix + messagesConfig.getString("adminmessages.incorrectusage")));
                    return true;
                }
                if (sender instanceof Player p){
                    adminEvents.spawnTeleport(p, args[1]);
                    return true;
                } else {
                    Bukkit.getLogger().info("Players only.");
                    return true;
                }
            }
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        for (String line : messagesConfig.getStringList("adminmessages.help")) {
            sender.sendMessage(Utils.getColor(line));
        }
    }
    
}
