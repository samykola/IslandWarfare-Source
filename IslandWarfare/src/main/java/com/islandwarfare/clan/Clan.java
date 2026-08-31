package com.islandwarfare.clan;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class Clan {

    private final UUID id;
    private String name;
    private String tag;
    private UUID leader;
    private double balance;
    private int islandId = -1;

    private int raidWins = 0;
    private int raidLosses = 0;

    // uuid -> member
    private final Map<UUID, ClanMember> members = new LinkedHashMap<>();

    public Clan(UUID id, String name, String tag, UUID leader) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.leader = leader;
    }

    public UUID getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public UUID getLeader() { return leader; }
    public void setLeader(UUID leader) { this.leader = leader; }

    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }
    public void deposit(double amount) { this.balance += amount; }
    public boolean withdraw(double amount) {
        if (balance < amount) return false;
        balance -= amount;
        return true;
    }

    public int getIslandId() { return islandId; }
    public void setIslandId(int islandId) { this.islandId = islandId; }

    public int getRaidWins() { return raidWins; }
    public void incrementRaidWins() { raidWins++; }

    public int getRaidLosses() { return raidLosses; }
    public void incrementRaidLosses() { raidLosses++; }

    /** Used by SeasonManager when a season ends and stat resets are configured. */
    public void resetRaidStats() {
        raidWins = 0;
        raidLosses = 0;
    }

    public Map<UUID, ClanMember> getMembers() { return members; }

    public ClanMember getMember(UUID uuid) { return members.get(uuid); }

    public boolean isMember(UUID uuid) { return members.containsKey(uuid); }

    public void addMember(ClanMember member) { members.put(member.getUuid(), member); }

    public void removeMember(UUID uuid) { members.remove(uuid); }

    public int getMemberCount() { return members.size(); }

    public boolean isLeader(UUID uuid) { return leader.equals(uuid); }

    public boolean isOfficerOrLeader(UUID uuid) {
        ClanMember member = members.get(uuid);
        return member != null && (member.getRole() == ClanRole.LEADER || member.getRole() == ClanRole.OFFICER);
    }

    /**
     * Simple fallback formula (no plugin/config access from this model
     * class). The real, config-driven, multi-system power calculation used
     * everywhere in-game lives in {@link ClanPowerService#calculate(Clan)}
     * and additionally accounts for active members, territory and research.
     */
    public double calculatePower(int islandLevel) {
        return (islandLevel * 100.0)
                + (getMemberCount() * 10.0)
                + (raidWins * 25.0)
                - (raidLosses * 10.0)
                + (balance / 100.0);
    }
}
