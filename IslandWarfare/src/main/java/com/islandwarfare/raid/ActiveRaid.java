package com.islandwarfare.raid;

import java.util.UUID;

public class ActiveRaid {

    private final int islandId;
    private final UUID attackerClanId;
    private final UUID defenderClanId;
    private final long startedAt;
    private final long expiresAt;
    private boolean lootClaimed = false;

    // ---- Real capture objective state ----
    private RaidStage stage = RaidStage.INFILTRATION;
    private double captureProgressSeconds = 0;
    private final double requiredCaptureSeconds;
    private final int defenseBonusSeconds;

    public ActiveRaid(int islandId, UUID attackerClanId, UUID defenderClanId, long durationMillis,
                       double requiredCaptureSeconds, int defenseBonusSeconds) {
        this.islandId = islandId;
        this.attackerClanId = attackerClanId;
        this.defenderClanId = defenderClanId;
        this.startedAt = System.currentTimeMillis();
        this.expiresAt = startedAt + durationMillis;
        this.requiredCaptureSeconds = requiredCaptureSeconds;
        this.defenseBonusSeconds = defenseBonusSeconds;
    }

    public int getIslandId() { return islandId; }
    public UUID getAttackerClanId() { return attackerClanId; }
    public UUID getDefenderClanId() { return defenderClanId; }
    public long getStartedAt() { return startedAt; }
    public long getExpiresAt() { return expiresAt; }

    public boolean isExpired() { return System.currentTimeMillis() >= expiresAt; }

    public boolean isLootClaimed() { return lootClaimed; }
    public void setLootClaimed(boolean lootClaimed) { this.lootClaimed = lootClaimed; }

    public long getRemainingMillis() { return Math.max(0, expiresAt - System.currentTimeMillis()); }

    public RaidStage getStage() { return stage; }
    public void setStage(RaidStage stage) { this.stage = stage; }

    public double getCaptureProgressSeconds() { return captureProgressSeconds; }
    public void setCaptureProgressSeconds(double seconds) {
        this.captureProgressSeconds = Math.max(0, Math.min(seconds, requiredCaptureSeconds));
    }

    public double getRequiredCaptureSeconds() { return requiredCaptureSeconds; }
    public int getDefenseBonusSeconds() { return defenseBonusSeconds; }

    public double getCapturePercent() {
        if (requiredCaptureSeconds <= 0) return 0;
        return (captureProgressSeconds / requiredCaptureSeconds) * 100.0;
    }

    public boolean isComplete() { return captureProgressSeconds >= requiredCaptureSeconds; }
}
