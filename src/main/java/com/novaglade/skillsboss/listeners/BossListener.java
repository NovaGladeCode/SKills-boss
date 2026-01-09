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
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
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

    private final Map<UUID, Set<UUID>> activeWaveMobs = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> activeBars = new ConcurrentHashMap<>();

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
                Component.text("The fabrics of reality tear as the Altar of Avernus rises...",
                        NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
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
                stand.getWorld().playSound(stand.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 1.2f);
                checkActivation(stand);
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        if (entity.getPersistentDataContainer().has(WAVE_MOB_KEY, PersistentDataType.STRING)) {
            String standUuidStr = entity.getPersistentDataContainer().get(WAVE_MOB_KEY, PersistentDataType.STRING);
            if (standUuidStr != null) {
                UUID standUuid = UUID.fromString(standUuidStr);
                Set<UUID> mobs = activeWaveMobs.get(standUuid);
                if (mobs != null) {
                    mobs.remove(entity.getUniqueId());
                    updateBossBar(standUuid, mobs.size());
                }
            }
        }

        if (entity.getCustomName() != null && entity.getCustomName().contains("THE AVERNUS OVERLORD")) {
            Location deathLoc = entity.getLocation();
            playerBroadcast(deathLoc.getWorld(),
                    Component.text("THE OVERLORD IS EXTINGUISHED!", NamedTextColor.GOLD, TextDecoration.BOLD));

            BossBar bar = activeBars.get(entity.getUniqueId());
            if (bar != null) {
                bar.removeAll();
                activeBars.remove(entity.getUniqueId());
            }

            new BukkitRunnable() {
                @Override
                public void run() {
                    startProgressionTwoTransition(deathLoc);
                }
            }.runTaskLater(SkillsBoss.getInstance(), 200);
        }
    }

    private void updateBossBar(UUID standUuid, int currentMobs) {
        BossBar bar = activeBars.get(standUuid);
        if (bar != null) {
            if (currentMobs <= 0) {
                bar.setProgress(0);
            } else {
                // Approximate max mobs per wave is 12
                bar.setProgress(Math.clamp(currentMobs / 12.0, 0.0, 1.0));
            }
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

            BossBar bar = Bukkit.createBossBar("§5§lRitual Progress", BarColor.PURPLE, BarStyle.SEGMENTED_10);
            for (Player p : stand.getWorld().getPlayers()) {
                bar.addPlayer(p);
            }
            activeBars.put(standUuid, bar);

            new BukkitRunnable() {
                int stage = 0;
                boolean waveActive = false;

                @Override
                public void run() {
                    if (!stand.isValid()) {
                        bar.removeAll();
                        activeBars.remove(standUuid);
                        activeWaveMobs.remove(standUuid);
                        cancel();
                        return;
                    }

                    Set<UUID> mobs = activeWaveMobs.get(standUuid);
                    if (waveActive) {
                        if (mobs == null || mobs.isEmpty()) {
                            waveActive = false;
                            stage++;
                            stand.getWorld().playSound(stand.getLocation(), Sound.ENTITY_ENDER_EYE_DEATH, 1.5f, 0.5f);
                        } else {
                            mobs.removeIf(id -> Bukkit.getEntity(id) == null || !Bukkit.getEntity(id).isValid());
                            return;
                        }
                    }

                    Location loc = stand.getLocation();
                    if (stage == 0) {
                        bar.setTitle("§c§lWave 1: The Soulbound");
                        playerBroadcast(loc.getWorld(),
                                Component.text("THE VOID WHISPERS...", NamedTextColor.RED, TextDecoration.BOLD));
                        waveActive = true;
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                spawnWave(stand, 1);
                            }
                        }.runTaskLater(SkillsBoss.getInstance(), 40);
                    } else if (stage == 1) {
                        bar.setTitle("§6§lWave 2: The Infernal Legion");
                        playerBroadcast(loc.getWorld(),
                                Component.text("THE HEAT RISES FROM BELOW!", NamedTextColor.GOLD, TextDecoration.BOLD));
                        waveActive = true;
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                spawnWave(stand, 2);
                            }
                        }.runTaskLater(SkillsBoss.getInstance(), 40);
                    } else if (stage == 2) {
                        bar.setTitle("§b§lWave 3: The Void Guard");
                        playerBroadcast(loc.getWorld(), Component.text("THE FINAL SENTINELS ARRIVE!",
                                NamedTextColor.AQUA, TextDecoration.BOLD));
                        waveActive = true;
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                spawnWave(stand, 3);
                            }
                        }.runTaskLater(SkillsBoss.getInstance(), 40);
                    } else if (stage == 3) {
                        bar.removeAll();
                        activeBars.remove(standUuid);
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
            // Wave 1: Echo Wraiths (Teleporting/Explosive trails)
            for (int i = 0; i < 10; i++) {
                Location spawn = loc.clone().add(Math.random() * 10 - 5, 0, Math.random() * 10 - 5);
                Vindicator v = (Vindicator) loc.getWorld().spawnEntity(spawn, EntityType.VINDICATOR);
                v.setCustomName("§7Echo Wraith");
                v.getAttribute(Attribute.MAX_HEALTH).setBaseValue(40);
                v.setHealth(40);
                v.getPersistentDataContainer().set(WAVE_MOB_KEY, PersistentDataType.STRING,
                        stand.getUniqueId().toString());
                mobs.add(v.getUniqueId());

                // Ability: Leave explosive particles
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!v.isValid()) {
                            cancel();
                            return;
                        }
                        Location trail = v.getLocation();
                        trail.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, trail, 5, 0.1, 0.1, 0.1, 0);
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                trail.getWorld().spawnParticle(Particle.EXPLOSION, trail, 1, 0, 0, 0, 0);
                                for (Entity e : trail.getWorld().getNearbyEntities(trail, 2, 2, 2)) {
                                    if (e instanceof Player p && !p.isOp())
                                        p.damage(4, v);
                                }
                            }
                        }.runTaskLater(SkillsBoss.getInstance(), 30);
                    }
                }.runTaskTimer(SkillsBoss.getInstance(), 20, 20);
            }
        } else if (waveNum == 2) {
            // Wave 2: Magma Cultists (Fire Rings)
            for (int i = 0; i < 8; i++) {
                Location spawn = loc.clone().add(Math.random() * 12 - 6, 0, Math.random() * 12 - 6);
                Pillager p = (Pillager) loc.getWorld().spawnEntity(spawn, EntityType.PILLAGER);
                p.setCustomName("§6Magma Cultist");
                p.getEquipment().setItemInMainHand(new ItemStack(Material.FIRE_CHARGE));
                p.getPersistentDataContainer().set(WAVE_MOB_KEY, PersistentDataType.STRING,
                        stand.getUniqueId().toString());
                mobs.add(p.getUniqueId());

                // Ability: Fire Ring Nova
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!p.isValid()) {
                            cancel();
                            return;
                        }
                        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1f);
                        for (double a = 0; a < Math.PI * 2; a += Math.PI / 8) {
                            Location plume = p.getLocation().add(Math.cos(a) * 3, 0.5, Math.sin(a) * 3);
                            p.getWorld().spawnParticle(Particle.FLAME, plume, 5, 0.1, 0.5, 0.1, 0.05);
                            for (Entity e : p.getWorld().getNearbyEntities(plume, 1, 1, 1)) {
                                if (e instanceof Player pl && !pl.isOp())
                                    pl.setFireTicks(40);
                            }
                        }
                    }
                }.runTaskTimer(SkillsBoss.getInstance(), 60, 100);
            }
        } else if (waveNum == 3) {
            // Wave 3: Void Sentinels (Heavy hitters, Gravity wells)
            for (int i = 0; i < 3; i++) {
                Location spawn = loc.clone().add(Math.random() * 8 - 4, 0, Math.random() * 8 - 4);
                IronGolem g = (IronGolem) loc.getWorld().spawnEntity(spawn, EntityType.IRON_GOLEM);
                g.setCustomName("§bVoid Sentinel");
                g.getAttribute(Attribute.MAX_HEALTH).setBaseValue(150);
                g.setHealth(150);
                g.getPersistentDataContainer().set(WAVE_MOB_KEY, PersistentDataType.STRING,
                        stand.getUniqueId().toString());
                mobs.add(g.getUniqueId());

                // Ability: Gravity Well (Sucking in)
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!g.isValid()) {
                            cancel();
                            return;
                        }
                        g.getWorld().spawnParticle(Particle.PORTAL, g.getLocation().add(0, 1, 0), 50, 4, 1, 4, 0);
                        for (Entity e : g.getNearbyEntities(8, 8, 8)) {
                            if (e instanceof Player pl && !pl.isOp()) {
                                Vector pull = g.getLocation().subtract(pl.getLocation()).toVector().normalize()
                                        .multiply(0.3);
                                pl.setVelocity(pull);
                            }
                        }
                    }
                }.runTaskTimer(SkillsBoss.getInstance(), 40, 40);
            }
        }
    }

    private void spawnBoss(Location loc) {
        playerBroadcast(loc.getWorld(),
                Component.text("THE AVERNUS OVERLORD HAS AWAKENED!", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        WitherSkeleton boss = (WitherSkeleton) loc.getWorld().spawnEntity(loc, EntityType.WITHER_SKELETON);
        boss.setCustomName("§4§lTHE AVERNUS OVERLORD");
        boss.setCustomNameVisible(true);
        boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(800);
        boss.setHealth(800);
        boss.getAttribute(Attribute.SCALE).setBaseValue(4.0);

        boss.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
        boss.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
        boss.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
        boss.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
        boss.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));

        BossBar bossBar = Bukkit.createBossBar("§4§lTHE AVERNUS OVERLORD", BarColor.RED, BarStyle.SOLID);
        for (Player p : boss.getWorld().getPlayers()) {
            bossBar.addPlayer(p);
        }
        activeBars.put(boss.getUniqueId(), bossBar);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!boss.isValid()) {
                    bossBar.removeAll();
                    activeBars.remove(boss.getUniqueId());
                    cancel();
                    return;
                }
                bossBar.setProgress(
                        Math.clamp(boss.getHealth() / boss.getAttribute(Attribute.MAX_HEALTH).getValue(), 0, 1));

                // Ability: Earth Shatter
                if (Math.random() < 0.2) {
                    boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1.5f, 0.5f);
                    for (Entity e : boss.getNearbyEntities(10, 5, 10)) {
                        if (e instanceof Player p && !p.isOp()) {
                            p.setVelocity(new Vector(0, 1.2, 0));
                            p.sendMessage(Component.text("The Overlord shatters the earth!", NamedTextColor.RED));
                        }
                    }
                }
            }
        }.runTaskTimer(SkillsBoss.getInstance(), 20, 20);
    }

    private void startProgressionTwoTransition(Location loc) {
        SkillsBoss.setProgressionLevel(2);
        transitionActive = true;
        transitionPortal = loc.clone().add(0, 1, 0); // Center of Altar

        // Spawn Portal INSIDE the Altar
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                loc.clone().add(x, y + 1.5, 0).getBlock().setType(Material.NETHER_PORTAL);
            }
        }

        Component mainTitle = Component.text("PROGRESSION II", NamedTextColor.RED, TextDecoration.BOLD);
        Component subTitle = Component.text("ENTER THE AVERNUS", NamedTextColor.DARK_RED, TextDecoration.BOLD);
        Title title = Title.title(mainTitle, subTitle,
                Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(2)));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
            p.sendMessage(Component.text("The Altar consumes your reality...", NamedTextColor.DARK_RED));
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
                if (!p.isOnline() || p.getWorld().getEnvironment() == World.Environment.NETHER || ticks > 1200) {
                    cancel();
                    return;
                }
                Location pLoc = p.getLocation();
                Vector dir = transitionPortal.clone().subtract(pLoc).toVector();

                if (dir.length() < 1.5) {
                    p.teleport(transitionPortal);
                    cancel();
                    return;
                }

                // Slower pull speed (0.3 instead of 1.5 per tick)
                dir.normalize().multiply(0.4);
                Location next = pLoc.clone().add(dir);

                // Delete blocks in path
                for (int x = -1; x <= 1; x++) {
                    for (int y = 0; y <= 2; y++) {
                        for (int z = -1; z <= 1; z++) {
                            Block b = next.clone().add(x, y, z).getBlock();
                            if (b.getType() != Material.AIR && b.getType() != Material.NETHER_PORTAL) {
                                b.breakNaturally();
                            }
                        }
                    }
                }

                p.teleport(next);
                p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, pLoc, 10, 0.4, 0.4, 0.4, 0.05);
                ticks++;
            }
        }.runTaskTimer(SkillsBoss.getInstance(), 0, 1);
    }

    private void generateRitualPit(Location center) {
        World world = center.getWorld();
        int size = 9;

        // Gothic Altar Platform
        for (int x = -size; x <= size; x++) {
            for (int z = -size; z <= size; z++) {
                Location l = center.clone().add(x, -1, z);
                double dist = Math.sqrt(x * x + z * z);

                if (dist > size)
                    continue;

                if (dist < 3) {
                    l.getBlock().setType(Material.NETHERITE_BLOCK);
                } else if (dist < 6) {
                    l.getBlock().setType(Material.CRYING_OBSIDIAN);
                } else {
                    l.getBlock().setType(Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
        }

        // Floating Void Pillars
        for (int i = 0; i < 4; i++) {
            double angle = i * (Math.PI / 2);
            int rx = (int) (Math.cos(angle) * 7);
            int rz = (int) (Math.sin(angle) * 7);
            for (int y = 0; y < 5; y++) {
                Location p = center.clone().add(rx, y, rz);
                p.getBlock().setType(y == 4 ? Material.REDSTONE_BLOCK : Material.OBSIDIAN);
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
