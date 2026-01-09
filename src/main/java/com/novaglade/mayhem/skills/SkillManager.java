package com.novaglade.mayhem.skills;

import com.novaglade.mayhem.MayhemCore;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SkillManager {
    private final MayhemCore plugin;
    private final Map<UUID, PlayerData> playersData = new HashMap<>();
    private final File dataFolder;

    public SkillManager(MayhemCore plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "userdata");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    public PlayerData getPlayerData(UUID uuid) {
        return playersData.computeIfAbsent(uuid, this::loadData);
    }

    public void saveAllData() {
        playersData.forEach((uuid, data) -> saveData(uuid));
    }

    private PlayerData loadData(UUID uuid) {
        File file = new File(dataFolder, uuid.toString() + ".yml");
        PlayerData data = new PlayerData(uuid);

        if (file.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            for (SkillType type : SkillType.values()) {
                if (config.contains("skills." + type.name())) {
                    data.getSkillLevels().put(type, config.getInt("skills." + type.name() + ".level"));
                    data.getSkillExp().put(type, config.getDouble("skills." + type.name() + ".exp"));
                }
            }
        }
        return data;
    }

    public void saveData(UUID uuid) {
        PlayerData data = playersData.get(uuid);
        if (data == null)
            return;

        File file = new File(dataFolder, uuid.toString() + ".yml");
        FileConfiguration config = new YamlConfiguration();

        for (SkillType type : SkillType.values()) {
            config.set("skills." + type.name() + ".level", data.getLevel(type));
            config.set("skills." + type.name() + ".exp", data.getExp(type));
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save player data for " + uuid + ": " + e.getMessage());
        }
    }

    public void unloadPlayer(UUID uuid) {
        saveData(uuid);
        playersData.remove(uuid);
    }
}
