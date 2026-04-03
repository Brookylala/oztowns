package net.ozanarchy.towns.town.claim;

import org.bukkit.Chunk;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkHandler {
    private final Map<String, Integer> chunkClaims = new ConcurrentHashMap<>();

    public void setClaim(Chunk chunk, int townId){
        chunkClaims.put(key(chunk), townId);
    }

    public void removeClaim(Chunk chunk) {
        chunkClaims.remove(key(chunk));
    }

    public Integer getTownId(Chunk chunk) {
        return chunkClaims.get(key(chunk));
    }

    private String key(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    public void setAll(Map<String, Integer> claims){
        chunkClaims.clear();
        chunkClaims.putAll(claims);
    }
}




