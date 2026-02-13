package com.novaglade.skillsboss;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
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
        registerCustomRecipes();
    }

    @Override
    public void onDisable() {
        logger.info("SkillsBoss has been disabled!");
    }

    private void registerCommands() {
        if (getCommand("admin") != null) {
            getCommand("admin").setExecutor(new com.novaglade.skillsboss.commands.AdminCommand());
        }
        if (getCommand("skills") != null) {
            getCommand("skills").setExecutor(new com.novaglade.skillsboss.commands.SkillsCommand());
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new com.novaglade.skillsboss.listeners.BossListener(), this);
        getServer().getPluginManager().registerEvents(new com.novaglade.skillsboss.listeners.ProgressionListener(),
                this);
        getServer().getPluginManager().registerEvents(new com.novaglade.skillsboss.listeners.DeathListener(), this);
    }

    private void registerCustomRecipes() {
        // Remove vanilla enchanting table recipe
        Bukkit.removeRecipe(NamespacedKey.minecraft("enchanting_table"));

        // Custom Enchanting Table: Amethyst Shards surrounding a Book
        NamespacedKey enchantTableKey = new NamespacedKey(this, "custom_enchanting_table");
        ShapedRecipe enchantTableRecipe = new ShapedRecipe(enchantTableKey, new ItemStack(Material.ENCHANTING_TABLE));
        enchantTableRecipe.shape("AAA", "ABA", "AAA");
        enchantTableRecipe.setIngredient('A', Material.AMETHYST_SHARD);
        enchantTableRecipe.setIngredient('B', Material.BOOK);
        Bukkit.addRecipe(enchantTableRecipe);

        logger.info("Custom recipes registered!");
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
