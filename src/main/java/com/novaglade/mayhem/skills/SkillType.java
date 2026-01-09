package com.novaglade.mayhem.skills;

public enum SkillType {
    COMBAT("Combat", "Increase your damage and survivability."),
    MINING("Mining", "Faster mining and chance for double drops."),
    AGILITY("Agility", "Faster movement and higher jumps."),
    MAYHEM("Mayhem", "Special skill for boss-slaying and chaos.");

    private final String displayName;
    private final String description;

    SkillType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
