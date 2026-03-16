package net.ozanarchy.towns.economy;

import net.ozanarchy.ozanarchyEconomy.api.EconomyAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.function.Consumer;

public class OzanarchyEconomyProvider implements EconomyProvider {
    private final EconomyAPI economy;

    private OzanarchyEconomyProvider(EconomyAPI economy) {
        this.economy = economy;
    }

    public static EconomyProvider create(JavaPlugin plugin) {
        EconomyAPI api = Bukkit.getServicesManager().load(EconomyAPI.class);
        if (api == null) {
            return null;
        }
        return new OzanarchyEconomyProvider(api);
    }

    @Override
    public void add(UUID playerUuid, double amount) {
        economy.add(playerUuid, amount);
    }

    @Override
    public void remove(UUID playerUuid, double amount, Consumer<Boolean> callback) {
        economy.remove(playerUuid, amount, callback::accept);
    }

    @Override
    public String getName() {
        return "ozanarchy-economy";
    }
}
