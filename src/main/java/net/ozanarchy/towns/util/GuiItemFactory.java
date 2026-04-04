package net.ozanarchy.towns.util;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class GuiItemFactory {
    private GuiItemFactory() {
    }

    public static ItemStack createItem(ConfigurationSection section, String displayName, List<String> lore, Material fallback) {
        String materialName = section.getString("material", fallback.name());
        String texture = section.getString("texture");

        ItemStack item;
        if ("PLAYER_HEAD".equalsIgnoreCase(materialName) && texture != null && !texture.isEmpty()) {
            item = SkullCreator.itemFromBase64(texture);
        } else {
            try {
                item = new ItemStack(Material.valueOf(materialName.toUpperCase()));
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
