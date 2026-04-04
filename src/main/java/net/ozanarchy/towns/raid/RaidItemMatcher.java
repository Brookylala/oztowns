package net.ozanarchy.towns.raid;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;

public final class RaidItemMatcher {
    private RaidItemMatcher() {
    }

    public static boolean matches(Plugin plugin, RaidItemDefinition def, ItemStack item) {
        if (def == null || !def.enabled()) {
            return true;
        }
        if (item == null || item.getType().isAir()) {
            return false;
        }

        if (def.matchMaterialFallback() && item.getType() != def.material()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return !def.strictMeta();
        }

        if (def.requirePdc()) {
            String namespace = (def.pdcNamespace() == null || def.pdcNamespace().isBlank())
                    ? plugin.getName().toLowerCase()
                    : def.pdcNamespace().toLowerCase();
            NamespacedKey key = new NamespacedKey(namespace, def.pdcKey());
            String value = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (!Objects.equals(value, def.pdcValue())) {
                return false;
            }
        }

        if (def.matchCustomModelData()) {
            if (def.customModelData() == null || !meta.hasCustomModelData() || !Objects.equals(meta.getCustomModelData(), def.customModelData())) {
                return false;
            }
        }

        if (def.matchDisplayName()) {
            if (!meta.hasDisplayName() || !Objects.equals(meta.getDisplayName(), def.displayName())) {
                return false;
            }
        }

        if (def.matchLore()) {
            List<String> lore = meta.getLore();
            if (lore == null || !lore.equals(def.lore())) {
                return false;
            }
        }

        if (def.strictMeta()) {
            if (meta.isUnbreakable() != def.unbreakable()) {
                return false;
            }
            if (meta.hasItemFlag(ItemFlag.HIDE_UNBREAKABLE) != def.unbreakable()) {
                return false;
            }
        }

        return true;
    }
}



