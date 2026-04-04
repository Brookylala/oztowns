package net.ozanarchy.towns.bank.gui;

import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.bank.repository.TownBankRepository;
import net.ozanarchy.towns.upkeep.repository.UpkeepRepository;
import net.ozanarchy.towns.util.db.DatabaseHandler;
import net.ozanarchy.towns.util.GuiItemFactory;
import net.ozanarchy.towns.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.ozanarchy.towns.TownsPlugin.guiConfig;
import static net.ozanarchy.towns.TownsPlugin.messagesConfig;

public class BankGui implements Listener {
    private enum PendingAction {
        DEPOSIT,
        WITHDRAW
    }

    private static final long PENDING_TIMEOUT_MS = 30_000L;
    private static final long REOPEN_DELAY_TICKS = 20L;

    private static final class PendingRequest {
        private final PendingAction action;
        private final long createdAtMs;

        private PendingRequest(PendingAction action, long createdAtMs) {
            this.action = action;
            this.createdAtMs = createdAtMs;
        }

        private boolean isExpired(long nowMs) {
            return nowMs - createdAtMs >= PENDING_TIMEOUT_MS;
        }
    }

    private final TownsPlugin plugin;
    private final TownBankRepository townBankRepository;
    private final UpkeepRepository upkeepRepository;
    private final Map<UUID, PendingRequest> pendingActions = new ConcurrentHashMap<>();

    public BankGui(TownsPlugin plugin) {
        this.plugin = plugin;
        this.townBankRepository = new TownBankRepository(plugin);
        this.upkeepRepository = new UpkeepRepository(plugin);
    }

    public void openGui(Player player) {
        ConfigurationSection bankSection = guiConfig.getConfigurationSection("bank_gui");
        if (bankSection == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseHandler db = new DatabaseHandler(plugin);
            Integer townId = db.getPlayerTownId(player.getUniqueId());
            double balance = 0;
            double upkeep = 0;
            if (townId != null) {
                balance = townBankRepository.getTownBalance(townId);
                upkeep = upkeepRepository.getTownUpkeep(townId);
            }

            double finalBalance = balance;
            double finalUpkeep = upkeep;
            Bukkit.getScheduler().runTask(plugin, () -> {
                String title = Utils.getColor(bankSection.getString("title", "&8Town Bank"));
                int size = bankSection.getInt("size", 27);
                Inventory inv = Bukkit.createInventory(null, size, title);

                ConfigurationSection items = bankSection.getConfigurationSection("items");
                if (items != null) {
                    for (String key : items.getKeys(false)) {
                        ConfigurationSection itemSection = items.getConfigurationSection(key);
                        if (itemSection == null) continue;

                        int slot = itemSection.getInt("slot");
                        String name = Utils.getColor(itemSection.getString("name", " "));
                        List<String> coloredLore = new ArrayList<>();
                        for (String line : itemSection.getStringList("lore")) {
                            String processedLine = line.replace("{balance}", String.valueOf(finalBalance))
                                    .replace("{upkeep}", String.valueOf(finalUpkeep));
                            coloredLore.add(Utils.getColor(processedLine));
                        }
                        inv.setItem(slot, GuiItemFactory.createItem(itemSection, name, coloredLore, Material.PAPER));
                    }
                }

                applyFiller(inv, bankSection);
                player.openInventory(inv);
            });
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        ConfigurationSection bankSection = guiConfig.getConfigurationSection("bank_gui");
        if (bankSection == null) return;

        String title = Utils.getColor(bankSection.getString("title", "&8Town Bank"));
        if (event.getView().getTitle().equals(title)) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            ConfigurationSection items = bankSection.getConfigurationSection("items");
            if (items != null) {
                for (String key : items.getKeys(false)) {
                    ConfigurationSection itemSection = items.getConfigurationSection(key);
                    if (itemSection == null) continue;

                    if (event.getSlot() == itemSection.getInt("slot")) {
                        String command = itemSection.getString("command");
                        if (command != null && !command.isEmpty()) {
                            String normalized = command.trim().toLowerCase();
                            if (normalized.equals("townbank deposit")) {
                                requestAmount(player, PendingAction.DEPOSIT);
                                return;
                            }
                            if (normalized.equals("townbank withdraw")) {
                                requestAmount(player, PendingAction.WITHDRAW);
                                return;
                            }

                            player.closeInventory();
                            player.performCommand(command);
                        }
                        break;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        PendingRequest request = pendingActions.remove(playerId);
        if (request == null) {
            return;
        }

        event.setCancelled(true);
        long nowMs = System.currentTimeMillis();
        if (request.isExpired(nowMs)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                String msg = messagesConfig.getString("messages.bankamounttimeout", "&cBank input timed out.");
                event.getPlayer().sendMessage(Utils.getColor(Utils.prefix() + msg));
                openGui(event.getPlayer());
            });
            return;
        }

        String input = event.getMessage().trim();

        if (input.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, () -> openGui(event.getPlayer()));
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(input);
        } catch (NumberFormatException e) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                event.getPlayer().sendMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString("messages.invalidamount")));
                openGui(event.getPlayer());
            });
            return;
        }

        if (amount < 1) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                event.getPlayer().sendMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString("messages.invalidamount")));
                openGui(event.getPlayer());
            });
            return;
        }

        String command = (request.action == PendingAction.DEPOSIT)
                ? "townbank deposit " + amount
                : "townbank withdraw " + amount;

        Bukkit.getScheduler().runTask(plugin, () -> {
            event.getPlayer().performCommand(command);
            Bukkit.getScheduler().runTaskLater(plugin, () -> openGui(event.getPlayer()), REOPEN_DELAY_TICKS);
        });
    }

    private void requestAmount(Player player, PendingAction action) {
        UUID playerId = player.getUniqueId();
        PendingRequest existing = pendingActions.get(playerId);
        long nowMs = System.currentTimeMillis();

        if (existing != null && !existing.isExpired(nowMs)) {
            String msg = messagesConfig.getString("messages.bankamountpending", "&ePlease finish your previous bank input or type cancel.");
            player.sendMessage(Utils.getColor(Utils.prefix() + msg));
            return;
        }

        pendingActions.put(playerId, new PendingRequest(action, nowMs));
        player.closeInventory();

        String messageKey = (action == PendingAction.DEPOSIT)
                ? "messages.depositprompt"
                : "messages.withdrawprompt";
        String prompt = messagesConfig.getString(messageKey, "&ePlease enter an amount.");
        player.sendMessage(Utils.getColor(Utils.prefix() + prompt));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingRequest current = pendingActions.get(playerId);
            if (current != null && current.action == action && current.isExpired(System.currentTimeMillis())) {
                pendingActions.remove(playerId, current);
            }
        }, (PENDING_TIMEOUT_MS / 1000L) * 20L);
    }

    private void applyFiller(Inventory inv, ConfigurationSection section) {
        if (section == null) return;
        ConfigurationSection filler = section.getConfigurationSection("filler");
        if (filler == null) return;

        String name = Utils.getColor(filler.getString("name", " "));
        List<String> coloredLore = colorizeLines(filler.getStringList("lore"));
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

    private List<String> colorizeLines(List<String> lines) {
        List<String> colored = new ArrayList<>(lines.size());
        for (String line : lines) {
            colored.add(Utils.getColor(line));
        }
        return colored;
    }

}




