package net.ozanarchy.towns.commands;

import net.ozanarchy.towns.util.Utils;
import net.ozanarchy.towns.events.MemberEvents;
import net.ozanarchy.towns.gui.BankGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static net.ozanarchy.towns.TownsPlugin.messagesConfig;

public class TownBankCommands implements CommandExecutor, TabCompleter {
    private final MemberEvents mEvents;
    private final BankGui gui;
    private final String prefix = Utils.prefix();

    public TownBankCommands(MemberEvents mEvents, BankGui gui) {
        this.mEvents = mEvents;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Bank actions are player-only.
        if(!(sender instanceof Player p)) return true;
        if(!hasPermission(p, "oztowns.commands") || !hasPermission(p, "oztowns.commands.bank")){
            return true;
        }
        if(args.length < 1){
            // Open bank GUI when no subcommand is provided.
            gui.openGui(p);
            return true;
        }
        // Route bank subcommands.
        switch (args[0].toLowerCase()) {
            case "gui" -> {
                gui.openGui(p);
                return true;
            }
            case "deposit" -> {
                if(!hasPermission(p, "oztowns.commands.bank.deposit")){
                    return true;
                }
                mEvents.giveTownMoney(p, args);
                return true;
            }
            case "withdraw" -> {
                if(!hasPermission(p, "oztowns.commands.bank.withdraw")){
                    return true;
                }
                mEvents.withdrawTownMoney(p, args);
                return true;
            }
            case "balance", "bal" -> {
                if(!hasPermission(p, "oztowns.commands.bank.balance")){
                    return true;
                }
                mEvents.townBalance(p);
                return true;
            }
            case "help", "commands" -> {
                if(!hasPermission(p, "oztowns.commands.bank.help")){
                    return true;
                }
                helpCommand(p);
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
            // First-argument suggestions for /townbank.
            List<String> subCommands = Arrays.asList("deposit", "withdraw", "balance", "bal", "help", "commands", "gui");
            return subCommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .sorted()
                    .collect(Collectors.toList());
        }

        return List.of();
    }

    private void helpCommand(Player p){
        p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.bankhelpmenu")));
        for (String line : messagesConfig.getStringList("bankhelp")){
            p.sendMessage(Utils.getColor(line));
        }
    }

    private boolean hasPermission(Player player, String permission) {
        if (player.hasPermission(permission)) {
            return true;
        }
        player.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.nopermission")));
        return false;
    }
}
