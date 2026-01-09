package com.novaglade.mayhem;

import com.novaglade.mayhem.boss.BossManager;
import com.novaglade.mayhem.commands.AdminCommand;
import com.novaglade.mayhem.commands.SkillCommand;
import com.novaglade.mayhem.items.ItemManager;
import com.novaglade.mayhem.listeners.BossListener;
import com.novaglade.mayhem.listeners.SkillListener;
import com.novaglade.mayhem.skills.SkillManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;

public class MayhemCore extends JavaPlugin implements Listener {

    private BossManager bossManager;
    private SkillManager skillManager;
    private ItemManager itemManager;

    @Override
    public void onEnable() {
        // Initialize Managers
        this.bossManager = new BossManager(this);
        this.skillManager = new SkillManager(this);
        this.itemManager = new ItemManager(this);

        // Register commands
        getCommand("admin").setExecutor(new AdminCommand(this));
        getCommand("skills").setExecutor(new SkillCommand(this));

        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new BossListener(this), this);
        getServer().getPluginManager().registerEvents(new SkillListener(this, skillManager), this);

        getLogger().info("MayhemCore 1.21.11 - Skills SMP System Enabled!");
    }

    @Override
    public void onDisable() {
        if (skillManager != null) {
            skillManager.saveAllData();
        }
        getLogger().info("MayhemCore has been disabled. Data saved.");
    }

    public BossManager getBossManager() {
        return bossManager;
    }

    public SkillManager getSkillManager() {
        return skillManager;
    }

    public ItemManager getItemManager() {
        return itemManager;
    }
}
