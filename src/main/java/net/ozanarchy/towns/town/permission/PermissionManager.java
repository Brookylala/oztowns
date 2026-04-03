package net.ozanarchy.towns.town.permission;

import net.ozanarchy.towns.TownsPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PermissionManager {
    private final TownsPlugin plugin;
    private final Map<String, CachedPermission> permissionCache = new ConcurrentHashMap<>();

    private static volatile long CACHE_TTL_MS = 15_000L;

    public static void setCacheTtlSeconds(long ttlSeconds) {
        long safeSeconds = Math.max(1L, ttlSeconds);
        CACHE_TTL_MS = safeSeconds * 1000L;
    }

    public PermissionManager(TownsPlugin plugin) {
        this.plugin = plugin;
    }

    private String buildCacheKey(int townId, UUID playerUuid, String node) {
        return townId + ":" + playerUuid + ":" + node.toUpperCase();
    }

    private CachedPermission getActiveCacheEntry(String key) {
        CachedPermission cachedEntry = permissionCache.get(key);
        if (cachedEntry == null) {
            return null;
        }
        if (cachedEntry.expiresAtMillis < System.currentTimeMillis()) {
            permissionCache.remove(key, cachedEntry);
            return null;
        }
        return cachedEntry;
    }

    /**
     * Gets a permission value for a member in a town.
     * Uses cache if available and not expired, otherwise fetches from database and caches it.
     */
    public CompletableFuture<Boolean> getPermission(int townId, UUID playerUuid, String node) {
        String key = buildCacheKey(townId, playerUuid, node);
        CachedPermission cached = getActiveCacheEntry(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached.value);
        }

        return CompletableFuture.supplyAsync(() -> {
            boolean value = fetchPermissionFromDatabase(townId, playerUuid, node);
            cachePermission(key, value);
            return value;
        });
    }

    public boolean getPermissionSync(int townId, UUID playerUuid, String node) {
        String key = buildCacheKey(townId, playerUuid, node);
        CachedPermission cached = getActiveCacheEntry(key);
        if (cached != null) {
            return cached.value;
        }

        boolean value = fetchPermissionFromDatabase(townId, playerUuid, node);
        cachePermission(key, value);
        return value;
    }

    private boolean fetchPermissionFromDatabase(int townId, UUID playerUuid, String node) {
        String sql = "SELECT value FROM town_member_permissions WHERE town_id = ? AND player_uuid = ? AND permission_node = ? LIMIT 1";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            stmt.setString(2, playerUuid.toString());
            stmt.setString(3, node.toUpperCase());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("value");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Sets a permission value for a member in a town asynchronously.
     */
    public CompletableFuture<Void> setPermission(int townId, UUID playerUuid, String node, boolean value) {
        String key = buildCacheKey(townId, playerUuid, node);
        cachePermission(key, value);

        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO town_member_permissions (town_id, player_uuid, permission_node, value) " +
                         "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE value = ?";
            try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
                stmt.setInt(1, townId);
                stmt.setString(2, playerUuid.toString());
                stmt.setString(3, node.toUpperCase());
                stmt.setBoolean(4, value);
                stmt.setBoolean(5, value);
                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    private void cachePermission(String key, boolean value) {
        permissionCache.put(key, new CachedPermission(value, System.currentTimeMillis() + CACHE_TTL_MS));
    }

    public void invalidateTown(int townId) {
        String prefix = townId + ":";
        permissionCache.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public void invalidateMember(int townId, UUID playerUuid) {
        String prefix = townId + ":" + playerUuid + ":";
        permissionCache.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static final class CachedPermission {
        private final boolean value;
        private final long expiresAtMillis;

        private CachedPermission(boolean value, long expiresAtMillis) {
            this.value = value;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}




