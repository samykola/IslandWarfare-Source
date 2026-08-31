package com.islandwarfare.research;

import java.util.List;
import java.util.Map;

public class TechDef {

    private final String id;
    private final String name;
    private final int tier;
    private final double costMoney;
    private final Map<String, Integer> costItems; // material name -> amount
    private final List<String> requires;
    private final String effectType;
    private final double effectValue;

    public TechDef(String id, String name, int tier, double costMoney, Map<String, Integer> costItems,
                    List<String> requires, String effectType, double effectValue) {
        this.id = id;
        this.name = name;
        this.tier = tier;
        this.costMoney = costMoney;
        this.costItems = costItems;
        this.requires = requires;
        this.effectType = effectType;
        this.effectValue = effectValue;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getTier() { return tier; }
    public double getCostMoney() { return costMoney; }
    public Map<String, Integer> getCostItems() { return costItems; }
    public List<String> getRequires() { return requires; }
    public String getEffectType() { return effectType; }
    public double getEffectValue() { return effectValue; }
}
