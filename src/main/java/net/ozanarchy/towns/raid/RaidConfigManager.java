package net.ozanarchy.towns.raid;

import net.ozanarchy.towns.config.RaidConfigFileHandler;
import net.ozanarchy.towns.town.tier.TownTier;
import net.ozanarchy.towns.upkeep.UpkeepState;
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
        boolean raidFileEnabled = raidConfig.getBoolean("raid.enabled", true);
        boolean globalEnabled = true;
        try {
            if (net.ozanarchy.towns.TownsPlugin.config != null) {
                globalEnabled = net.ozanarchy.towns.TownsPlugin.config.getBoolean("features.raids", true);
            } else {
                globalEnabled = plugin.getConfig().getBoolean("features.raids", true);
            }
        } catch (RuntimeException ignored) {
            globalEnabled = true;
        }
        return raidFileEnabled && globalEnabled;
    }

    public int raidDurationSeconds() {
        return Math.max(1, raidConfig.getInt("raid.duration-seconds", 60));
    }

    public RaidStateBehaviorDefinition stateBehavior(UpkeepState state) {
        UpkeepState safeState = state == null ? UpkeepState.ACTIVE : state;
        String base = "raid.state-behavior." + safeState.name() + ".";

        RaidFlowType defaultMode = safeState == UpkeepState.DECAYING ? RaidFlowType.CLEANUP : RaidFlowType.NORMAL;
        int defaultTimer = switch (safeState) {
            case ABANDONED -> 30;
            case DECAYING -> 3;
            default -> raidDurationSeconds();
        };
        boolean defaultAlerts = safeState != UpkeepState.ABANDONED && safeState != UpkeepState.DECAYING;
        double defaultPercent = switch (safeState) {
            case ABANDONED -> 0.9;
            case DECAYING -> 0.5;
            default -> 1.0;
        };
        boolean defaultDelete = safeState == UpkeepState.DECAYING;

        RaidFlowType mode = parseFlowType(raidConfig.getString(base + "mode", defaultMode.name()), defaultMode);
        int timerSeconds = Math.max(1, raidConfig.getInt(base + "timer-seconds", defaultTimer));
        boolean alertsEnabled = raidConfig.getBoolean(base + "alerts.enabled", defaultAlerts);
        RaidPayoutMode payoutMode = parsePayoutMode(raidConfig.getString(base + "payout.mode", "PERCENT"), RaidPayoutMode.PERCENT);
        double payoutPercent = raidConfig.getDouble(base + "payout.percent", defaultPercent);
        boolean deleteTownOnSuccess = raidConfig.getBoolean(base + "delete-town-on-success", defaultDelete);

        return new RaidStateBehaviorDefinition(
                safeState,
                mode,
                timerSeconds,
                alertsEnabled,
                payoutMode,
                payoutPercent,
                deleteTownOnSuccess
        );
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

    public int raidCooldownSeconds() {
        return Math.max(0, raidConfig.getInt("raid.cooldown.seconds", 0));
    }

    public String raidCooldownMessage() {
        return raidConfig.getString("raid.cooldown.message", "&cThis town can be raided again in &f{seconds}s&c.");
    }

    public String lockpickCannotPlaceMessage() {
        return raidConfig.getString("raid.item.messages.cannot-place", "&cRaid lockpicks cannot be placed.");
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

    public RaidMinigameSettings minigameSettings(TownTier tier) {
        boolean enabled = raidConfig.getBoolean("raid.minigame.enabled", true);
        String title = raidConfig.getString("raid.minigame.gui.title", "&cRaid Breach");
        int durationTicks = Math.max(10, raidConfig.getInt("raid.minigame.duration-ticks", 50));
        int updateIntervalTicks = Math.max(1, raidConfig.getInt("raid.minigame.update-interval-ticks", 2));
        int successZoneSize = Math.max(1, raidConfig.getInt("raid.minigame.success-zone-size", 3));
        boolean closeOnSuccess = raidConfig.getBoolean("raid.minigame.close-on-success", true);
        boolean closeOnFail = raidConfig.getBoolean("raid.minigame.close-on-fail", true);

        boolean useTierScaling = raidConfig.getBoolean("raid.minigame.difficulty.use-tier-scaling", true);
        if (useTierScaling && tier != null) {
            String base = "raid.minigame.difficulty.tier-overrides." + tier.name() + ".";
            if (raidConfig.contains(base + "duration-ticks")) {
                durationTicks = Math.max(10, raidConfig.getInt(base + "duration-ticks", durationTicks));
            }
            if (raidConfig.contains(base + "update-interval-ticks")) {
                updateIntervalTicks = Math.max(1, raidConfig.getInt(base + "update-interval-ticks", updateIntervalTicks));
            }
            if (raidConfig.contains(base + "success-zone-size")) {
                successZoneSize = Math.max(1, raidConfig.getInt(base + "success-zone-size", successZoneSize));
            }
        }

        String tickSound = raidConfig.getString("raid.minigame.sounds.tick", "UI_BUTTON_CLICK");
        String successSound = raidConfig.getString("raid.minigame.sounds.success", "ENTITY_PLAYER_LEVELUP");
        String failSound = raidConfig.getString("raid.minigame.sounds.fail", "BLOCK_ANVIL_LAND");

        String successMessage = raidConfig.getString("raid.minigame.messages.success", "&aBreach success.");
        String failMessage = raidConfig.getString("raid.minigame.messages.fail", "&cBreach failed.");
        String timeoutMessage = raidConfig.getString("raid.minigame.messages.timeout", "&cBreach timed out.");
        String cancelledMessage = raidConfig.getString("raid.minigame.messages.cancelled", "&cBreach cancelled.");

        return new RaidMinigameSettings(
                enabled,
                Utils.getColor(title),
                durationTicks,
                updateIntervalTicks,
                Math.min(9, successZoneSize),
                closeOnSuccess,
                closeOnFail,
                tickSound,
                successSound,
                failSound,
                successMessage,
                failMessage,
                timeoutMessage,
                cancelledMessage
        );
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

    private RaidFlowType parseFlowType(String value, RaidFlowType fallback) {
        try {
            return RaidFlowType.valueOf(value.toUpperCase());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private RaidPayoutMode parsePayoutMode(String value, RaidPayoutMode fallback) {
        try {
            return RaidPayoutMode.valueOf(value.toUpperCase());
        } catch (Exception ignored) {
            return fallback;
        }
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



