package net.ozanarchy.towns.town.listener;

import net.ozanarchy.towns.town.permission.PermissionManager;
import net.ozanarchy.towns.util.Utils;
import net.ozanarchy.towns.town.claim.ChunkHandler;
import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.util.db.DatabaseHandler;
import org.bukkit.Material;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.Event;
import org.bukkit.inventory.InventoryHolder;

import static net.ozanarchy.towns.TownsPlugin.messagesConfig;
import static net.ozanarchy.towns.TownsPlugin.protectionConfig;

public class ProtectionListener implements Listener {
    private final DatabaseHandler db;
    private final ChunkHandler chunkCache;
    private final PermissionManager permissionManager;

    public ProtectionListener(DatabaseHandler db, ChunkHandler chunkCache, PermissionManager permissionManager) {
        this.db = db;
        this.chunkCache = chunkCache;
        this.permissionManager = permissionManager;
    }

    public ProtectionListener(TownsPlugin plugin, DatabaseHandler db, ChunkHandler chunkCache, PermissionManager permissionManager) {
        this(db, chunkCache, permissionManager);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreakBlock(BlockBreakEvent event){
        if (!isEnabled("protection.block-break", true)) return;

        if (event.getBlock().getType() == Material.LODESTONE) {
            Integer townId = chunkCache.getTownId(event.getBlock().getChunk());
            if (townId != null) {
                Integer playerTownId = db.getPlayerTownId(event.getPlayer().getUniqueId());
                if (!townId.equals(playerTownId)) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString("messages.missinginteractperm")));
                    return;
                }

                Location townSpawn = db.getTownSpawn(townId);
                if (townSpawn != null && townSpawn.getBlock().equals(event.getBlock())) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString("messages.cannotbreaktownlodestone")));
                    return;
                }
            }
        }
        protection(event.getPlayer(), event.getBlock().getChunk(), "CAN_BUILD", event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlaceBlock(BlockPlaceEvent event){
        if (!isEnabled("protection.block-place", true)) return;

        Block placedBlock = event.getBlock();
        Location loc = placedBlock.getLocation();

        Integer townId = chunkCache.getTownId(placedBlock.getChunk());
        if (townId != null) {
            Location spawnLoc = db.getTownSpawn(townId);
            if (spawnLoc != null && spawnLoc.getWorld().equals(loc.getWorld()) &&
                spawnLoc.getBlockX() == loc.getBlockX() && spawnLoc.getBlockZ() == loc.getBlockZ() &&
                loc.getBlockY() > spawnLoc.getBlockY()) {

                if (placedBlock.getType().isOccluding()) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString("messages.spawnskyrestricted")));
                    return;
                }
            }
        }

        protection(event.getPlayer(), event.getBlock().getChunk(), "CAN_BUILD", event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!isEnabled("protection.interact", true)) return;

        Block block = event.getClickedBlock();
        if (block != null) {
            Material type = block.getType();
            if (type == Material.LODESTONE) {
                Integer townId = chunkCache.getTownId(block.getChunk());
                if (townId != null) {
                    Integer playerTownId = db.getPlayerTownId(event.getPlayer().getUniqueId());

                    if (townId.equals(playerTownId)) {
                        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                            event.setCancelled(true);
                            event.getPlayer().performCommand("towns gui");
                        }
                        return;
                    }

                    // Allow non-member lodestone right-click so OzTowns raid entry listener can process lockpick attempts.
                    event.setCancelled(false);
                    event.setUseInteractedBlock(Event.Result.ALLOW);
                    event.setUseItemInHand(Event.Result.ALLOW);
                    return;
                }

                return;
            }

            if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                    && isEnabled("protection.bypass.inventory-holders", true)
                    && block.getState() instanceof InventoryHolder) {
                return;
            }
        }
        if (event.getClickedBlock() != null) {
            protection(event.getPlayer(), event.getClickedBlock().getChunk(), "CAN_INTERACT", event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractContainer(InventoryOpenEvent event){
        if (!isEnabled("protection.inventory-open", true)) return;

        if (!(event.getPlayer() instanceof Player player)) return;
        InventoryHolder holder = event.getInventory().getHolder();
        Block block = null;

        if (holder instanceof Container c) {
            block = c.getBlock();
        } else if (holder instanceof DoubleChest dc) {
            block = dc.getLocation().getBlock();
        }

        if (block != null) {
            if (isEnabled("protection.bypass.inventory-holders", true) && block.getState() instanceof InventoryHolder) {
                return;
            }

            if (isEnabled("protection.bypass.town-lodestone-inventory", true) && block.getType() == Material.LODESTONE) {
                Integer tId = chunkCache.getTownId(block.getChunk());
                if (tId != null) {
                    Location townSpawn = db.getTownSpawn(tId);
                    if (townSpawn != null && townSpawn.getBlock().equals(block)) {
                        return;
                    }
                }
            }
        }

        if (block == null) return;

        Chunk chunk = block.getChunk();

        Integer townId = chunkCache.getTownId(chunk);
        if (townId == null) return;

        if (player.hasPermission("oztowns.admin.protectionbypass")) return;

        Integer playerTown = db.getPlayerTownId(player.getUniqueId());
        boolean allowed = playerTown != null &&
                            townId.equals(playerTown) &&
                            (db.isTownAdmin(player.getUniqueId(), playerTown) ||
                             permissionManager.getPermissionSync(playerTown, player.getUniqueId(), "CAN_INTERACT"));

        if (allowed) return;

        event.setCancelled(true);
        player.sendMessage(Utils.getColor(Utils.prefix() + messagesConfig.getString("messages.missingcontainerperm")));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!isEnabled("protection.entity-explode", true)) return;
        event.blockList().removeIf(block -> chunkCache.getTownId(block.getChunk()) != null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!isEnabled("protection.block-explode", true)) return;
        event.blockList().removeIf(block -> chunkCache.getTownId(block.getChunk()) != null);
    }

    private void protection(Player player, Chunk chunk, String permission, Cancellable event){
        if (player.hasPermission("oztowns.admin.protectionbypass")) return;

        Integer claimTown = chunkCache.getTownId(chunk);
        if (claimTown == null) return;
        Integer playerTown = db.getPlayerTownId(player.getUniqueId());

        boolean allowed = playerTown != null &&
                            claimTown.equals(playerTown) &&
                            (db.isTownAdmin(player.getUniqueId(), playerTown) ||
                             permissionManager.getPermissionSync(playerTown, player.getUniqueId(), permission));

        if(!allowed) {
            event.setCancelled(true);
            player.sendMessage(Utils.getColor(Utils.prefix() +
                    messagesConfig.getString("messages.missinginteractperm")));
        }
    }

    private boolean isEnabled(String path, boolean fallback) {
        if (protectionConfig == null) return fallback;
        return protectionConfig.getBoolean(path, fallback);
    }
}
