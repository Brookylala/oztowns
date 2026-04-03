package net.ozanarchy.towns.town.gui;

import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.util.SkullCreator;
import net.ozanarchy.towns.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

import static net.ozanarchy.towns.TownsPlugin.guiConfig;

public class MainGui implements Listener {
    private final TownsPlugin plugin;

    public MainGui(TownsPlugin plugin) {
        this.plugin = plugin;
    }

    public void openGui(Player player) {
        String title = Utils.getColor(guiConfig.getString("title", "&8Town Management"));
        int size = guiConfig.getInt("size", 27);
        Inventory inv = Bukkit.createInventory(null, size, title);

        ConfigurationSection items = guiConfig.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection itemSection = items.getConfigurationSection(key);
                if (itemSection == null) continue;

                int slot = itemSection.getInt("slot");
                String name = Utils.getColor(itemSection.getString("name", " "));
                List<String> coloredLore = colorizeLines(itemSection.getStringList("lore"));
                inv.setItem(slot, createItem(itemSection, name, coloredLore, Material.PAPER));
            }
        }

        applyFiller(inv, guiConfig);
        player.openInventory(inv);
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
        List<String> coloredLore = colorizeLines(filler.getStringList("lore"));
        ItemStack fillerItem = createItem(filler, name, coloredLore, Material.GRAY_STAINED_GLASS_PANE);

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

    private List<String> colorizeLines(List<String> lines) {
        List<String> colored = new ArrayList<>(lines.size());
        for (String line : lines) {
            colored.add(Utils.getColor(line));
        }
        return colored;
    }

    private ItemStack createItem(ConfigurationSection section, String displayName, List<String> lore, Material fallback) {
        String materialName = section.getString("material", fallback.name());
        String texture = section.getString("texture");

        ItemStack item;
        if ("PLAYER_HEAD".equals(materialName) && texture != null && !texture.isEmpty()) {
            item = SkullCreator.itemFromBase64(texture);
        } else {
            try {
                item = new ItemStack(Material.valueOf(materialName));
            } catch (IllegalArgumentException e) {
                item = new ItemStack(fallback);
            }
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}




