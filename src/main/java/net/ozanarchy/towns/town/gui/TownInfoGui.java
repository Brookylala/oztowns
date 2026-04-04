package net.ozanarchy.towns.town.gui;

import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.town.model.TownMember;
import net.ozanarchy.towns.town.tier.TownTier;
import net.ozanarchy.towns.town.tier.TownTierDefinition;
import net.ozanarchy.towns.upkeep.UpkeepState;
import net.ozanarchy.towns.upkeep.repository.UpkeepRepository;
import net.ozanarchy.towns.util.GuiItemFactory;
import net.ozanarchy.towns.util.Utils;
import net.ozanarchy.towns.util.db.DatabaseHandler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.ozanarchy.towns.TownsPlugin.guiConfig;
import static net.ozanarchy.towns.TownsPlugin.messagesConfig;

public class TownInfoGui implements Listener {
    private final TownsPlugin plugin;
    private final DatabaseHandler db;
    private final UpkeepRepository upkeepRepository;
    private final DecimalFormat moneyFormat = new DecimalFormat("0.##");

    public TownInfoGui(TownsPlugin plugin, DatabaseHandler db) {
        this.plugin = plugin;
        this.db = db;
        this.upkeepRepository = new UpkeepRepository(plugin);
    }

    public void openForSelf(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getPlayerTownId(player.getUniqueId());
            if (townId == null) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString("messages.notown")))
                );
                return;
            }
            openForTownId(player, townId);
        });
    }

    public void openForTownName(Player player, String townName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getTownIdByName(townName);
            if (townId == null) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString("messages.notownexists")))
                );
                return;
            }
            openForTownId(player, townId);
        });
    }

    private void openForTownId(Player viewer, int townId) {
        Map<String, String> placeholders = buildPlaceholders(townId);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!viewer.isOnline()) {
                return;
            }
            ConfigurationSection section = guiConfig.getConfigurationSection("town_info_gui");
            String titleTemplate = section != null ? section.getString("title", "&8Town Info: {town}") : "&8Town Info: {town}";
            String title = Utils.getColor(applyPlaceholders(titleTemplate, placeholders));
            int size = section != null ? section.getInt("size", 27) : 27;
            Inventory inventory = Bukkit.createInventory(null, size, title);

            if (section != null) {
                ConfigurationSection items = section.getConfigurationSection("items");
                if (items != null) {
                    for (String key : items.getKeys(false)) {
                        ConfigurationSection item = items.getConfigurationSection(key);
                        if (item == null) {
                            continue;
                        }
                        int slot = item.getInt("slot");
                        String displayName = Utils.getColor(applyPlaceholders(item.getString("name", " "), placeholders));
                        List<String> lore = colorizeLines(item.getStringList("lore"), placeholders);
                        inventory.setItem(slot, GuiItemFactory.createItem(item, displayName, lore, Material.PAPER));
                    }
                }
                applyFiller(inventory, section);
            }

            viewer.openInventory(inventory);
        });
    }

    private Map<String, String> buildPlaceholders(int townId) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("town", safe(db.getTownName(townId), "Unknown"));
        List<TownMember> members = db.getTownMembers(townId);
        placeholders.put("members", String.valueOf(members.size()));
        placeholders.put("claims", String.valueOf(db.getClaimCount(townId)));
        placeholders.put("balance", moneyFormat.format(db.getTownBalance(townId)));

        String mayor = "Unknown";
        for (TownMember member : members) {
            if (!"MAYOR".equalsIgnoreCase(member.getRole())) {
                continue;
            }
            OfflinePlayer mayorPlayer = Bukkit.getOfflinePlayer(member.getUuid());
            if (mayorPlayer.getName() != null && !mayorPlayer.getName().isBlank()) {
                mayor = mayorPlayer.getName();
                break;
            }
            mayor = member.getUuid().toString();
        }
        placeholders.put("mayor", mayor);

        if (plugin.getTownTierService() != null) {
            TownTier currentTier = plugin.getTownTierService().getTownTier(townId);
            TownTierDefinition definition = plugin.getTownTierService().getDefinition(currentTier);
            double progress = plugin.getTownTierService().getTierProgress(townId);
            double required = Math.max(0.0, definition.upgradeProgressRequired());
            TownTier nextTier = plugin.getTownTierService().getNextTier(currentTier);
            boolean hasNext = nextTier.level() > currentTier.level();
            String nextTierDisplay = hasNext
                    ? stripColor(plugin.getTownTierService().getDefinition(nextTier).displayName())
                    : "MAX";

            placeholders.put("tier-name", stripColor(definition.displayName()));
            placeholders.put("tier-progress", String.valueOf((int) Math.floor(progress)));
            placeholders.put("tier-required", String.valueOf((int) Math.floor(required)));
            placeholders.put("next-tier", nextTierDisplay);
        } else {
            placeholders.put("tier-name", "Unknown");
            placeholders.put("tier-progress", "0");
            placeholders.put("tier-required", "0");
            placeholders.put("next-tier", "Unknown");
        }

        UpkeepState upkeepState = upkeepRepository.getUpkeepState(townId);
        placeholders.put("upkeep-state", formatState(upkeepState));
        return placeholders;
    }

    private List<String> colorizeLines(List<String> lines, Map<String, String> placeholders) {
        List<String> rendered = new ArrayList<>(lines.size());
        for (String line : lines) {
            rendered.add(Utils.getColor(applyPlaceholders(line, placeholders)));
        }
        return rendered;
    }

    private String applyPlaceholders(String input, Map<String, String> placeholders) {
        String value = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return value;
    }

    private void applyFiller(Inventory inventory, ConfigurationSection root) {
        ConfigurationSection filler = root.getConfigurationSection("filler");
        if (filler == null) {
            return;
        }
        String fillerName = Utils.getColor(filler.getString("name", " "));
        List<String> lore = colorizeLines(filler.getStringList("lore"), Map.of());
        org.bukkit.inventory.ItemStack fillerItem = GuiItemFactory.createItem(filler, fillerName, lore, Material.GRAY_STAINED_GLASS_PANE);

        if (filler.getBoolean("fill", false)) {
            for (int i = 0; i < inventory.getSize(); i++) {
                org.bukkit.inventory.ItemStack existing = inventory.getItem(i);
                if (existing == null || existing.getType() == Material.AIR) {
                    inventory.setItem(i, fillerItem.clone());
                }
            }
        }

        for (Integer slot : filler.getIntegerList("slots")) {
            if (slot == null || slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            inventory.setItem(slot, fillerItem.clone());
        }
    }

    private String safe(String input, String fallback) {
        return input == null || input.isBlank() ? fallback : input;
    }

    private String stripColor(String text) {
        return org.bukkit.ChatColor.stripColor(Utils.getColor(text == null ? "" : text));
    }

    private String formatState(UpkeepState state) {
        if (state == null) {
            return "Unknown";
        }
        String lower = state.name().toLowerCase();
        return lower.substring(0, 1).toUpperCase() + lower.substring(1);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        ConfigurationSection section = guiConfig.getConfigurationSection("town_info_gui");
        if (section == null) {
            return;
        }
        String template = Utils.getColor(section.getString("title", "&8Town Info: {town}"));
        String expectedPrefix = template.contains("{town}") ? template.substring(0, template.indexOf("{town}")) : template;
        String strippedView = org.bukkit.ChatColor.stripColor(event.getView().getTitle());
        String strippedPrefix = org.bukkit.ChatColor.stripColor(expectedPrefix);
        if (strippedPrefix == null || strippedView == null || !strippedView.startsWith(strippedPrefix)) {
            return;
        }
        event.setCancelled(true);
    }
}
