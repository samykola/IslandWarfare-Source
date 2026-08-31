package com.islandwarfare.raid;

public enum RaidStage {
    /** Attacker has opened the raid but hasn't reached the capture point yet. */
    INFILTRATION,
    /** Attacker is at the capture point, uncontested, and progress is accumulating. */
    CAPTURING,
    /** A defender is present at the capture point, freezing/decaying progress. */
    CONTESTED,
    /** Capture timer reached 100% - attacker wins, loot is paid out. */
    CAPTURED,
    /** The attack window expired before capture completed - defender wins, no loot. */
    DEFENDED
}
