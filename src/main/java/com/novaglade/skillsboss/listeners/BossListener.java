package com.novaglade.skillsboss.listeners;

import com.novaglade.skillsboss.SkillsBoss;
import com.novaglade.skillsboss.items.ItemManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;

public class BossListener implements Listener {

    private static final NamespacedKey ALTAR_KEY = new NamespacedKey(SkillsBoss.getInstance(), "boss_altar");
    private static boolean transitionActive = false;
    private static Location transitionPortal = null;
    private final Map<UUID, BukkitRunnable> activePulls = new HashMap<>();

    public static boolean isTransitionActive() {
        return transitionActive;
    }

    @EventHandler
    public void onAltarPlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() == null)
            return;
        ItemStack item = event.getItem();
        if (item == null || !ItemManager.isBossSpawnItem(item))
            return;

        event.setCancelled(true);
        Block block = event.getClickedBlock();
        if (block == null)
            return;

        Location center = block.getLocation().add(0.5, 1, 0.5);
        item.setAmount(item.getAmount() - 1);

        playerBroadcast(center.getWorld(),
                Component.text("The earth trembles as the Altar of Avernus manifests...", NamedTextColor.DARK_PURPLE));
        generateGazebo(center);
        spawnAltarArmorStand(center.clone().add(0, 1.5, 0));
    }

    @EventHandler
    public void onAltarInteract(PlayerInteractAtEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof ArmorStand stand))
            return;
        if (!stand.getPersistentDataContainer().has(ALTAR_KEY, PersistentDataType.BYTE))
            return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (hand.getType() == Material.AIR)
            return;

        // Check if item is legendary diamond gear
        if (ItemManager.isCustomItem(hand)) {
            Material type = hand.getType();
            boolean accepted = false;

            ItemStack helmet = stand.getEquipment().getHelmet();
            ItemStack chest = stand.getEquipment().getChestplate();
            ItemStack legs = stand.getEquipment().getLeggings();
            ItemStack boots = stand.getEquipment().getBoots();

            if (type == Material.DIAMOND_HELMET && (helmet == null || helmet.getType() == Material.AIR)) {
                stand.getEquipment().setHelmet(hand.clone());
                accepted = true;
            } else if (type == Material.DIAMOND_CHESTPLATE
                    && (chest == null || chest.getType() == Material.AIR)) {
                stand.getEquipment().setChestplate(hand.clone());
                accepted = true;
            } else if (type == Material.DIAMOND_LEGGINGS
                    && (legs == null || legs.getType() == Material.AIR)) {
                stand.getEquipment().setLeggings(hand.clone());
                accepted = true;
            } else if (type == Material.DIAMOND_BOOTS && (boots == null || boots.getType() == Material.AIR)) {
                stand.getEquipment().setBoots(hand.clone());
                accepted = true;
            }

            if (accepted) {
                hand.setAmount(hand.getAmount() - 1);
                stand.getWorld().playSound(stand.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 0.5f);
                checkActivation(stand);
            }
        }
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (entity.getCustomName() != null && entity.getCustomName().contains("THE AVERNUS OVERLORD")) {
            Location deathLoc = entity.getLocation();
            playerBroadcast(deathLoc.getWorld(),
                    Component.text("THE OVERLORD HAS FALLEN!", NamedTextColor.GOLD, TextDecoration.BOLD));

            new BukkitRunnable() {
                @Override
                public void run() {
                    startProgressionTwoTransition(deathLoc);
                }
            }.runTaskLater(SkillsBoss.getInstance(), 200); // 10 seconds later
        }
    }

    private void checkActivation(ArmorStand stand) {
        ItemStack helmet = stand.getEquipment().getHelmet();
        ItemStack chest = stand.getEquipment().getChestplate();
        ItemStack legs = stand.getEquipment().getLeggings();
        ItemStack boots = stand.getEquipment().getBoots();

        boolean full = helmet != null && helmet.getType() != Material.AIR &&
                chest != null && chest.getType() != Material.AIR &&
                legs != null && legs.getType() != Material.AIR &&
                boots != null && boots.getType() != Material.AIR;

        if (full) {
            new BukkitRunnable() {
                int stage = 0;

                @Override
                public void run() {
                    Location loc = stand.getLocation();
                    if (stage == 0) {
                        playerBroadcast(loc.getWorld(),
                                Component.text("THE RITUAL BEGINS!", NamedTextColor.RED, TextDecoration.BOLD));
                        loc.getWorld().strikeLightningEffect(loc);
                    } else if (stage == 1) {
                        spawnWave(loc, 1);
                    } else if (stage == 2) {
                        // Wait for wave 1 to clear? Or just timed?
                        // User said waves then boss. I'll use timed for simplicity/safety against stuck
                        // mobs
                        spawnWave(loc, 2);
                    } else if (stage == 3) {
                        spawnBoss(loc);
                        stand.remove();
                        cancel();
                        return;
                    }
                    stage++;
                }
            }.runTaskTimer(SkillsBoss.getInstance(), 40, 200); // Every 10 seconds
        }
    }

    private void spawnWave(Location loc, int waveNum) {
        playerBroadcast(loc.getWorld(), Component.text("WAVE " + waveNum + " IS COMING!", NamedTextColor.RED));
        for (int i = 0; i < 10; i++) {
            Location spawn = loc.clone().add(Math.random() * 10 - 5, 0, Math.random() * 10 - 5);
            Zombie z = (Zombie) loc.getWorld().spawnEntity(spawn, EntityType.ZOMBIE);
            z.getAttribute(Attribute.MAX_HEALTH).setBaseValue(40);
            z.setHealth(40);
            z.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
            z.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        }
    }

    private void spawnBoss(Location loc) {
        playerBroadcast(loc.getWorld(),
                Component.text("THE AVERNUS OVERLORD HAS AWAKENED!", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        WitherSkeleton boss = (WitherSkeleton) loc.getWorld().spawnEntity(loc, EntityType.WITHER_SKELETON);
        boss.setCustomName("§4§lTHE AVERNUS OVERLORD");
        boss.setCustomNameVisible(true);
        boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(500);
        boss.setHealth(500);
        boss.getAttribute(Attribute.SCALE).setBaseValue(3.0);

        boss.getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        boss.getEquipment().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
        boss.getEquipment().setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
        boss.getEquipment().setBoots(new ItemStack(Material.DIAMOND_BOOTS));
        boss.getEquipment().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
    }

    private void startProgressionTwoTransition(Location loc) {
        SkillsBoss.setProgressionLevel(2);
        transitionActive = true;
        transitionPortal = loc.clone();

        // Build a custom portal structure (Nether Portal blocks)
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 2; y++) {
                loc.clone().add(x, y, 0).getBlock().setType(Material.NETHER_PORTAL);
            }
        }

        Component mainTitle = Component.text("PROGRESSION II", NamedTextColor.RED, TextDecoration.BOLD);
        Component subTitle = Component.text("ENTER THE AVERNUS", NamedTextColor.DARK_RED, TextDecoration.BOLD);
        Title title = Title.title(mainTitle, subTitle,
                Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(2)));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
            p.sendMessage(Component.text("You are being pulled into the Avernus...", NamedTextColor.DARK_RED));
            startPullingPlayer(p);
        }

        // Disable transition active after some time?
        // User said missed players also get pulled, so I'll keep it active.
    }

    public static void startPullingPlayer(Player p) {
        if (transitionPortal == null)
            return;

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.getWorld().getEnvironment() == World.Environment.NETHER || ticks > 600) {
                    cancel();
                    return;
                }

                Location pLoc = p.getLocation();
                Vector dir = transitionPortal.clone().subtract(pLoc).toVector();
                double dist = dir.length();

                if (dist < 2) {
                    p.teleport(transitionPortal); // Enter portal
                    cancel();
                    return;
                }

                dir.normalize().multiply(1.5); // Faster pull speed
                Location next = pLoc.clone().add(dir);
                p.teleport(next); // Teleport ensures phasing through blocks

                // Visual effect: Soul fire and smoke trails
                p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, pLoc, 10, 0.4, 0.4, 0.4, 0.05);
                p.getWorld().spawnParticle(Particle.SMOKE, pLoc, 5, 0.1, 0.1, 0.1, 0.02);

                ticks++;
            }
        }.runTaskTimer(SkillsBoss.getInstance(), 0, 1);
    }

    private void generateGazebo(Location center) {
        World world = center.getWorld();
        int size = 10; // 20x20 area centered

        // Floor
        for (int x = -size; x <= size; x++) {
            for (int z = -size; z <= size; z++) {
                world.getBlockAt(center.clone().add(x, -1, z)).setType(Material.POLISHED_BLACKSTONE_BRICKS);
            }
        }

        // Pillars
        int[] corners = { -size, size };
        for (int cx : corners) {
            for (int cz : corners) {
                for (int y = 0; y < 6; y++) {
                    world.getBlockAt(center.clone().add(cx, y, cz)).setType(Material.CHISELED_POLISHED_BLACKSTONE);
                }
            }
        }

        // Roof (Simple flat top with stairs)
        for (int x = -size; x <= size; x++) {
            for (int z = -size; z <= size; z++) {
                world.getBlockAt(center.clone().add(x, 6, z)).setType(Material.POLISHED_BLACKSTONE_BRICK_SLAB);
            }
        }
    }

    private void spawnAltarArmorStand(Location loc) {
        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setBasePlate(false);
        stand.setArms(true);
        stand.setCustomName("§d§lAltar of Avernus");
        stand.setCustomNameVisible(true);
        stand.getPersistentDataContainer().set(ALTAR_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    private void playerBroadcast(World world, Component msg) {
        for (Player p : world.getPlayers()) {
            p.sendMessage(msg);
        }
    }
}
