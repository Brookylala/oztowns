package net.ozanarchy.towns.raid;

public record RaidMinigameSettings(
        boolean enabled,
        String title,
        int durationTicks,
        int updateIntervalTicks,
        int successZoneSize,
        boolean closeOnSuccess,
        boolean closeOnFail,
        String tickSound,
        String successSound,
        String failSound,
        String successMessage,
        String failMessage,
        String timeoutMessage,
        String cancelledMessage
) {
}
