package com.novaglade.skillsboss;

import org.bukkit.plugin.java.JavaPlugin;

public class SkillsBoss extends JavaPlugin {

    private static SkillsBoss instance;
    private static int progressionLevel = -1;
    private final java.util.logging.Logger logger = getLogger();

    @Override
    public void onEnable() {
        instance = this;

        logger.info("SkillsBoss has been enabled!");
        logger.info("Running on Paper 1.21.11");

        // Register commands and events here
        registerCommands();
        registerListeners();
    }

    @Override
    public void onDisable() {
        logger.info("SkillsBoss has been disabled!");
    }

    private void registerCommands() {
        if (getCommand("admin") != null) {
            getCommand("admin").setExecutor(new com.novaglade.skillsboss.commands.AdminCommand());
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new com.novaglade.skillsboss.listeners.BossListener(), this);
        getServer().getPluginManager().registerEvents(new com.novaglade.skillsboss.listeners.ProgressionListener(),
                this);
        getServer().getPluginManager().registerEvents(new com.novaglade.skillsboss.listeners.DeathListener(), this);
    }

    public static SkillsBoss getInstance() {
        return instance;
    }

    public static int getProgressionLevel() {
        return progressionLevel;
    }

    public static void setProgressionLevel(int level) {
        progressionLevel = level;
    }
}
