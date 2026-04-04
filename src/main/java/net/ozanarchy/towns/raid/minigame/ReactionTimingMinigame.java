package net.ozanarchy.towns.raid.minigame;

import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.util.Utils;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ReactionTimingMinigame implements RaidMinigame {
    private static final int TRACK_START_SLOT = 9;
    private static final int TRACK_SIZE = 9;

    private final TownsPlugin plugin;
    private final RaidMinigameService service;

    public ReactionTimingMinigame(TownsPlugin plugin, RaidMinigameService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public void start(RaidMinigameSession session) {
        drawFrame(session);
        session.player().openInventory(session.getInventory());

        int interval = Math.max(1, session.settings().updateIntervalTicks());
        session.setTask(plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!session.player().isOnline() || session.completed()) {
                cancel(session, MinigameResult.CANCELLED);
                return;
            }

            session.addElapsedTicks(interval);
            if (session.elapsedTicks() >= Math.max(1, session.settings().durationTicks())) {
                cancel(session, MinigameResult.TIMEOUT);
                return;
            }

            int next = session.indicatorIndex() + session.direction();
            if (next >= TRACK_SIZE) {
                next = TRACK_SIZE - 2;
                session.setDirection(-1);
            } else if (next < 0) {
                next = 1;
                session.setDirection(1);
            }
            session.setIndicatorIndex(next);

            playSound(session.player(), session.settings().tickSound());
            drawFrame(session);
        }, 1L, interval));
    }

    @Override
    public void handleClick(RaidMinigameSession session, Player player, int slot) {
        if (session.completed()) {
            return;
        }

        int clickedIndex = slot - TRACK_START_SLOT;
        if (clickedIndex < 0 || clickedIndex >= TRACK_SIZE) {
            return;
        }

        boolean success = isInSuccessZone(session.indicatorIndex(), session.settings().successZoneSize());
        if (success) {
            playSound(player, session.settings().successSound());
            cancel(session, MinigameResult.SUCCESS);
            if (session.settings().closeOnSuccess()) {
                player.closeInventory();
            }
            player.sendMessage(Utils.getColor(Utils.prefix() + session.settings().successMessage()));
            return;
        }

        playSound(player, session.settings().failSound());
        cancel(session, MinigameResult.FAIL);
        if (session.settings().closeOnFail()) {
            player.closeInventory();
        }
    }

    @Override
    public void cancel(RaidMinigameSession session, MinigameResult result) {
        if (session.completed()) {
            return;
        }
        session.markCompleted();
        if (session.task() != null) {
            session.task().cancel();
        }
        service.completeSession(session, result);
    }

    private void drawFrame(RaidMinigameSession session) {
        Inventory inv = session.getInventory();

        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        int successZone = Math.max(1, Math.min(TRACK_SIZE, session.settings().successZoneSize()));
        int mid = TRACK_SIZE / 2;
        int half = successZone / 2;
        int zoneStart = Math.max(0, mid - half);
        int zoneEnd = Math.min(TRACK_SIZE - 1, zoneStart + successZone - 1);

        for (int i = 0; i < TRACK_SIZE; i++) {
            Material mat = i >= zoneStart && i <= zoneEnd ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
            inv.setItem(TRACK_START_SLOT + i, item(mat, " ", List.of()));
        }

        inv.setItem(TRACK_START_SLOT + session.indicatorIndex(), item(Material.RED_STAINED_GLASS_PANE,
                "&c&lCLICK NOW",
                new ArrayList<>(List.of("&7Stop inside the green zone."))));
    }

    private boolean isInSuccessZone(int indicator, int successZone) {
        int safe = Math.max(1, Math.min(TRACK_SIZE, successZone));
        int mid = TRACK_SIZE / 2;
        int half = safe / 2;
        int zoneStart = Math.max(0, mid - half);
        int zoneEnd = Math.min(TRACK_SIZE - 1, zoneStart + safe - 1);
        return indicator >= zoneStart && indicator <= zoneEnd;
    }

    private ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Utils.getColor(name));
            if (!lore.isEmpty()) {
                meta.setLore(lore.stream().map(Utils::getColor).toList());
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void playSound(Player player, String soundName) {
        if (soundName == null || soundName.isBlank()) {
            return;
        }
        try {
            player.playSound(player.getLocation(), Sound.valueOf(soundName.toUpperCase()), 1f, 1f);
        } catch (IllegalArgumentException ignored) {
        }
    }
}
