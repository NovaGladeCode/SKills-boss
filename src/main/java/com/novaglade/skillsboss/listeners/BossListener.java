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
import java.util.concurrent.ConcurrentHashMap;

public class BossListener implements Listener {

    private static final NamespacedKey ALTAR_KEY = new NamespacedKey(SkillsBoss.getInstance(), "boss_altar");
    private static final NamespacedKey WAVE_MOB_KEY = new NamespacedKey(SkillsBoss.getInstance(), "wave_mob");
    private static boolean transitionActive = false;
    private static Location transitionPortal = null;

    // Tracks active wave mobs for each ritual armor stand
    private final Map<UUID, Set<UUID>> activeWaveMobs = new ConcurrentHashMap<>();

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
                Component.text("The earth cracks as the Pit of Avernus opens...", NamedTextColor.DARK_PURPLE));
        generateRitualPit(center);
        spawnAltarArmorStand(center.clone().add(0, 0.5, 0));
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
            } else if (type == Material.DIAMOND_CHESTPLATE && (chest == null || chest.getType() == Material.AIR)) {
                stand.getEquipment().setChestplate(hand.clone());
                accepted = true;
            } else if (type == Material.DIAMOND_LEGGINGS && (legs == null || legs.getType() == Material.AIR)) {
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
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        // Handle Wave Mob tracking
        if (entity.getPersistentDataContainer().has(WAVE_MOB_KEY, PersistentDataType.STRING)) {
            String standUuidStr = entity.getPersistentDataContainer().get(WAVE_MOB_KEY, PersistentDataType.STRING);
            if (standUuidStr != null) {
                UUID standUuid = UUID.fromString(standUuidStr);
                Set<UUID> mobs = activeWaveMobs.get(standUuid);
                if (mobs != null) {
                    mobs.remove(entity.getUniqueId());
                }
            }
        }

        // Handle Overlord Death
        if (entity.getCustomName() != null && entity.getCustomName().contains("THE AVERNUS OVERLORD")) {
            Location deathLoc = entity.getLocation();
            playerBroadcast(deathLoc.getWorld(),
                    Component.text("THE OVERLORD HAS FALLEN!", NamedTextColor.GOLD, TextDecoration.BOLD));

            new BukkitRunnable() {
                @Override
                public void run() {
                    startProgressionTwoTransition(deathLoc);
                }
            }.runTaskLater(SkillsBoss.getInstance(), 200);
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
            UUID standUuid = stand.getUniqueId();
            activeWaveMobs.put(standUuid, Collections.synchronizedSet(new HashSet<>()));

            new BukkitRunnable() {
                int stage = 0;
                boolean waveActive = false;

                @Override
                public void run() {
                    if (!stand.isValid()) {
                        activeWaveMobs.remove(standUuid);
                        cancel();
                        return;
                    }

                    Set<UUID> mobs = activeWaveMobs.get(standUuid);
                    if (waveActive) {
                        if (mobs == null || mobs.isEmpty()) {
                            waveActive = false;
                            stage++;
                            stand.getWorld().playSound(stand.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f,
                                    0.5f);
                        } else {
                            // Check if mobs are actually still alive/valid (safety check)
                            mobs.removeIf(id -> Bukkit.getEntity(id) == null || !Bukkit.getEntity(id).isValid());
                            return; // Wait for wave to be cleared
                        }
                    }

                    Location loc = stand.getLocation();
                    if (stage == 0) {
                        playerBroadcast(loc.getWorld(),
                                Component.text("THE PIT HUNGER FOR SOULS...", NamedTextColor.RED, TextDecoration.BOLD));
                        loc.getWorld().strikeLightningEffect(loc);
                        waveActive = true;
                        // Small delay before spawning
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                spawnWave(stand, 1);
                            }
                        }.runTaskLater(SkillsBoss.getInstance(), 40);
                    } else if (stage == 1) {
                        playerBroadcast(loc.getWorld(), Component.text("THE AVERNUS SENDS ITS GUARDIANS!",
                                NamedTextColor.DARK_RED, TextDecoration.BOLD));
                        waveActive = true;
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                spawnWave(stand, 2);
                            }
                        }.runTaskLater(SkillsBoss.getInstance(), 40);
                    } else if (stage == 2) {
                        spawnBoss(loc);
                        stand.remove();
                        activeWaveMobs.remove(standUuid);
                        cancel();
                    }
                }
            }.runTaskTimer(SkillsBoss.getInstance(), 40, 20);
        }
    }

    private void spawnWave(ArmorStand stand, int waveNum) {
        Location loc = stand.getLocation();
        Set<UUID> mobs = activeWaveMobs.get(stand.getUniqueId());
        if (mobs == null)
            return;

        if (waveNum == 1) {
            // Shadow Stalkers: Fast but semi-transparent zombies
            for (int i = 0; i < 8; i++) {
                Location spawn = loc.clone().add(Math.random() * 12 - 6, 0, Math.random() * 12 - 6);
                Zombie z = (Zombie) loc.getWorld().spawnEntity(spawn, EntityType.ZOMBIE);
                z.setCustomName("§8Shadow Stalker");
                z.getAttribute(Attribute.MAX_HEALTH).setBaseValue(40);
                z.setHealth(40);
                z.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.4);
                z.getPersistentDataContainer().set(WAVE_MOB_KEY, PersistentDataType.STRING,
                        stand.getUniqueId().toString());
                mobs.add(z.getUniqueId());

                // Ability: Leap every 5 seconds
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!z.isValid()) {
                            cancel();
                            return;
                        }
                        if (z.getTarget() != null) {
                            Vector v = z.getTarget().getLocation().subtract(z.getLocation()).toVector().normalize()
                                    .multiply(0.8).setY(0.4);
                            z.setVelocity(v);
                            z.getWorld().playSound(z.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.5f, 1.5f);
                        }
                    }
                }.runTaskTimer(SkillsBoss.getInstance(), 40, 100);
            }
        } else if (waveNum == 2) {
            // Avernus Scorchargers: Blazes that drop fire and skeletons with fire arrows
            for (int i = 0; i < 6; i++) {
                Location spawn = loc.clone().add(Math.random() * 14 - 7, 0, Math.random() * 14 - 7);
                Skeleton s = (Skeleton) loc.getWorld().spawnEntity(spawn, EntityType.SKELETON);
                s.setCustomName("§cScorched Guardian");
                s.getEquipment().setHelmet(new ItemStack(Material.GOLDEN_HELMET));
                s.getPersistentDataContainer().set(WAVE_MOB_KEY, PersistentDataType.STRING,
                        stand.getUniqueId().toString());
                mobs.add(s.getUniqueId());

                Blaze b = (Blaze) loc.getWorld().spawnEntity(spawn.clone().add(0, 3, 0), EntityType.BLAZE);
                b.setCustomName("§6Avernus Ember");
                b.getPersistentDataContainer().set(WAVE_MOB_KEY, PersistentDataType.STRING,
                        stand.getUniqueId().toString());
                mobs.add(b.getUniqueId());
            }
        }
    }

    private void spawnBoss(Location loc) {
        playerBroadcast(loc.getWorld(),
                Component.text("THE AVERNUS OVERLORD HAS AWAKENED!", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        WitherSkeleton boss = (WitherSkeleton) loc.getWorld().spawnEntity(loc, EntityType.WITHER_SKELETON);
        boss.setCustomName("§4§lTHE AVERNUS OVERLORD");
        boss.setCustomNameVisible(true);
        boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(600);
        boss.setHealth(600);
        boss.getAttribute(Attribute.SCALE).setBaseValue(3.5);

        boss.getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        boss.getEquipment().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
        boss.getEquipment().setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
        boss.getEquipment().setBoots(new ItemStack(Material.DIAMOND_BOOTS));
        boss.getEquipment().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));

        // Boss Ability: Pull players every 8 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!boss.isValid()) {
                    cancel();
                    return;
                }
                boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1f, 0.5f);
                for (Entity e : boss.getNearbyEntities(15, 15, 15)) {
                    if (e instanceof Player p && !p.isOp()) {
                        Vector v = boss.getLocation().subtract(p.getLocation()).toVector().normalize().multiply(1.5);
                        p.setVelocity(v);
                    }
                }
            }
        }.runTaskTimer(SkillsBoss.getInstance(), 100, 160);
    }

    private void startProgressionTwoTransition(Location loc) {
        SkillsBoss.setProgressionLevel(2);
        transitionActive = true;
        transitionPortal = loc.clone();

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
                if (dir.length() < 2) {
                    p.teleport(transitionPortal);
                    cancel();
                    return;
                }
                dir.normalize().multiply(1.5);
                p.teleport(pLoc.clone().add(dir));
                p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, pLoc, 10, 0.4, 0.4, 0.4, 0.05);
                ticks++;
            }
        }.runTaskTimer(SkillsBoss.getInstance(), 0, 1);
    }

    private void generateRitualPit(Location center) {
        World world = center.getWorld();
        int size = 8;

        // Detailed Floor (Gothic Cross Pattern)
        for (int x = -size; x <= size; x++) {
            for (int z = -size; z <= size; z++) {
                Location l = center.clone().add(x, -1, z);
                if (Math.abs(x) == size || Math.abs(z) == size) {
                    l.getBlock().setType(Material.CHISELED_POLISHED_BLACKSTONE);
                } else if (x == 0 || z == 0) {
                    l.getBlock().setType(Material.CRYING_OBSIDIAN);
                } else {
                    l.getBlock().setType(Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
        }

        // Corner Spikes (Replacing Gazebo Pillars)
        int[] corners = { -size, size };
        for (int cx : corners) {
            for (int cz : corners) {
                for (int y = 0; y < 4; y++) {
                    Material mat = (y == 3) ? Material.SOUL_LANTERN : Material.OBSIDIAN;
                    world.getBlockAt(center.clone().add(cx, y, cz)).setType(mat);
                }
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
