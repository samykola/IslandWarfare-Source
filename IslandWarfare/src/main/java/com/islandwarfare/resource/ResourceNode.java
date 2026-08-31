package com.islandwarfare.resource;

import org.bukkit.Material;

public class ResourceNode {

    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final String nodeType;
    private final Material originalBlock;

    private boolean depleted = false;
    private long respawnAt = 0;

    public ResourceNode(String world, int x, int y, int z, String nodeType, Material originalBlock) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.nodeType = nodeType;
        this.originalBlock = originalBlock;
    }

    public String getWorld() { return world; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public String getNodeType() { return nodeType; }
    public Material getOriginalBlock() { return originalBlock; }

    public boolean isDepleted() { return depleted; }

    public void deplete(long respawnAt) {
        this.depleted = true;
        this.respawnAt = respawnAt;
    }

    public void restock() {
        this.depleted = false;
        this.respawnAt = 0;
    }

    public long getRespawnAt() { return respawnAt; }

    public String key() {
        return world + ":" + x + ":" + y + ":" + z;
    }
}
