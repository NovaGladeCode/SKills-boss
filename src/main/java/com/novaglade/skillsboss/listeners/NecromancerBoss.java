package com.novaglade.skillsboss.listeners;

import com.novaglade.skillsboss.SkillsBoss;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class NecromancerBoss implements Listener {

    private static final NamespacedKey BOSS_KEY = new NamespacedKey(SkillsBoss.getInstance(), "is_necromancer");
    private static final NamespacedKey MINION_KEY = new NamespacedKey(SkillsBoss.getInstance(), "necromancer_minion");
    private final Map<UUID, BossBar> activeBars = new HashMap<>();
    private final Set<UUID> activeBosses = new HashSet<>();

    @EventHandler
    public void onEntitySpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() == EntityType.ENDER_DRAGON) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        if (event.getPlayer().getWorld().getEnvironment() == World.Environment.THE_END) {
            // Remove pre-existing dragons and crystals
            for (Entity e : event.getPlayer().getWorld().getEntities()) {
                if (e instanceof EnderDragon || e instanceof EnderCrystal) {
                    e.remove();
                }
            }
        }
    }

    public void spawnNecromancer(Location loc) {
        loc.getChunk().load();
        
        // 1. Spawning Animation (Beam Effect)
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks == 0) {
                    loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_SPAWN, 2f, 0.5f);
                    loc.getWorld().strikeLightningEffect(loc);
                }
                
                // Beam effect using particles
                for (double i = 0; i < 20; i += 0.5) {
                    loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, i, 0), 5, 0.1, 0.1, 0.1, 0.05);
                }
                
                // Spiral particles
                double angle = ticks * 0.5;
                double x = Math.cos(angle) * 3;
                double z = Math.sin(angle) * 3;
                loc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(x, ticks * 0.2, z), 5, 0, 0, 0, 0.01);

                if (ticks >= 40) { // 2 seconds
                    actualSpawn(loc);
                    cancel();
                }
                ticks++;
            }
        }.runTaskTimer(SkillsBoss.getInstance(), 0, 1);
    }

    private void actualSpawn(Location loc) {
        WitherSkeleton logicEntity = (WitherSkeleton) loc.getWorld().spawnEntity(loc, EntityType.WITHER_SKELETON);
        logicEntity.setInvisible(false);
        logicEntity.setSilent(true);
        logicEntity.setInvulnerable(false);
        logicEntity.setPersistent(true);
        logicEntity.getPersistentDataContainer().set(BOSS_KEY, PersistentDataType.BYTE, (byte) 1);
        
        logicEntity.getEquipment().clear();
        logicEntity.getEquipment().setHelmet(new ItemStack(Material.WITHER_SKELETON_SKULL));
        logicEntity.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
        logicEntity.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
        logicEntity.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));

        ItemStack weapon = new ItemStack(Material.NETHERITE_HOE);
        org.bukkit.inventory.meta.ItemMeta weaponMeta = weapon.getItemMeta();
        weaponMeta.addEnchant(org.bukkit.enchantments.Enchantment.SHARPNESS, 5, true);
        weapon.setItemMeta(weaponMeta);
        logicEntity.getEquipment().setItemInMainHand(weapon);
        
        logicEntity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(2500);
        logicEntity.setHealth(2500);
        if (logicEntity.getAttribute(Attribute.SCALE) != null) {
             logicEntity.getAttribute(Attribute.SCALE).setBaseValue(2.0);
        }
        logicEntity.getAttribute(Attribute.FOLLOW_RANGE).setBaseValue(100);
        logicEntity.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1.0);

        // Aura effect directly on the boss
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!logicEntity.isValid() || logicEntity.isDead()) {
                    cancel();
                    return;
                }
                logicEntity.getWorld().spawnParticle(Particle.SOUL, logicEntity.getLocation().add(0, 1.5, 0), 3, 1, 1, 1, 0.02);
            }
        }.runTaskTimer(SkillsBoss.getInstance(), 0, 1);

        // Boss Bar
        BossBar bar = Bukkit.createBossBar("§8§l[ §4§lThe Necromancer §8§l]", BarColor.PURPLE, BarStyle.SOLID);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(loc.getWorld())) bar.addPlayer(p);
        }
        activeBars.put(logicEntity.getUniqueId(), bar);
        activeBosses.add(logicEntity.getUniqueId());

        // Announcements
        loc.getWorld().getPlayers().forEach(p -> {
            p.showTitle(Title.title(
                Component.text("THE NECROMANCER", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                Component.text("The End belongs to the Dead now...", NamedTextColor.GRAY)
            ));
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.5f);
        });

        // Boss Logic Task
        startBossTask(logicEntity);
    }

    private void startBossTask(WitherSkeleton boss) {
        new BukkitRunnable() {
            int summonCooldown = 0;
            int attackTicks = 0;

            @Override
            public void run() {
                if (!boss.isValid() || boss.isDead()) {
                    cleanup(boss.getUniqueId());
                    cancel();
                    return;
                }

                // Update Boss Bar
                BossBar bar = activeBars.get(boss.getUniqueId());
                if (bar != null) {
                    bar.setProgress(boss.getHealth() / boss.getAttribute(Attribute.MAX_HEALTH).getValue());
                    // Keep players in range on the bar
                    for (Player p : boss.getWorld().getPlayers()) {
                        if (p.getLocation().distanceSquared(boss.getLocation()) < 10000) {
                            if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
                        } else {
                            bar.removePlayer(p);
                        }
                    }
                }

                // Summoning Logic
                if (summonCooldown <= 0) {
                    summonCooldown = 400; // 20 seconds
                    performSummon(boss);
                } else {
                    summonCooldown--;
                }

                // Phase 2: Levitation / Soul Blast
                if (attackTicks % 120 == 0) {
                    if (boss.getHealth() < boss.getAttribute(Attribute.MAX_HEALTH).getValue() * 0.5) {
                        performLevitation(boss);
                    } else {
                        performSoulBlast(boss);
                    }
                }
                
                // Passive Particles
                if (attackTicks % 5 == 0) {
                    boss.getWorld().spawnParticle(Particle.PORTAL, boss.getLocation().add(0, 1, 0), 20, 2, 2, 2, 0.1);
                }

                attackTicks++;
            }
        }.runTaskTimer(SkillsBoss.getInstance(), 0, 1);
    }

    private void performLevitation(WitherSkeleton boss) {
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 2f, 0.5f);
        boss.getWorld().getPlayers().stream()
            .filter(p -> p.getLocation().distanceSquared(boss.getLocation()) < 400)
            .forEach(p -> {
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION, 60, 1));
                p.sendMessage(Component.text("The Dead pull you upwards...", NamedTextColor.DARK_PURPLE));
            });
    }

    private void performSummon(WitherSkeleton boss) {
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 2f, 0.5f);
        boss.getWorld().spawnParticle(Particle.GLOW, boss.getLocation(), 100, 5, 2, 5, 0.1);

        for (int i = 0; i < 4; i++) {
            double angle = Math.random() * Math.PI * 2;
            double radius = 5 + Math.random() * 5;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location spawnLoc = boss.getLocation().clone().add(x, 0, z);
            
            // Safe End check: highest block or just spawn at Y=65 if void
            int y = boss.getWorld().getHighestBlockYAt(spawnLoc);
            if (y < 20) y = 65; 
            spawnLoc.setY(y + 1);

            spawnSummon(spawnLoc);
        }
    }

    private void spawnSummon(Location targetLoc) {
        // Particles for rising
        targetLoc.getWorld().spawnParticle(Particle.BLOCK, targetLoc, 50, 0.5, 0.5, 0.5, 0.1, Material.SOUL_SAND.createBlockData());
        targetLoc.getWorld().playSound(targetLoc, Sound.BLOCK_SOUL_SAND_BREAK, 1f, 0.8f);

        new BukkitRunnable() {
            int ticks = 0;
            ArmorStand risingStand;
            LivingEntity minion;

            @Override
            public void run() {
                if (ticks == 0) {
                    // Start with an invisible mob rising
                    minion = (LivingEntity) targetLoc.getWorld().spawnEntity(targetLoc.clone().subtract(0, 2, 0), 
                        ThreadLocalRandom.current().nextBoolean() ? EntityType.WITHER_SKELETON : EntityType.ZOMBIE);
                    minion.getPersistentDataContainer().set(MINION_KEY, PersistentDataType.BYTE, (byte) 1);
                    minion.getAttribute(Attribute.MAX_HEALTH).setBaseValue(100);
                    minion.setHealth(100);
                    
                    // OP Gear
                    minion.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
                    minion.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                    minion.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
                }

                if (ticks < 20) {
                    minion.teleport(minion.getLocation().add(0, 0.1, 0));
                    minion.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, minion.getLocation(), 2, 0.1, 0.1, 0.1, 0.05);
                } else {
                    minion.getWorld().playSound(minion.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1f, 1f);
                    cancel();
                }
                ticks++;
            }
        }.runTaskTimer(SkillsBoss.getInstance(), 0, 1);
    }

    private void performSoulBlast(WitherSkeleton boss) {
        Player target = boss.getWorld().getPlayers().stream()
            .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(boss.getLocation())))
            .orElse(null);

        if (target != null) {
            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1f, 0.5f);
            WitherSkull skull = boss.launchProjectile(WitherSkull.class, target.getLocation().toVector().subtract(boss.getLocation().toVector()).normalize());
            skull.setCharged(true);
            skull.setYield(2.0f);
        }
    }

    @EventHandler
    public void onBossDamage(EntityDamageByEntityEvent event) {
        if (activeBosses.contains(event.getEntity().getUniqueId())) {
            // Boss takes damage
            WitherSkeleton boss = (WitherSkeleton) event.getEntity();
            boss.getWorld().spawnParticle(Particle.WITCH, boss.getLocation().add(0, 1.5, 0), 20, 0.5, 0.5, 0.5, 0.05);
            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ZOMBIE_HURT, 1f, 0.5f);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        UUID uuid = event.getEntity().getUniqueId();
        if (activeBosses.contains(uuid)) {
            cleanup(uuid);
            Location loc = event.getEntity().getLocation();
            
            // Epic Death
            loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_DEATH, 3f, 0.5f);
            loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 5, 1, 1, 1, 0);
            
            loc.getWorld().getPlayers().forEach(p -> {
                p.sendMessage(Component.text("THE NECROMANCER HAS BEEN DEFEATED!", NamedTextColor.GOLD, TextDecoration.BOLD));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            });
            
            // Drop rewards?
            loc.getWorld().dropItemNaturally(loc, new ItemStack(Material.NETHER_STAR));
            loc.getWorld().dropItemNaturally(loc, new ItemStack(Material.DRAGON_EGG));
        }
        
        if (event.getEntity().getPersistentDataContainer().has(MINION_KEY, PersistentDataType.BYTE)) {
            event.getDrops().clear();
            event.setDroppedExp(10);
        }
    }

    private void cleanup(UUID uuid) {
        activeBosses.remove(uuid);
        BossBar bar = activeBars.remove(uuid);
        if (bar != null) bar.removeAll();
    }
}
