package net.ozanarchy.towns.integration.ominous;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

public final class OminousChestLockCompat {
    private static final String OCL_PLUGIN_NAME = "OminousChestLock";
    private static final String NEW_PICK_SUCCESS_EVENT = "com.ominouschestlock.paper.api.event.LockPickSuccessEvent";
    private static final String OLD_PICK_SUCCESS_EVENT = "net.ozanarchy.chestlock.events.LockPickSuccessEvent";
    private static final String NEW_API_CLASS = "com.ominouschestlock.paper.api.OminousChestLockApi";
    private static final String OLD_PLUGIN_CLASS = "net.ozanarchy.chestlock.ChestLockPlugin";

    private OminousChestLockCompat() {
    }

    public static boolean isPluginEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled(OCL_PLUGIN_NAME);
    }

    @SuppressWarnings("unchecked")
    public static boolean registerLockPickSuccessListener(JavaPlugin plugin, Listener listener, EventExecutor executor) {
        PluginManager pm = plugin.getServer().getPluginManager();
        Class<?> eventClass = resolveClass(NEW_PICK_SUCCESS_EVENT);
        if (eventClass != null && Event.class.isAssignableFrom(eventClass)) {
            pm.registerEvent((Class<? extends Event>) eventClass, listener, EventPriority.NORMAL, executor, plugin);
            return true;
        }

        eventClass = resolveClass(OLD_PICK_SUCCESS_EVENT);
        if (eventClass != null && Event.class.isAssignableFrom(eventClass)) {
            pm.registerEvent((Class<? extends Event>) eventClass, listener, EventPriority.NORMAL, executor, plugin);
            return true;
        }
        return false;
    }

    public static Player getEventPlayer(Event event) {
        try {
            Object result = event.getClass().getMethod("getPlayer").invoke(event);
            return result instanceof Player player ? player : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Block getEventBlock(Event event) {
        try {
            Object block = event.getClass().getMethod("getBlock").invoke(event);
            if (block instanceof Block b) {
                return b;
            }
        } catch (Throwable ignored) {
        }

        try {
            Object loc = event.getClass().getMethod("getLocation").invoke(event);
            if (loc instanceof Location location) {
                return location.getBlock();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static ItemStack getEventItem(Event event, Player fallbackPlayer) {
        String[] accessors = new String[]{"getItemStack", "getItem", "getTool", "getPickItem"};
        for (String accessor : accessors) {
            try {
                Object value = event.getClass().getMethod(accessor).invoke(event);
                if (value instanceof ItemStack stack) {
                    return stack;
                }
            } catch (Throwable ignored) {
            }
        }
        return fallbackPlayer != null ? fallbackPlayer.getInventory().getItemInMainHand() : null;
    }

    public static void setCancelled(Event event, boolean cancelled) {
        try {
            event.getClass().getMethod("setCancelled", boolean.class).invoke(event, cancelled);
        } catch (Throwable ignored) {
        }
    }

    public static void unlockIfLocked(Block block, Player actor) {
        if (block == null || !isPluginEnabled()) {
            return;
        }

        if (unlockViaNewApi(block, actor)) {
            return;
        }
        unlockViaOldApi(block);
    }

    private static boolean unlockViaNewApi(Block block, Player actor) {
        try {
            Object api = getNewApiService();
            if (api == null) {
                return false;
            }

            Location location = block.getLocation();
            Method isLocked = api.getClass().getMethod("isLocked", Location.class);
            Object locked = isLocked.invoke(api, location);
            if (!(locked instanceof Boolean b) || !b) {
                return true;
            }

            Method snapshot = api.getClass().getMethod("getLockSnapshot", Location.class);
            Object lockSnapshot = snapshot.invoke(api, location);
            if (lockSnapshot == null) {
                return true;
            }

            if (actor != null) {
                try {
                    Method unlock = api.getClass().getMethod("unlock", Location.class, org.bukkit.OfflinePlayer.class);
                    unlock.invoke(api, location, actor);
                    return true;
                } catch (NoSuchMethodException ignored) {
                }
                try {
                    Method unlock = api.getClass().getMethod("unlock", Location.class, Player.class);
                    unlock.invoke(api, location, actor);
                    return true;
                } catch (NoSuchMethodException ignored) {
                }
            }

            try {
                Method unlock = api.getClass().getMethod("unlock", Location.class);
                unlock.invoke(api, location);
                return true;
            } catch (NoSuchMethodException ignored) {
                return false;
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object getNewApiService() {
        try {
            Class<?> apiClass = Class.forName(NEW_API_CLASS);
            RegisteredServiceProvider<?> provider = Bukkit.getServicesManager().getRegistration(apiClass);
            return provider != null ? provider.getProvider() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void unlockViaOldApi(Block block) {
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin(OCL_PLUGIN_NAME);
            if (plugin == null) {
                return;
            }

            Class<?> pluginClass = Class.forName(OLD_PLUGIN_CLASS);
            if (!pluginClass.isInstance(plugin)) {
                return;
            }

            Object lockService = pluginClass.getMethod("getLockService").invoke(plugin);
            if (lockService == null) {
                return;
            }

            Object lockInfo = lockService.getClass().getMethod("getLockInfo", Block.class).invoke(lockService, block);
            if (lockInfo == null) {
                return;
            }

            String keyName = (String) lockInfo.getClass().getMethod("keyName").invoke(lockInfo);
            lockService.getClass().getMethod("unlock", Block.class, String.class).invoke(lockService, block, keyName);
        } catch (Throwable ignored) {
        }
    }

    private static Class<?> resolveClass(String fqcn) {
        try {
            return Class.forName(fqcn);
        } catch (Throwable ignored) {
            return null;
        }
    }
}



