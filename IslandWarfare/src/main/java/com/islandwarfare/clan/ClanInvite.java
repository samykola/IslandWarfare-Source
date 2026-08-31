package com.islandwarfare.clan;

import java.util.UUID;

public class ClanInvite {
    private final UUID clanId;
    private final long timestamp;

    public ClanInvite(UUID clanId) {
        this.clanId = clanId;
        this.timestamp = System.currentTimeMillis();
    }

    public UUID getClanId() { return clanId; }
    public long getTimestamp() { return timestamp; }
}
