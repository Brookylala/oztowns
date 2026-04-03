package net.ozanarchy.towns.raid;

import org.bukkit.Material;

import java.util.Collections;
import java.util.List;

public record RaidItemDefinition(
        boolean enabled,
        Material material,
        int amount,
        String displayName,
        List<String> lore,
        Integer customModelData,
        boolean unbreakable,
        boolean requirePdc,
        String pdcNamespace,
        String pdcKey,
        String pdcValue,
        boolean matchCustomModelData,
        boolean matchDisplayName,
        boolean matchLore,
        boolean matchMaterialFallback,
        boolean strictMeta
) {
    public RaidItemDefinition {
        lore = lore == null ? Collections.emptyList() : List.copyOf(lore);
    }
}



