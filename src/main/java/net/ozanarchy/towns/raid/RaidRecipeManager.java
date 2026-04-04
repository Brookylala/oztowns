package net.ozanarchy.towns.raid;

import net.ozanarchy.towns.TownsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RaidRecipeManager {
    private static final String LOCKPICK_RECIPE_KEY = "oztowns_raid_lockpick";

    private final TownsPlugin plugin;
    private final NamespacedKey recipeKey;

    public RaidRecipeManager(TownsPlugin plugin) {
        this.plugin = plugin;
        this.recipeKey = new NamespacedKey(plugin, LOCKPICK_RECIPE_KEY);
    }

    public void reloadRecipe() {
        Bukkit.removeRecipe(recipeKey);

        RaidConfigManager raidConfig = plugin.getRaidConfigManager();
        if (raidConfig == null || !raidConfig.lockpickRecipeEnabled()) {
            return;
        }

        List<String> shape = raidConfig.lockpickRecipeShape();
        if (shape.size() != 3 || shape.stream().anyMatch(line -> line == null || line.length() != 3)) {
            plugin.getLogger().warning("Invalid raid lockpick recipe shape. Expected exactly 3 rows with 3 characters each.");
            return;
        }

        Map<Character, Material> ingredients = raidConfig.lockpickRecipeIngredients();
        if (ingredients.isEmpty()) {
            plugin.getLogger().warning("Raid lockpick recipe is enabled but no valid ingredients were provided.");
            return;
        }

        Set<Character> usedSymbols = new HashSet<>();
        for (String row : shape) {
            for (char c : row.toCharArray()) {
                if (c != ' ') {
                    usedSymbols.add(c);
                }
            }
        }

        for (char symbol : usedSymbols) {
            if (!ingredients.containsKey(symbol)) {
                plugin.getLogger().warning("Raid lockpick recipe is missing ingredient mapping for symbol '" + symbol + "'.");
                return;
            }
        }

        ItemStack result = raidConfig.createRaidLockpickItem(1);
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, result);
        recipe.shape(shape.get(0), shape.get(1), shape.get(2));

        for (Map.Entry<Character, Material> entry : ingredients.entrySet()) {
            if (usedSymbols.contains(entry.getKey())) {
                recipe.setIngredient(entry.getKey(), entry.getValue());
            }
        }

        boolean added = Bukkit.addRecipe(recipe);
        if (!added) {
            plugin.getLogger().warning("Failed to register raid lockpick crafting recipe for key '" + LOCKPICK_RECIPE_KEY + "'.");
        }
    }

    public void unregisterRecipe() {
        Bukkit.removeRecipe(recipeKey);
    }
}



