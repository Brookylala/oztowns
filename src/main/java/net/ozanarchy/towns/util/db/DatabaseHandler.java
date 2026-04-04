package net.ozanarchy.towns.util.db;

import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.bank.repository.TownBankRepository;
import net.ozanarchy.towns.town.model.TownMember;
import net.ozanarchy.towns.town.repository.TownLifecycleRepository;
import net.ozanarchy.towns.town.permission.PermissionManager;
import net.ozanarchy.towns.upkeep.repository.UpkeepRepository;
import org.bukkit.Chunk;
import org.bukkit.Location;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DatabaseHandler {
    private static volatile long CACHE_TTL_MS = 15_000L;
    private static final int NULL_TOWN_ID = -1;

    private static final Map<String, Integer> claimTownCache = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> playerTownCache = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> playerTownCacheExpiresAt = new ConcurrentHashMap<>();
    private static final Map<UUID, String> memberRoleCache = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> memberRoleCacheExpiresAt = new ConcurrentHashMap<>();
    private static final Map<Integer, String> townNameByIdCache = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> townNameByIdCacheExpiresAt = new ConcurrentHashMap<>();
    private static final Map<String, Integer> townIdByNameCache = new ConcurrentHashMap<>();
    private static final Map<String, Long> townIdByNameCacheExpiresAt = new ConcurrentHashMap<>();
    private static final Map<Integer, Location> townSpawnCache = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> townSpawnCacheExpiresAt = new ConcurrentHashMap<>();
    private static final Map<Integer, List<TownMember>> townMembersCache = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> townMembersCacheExpiresAt = new ConcurrentHashMap<>();

    private final TownsPlugin plugin;
    private final TownBankRepository townBankRepository;
    private final UpkeepRepository upkeepRepository;
    private final TownLifecycleRepository townLifecycleRepository;

    public DatabaseHandler(TownsPlugin plugin) {
        this.plugin = plugin;
        this.townBankRepository = new TownBankRepository(plugin);
        this.upkeepRepository = new UpkeepRepository(plugin);
        this.townLifecycleRepository = new TownLifecycleRepository(plugin);
    }

    public static void setCacheTtlSeconds(long ttlSeconds) {
        long safeSeconds = Math.max(1L, ttlSeconds);
        CACHE_TTL_MS = safeSeconds * 1000L;
    }

    private boolean isExpired(Map<?, Long> expiresAtMap, Object key) {
        Long expiresAt = expiresAtMap.get(key);
        return expiresAt == null || expiresAt < System.currentTimeMillis();
    }

    private void cacheWithTtl(Map<UUID, Integer> map, Map<UUID, Long> expiresAtMap, UUID key, Integer value) {
        map.put(key, value == null ? NULL_TOWN_ID : value);
        expiresAtMap.put(key, System.currentTimeMillis() + CACHE_TTL_MS);
    }

    private String chunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    private void cacheTownIdentity(int townId, String townName) {
        if (townName == null) {
            return;
        }
        String normalized = townName.toLowerCase();
        long expiresAt = System.currentTimeMillis() + CACHE_TTL_MS;
        townNameByIdCache.put(townId, townName);
        townNameByIdCacheExpiresAt.put(townId, expiresAt);
        townIdByNameCache.put(normalized, townId);
        townIdByNameCacheExpiresAt.put(normalized, expiresAt);
    }

    private void cacheTownSpawn(int townId, Location location) {
        if (location == null) {
            townSpawnCache.remove(townId);
            townSpawnCacheExpiresAt.remove(townId);
            return;
        }
        townSpawnCache.put(townId, location);
        townSpawnCacheExpiresAt.put(townId, System.currentTimeMillis() + CACHE_TTL_MS);
    }

    private void cacheMemberContext(UUID uuid, int townId, String role) {
        cacheWithTtl(playerTownCache, playerTownCacheExpiresAt, uuid, townId);
        if (role != null) {
            memberRoleCache.put(uuid, role);
            memberRoleCacheExpiresAt.put(uuid, System.currentTimeMillis() + CACHE_TTL_MS);
        }
    }

    private void invalidatePermissionCacheForTown(int townId) {
        PermissionManager permissionManager = plugin.getPermissionManager();
        if (permissionManager != null) {
            permissionManager.invalidateTown(townId);
        }
    }

    private void invalidatePermissionCacheForMember(int townId, UUID uuid) {
        PermissionManager permissionManager = plugin.getPermissionManager();
        if (permissionManager != null) {
            permissionManager.invalidateMember(townId, uuid);
        }
    }

    private void invalidatePlayerTown(UUID uuid) {
        playerTownCache.remove(uuid);
        playerTownCacheExpiresAt.remove(uuid);
        memberRoleCache.remove(uuid);
        memberRoleCacheExpiresAt.remove(uuid);
    }

    private void invalidateTownIdentity(int townId) {
        String cachedName = townNameByIdCache.remove(townId);
        townNameByIdCacheExpiresAt.remove(townId);
        if (cachedName != null) {
            townIdByNameCache.remove(cachedName.toLowerCase());
            townIdByNameCacheExpiresAt.remove(cachedName.toLowerCase());
        }
    }

    private void invalidateTownMembers(int townId) {
        townMembersCache.remove(townId);
        townMembersCacheExpiresAt.remove(townId);
    }

    // ==========================================
    // CHUNK CLAIMS
    // ==========================================

    /**
     * Checks if a specific chunk is claimed by any town.
     */
    public boolean getChunkClaimed(Chunk chunk){
        return getChunkTownId(chunk) != null;
    }

    /**
     * Saves a new claim for a town.
     */
    public void saveClaim(Chunk chunk, int townID){
        String sql = """
            INSERT INTO claims (world, chunkx, chunkz, town_id)
            VALUES (?, ?, ?, ?)
        """;

        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setString(1, chunk.getWorld().getName());
            stmt.setInt(2, chunk.getX());
            stmt.setInt(3, chunk.getZ());
            stmt.setInt(4, townID);
            stmt.executeUpdate();
            claimTownCache.put(chunkKey(chunk), townID);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets the town ID associated with a specific chunk.
     */
    public Integer getChunkTownId(Chunk chunk) {
        String key = chunkKey(chunk);
        Integer cachedTownId = claimTownCache.get(key);
        if (cachedTownId != null) {
            return cachedTownId;
        }

        String sql = """
            SELECT town_id FROM claims
            WHERE world=? AND chunkx=? AND chunkz=?
            LIMIT 1
        """;

        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setString(1, chunk.getWorld().getName());
            stmt.setInt(2, chunk.getX());
            stmt.setInt(3, chunk.getZ());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Integer townId = rs.getInt("town_id");
                    claimTownCache.put(key, townId);
                    return townId;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Removes a claim for a specific chunk and town.
     */
    public boolean unClaimChunk(Chunk chunk, int townId){
        String sql = """
                DELETE FROM claims
                WHERE world=? AND chunkx=? AND chunkz=? AND town_id=?
                """;
        try(PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setString(1, chunk.getWorld().getName());
            stmt.setInt(2, chunk.getX());
            stmt.setInt(3, chunk.getZ());
            stmt.setInt(4, townId);

            boolean removed = stmt.executeUpdate() > 0;
            if (removed) {
                claimTownCache.remove(chunkKey(chunk));
            }
            return removed;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Deletes all claims associated with a town.
     */
    public void deleteClaim(int townId){
        String sql = "DELETE FROM claims WHERE town_id=?";

        try(PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)){
            stmt.setInt(1, townId);
            stmt.executeUpdate();
            claimTownCache.entrySet().removeIf(entry -> entry.getValue() != null && entry.getValue() == townId);
        } catch (SQLException e){
            e.printStackTrace();
        }
    }

    /**
     * Checks if a town has any claimed chunks.
     */
    public boolean hasClaims(int townId) {
        String sql = "SELECT 1 FROM claims WHERE town_id = ? LIMIT 1";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Checks if a chunk is adjacent to any chunks already claimed by the town.
     */
    public boolean isAdjacentClaim(int townId, Chunk chunk) {
        String sql = """
            SELECT 1 FROM claims
            WHERE town_id = ? AND world = ? AND (
                (chunkx = ? AND ABS(chunkz - ?) = 1) OR
                (chunkz = ? AND ABS(chunkx - ?) = 1)
            )
            LIMIT 1
        """;
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            stmt.setString(2, chunk.getWorld().getName());
            stmt.setInt(3, chunk.getX());
            stmt.setInt(4, chunk.getZ());
            stmt.setInt(5, chunk.getZ());
            stmt.setInt(6, chunk.getX());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Loads all claims from the database into a map (used for caching).
     */
    public Map<String, Integer> loadAllClaims(){
        Map<String, Integer> claims = new ConcurrentHashMap<>();
        String sql = "SELECT world, chunkx, chunkz, town_id FROM claims";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)){
            ResultSet rs = stmt.executeQuery();
            while (rs.next()){
                String world = rs.getString("world");
                int x = rs.getInt("chunkx");
                int z = rs.getInt("chunkz");
                int townId = rs.getInt("town_id");

                claims.put(world + ":" + x + ":" + z, townId);
            }
        }catch (SQLException e){
            e.printStackTrace();
        }
        claimTownCache.clear();
        claimTownCache.putAll(claims);
        return claims;
    }

    public boolean decayOneClaim(int townId, boolean preserveSpawnChunk) {
        Location spawn = preserveSpawnChunk ? getTownSpawn(townId) : null;
        String spawnWorld = (spawn != null && spawn.getWorld() != null) ? spawn.getWorld().getName() : null;
        int spawnChunkX = spawn != null ? (spawn.getBlockX() >> 4) : 0;
        int spawnChunkZ = spawn != null ? (spawn.getBlockZ() >> 4) : 0;

        String selectSql = "SELECT world, chunkx, chunkz FROM claims WHERE town_id = ? ORDER BY world ASC, chunkx ASC, chunkz ASC";
        List<ClaimCoord> claims = new ArrayList<>();
        Set<String> claimKeys = new HashSet<>();

        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(selectSql)) {
            stmt.setInt(1, townId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ClaimCoord coord = new ClaimCoord(
                            rs.getString("world"),
                            rs.getInt("chunkx"),
                            rs.getInt("chunkz")
                    );
                    claims.add(coord);
                    claimKeys.add(coord.key());
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }

        if (claims.isEmpty()) {
            return false;
        }

        List<ClaimCoord> candidates = new ArrayList<>();
        for (ClaimCoord claim : claims) {
            if (isProtectedSpawnClaim(claim, spawnWorld, spawnChunkX, spawnChunkZ)) {
                continue;
            }
            if (isEdgeClaim(claim, claimKeys)) {
                candidates.add(claim);
            }
        }

        if (candidates.isEmpty()) {
            for (ClaimCoord claim : claims) {
                if (!isProtectedSpawnClaim(claim, spawnWorld, spawnChunkX, spawnChunkZ)) {
                    candidates.add(claim);
                }
            }
        }

        if (candidates.isEmpty()) {
            return false;
        }

        candidates.sort(Comparator
                .comparing(ClaimCoord::world, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(ClaimCoord::chunkX)
                .thenComparingInt(ClaimCoord::chunkZ));
        ClaimCoord target = candidates.get(0);

        String deleteSql = "DELETE FROM claims WHERE town_id=? AND world=? AND chunkx=? AND chunkz=? LIMIT 1";
        try (PreparedStatement deleteStmt = plugin.getConnection().prepareStatement(deleteSql)) {
            deleteStmt.setInt(1, townId);
            deleteStmt.setString(2, target.world());
            deleteStmt.setInt(3, target.chunkX());
            deleteStmt.setInt(4, target.chunkZ());
            boolean removed = deleteStmt.executeUpdate() > 0;
            if (removed) {
                claimTownCache.remove(target.key());
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean isEdgeClaim(ClaimCoord claim, Set<String> claimKeys) {
        return !claimKeys.contains(claim.neighborKey(1, 0))
                || !claimKeys.contains(claim.neighborKey(-1, 0))
                || !claimKeys.contains(claim.neighborKey(0, 1))
                || !claimKeys.contains(claim.neighborKey(0, -1));
    }

    private boolean isProtectedSpawnClaim(ClaimCoord claim, String spawnWorld, int spawnChunkX, int spawnChunkZ) {
        return spawnWorld != null
                && claim.world().equalsIgnoreCase(spawnWorld)
                && claim.chunkX() == spawnChunkX
                && claim.chunkZ() == spawnChunkZ;
    }

    private record ClaimCoord(String world, int chunkX, int chunkZ) {
        private String key() {
            return world + ":" + chunkX + ":" + chunkZ;
        }

        private String neighborKey(int dx, int dz) {
            return world + ":" + (chunkX + dx) + ":" + (chunkZ + dz);
        }
    }

    // ==========================================
    // TOWN MANAGEMENT
    // ==========================================

    /**
     * Creates a new town in the database.
     */
    public int createTown(String name, UUID mayor, String world, double x, double y, double z) throws SQLException {
        String sql = "INSERT INTO towns (name, mayor_uuid, world, spawn_x, spawn_y, spawn_z, created_at) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";

        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setString(2, mayor.toString());
            stmt.setString(3, world);
            stmt.setDouble(4, x);
            stmt.setDouble(5, y);
            stmt.setDouble(6, z);
            stmt.executeUpdate();

            try(ResultSet rs = stmt.getGeneratedKeys()) {
                if(rs.next()){
                    int townId = rs.getInt(1);
                    cacheTownIdentity(townId, name);
                    return townId;
                }
            }
        }
        throw new SQLException("Failed to create town");
    }

    /**
     * Deletes a town from the database.
     */
    public void deleteTown(int townId){
        String sql = "DELETE FROM towns WHERE id=?";

        try(PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            deleteTownPermissions(townId);
            stmt.setInt(1, townId);
            stmt.executeUpdate();
            invalidateTownIdentity(townId);
            townSpawnCache.remove(townId);
            townSpawnCacheExpiresAt.remove(townId);
            invalidateTownMembers(townId);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks if a town with the given name exists.
     */
    public boolean townExists(String name){
        return getTownIdByName(name) != null;
    }

    /**
     * Checks if a town with the given primary key exists.
     */
    public boolean townExists(int townId) {
        String sql = "SELECT 1 FROM towns WHERE id = ? LIMIT 1";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Gets the name of a town by its ID.
     */
    public String getTownName(int townId) {
        if (!isExpired(townNameByIdCacheExpiresAt, townId) && townNameByIdCache.containsKey(townId)) {
            return townNameByIdCache.get(townId);
        }

        String sql = """
            SELECT name
            FROM towns
            WHERE id = ?
            LIMIT 1
        """;

        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString("name");
                    cacheTownIdentity(townId, name);
                    return name;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Gets the town ID by name (case-insensitive).
     */
    public Integer getTownIdByName(String name) {
        String normalized = name == null ? null : name.toLowerCase();
        if (normalized != null && !isExpired(townIdByNameCacheExpiresAt, normalized) && townIdByNameCache.containsKey(normalized)) {
            Integer cachedTownId = townIdByNameCache.get(normalized);
            return cachedTownId == NULL_TOWN_ID ? null : cachedTownId;
        }

        String sql = "SELECT id FROM towns WHERE LOWER(name) = LOWER(?) LIMIT 1";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int townId = rs.getInt("id");
                    if (normalized != null) {
                        townIdByNameCache.put(normalized, townId);
                        townIdByNameCacheExpiresAt.put(normalized, System.currentTimeMillis() + CACHE_TTL_MS);
                    }
                    return townId;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        if (normalized != null) {
            townIdByNameCache.put(normalized, NULL_TOWN_ID);
            townIdByNameCacheExpiresAt.put(normalized, System.currentTimeMillis() + CACHE_TTL_MS);
        }
        return null;
    }

    /**
     * Gets a list of towns that do not have a spawn point set.
     */
    public ResultSet getTownsWithoutSpawn() throws SQLException {
        return townLifecycleRepository.getTownsWithoutSpawn();
    }

    /**
     * Gets the age of a town in seconds since its creation.
     */
    public long getTownAgeInSeconds(int townId) {
        return townLifecycleRepository.getTownAgeInSeconds(townId);
    }

    /**
     * Updates the spawn location for a town.
     */
    public void updateTownSpawn(int townId, String world, double x, double y, double z) {
        String sql = "UPDATE towns SET world = ?, spawn_x = ?, spawn_y = ?, spawn_z = ? WHERE id = ?";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setString(1, world);
            stmt.setDouble(2, x);
            stmt.setDouble(3, y);
            stmt.setDouble(4, z);
            stmt.setInt(5, townId);
            stmt.executeUpdate();
            if (world == null) {
                cacheTownSpawn(townId, null);
            } else {
                org.bukkit.World bukkitWorld = org.bukkit.Bukkit.getWorld(world);
                if (bukkitWorld != null) {
                    cacheTownSpawn(townId, new Location(bukkitWorld, x, y, z));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Resets the town creation time to now (used for the spawn setting grace period).
     */
    public void resetTownCreationTime(int townId) {
        String sql = "UPDATE towns SET created_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets the spawn location of a town.
     */
    public org.bukkit.Location getTownSpawn(int townId) {
        if (!isExpired(townSpawnCacheExpiresAt, townId) && townSpawnCache.containsKey(townId)) {
            return townSpawnCache.get(townId);
        }

        String sql = "SELECT world, spawn_x, spawn_y, spawn_z FROM towns WHERE id = ? LIMIT 1";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String worldName = rs.getString("world");
                    if (worldName == null) return null;
                    org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
                    if (world == null) return null;
                    org.bukkit.Location location = new org.bukkit.Location(world, rs.getDouble("spawn_x"), rs.getDouble("spawn_y"), rs.getDouble("spawn_z"));
                    cacheTownSpawn(townId, location);
                    return location;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Renames the town.
     */
    public void renameTown(int townId, String name){
        String sql = "UPDATE towns SET name = ? WHERE id = ?";
        try(PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)){
            stmt.setString(1, name);
            stmt.setInt(2, townId);
            stmt.executeUpdate();
            invalidateTownIdentity(townId);
            cacheTownIdentity(townId, name);
        } catch(SQLException e){
            e.printStackTrace();
        }
    }

    public void initializeTownTierData(int townId, String defaultTierKey) {
        String sql = """
            UPDATE towns
            SET tier = ?,
                tier_progress = COALESCE(tier_progress, 0),
                tier_streak = COALESCE(tier_streak, 0)
            WHERE id = ?
        """;
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setString(1, defaultTierKey);
            stmt.setInt(2, townId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getTownTierKey(int townId) {
        String sql = "SELECT tier FROM towns WHERE id = ? LIMIT 1";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("tier");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void setTownTierKey(int townId, String tierKey) {
        String sql = "UPDATE towns SET tier = ? WHERE id = ?";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setString(1, tierKey);
            stmt.setInt(2, townId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public double getTownTierProgress(int townId) {
        String sql = "SELECT COALESCE(tier_progress, 0) AS tier_progress FROM towns WHERE id = ? LIMIT 1";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("tier_progress");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    public void setTownTierProgress(int townId, double progress) {
        String sql = "UPDATE towns SET tier_progress = ? WHERE id = ?";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, progress);
            stmt.setInt(2, townId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void incrementTownTierProgress(int townId, double amount) {
        String sql = "UPDATE towns SET tier_progress = COALESCE(tier_progress, 0) + ? WHERE id = ?";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.setInt(2, townId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public int getTownTierStreak(int townId) {
        String sql = "SELECT COALESCE(tier_streak, 0) AS tier_streak FROM towns WHERE id = ? LIMIT 1";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("tier_streak");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void setTownTierStreak(int townId, int streak) {
        String sql = "UPDATE towns SET tier_streak = ? WHERE id = ?";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, streak);
            stmt.setInt(2, townId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public long getRaidCooldownUntilMillis(int townId) {
        String sql = "SELECT COALESCE(raid_cooldown_until, 0) AS raid_cooldown_until FROM towns WHERE id = ? LIMIT 1";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("raid_cooldown_until");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0L;
    }

    public void setRaidCooldownUntilMillis(int townId, long cooldownUntilMillis) {
        String sql = "UPDATE towns SET raid_cooldown_until = ? WHERE id = ?";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setLong(1, Math.max(0L, cooldownUntilMillis));
            stmt.setInt(2, townId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ==========================================
    // MEMBER MANAGEMENT
    // ==========================================

    /**
     * Adds a player as a member of a town.
     */
    public boolean addMember(int townId, UUID uuid, String role){
        String sql = """
                INSERT INTO town_members (town_id, uuid, role)
                VALUES (?, ?, ?)
                """;

        try(PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            stmt.setString(2, uuid.toString());
            stmt.setString(3, role);
            boolean added = stmt.executeUpdate() > 0;
            if (added) {
                cacheMemberContext(uuid, townId, role);
                invalidateTownMembers(townId);
            }
            return added;
        }catch (SQLException e){
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Gets the town ID for a specific player.
     */
    public Integer getPlayerTownId(UUID uuid) {
        if (!isExpired(playerTownCacheExpiresAt, uuid) && playerTownCache.containsKey(uuid)) {
            Integer cachedTownId = playerTownCache.get(uuid);
            return cachedTownId == NULL_TOWN_ID ? null : cachedTownId;
        }

        String sql = """
            SELECT town_id
            FROM town_members
            WHERE uuid = ?
            LIMIT 1
        """;

        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Integer townId = rs.getInt("town_id");
                    cacheWithTtl(playerTownCache, playerTownCacheExpiresAt, uuid, townId);
                    return townId;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        cacheWithTtl(playerTownCache, playerTownCacheExpiresAt, uuid, null);

        return null;
    }

    /**
     * Checks if a player is a member of a town.
     */
    public boolean isMember(UUID uuid, int townId) {
        String sql = """
            SELECT 1 FROM town_members
            WHERE uuid = ? AND town_id = ?
            LIMIT 1
        """;

        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, townId);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Sets the role of a player in a town.
     */
    public boolean setRole(UUID uuid, int townId, String role){
        String sql = """
            UPDATE town_members
            SET role=?
            WHERE uuid=? AND town_id=?
        """;

        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)){
            stmt.setString(1, role);
            stmt.setString(2, uuid.toString());
            stmt.setInt(3, townId);
            boolean updated = stmt.executeUpdate() > 0;
            if (updated) {
                cacheMemberContext(uuid, townId, role);
                invalidateTownMembers(townId);
            }
            return updated;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Checks if a player has the 'MEMBER' rank in a town.
     */
    public boolean isMemberRank(UUID uuid, int townId){
        Integer playerTownId = getPlayerTownId(uuid);
        if (playerTownId == null || playerTownId != townId) {
            return false;
        }
        String role = getMemberRole(uuid);
        return "MEMBER".equals(role);
    }

    /**
     * Removes a player from a town.
     */
    public boolean removeMember(UUID uuid, int townId){
        String sql = "DELETE FROM town_members WHERE uuid=? AND town_id=?";

        try(PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)){
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, townId);
            boolean removed = stmt.executeUpdate() > 0;
            if (removed) {
                deleteMemberPermissions(townId, uuid);
                invalidatePlayerTown(uuid);
                invalidateTownMembers(townId);
            }
            return removed;
        } catch(SQLException e){
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Gets a list of members for a town ordered by role.
     */
    public java.util.List<TownMember> getTownMembers(int townId) {
        if (!isExpired(townMembersCacheExpiresAt, townId) && townMembersCache.containsKey(townId)) {
            return new ArrayList<>(townMembersCache.get(townId));
        }

        java.util.List<TownMember> members = new java.util.ArrayList<>();
        String sql = """
            SELECT uuid, role
            FROM town_members
            WHERE town_id = ?
            ORDER BY CASE role
                WHEN 'MAYOR' THEN 1
                WHEN 'OFFICER' THEN 2
                WHEN 'MEMBER' THEN 3
                ELSE 4
            END, uuid
        """;

        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String uuidStr = rs.getString("uuid");
                    String role = rs.getString("role");
                    try {
                        UUID memberUuid = UUID.fromString(uuidStr);
                        members.add(new TownMember(memberUuid, role));
                        cacheMemberContext(memberUuid, townId, role);
                    } catch (IllegalArgumentException ignored) {
                        // Skip malformed UUIDs
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        townMembersCache.put(townId, Collections.unmodifiableList(new ArrayList<>(members)));
        townMembersCacheExpiresAt.put(townId, System.currentTimeMillis() + CACHE_TTL_MS);
        return members;
    }

    /**
     * Checks if a player is the mayor of a town.
     */
    public boolean isMayor(UUID uuid, int townId) {
        Integer playerTownId = getPlayerTownId(uuid);
        if (playerTownId == null || playerTownId != townId) {
            return false;
        }
        return "MAYOR".equals(getMemberRole(uuid));
    }

    /** 
     * Sets a new mayor for a town.
     */
    public boolean setMayor(UUID uuid, int townId) {
        String readCurrentMayorsSql = """
            SELECT uuid
            FROM town_members
            WHERE town_id = ? AND role = 'MAYOR'
        """;
        String demoteCurrentMayorsSql = """
            UPDATE town_members
            SET role='OFFICER'
            WHERE town_id=? AND role='MAYOR'
        """;
        String promoteTargetSql = """
            UPDATE town_members
            SET role='MAYOR'
            WHERE uuid=? AND town_id=?
        """;

        List<UUID> previousMayors = new ArrayList<>();
        try (PreparedStatement readStmt = plugin.getConnection().prepareStatement(readCurrentMayorsSql)) {
            readStmt.setInt(1, townId);
            try (ResultSet rs = readStmt.executeQuery()) {
                while (rs.next()) {
                    try {
                        previousMayors.add(UUID.fromString(rs.getString("uuid")));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }

        try (PreparedStatement demoteStmt = plugin.getConnection().prepareStatement(demoteCurrentMayorsSql);
             PreparedStatement promoteStmt = plugin.getConnection().prepareStatement(promoteTargetSql)) {
            demoteStmt.setInt(1, townId);
            demoteStmt.executeUpdate();

            promoteStmt.setString(1, uuid.toString());
            promoteStmt.setInt(2, townId);
            boolean updated = promoteStmt.executeUpdate() > 0;
            if (updated) {
                for (UUID previousMayor : previousMayors) {
                    cacheMemberContext(previousMayor, townId, "OFFICER");
                    deleteMemberPermissions(townId, previousMayor);
                }
                cacheMemberContext(uuid, townId, "MAYOR");
                deleteMemberPermissions(townId, uuid);
                invalidateTownMembers(townId);
            }
            return updated;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private String getMemberRole(UUID uuid) {
        if (!isExpired(memberRoleCacheExpiresAt, uuid) && memberRoleCache.containsKey(uuid)) {
            return memberRoleCache.get(uuid);
        }

        String sql = "SELECT role FROM town_members WHERE uuid = ? LIMIT 1";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String role = rs.getString("role");
                    if (role != null) {
                        memberRoleCache.put(uuid, role);
                        memberRoleCacheExpiresAt.put(uuid, System.currentTimeMillis() + CACHE_TTL_MS);
                    }
                    return role;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Checks if a player is a town administrator (Mayor or Officer).
     */
    public boolean isTownAdmin(UUID uuid, int townId){
        Integer playerTownId = getPlayerTownId(uuid);
        if (playerTownId == null || playerTownId != townId) {
            return false;
        }
        String role = getMemberRole(uuid);
        return "MAYOR".equals(role) || "OFFICER".equals(role);
    }

    /**
     * Deletes all members associated with a town.
     */
    public void deleteMembers(int townId){
        String sql = "DELETE FROM town_members WHERE town_id=?";

        try(PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)){
            List<TownMember> existingMembers = getTownMembers(townId);
            stmt.setInt(1, townId);
            stmt.executeUpdate();
            deleteTownPermissions(townId);
            for (TownMember member : existingMembers) {
                invalidatePlayerTown(member.getUuid());
            }
            invalidateTownMembers(townId);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks if a player has permission to build/interact in a specific chunk.
     */
    public boolean canBuild(UUID uuid, Chunk chunk){
        Integer chunkTownId = getChunkTownId(chunk);
        if (chunkTownId == null) {
            return true;
        }
        Integer playerTownId = getPlayerTownId(uuid);
        return playerTownId != null && playerTownId.equals(chunkTownId);
    }

    // ==========================================
    // BANKING AND UPKEEP
    // ==========================================

    /**
     * Creates a new bank account for a town.
     */
    public void createTownBank(int townId){
        townBankRepository.createTownBank(townId);
    }

    /**
     * Deletes a town's bank account.
     */
    public void deleteTownBank(int townId){
        townBankRepository.deleteTownBank(townId);
    }

    /**
     * Transfers all money from one town bank to another.
     */
    public void transferBankBalance(int fromTownId, int toTownId) {
        townBankRepository.transferBankBalance(fromTownId, toTownId);
    }

    /**
     * Deposits money into a town's bank account.
     */
    public boolean depositTownMoney(int townId, double amount) {
        return townBankRepository.depositTownMoney(townId, amount);
    }

    /**
     * Withdraws money from a town's bank account (checks for sufficient funds).
     */
    public boolean withdrawTownMoney(int townId, double amount){
        return townBankRepository.withdrawTownMoney(townId, amount);
    }

    /**
     * Gets the current balance of a town's bank.
     */
    public double getTownBalance(int townId){
        return townBankRepository.getTownBalance(townId);
    }

    /**
     * Increases the daily upkeep cost for a town.
     */
    public void increaseUpkeep(int townId, double amount){
        upkeepRepository.increaseUpkeep(townId, amount);
    }

    /**
     * Decreases the daily upkeep cost for a town.
     */
    public void decreaseUpkeep(int townId, double amount){
        upkeepRepository.decreaseUpkeep(townId, amount);
    }

    /**
     * Updates the timestamp of the last upkeep payment.
     */
    public void updateLastUpkeep(int townId) {
        upkeepRepository.updateLastUpkeep(townId);
    }

    /**
     * Gets the current daily upkeep cost for a town.
     */
    public double getTownUpkeep(int townId) {
        return upkeepRepository.getTownUpkeep(townId);
    }

    /**
     * Gets a list of towns that are due for an upkeep payment.
     */
    public ResultSet getTownsNeedingUpkeep() throws SQLException {
        return upkeepRepository.getTownsNeedingUpkeep();
    }

    public void deleteTownPermissions(int townId) {
        String sql = "DELETE FROM town_member_permissions WHERE town_id=?";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            stmt.executeUpdate();
            invalidatePermissionCacheForTown(townId);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteMemberPermissions(int townId, UUID playerUuid) {
        String sql = "DELETE FROM town_member_permissions WHERE town_id=? AND player_uuid=?";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            stmt.setString(2, playerUuid.toString());
            stmt.executeUpdate();
            invalidatePermissionCacheForMember(townId, playerUuid);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}




