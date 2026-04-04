package net.ozanarchy.towns.town.tier;

public enum TownTier {
    TIER_1(1),
    TIER_2(2),
    TIER_3(3),
    TIER_4(4),
    TIER_5(5);

    private final int level;

    TownTier(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    public static TownTier fromKey(String key, TownTier fallback) {
        if (key == null || key.isBlank()) {
            return fallback;
        }
        try {
            return TownTier.valueOf(key.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public static TownTier fromLevel(int level, TownTier fallback) {
        for (TownTier tier : values()) {
            if (tier.level == level) {
                return tier;
            }
        }
        return fallback;
    }
}

