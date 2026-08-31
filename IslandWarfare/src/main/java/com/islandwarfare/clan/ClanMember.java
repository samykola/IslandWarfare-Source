package com.islandwarfare.clan;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

public class ClanMember {

    private final UUID uuid;
    private String name;
    private ClanRole role;
    private final Set<IslandFlag> permissions;
    private long joinedAt;
    private long lastSeen;

    public ClanMember(UUID uuid, String name, ClanRole role) {
        this.uuid = uuid;
        this.name = name;
        this.role = role;
        this.permissions = EnumSet.noneOf(IslandFlag.class);
        this.joinedAt = System.currentTimeMillis();
        this.lastSeen = this.joinedAt;
    }

    public UUID getUuid() { return uuid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ClanRole getRole() { return role; }
    public void setRole(ClanRole role) { this.role = role; }

    public Set<IslandFlag> getPermissions() { return permissions; }

    public boolean hasFlag(IslandFlag flag) {
        return role == ClanRole.LEADER || permissions.contains(flag);
    }

    public void grant(IslandFlag flag) { permissions.add(flag); }
    public void revoke(IslandFlag flag) { permissions.remove(flag); }

    public long getJoinedAt() { return joinedAt; }
    public void setJoinedAt(long joinedAt) { this.joinedAt = joinedAt; }

    public long getLastSeen() { return lastSeen; }
    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }
}
