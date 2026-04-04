package net.ozanarchy.towns.util;

import org.bukkit.Chunk;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class ChunkVisuals {

    public static void showChunk(Player player, Chunk chunk, Particle particle) {
        World world = chunk.getWorld();

        int minX = chunk.getX() << 4;
        int minZ = chunk.getZ() << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;

        int y = world.getHighestBlockYAt(player.getLocation()) + 1;

        for (int x = minX; x <= maxX; x++) {
            spawn(player, world, x, y, minZ, particle);
            spawn(player, world, x, y, maxZ, particle);
        }

        for (int z = minZ; z <= maxZ; z++) {
            spawn(player, world, minX, y, z, particle);
            spawn(player, world, maxX, y, z, particle);
        }
    }

    private static void spawn(Player p, World w, int x, int y, int z, Particle particle) {
        Object data = null;
        if (particle == Particle.DUST) {
            data = new Particle.DustOptions(org.bukkit.Color.RED, 1.0F);
        }

        p.spawnParticle(
                particle,
                x + 0.5,
                y,
                z + 0.5,
                5,
                0.1, 0.1, 0.1,
                0,
                data
        );
    }
}



