package com.islandwarfare.island;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public class Island {

    private final int id;
    private UUID ownerClanId;
    private final String worldName;

    private double centerX;
    private double centerY;
    private double centerZ;

    private int level;
    private int radius; // half-size of the square bounding box

    private double homeX;
    private double homeY;
    private double homeZ;
    private float homeYaw;
    private float homePitch;

    private IslandStatus status = IslandStatus.ACTIVE;

    public Island(int id, UUID ownerClanId, String worldName, double centerX, double centerY, double centerZ,
                  int level, int radius) {
        this.id = id;
        this.ownerClanId = ownerClanId;
        this.worldName = worldName;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.level = level;
        this.radius = radius;
        // default home = center, a couple blocks above paste height
        this.homeX = centerX;
        this.homeY = centerY + 2;
        this.homeZ = centerZ;
        this.homeYaw = 0f;
        this.homePitch = 0f;
    }

    public int getId() { return id; }

    public UUID getOwnerClanId() { return ownerClanId; }
    public void setOwnerClanId(UUID ownerClanId) { this.ownerClanId = ownerClanId; }

    public String getWorldName() { return worldName; }

    public double getCenterX() { return centerX; }
    public double getCenterY() { return centerY; }
    public double getCenterZ() { return centerZ; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public int getRadius() { return radius; }
    public void setRadius(int radius) { this.radius = radius; }

    public IslandStatus getStatus() { return status; }
    public void setStatus(IslandStatus status) { this.status = status; }

    public void setHome(double x, double y, double z, float yaw, float pitch) {
        this.homeX = x; this.homeY = y; this.homeZ = z; this.homeYaw = yaw; this.homePitch = pitch;
    }

    public Location getHomeLocation() {
        World world = org.bukkit.Bukkit.getWorld(worldName);
        return new Location(world, homeX, homeY, homeZ, homeYaw, homePitch);
    }

    public double getHomeX() { return homeX; }
    public double getHomeY() { return homeY; }
    public double getHomeZ() { return homeZ; }
    public float getHomeYaw() { return homeYaw; }
    public float getHomePitch() { return homePitch; }

    /** Minimum X of the island's square bounding box. */
    public double getMinX() { return centerX - radius; }
    public double getMaxX() { return centerX + radius; }
    public double getMinZ() { return centerZ - radius; }
    public double getMaxZ() { return centerZ + radius; }

    public boolean contains(double x, double z) {
        return x >= getMinX() && x <= getMaxX() && z >= getMinZ() && z <= getMaxZ();
    }

    /** Same as {@link #contains(double, double)} but extended outward by {@code buffer} blocks on every side. */
    public boolean containsWithBuffer(double x, double z, double buffer) {
        return x >= getMinX() - buffer && x <= getMaxX() + buffer
                && z >= getMinZ() - buffer && z <= getMaxZ() + buffer;
    }

    public boolean contains(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(worldName)) return false;
        return contains(loc.getX(), loc.getZ());
    }

    /** Checks bounding-box overlap against another island, including a safety gap. */
    public boolean overlaps(Island other, int gap) {
        return getMinX() - gap < other.getMaxX() + gap
                && getMaxX() + gap > other.getMinX() - gap
                && getMinZ() - gap < other.getMaxZ() + gap
                && getMaxZ() + gap > other.getMinZ() - gap;
    }

    public double distanceToCenter(double x, double z) {
        double dx = x - centerX;
        double dz = z - centerZ;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
