package net.ozanarchy.towns.town.gui;

import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.town.tier.TownTier;
import net.ozanarchy.towns.town.tier.TownTierDefinition;
import net.ozanarchy.towns.upkeep.UpkeepState;
import net.ozanarchy.towns.upkeep.repository.UpkeepRepository;
import net.ozanarchy.towns.util.GuiItemFactory;
import net.ozanarchy.towns.util.Utils;
import net.ozanarchy.towns.util.db.DatabaseHandler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.ozanarchy.towns.TownsPlugin.guiConfig;

public class MainGui implements Listener {
    private final TownsPlugin plugin;
    private final DatabaseHandler db;
    private final UpkeepRepository upkeepRepository;

    public MainGui(TownsPlugin plugin) {
        this.plugin = plugin;
        this.db = new DatabaseHandler(plugin);
        this.upkeepRepository = new UpkeepRepository(plugin);
    }

    public void openGui(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, String> placeholders = buildTownPlaceholders(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                String title = Utils.getColor(guiConfig.getString("title", "&8Town Management"));
                int size = guiConfig.getInt("size", 27);
                Inventory inv = Bukkit.createInventory(null, size, title);

                ConfigurationSection items = guiConfig.getConfigurationSection("items");
                if (items != null) {
                    for (String key : items.getKeys(false)) {
                        ConfigurationSection itemSection = items.getConfigurationSection(key);
                        if (itemSection == null) continue;

                        int slot = itemSection.getInt("slot");
                        String name = Utils.getColor(applyPlaceholders(itemSection.getString("name", " "), placeholders));
                        List<String> coloredLore = colorizeLines(itemSection.getStringList("lore"), placeholders);
                        inv.setItem(slot, GuiItemFactory.createItem(itemSection, name, coloredLore, Material.PAPER));
                    }
                }

                applyFiller(inv, guiConfig);
                player.openInventory(inv);
            });
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = Utils.getColor(guiConfig.getString("title", "&8Town Management"));
        if (event.getView().getTitle().equals(title)) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            ConfigurationSection items = guiConfig.getConfigurationSection("items");
            if (items != null) {
                for (String key : items.getKeys(false)) {
                    ConfigurationSection itemSection = items.getConfigurationSection(key);
                    if (itemSection == null) continue;

                    if (event.getSlot() == itemSection.getInt("slot")) {
                        String command = itemSection.getString("command");
                        if (command != null && !command.isEmpty()) {
                            player.closeInventory();
                            player.performCommand(command);
                        }
                        break;
                    }
                }
            }
        }
    }

    private void applyFiller(Inventory inv, ConfigurationSection root) {
        if (root == null) return;
        ConfigurationSection filler = root.getConfigurationSection("filler");
        if (filler == null) return;

        String materialName = filler.getString("material", "GRAY_STAINED_GLASS_PANE");
        String name = Utils.getColor(filler.getString("name", " "));
        List<String> coloredLore = colorizeLines(filler.getStringList("lore"), Map.of());
        ItemStack fillerItem = GuiItemFactory.createItem(filler, name, coloredLore, Material.GRAY_STAINED_GLASS_PANE);

        boolean fill = filler.getBoolean("fill", false);
        List<Integer> slots = filler.getIntegerList("slots");

        if (fill) {
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack existing = inv.getItem(i);
                if (existing == null || existing.getType() == Material.AIR) {
                    inv.setItem(i, fillerItem.clone());
                }
            }
        }

        for (Integer slot : slots) {
            if (slot == null) continue;
            if (slot < 0 || slot >= inv.getSize()) continue;
            inv.setItem(slot, fillerItem.clone());
        }
    }

    private List<String> colorizeLines(List<String> lines, Map<String, String> placeholders) {
        List<String> colored = new ArrayList<>(lines.size());
        for (String line : lines) {
            colored.add(Utils.getColor(applyPlaceholders(line, placeholders)));
        }
        return colored;
    }

    private String applyPlaceholders(String input, Map<String, String> placeholders) {
        String value = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return value;
    }

    private Map<String, String> buildTownPlaceholders(java.util.UUID playerId) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("tier-name", "Unranked");
        placeholders.put("tier-progress", "0");
        placeholders.put("tier-required", "0");
        placeholders.put("upkeep-state", "N/A");

        Integer townId = db.getPlayerTownId(playerId);
        if (townId == null) {
            return placeholders;
        }

        if (plugin.getTownTierService() != null) {
            TownTier tier = plugin.getTownTierService().getTownTier(townId);
            TownTierDefinition definition = plugin.getTownTierService().getDefinition(tier);
            double progress = plugin.getTownTierService().getTierProgress(townId);
            placeholders.put("tier-name", stripColor(definition.displayName()));
            placeholders.put("tier-progress", String.valueOf((int) Math.floor(progress)));
            placeholders.put("tier-required", String.valueOf((int) Math.floor(Math.max(0.0, definition.upgradeProgressRequired()))));
        }

        UpkeepState upkeepState = upkeepRepository.getUpkeepState(townId);
        placeholders.put("upkeep-state", formatState(upkeepState));
        return placeholders;
    }

    private String formatState(UpkeepState state) {
        if (state == null) {
            return "Unknown";
        }
        String raw = state.name().toLowerCase();
        return raw.substring(0, 1).toUpperCase() + raw.substring(1);
    }

    private String stripColor(String text) {
        if (text == null) {
            return "";
        }
        return org.bukkit.ChatColor.stripColor(Utils.getColor(text));
    }
}




