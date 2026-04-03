package net.ozanarchy.towns.raid;

import net.ozanarchy.towns.config.RaidConfigFileHandler;
import net.ozanarchy.towns.util.Utils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RaidConfigManager {
    private final JavaPlugin plugin;
    private final RaidConfigFileHandler configHandler;
    private FileConfiguration raidConfig;

    public RaidConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configHandler = new RaidConfigFileHandler(plugin);
    }

    public void load() {
        raidConfig = configHandler.load();
    }

    public void reload() {
        raidConfig = configHandler.reload();
    }

    public void save() {
        configHandler.save();
    }

    public boolean isRaidingAllowed() {
        return raidConfig.getBoolean("raid.enabled", true);
    }

    public int raidDurationSeconds() {
        return Math.max(1, raidConfig.getInt("raid.duration-seconds", 60));
    }

    public boolean failOnDamage() {
        return raidConfig.getBoolean("raid.fail-on-damage", true);
    }

    public boolean failOnDeath() {
        return raidConfig.getBoolean("raid.fail-on-death", true);
    }

    public boolean failOnDisconnect() {
        return raidConfig.getBoolean("raid.fail-on-disconnect", true);
    }

    public boolean isWorldAllowed(String world) {
        List<String> allowed = raidConfig.getStringList("raid.worlds.allowed");
        List<String> blocked = raidConfig.getStringList("raid.worlds.blocked");
        if (!allowed.isEmpty() && allowed.stream().noneMatch(w -> w.equalsIgnoreCase(world))) {
            return false;
        }
        return blocked.stream().noneMatch(w -> w.equalsIgnoreCase(world));
    }

    public BarColor raidBarColor() {
        return parseBarColor(raidConfig.getString("raid.bossbar.color", "RED"), BarColor.RED);
    }

    public BarStyle raidBarStyle() {
        return parseBarStyle(raidConfig.getString("raid.bossbar.style", "SEGMENTED_10"), BarStyle.SEGMENTED_10);
    }

    public boolean raidBroadcastEnabled() {
        return raidConfig.getBoolean("raid.broadcast.enabled", true);
    }

    public boolean playSuccessSound() {
        return raidConfig.getBoolean("raid.sounds.success.enabled", true);
    }

    public Sound successSound() {
        String name = raidConfig.getString("raid.sounds.success.sound", "BLOCK_STONE_BREAK");
        try {
            return Sound.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return Sound.BLOCK_STONE_BREAK;
        }
    }

    public float successVolume() {
        return (float) raidConfig.getDouble("raid.sounds.success.volume", 1.0D);
    }

    public float successPitch() {
        return (float) raidConfig.getDouble("raid.sounds.success.pitch", 1.0D);
    }

    public RaidItemDefinition raidItemDefinition() {
        boolean enabled = raidConfig.getBoolean("raid.item.enabled", false);
        Material material;
        try {
            material = Material.valueOf(raidConfig.getString("raid.item.material", "TRIPWIRE_HOOK").toUpperCase());
        } catch (IllegalArgumentException ignored) {
            material = Material.TRIPWIRE_HOOK;
        }
        int amount = Math.max(1, raidConfig.getInt("raid.item.amount", 1));
        String displayName = raidConfig.getString("raid.item.display-name", "&cRaid Pick");
        List<String> lore = raidConfig.getStringList("raid.item.lore");
        Integer cmd = raidConfig.isInt("raid.item.custom-model-data") ? raidConfig.getInt("raid.item.custom-model-data") : null;
        boolean unbreakable = raidConfig.getBoolean("raid.item.unbreakable", false);
        boolean requirePdc = raidConfig.getBoolean("raid.item.identification.require-pdc", false);
        String pdcNamespace = raidConfig.getString("raid.item.identification.pdc.namespace", plugin.getName().toLowerCase());
        String pdcKey = raidConfig.getString("raid.item.identification.pdc.key", "raid_pick");
        String pdcValue = raidConfig.getString("raid.item.identification.pdc.value", "true");
        boolean matchCmd = raidConfig.getBoolean("raid.item.identification.match-custom-model-data", false);
        boolean matchDisplay = raidConfig.getBoolean("raid.item.identification.match-display-name", false);
        boolean matchLore = raidConfig.getBoolean("raid.item.identification.match-lore", false);
        boolean matchMaterial = raidConfig.getBoolean("raid.item.identification.match-material-fallback", true);
        boolean strictMeta = raidConfig.getBoolean("raid.item.identification.strict-meta", false);

        return new RaidItemDefinition(enabled, material, amount, displayName, lore, cmd, unbreakable, requirePdc, pdcNamespace, pdcKey, pdcValue, matchCmd, matchDisplay, matchLore, matchMaterial, strictMeta);
    }

    public ItemStack createRaidLockpickItem(int multiplier) {
        RaidItemDefinition def = raidItemDefinition();
        int itemAmount = Math.max(1, def.amount()) * Math.max(1, multiplier);
        ItemStack stack = new ItemStack(def.material(), itemAmount);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        if (def.displayName() != null && !def.displayName().isBlank()) {
            meta.setDisplayName(Utils.getColor(def.displayName()));
        }
        if (!def.lore().isEmpty()) {
            meta.setLore(def.lore().stream().map(Utils::getColor).toList());
        }
        if (def.customModelData() != null && def.customModelData() > 0) {
            meta.setCustomModelData(def.customModelData());
        }
        meta.setUnbreakable(def.unbreakable());
        if (def.unbreakable()) {
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        }
        if (def.requirePdc() && def.pdcKey() != null && !def.pdcKey().isBlank()) {
            String namespace = (def.pdcNamespace() == null || def.pdcNamespace().isBlank())
                    ? plugin.getName().toLowerCase()
                    : def.pdcNamespace().toLowerCase();
            NamespacedKey pdcKey = new NamespacedKey(namespace, def.pdcKey());
            String pdcValue = def.pdcValue() == null ? "" : def.pdcValue();
            meta.getPersistentDataContainer().set(pdcKey, PersistentDataType.STRING, pdcValue);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean lockpickRecipeEnabled() {
        return raidConfig.getBoolean("recipe.enabled", false);
    }

    public List<String> lockpickRecipeShape() {
        return raidConfig.getStringList("recipe.shape");
    }

    public Map<Character, Material> lockpickRecipeIngredients() {
        Map<Character, Material> ingredients = new LinkedHashMap<>();
        ConfigurationSection section = raidConfig.getConfigurationSection("recipe.ingredients");
        if (section == null) {
            return ingredients;
        }

        for (String rawKey : section.getKeys(false)) {
            if (rawKey == null || rawKey.length() != 1) {
                continue;
            }
            char key = rawKey.charAt(0);
            if (key == ' ') {
                continue;
            }
            String materialName = section.getString(rawKey, "").toUpperCase();
            Material material = Material.matchMaterial(materialName);
            if (material != null && material.isItem()) {
                ingredients.put(key, material);
            }
        }
        return ingredients;
    }

    private BarColor parseBarColor(String value, BarColor fallback) {
        try {
            return BarColor.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private BarStyle parseBarStyle(String value, BarStyle fallback) {
        try {
            return BarStyle.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}



