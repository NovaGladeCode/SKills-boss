package com.novaglade.mayhem.listeners;

import com.novaglade.mayhem.MayhemCore;
import com.novaglade.mayhem.boss.BossManager;
import com.novaglade.mayhem.skills.PlayerData;
import com.novaglade.mayhem.skills.SkillManager;
import com.novaglade.mayhem.skills.SkillType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class SkillListener implements Listener {

    private final MayhemCore plugin;
    private final SkillManager skillManager;

    public SkillListener(MayhemCore plugin, SkillManager skillManager) {
        this.plugin = plugin;
        this.skillManager = skillManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Data is loaded on demand in getPlayerData
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        skillManager.unloadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onEntityKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            PlayerData data = skillManager.getPlayerData(killer.getUniqueId());

            double exp = 10.0;
            if (event.getEntity().hasMetadata(BossManager.BOSS_METADATA_KEY)) {
                exp = 1000.0; // Huge boost for killing the boss
                data.addExp(killer, SkillType.MAYHEM, 50.0);
            }

            data.addExp(killer, SkillType.COMBAT, exp);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material material = event.getBlock().getType();

        // Only grant EXP for "valuable" blocks
        if (material.name().contains("ORE") || material == Material.STONE || material == Material.DEEPSLATE) {
            PlayerData data = skillManager.getPlayerData(player.getUniqueId());
            double exp = 1.0;

            if (material.name().contains("DIAMOND") || material.name().contains("EMERALD")
                    || material.name().contains("NETHERITE")) {
                exp = 20.0;
            } else if (material.name().contains("IRON") || material.name().contains("GOLD")) {
                exp = 5.0;
            }

            data.addExp(player, SkillType.MINING, exp);
        }
    }
}
