package net.ozanarchy.towns.economy;

import java.util.UUID;
import java.util.function.Consumer;

public interface EconomyProvider {
    void add(UUID playerUuid, double amount);
    void remove(UUID playerUuid, double amount, Consumer<Boolean> callback);
    String getName();
}
