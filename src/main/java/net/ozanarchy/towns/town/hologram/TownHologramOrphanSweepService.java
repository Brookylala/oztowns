package net.ozanarchy.towns.town.hologram;

import eu.decentsoftware.holograms.api.DHAPI;
import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.util.DebugLogger;
import net.ozanarchy.towns.util.db.DatabaseHandler;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.ozanarchy.towns.TownsPlugin.hologramsConfig;

public class TownHologramOrphanSweepService {
    private final TownsPlugin plugin;
    private final DatabaseHandler db;

    public TownHologramOrphanSweepService(TownsPlugin plugin, DatabaseHandler db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void runStartupSweep() {
        if (!isEnabled()) {
            return;
        }

        String idPrefix = getIdPrefix();
        String requiredPrefix = idPrefix + "town_spawn_";
        int removed = 0;

        for (String hologramId : discoverAllHologramIds()) {
            if (hologramId == null || !hologramId.startsWith(requiredPrefix)) {
                continue;
            }

            String suffix = hologramId.substring(requiredPrefix.length());
            if (!suffix.matches("\\d+")) {
                DebugLogger.debug(plugin, "Skipping malformed town hologram id '" + hologramId + "' (expected numeric town id suffix).");
                continue;
            }

            int townId = Integer.parseInt(suffix);
            if (db.townExists(townId)) {
                continue;
            }

            DHAPI.removeHologram(hologramId);
            removed++;
            DebugLogger.debug(plugin, "Removed orphan town hologram '" + hologramId + "' (missing towns.id=" + townId + ").");
        }

        if (removed > 0) {
            plugin.getLogger().info("Removed " + removed + " orphan town spawn hologram(s) on startup.");
        }
    }

    private boolean isEnabled() {
        FileConfiguration cfg = hologramsConfig;
        return cfg != null
                && cfg.getBoolean("holograms.enabled", true)
                && Bukkit.getPluginManager().isPluginEnabled("DecentHolograms");
    }

    private String getIdPrefix() {
        FileConfiguration cfg = hologramsConfig;
        if (cfg == null) {
            return "oztowns_";
        }
        return cfg.getString("holograms.id-prefix", "oztowns_");
    }

    @SuppressWarnings("unchecked")
    private List<String> discoverAllHologramIds() {
        List<String> ids = new ArrayList<>();
        try {
            Method[] methods = DHAPI.class.getMethods();
            for (Method method : methods) {
                if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0) {
                    continue;
                }

                Object value = method.invoke(null);
                if (value == null) {
                    continue;
                }

                if (value instanceof Map<?, ?> map) {
                    for (Object key : map.keySet()) {
                        if (key instanceof String keyString) {
                            ids.add(keyString);
                        }
                    }
                } else if (value instanceof Collection<?> collection) {
                    for (Object item : collection) {
                        String id = resolveHologramId(item);
                        if (id != null) {
                            ids.add(id);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to inspect DecentHolograms registry for orphan sweep: " + ex.getMessage());
        }
        return dedupe(ids);
    }

    private String resolveHologramId(Object object) {
        if (object == null) {
            return null;
        }
        if (object instanceof String stringValue) {
            return stringValue;
        }
        for (String methodName : List.of("getName", "getId")) {
            try {
                Method method = object.getClass().getMethod(methodName);
                if (method.getParameterCount() != 0) {
                    continue;
                }
                Object value = method.invoke(object);
                if (value instanceof String id && !id.isBlank()) {
                    return id;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private List<String> dedupe(List<String> ids) {
        Set<String> unique = new java.util.LinkedHashSet<>(ids);
        return new ArrayList<>(unique);
    }
}
