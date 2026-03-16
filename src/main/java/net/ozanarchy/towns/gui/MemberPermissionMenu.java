package net.ozanarchy.towns.gui;

import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.handlers.DatabaseHandler;
import net.ozanarchy.towns.handlers.PermissionManager;
import net.ozanarchy.towns.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static net.ozanarchy.towns.TownsPlugin.guiConfig;

public class MemberPermissionMenu implements InventoryHolder, Listener {
    private final TownsPlugin plugin;
    private final DatabaseHandler db;
    private final PermissionManager permissionManager;
    private final UUID targetUuid;
    private final String targetName;

    public MemberPermissionMenu(TownsPlugin plugin, DatabaseHandler db, PermissionManager permissionManager, UUID targetUuid) {
        this.plugin = plugin;
        this.db = db;
        this.permissionManager = permissionManager;
        this.targetUuid = targetUuid;
        if (targetUuid != null) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(targetUuid);
            this.targetName = op.getName() != null ? op.getName() : targetUuid.toString().substring(0, 8);
        } else {
            this.targetName = "None";
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Bukkit.createInventory(this, 27, "Permissions: " + targetName);
    }

    public void open(Player mayor) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getPlayerTownId(mayor.getUniqueId());
            if (townId == null || !db.isMayor(mayor.getUniqueId(), townId)) {
                return; // Only mayors can manage permissions
            }

            ConfigurationSection section = guiConfig.getConfigurationSection("permissions_gui");
            String title = Utils.getColor(section != null ? section.getString("title", "&8Permissions: {player}").replace("{player}", targetName) : "&8Permissions: " + targetName);
            int size = section != null ? section.getInt("size", 27) : 27;

            Inventory inv = Bukkit.createInventory(this, size, title);

            // Filler
            if (section != null && section.getBoolean("filler.fill", true)) {
                String fillerMat = section.getString("filler.material", "GRAY_STAINED_GLASS_PANE");
                String fillerName = Utils.getColor(section.getString("filler.name", " "));
                ItemStack filler = createItem(fillerMat, fillerName, new ArrayList<>(), false);
                for (int i = 0; i < size; i++) {
                    inv.setItem(i, filler);
                }
            }

            // Permissions
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            futures.add(addPermissionItem(inv, townId, "CAN_CLAIM", "can_claim"));
            futures.add(addPermissionItem(inv, townId, "CAN_UNCLAIM", "can_unclaim"));
            futures.add(addPermissionItem(inv, townId, "CAN_WITHDRAW", "can_withdraw"));
            futures.add(addPermissionItem(inv, townId, "CAN_INVITE", "can_invite"));
            futures.add(addPermissionItem(inv, townId, "CAN_BUILD", "can_build"));
            futures.add(addPermissionItem(inv, townId, "CAN_INTERACT", "can_interact"));

            // Back button
            if (section != null && section.contains("back_button")) {
                ConfigurationSection back = section.getConfigurationSection("back_button");
                int slot = back.getInt("slot", 18);
                inv.setItem(slot, createItem(back.getString("material", "ARROW"), Utils.getColor(back.getString("name", "&cBack")), back.getStringList("lore"), false));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
                Bukkit.getScheduler().runTask(plugin, () -> mayor.openInventory(inv));
            });
        });
    }

    private CompletableFuture<Void> addPermissionItem(Inventory inv, int townId, String node, String configKey) {
        return permissionManager.getPermission(townId, targetUuid, node).thenAccept(value -> {
            ConfigurationSection section = guiConfig.getConfigurationSection("permissions_gui.items." + configKey);
            if (section == null) return;

            int slot = section.getInt("slot");
            String material = section.getString("material", "PAPER");
            String name = Utils.getColor(section.getString("name", node));
            List<String> loreTemplate = section.getStringList("lore");
            List<String> lore = new ArrayList<>();
            String status = value ? "&aEnabled" : "&cDisabled";
            for (String line : loreTemplate) {
                lore.add(Utils.getColor(line.replace("{status}", status)));
            }

            ItemStack item = createItem(material, name, lore, value);
            inv.setItem(slot, item);
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MemberPermissionMenu menu)) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        ConfigurationSection section = guiConfig.getConfigurationSection("permissions_gui");
        if (section == null) return;

        // Back button check
        if (section.contains("back_button") && slot == section.getInt("back_button.slot", 18)) {
            new MembersGui(plugin, db, permissionManager).openGui(player);
            return;
        }

        // Permission toggle check
        ConfigurationSection items = section.getConfigurationSection("items");
        if (items == null) return;

        for (String key : items.getKeys(false)) {
            ConfigurationSection itemSection = items.getConfigurationSection(key);
            if (itemSection != null && itemSection.getInt("slot") == slot) {
                String node = key.toUpperCase();
                menu.togglePermission(player, node, slot);
                return;
            }
        }
    }

    private void togglePermission(Player mayor, String node, int slot) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Integer townId = db.getPlayerTownId(mayor.getUniqueId());
            if (townId == null || !db.isMayor(mayor.getUniqueId(), townId)) return;

            permissionManager.getPermission(townId, targetUuid, node).thenAccept(currentValue -> {
                boolean newValue = !currentValue;
                permissionManager.setPermission(townId, targetUuid, node, newValue).thenRun(() -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        mayor.playSound(mayor.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                        updateItem(mayor.getOpenInventory().getTopInventory(), townId, node, slot, newValue);
                    });
                });
            });
        });
    }

    private void updateItem(Inventory inv, int townId, String node, int slot, boolean newValue) {
        String configKey = node.toLowerCase();
        ConfigurationSection section = guiConfig.getConfigurationSection("permissions_gui.items." + configKey);
        if (section == null) return;

        String material = section.getString("material", "PAPER");
        String name = Utils.getColor(section.getString("name", node));
        List<String> loreTemplate = section.getStringList("lore");
        List<String> lore = new ArrayList<>();
        String status = newValue ? "&aEnabled" : "&cDisabled";
        for (String line : loreTemplate) {
            lore.add(Utils.getColor(line.replace("{status}", status)));
        }

        inv.setItem(slot, createItem(material, name, lore, newValue));
    }

    private ItemStack createItem(String materialName, String name, List<String> lore, boolean enchanted) {
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            material = Material.PAPER;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            if (enchanted) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
