package com.islandwarfare.season;

public class SeasonResult {

    private final String clanName;
    private final String clanTag;
    private final double power;
    private final int raidWins;
    private final int raidLosses;
    private final int territories;
    private final int islandLevel;

    public SeasonResult(String clanName, String clanTag, double power, int raidWins, int raidLosses,
                         int territories, int islandLevel) {
        this.clanName = clanName;
        this.clanTag = clanTag;
        this.power = power;
        this.raidWins = raidWins;
        this.raidLosses = raidLosses;
        this.territories = territories;
        this.islandLevel = islandLevel;
    }

    public String getClanName() { return clanName; }
    public String getClanTag() { return clanTag; }
    public double getPower() { return power; }
    public int getRaidWins() { return raidWins; }
    public int getRaidLosses() { return raidLosses; }
    public int getTerritories() { return territories; }
    public int getIslandLevel() { return islandLevel; }
}
