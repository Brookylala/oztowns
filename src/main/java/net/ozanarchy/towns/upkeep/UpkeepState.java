package net.ozanarchy.towns.upkeep;

public enum UpkeepState {
    ACTIVE,
    OVERDUE,
    NEGLECTED,
    ABANDONED,
    DECAYING;

    public boolean atLeast(UpkeepState other) {
        return this.ordinal() >= other.ordinal();
    }

    public static UpkeepState fromString(String raw, UpkeepState fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return UpkeepState.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}

