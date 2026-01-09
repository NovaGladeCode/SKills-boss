package com.novaglade.mayhem.skills;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerData {
    private final UUID uuid;
    private final Map<SkillType, Integer> skillLevels = new HashMap<>();
    private final Map<SkillType, Double> skillExp = new HashMap<>();

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        for (SkillType type : SkillType.values()) {
            skillLevels.put(type, 1);
            skillExp.put(type, 0.0);
        }
    }

    public int getLevel(SkillType type) {
        return skillLevels.getOrDefault(type, 1);
    }

    public double getExp(SkillType type) {
        return skillExp.getOrDefault(type, 0.0);
    }

    public void addExp(Player player, SkillType type, double amount) {
        double currentExp = getExp(type) + amount;
        int currentLevel = getLevel(type);
        double neededExp = getNeededExp(currentLevel);

        if (currentExp >= neededExp) {
            levelUp(player, type);
            currentExp -= neededExp;
        }
        skillExp.put(type, currentExp);
    }

    private void levelUp(Player player, SkillType type) {
        int newLevel = getLevel(type) + 1;
        skillLevels.put(type, newLevel);

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "LEVEL UP! " + ChatColor.YELLOW
                + type.getDisplayName() + " level " + newLevel);
        player.sendMessage(ChatColor.GRAY + "You have unlocked new potential!");
        player.sendMessage("");

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
    }

    public double getNeededExp(int level) {
        return 100 * Math.pow(1.5, level - 1);
    }

    public UUID getUuid() {
        return uuid;
    }

    public Map<SkillType, Integer> getSkillLevels() {
        return skillLevels;
    }

    public Map<SkillType, Double> getSkillExp() {
        return skillExp;
    }
}
