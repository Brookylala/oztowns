package net.ozanarchy.towns.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.function.Consumer;

public class VaultEconomyProvider implements EconomyProvider {
    private final Economy economy;

    private VaultEconomyProvider(Economy economy) {
        this.economy = economy;
    }

    public static EconomyProvider create(JavaPlugin plugin) {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null || rsp.getProvider() == null) {
            return null;
        }
        return new VaultEconomyProvider(rsp.getProvider());
    }

    @Override
    public void add(UUID playerUuid, double amount) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
        economy.depositPlayer(player, amount);
    }

    @Override
    public void remove(UUID playerUuid, double amount, Consumer<Boolean> callback) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
        boolean success = economy.withdrawPlayer(player, amount).transactionSuccess();
        callback.accept(success);
    }

    @Override
    public String getName() {
        return "vault";
    }
}



