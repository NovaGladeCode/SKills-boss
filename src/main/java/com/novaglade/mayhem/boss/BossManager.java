package com.novaglade.mayhem.boss;

import com.novaglade.mayhem.MayhemCore;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class BossManager {

    private final MayhemCore plugin;
    private final List<UUID> activeBosses = new ArrayList<>();
    public static final String BOSS_METADATA_KEY = "MayhemBoss";

    public BossManager(MayhemCore plugin) {
        this.plugin = plugin;
    }

    public void spawnBoss(Location location) {
        Zombie boss = (Zombie) location.getWorld().spawnEntity(location, EntityType.ZOMBIE);

        boss.setCustomName(ChatColor.RED + "" + ChatColor.BOLD + "Mayhem Titan");
        boss.setCustomNameVisible(true);
        boss.setMetadata(BOSS_METADATA_KEY, new FixedMetadataValue(plugin, true));

        // Boost Stats
        Objects.requireNonNull(boss.getAttribute(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(500.0);
        boss.setHealth(500.0);
        Objects.requireNonNull(boss.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE)).setBaseValue(15.0);
        Objects.requireNonNull(boss.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)).setBaseValue(0.35);
        Objects.requireNonNull(boss.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE)).setBaseValue(1.0);

        // Equipment
        ItemStack helmet = new ItemStack(Material.NETHERITE_HELMET);
        ItemStack chest = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemStack legs = new ItemStack(Material.NETHERITE_LEGGINGS);
        ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);

        boss.getEquipment().setHelmet(helmet);
        boss.getEquipment().setChestplate(chest);
        boss.getEquipment().setLeggings(legs);
        boss.getEquipment().setBoots(boots);
        boss.getEquipment().setItemInMainHand(sword);

        // Visual Effects
        boss.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 1));
        boss.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 2));

        activeBosses.add(boss.getUniqueId());

        // Spawn particles
        location.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, location, 1);
        location.getWorld().playSound(location, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);
    }

    public void clearBosses() {
        activeBosses.forEach(uuid -> {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
                entity.remove();
            }
        });
        activeBosses.clear();
    }
}
