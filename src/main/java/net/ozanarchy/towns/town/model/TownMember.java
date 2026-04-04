package net.ozanarchy.towns.town.model;

import net.ozanarchy.towns.town.model.TownMember;

import java.util.UUID;

public class TownMember {
    private final UUID uuid;
    private final String role;

    public TownMember(UUID uuid, String role) {
        this.uuid = uuid;
        this.role = role;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getRole() {
        return role;
    }
}

