package com.novaglade.mayhem.listeners;

import com.novaglade.mayhem.MayhemCore;
import com.novaglade.mayhem.boss.BossManager;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

public class BossListener implements Listener {

    private final MayhemCore plugin;

    public BossListener(MayhemCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (entity.hasMetadata(BossManager.BOSS_METADATA_KEY)) {
            event.getDrops().clear();

            // Drop Chaos Core
            event.getDrops().add(plugin.getItemManager().createChaosCore());

            event.getEntity().getWorld()
                    .broadcastMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "THE MAYHEM TITAN HAS FALLEN!");
            event.getEntity().getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, entity.getLocation(), 10);
            event.getEntity().getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 1.0f);
        }
    }

    @EventHandler
    public void onBossDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity().hasMetadata(BossManager.BOSS_METADATA_KEY)) {
            if (event.getDamager() instanceof Player player) {
                ItemStack hand = player.getInventory().getItemInMainHand();

                // Titan Slayer Bonus
                if (plugin.getItemManager().isCustomItem(hand, "titan_slayer")) {
                    event.setDamage(event.getDamage() * 2.0);
                    player.sendMessage(
                            ChatColor.RED + "" + ChatColor.ITALIC + "Your spear pierces through the Titan's armor!");
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.5f, 1.5f);
                }

                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.3f, 2.0f);
                event.getEntity().getWorld().spawnParticle(Particle.CRIT, event.getEntity().getLocation().add(0, 1, 0),
                        10);
            }
        }
    }
}
