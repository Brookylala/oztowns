package net.ozanarchy.towns.gui;

import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.handlers.DatabaseHandler;
import net.ozanarchy.towns.handlers.PermissionManager;
import net.ozanarchy.towns.util.SkullCreator;
import net.ozanarchy.towns.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static net.ozanarchy.towns.TownsPlugin.guiConfig;
import static net.ozanarchy.towns.TownsPlugin.messagesConfig;

public class MembersGui implements Listener {
    private final TownsPlugin plugin;
    private final DatabaseHandler db;
    private final PermissionManager permissionManager;
    private final NamespacedKey uuidKey;

    public MembersGui(TownsPlugin plugin, DatabaseHandler db, PermissionManager permissionManager) {
        this.plugin = plugin;
        this.db = db;
        this.permissionManager = permissionManager;
        this.uuidKey = new NamespacedKey(plugin, "member_uuid");
    }

    public void openGui(Player player) {
        UUID playerId = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getPlayerTownId(playerId);
            if (townId == null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString("messages.notown")));
                });
                return;
            }

            List<DatabaseHandler.TownMember> members = db.getTownMembers(townId);

            Bukkit.getScheduler().runTask(plugin, () -> buildGui(player, members));
        });
    }

    private void buildGui(Player player, List<DatabaseHandler.TownMember> members) {
        if (!player.isOnline()) {
            return;
        }
        ConfigurationSection section = guiConfig.getConfigurationSection("members_gui");
        String title = Utils.getColor(section != null ? section.getString("title", "&8Town Members") : "&8Town Members");
        int size = section != null ? section.getInt("size", 54) : 54;
        Inventory inv = Bukkit.createInventory(null, size, title);

        if (members.isEmpty()) {
            if (section != null) {
                ConfigurationSection emptySection = section.getConfigurationSection("empty_item");
                if (emptySection != null) {
                    int slot = emptySection.getInt("slot", size / 2);
                    String materialName = emptySection.getString("material", "BARRIER");
                    String name = Utils.getColor(emptySection.getString("name", "&cNo members"));
                    List<String> lore = emptySection.getStringList("lore");
                    ItemStack item = createItem(materialName, name, lore);
                    inv.setItem(Math.min(Math.max(slot, 0), size - 1), item);
                    player.openInventory(inv);
                    return;
                }
            }

            ItemStack item = createItem("BARRIER", Utils.getColor("&cNo members"), List.of(Utils.getColor("&7Your town has no members.")));
            inv.setItem(size / 2, item);
            player.openInventory(inv);
            return;
        }

        List<String> loreTemplate = section != null ? section.getStringList("member_lore") : List.of("&7Role: {role}", "&7Status: {status}");
        String onlineText = section != null ? section.getString("status_online", "&aOnline") : "&aOnline";
        String offlineText = section != null ? section.getString("status_offline", "&cOffline") : "&cOffline";

        int slot = 0;
        for (DatabaseHandler.TownMember member : members) {
            if (slot >= size) {
                break;
            }

            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member.getUuid());
            String name = offlinePlayer.getName();
            if (name == null || name.isBlank()) {
                name = member.getUuid().toString().substring(0, 8);
            }

            boolean online = offlinePlayer.isOnline();
            String statusText = online ? onlineText : offlineText;
            String roleText = formatRole(member.getRole());

            ItemStack head = SkullCreator.itemFromPlayer(offlinePlayer);
            ItemMeta meta = head.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(Utils.getColor("&e" + name));
                List<String> lore = new ArrayList<>();
                for (String line : loreTemplate) {
                    String processed = line.replace("{role}", roleText)
                            .replace("{status}", statusText);
                    lore.add(Utils.getColor(processed));
                }
                meta.setLore(lore);
                meta.getPersistentDataContainer().set(uuidKey, PersistentDataType.STRING, member.getUuid().toString());
                head.setItemMeta(meta);
            }

            inv.setItem(slot, head);
            slot++;
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        ConfigurationSection section = guiConfig.getConfigurationSection("members_gui");
        String title = Utils.getColor(section != null ? section.getString("title", "&8Town Members") : "&8Town Members");
        if (!event.getView().getTitle().equals(title)) {
            return;
        }
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        if (meta.getPersistentDataContainer().has(uuidKey, PersistentDataType.STRING)) {
            String uuidStr = meta.getPersistentDataContainer().get(uuidKey, PersistentDataType.STRING);
            if (uuidStr == null) return;
            UUID targetUuid = UUID.fromString(uuidStr);

            // Verify if the clicker is the mayor
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                Integer townId = db.getPlayerTownId(player.getUniqueId());
                if (townId != null && db.isMayor(player.getUniqueId(), townId)) {
                    // It's the mayor, open the permissions menu
                    new MemberPermissionMenu(plugin, db, permissionManager, targetUuid).open(player);
                }
            });
        }
    }

    private ItemStack createItem(String materialName, String name, List<String> lore) {
        ItemStack item;
        try {
            Material material = Material.valueOf(materialName);
            item = new ItemStack(material);
        } catch (IllegalArgumentException e) {
            item = new ItemStack(Material.PAPER);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(Utils.getColor(line));
            }
            meta.setLore(coloredLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatRole(String role) {
        if (role == null) {
            return "Member";
        }
        String lower = role.toLowerCase();
        return lower.substring(0, 1).toUpperCase() + lower.substring(1);
    }
}
