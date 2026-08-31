package com.islandwarfare.territory;

import java.util.UUID;

public class TerritoryZone {

    private final int id;
    private final ZoneType type;
    private final String worldName;
    private final double centerX;
    private final double centerY;
    private final double centerZ;
    private final int radius;

    private UUID ownerClanId; // null = unclaimed
    private UUID capturingClanId; // clan currently making progress, null if none
    private double captureProgress; // 0..requiredSeconds

    private String name;

    public TerritoryZone(int id, ZoneType type, String worldName, double centerX, double centerY, double centerZ, int radius) {
        this.id = id;
        this.type = type;
        this.worldName = worldName;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.radius = radius;
        this.name = type.name() + "-" + id;
    }

    public int getId() { return id; }
    public ZoneType getType() { return type; }
    public String getWorldName() { return worldName; }
    public double getCenterX() { return centerX; }
    public double getCenterY() { return centerY; }
    public double getCenterZ() { return centerZ; }
    public int getRadius() { return radius; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public UUID getOwnerClanId() { return ownerClanId; }
    public void setOwnerClanId(UUID ownerClanId) { this.ownerClanId = ownerClanId; }

    public UUID getCapturingClanId() { return capturingClanId; }
    public void setCapturingClanId(UUID capturingClanId) { this.capturingClanId = capturingClanId; }

    public double getCaptureProgress() { return captureProgress; }
    public void setCaptureProgress(double captureProgress) { this.captureProgress = Math.max(0, captureProgress); }

    public boolean contains(String world, double x, double z) {
        if (!worldName.equals(world)) return false;
        double dx = x - centerX;
        double dz = z - centerZ;
        return (dx * dx + dz * dz) <= (double) radius * radius;
    }

    public boolean isUnclaimed() { return ownerClanId == null; }
}
