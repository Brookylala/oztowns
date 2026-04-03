package net.ozanarchy.towns.town.command;

import net.ozanarchy.towns.town.model.TownMember;
import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.util.Utils;
import net.ozanarchy.towns.util.db.DatabaseHandler;

import static net.ozanarchy.towns.TownsPlugin.config;
import static net.ozanarchy.towns.TownsPlugin.messagesConfig;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TownMessageCommand implements CommandExecutor{
    private final TownsPlugin plugin;
    private final DatabaseHandler db;
    private final String prefix = Utils.prefix();

    public TownMessageCommand(TownsPlugin plugin, DatabaseHandler db){
        this.plugin = plugin;
        this.db = db;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command arg1, String arg2, String[] args) {
        if(!(sender instanceof Player p)){
            Bukkit.getLogger().info("Town messaging is only for players.");
            return true;
        }

        if (args.length == 0) {
            p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.incorrectusage")));
            return true;
        }

        townChat(p, args);

        return true;
    }

    public void townChat(Player p, String[] args){
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getPlayerTownId(p.getUniqueId());

            if(townId == null){
                Bukkit.getScheduler().runTask(plugin, () ->{
                    p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.notown")));
                });
                return;
            }
            
            List<UUID> townMemberUUID = db.getTownMembers(townId).stream()
                    .map(TownMember::getUuid)
                    .toList();

            String msg = String.join(" ", args).trim();
            if (msg.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        p.sendMessage(Utils.getColor(prefix + messagesConfig.getString("messages.incorrectusage"))));
                return;
            }
            
            for (UUID uuid : townMemberUUID) {
                Player member = Bukkit.getPlayer(uuid);
                if(member != null && member.isOnline()){
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        member.sendMessage(Utils.getColor(config.getString("townusercolor") + p.getDisplayName() + "&f&l>&7 " + msg));
                    });
                }
            }
        });
    }
    
}






