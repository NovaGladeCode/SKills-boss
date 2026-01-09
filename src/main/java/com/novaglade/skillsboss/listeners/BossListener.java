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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BossListener implements Listener {

    private static final NamespacedKey ALTAR_KEY = new NamespacedKey(SkillsBoss.getInstance(), "boss_altar");
    private static final NamespacedKey WAVE_MOB_KEY = new NamespacedKey(SkillsBoss.getInstance(), "wave_mob");
    private static final NamespacedKey BOSS_COLLISION_KEY = new NamespacedKey(SkillsBoss.getInstance(),
            "boss_collision");

    private static boolean transitionActive = false;
    private static Location transitionPortal = null;

    private final Map<UUID, Set<UUID>> activeWaveMobs = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> activeBars = new ConcurrentHashMap<>();
    private final Set<UUID> bossGroup = Collections.synchronizedSet(new HashSet<>());

    private Team bossTeam;

    public BossListener() {
        // Setup team to prevent friendly fire between bosses
        org.bukkit.scoreboard.Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        bossTeam = sb.getTeam("AvernusBosses");
        if (bossTeam == null) {
            bossTeam = sb.registerNewTeam("AvernusBosses");
        }
        bossTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        bossTeam.setAllowFriendlyFire(false);
    }

    public static boolean isTransitionActive() {
        return transitionActive;
    }

    @EventHandler
    public void onAltarPlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        if (event.getHand() != EquipmentSlot.HAND)
            return; // Prevent double trigger

        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR)
            return;

        if (ItemManager.isBossSpawnItem(item)) {
            event.setCancelled(true);
            Block block = event.getClickedBlock();
            if (block == null)
                return;

            Location center = block.getLocation().add(0.5, 1, 0.5);
            item.setAmount(item.getAmount() - 1);

            playerBroadcast(center.getWorld(),
                    Component.text("The fabrics of space-time rupture... The Avernus has arrived.", NamedTextColor.RED,
                            TextDecoration.BOLD));
            generateSmallAltar(center);
            spawnAltarArmorStand(center.clone().add(0, 0.1, 0));

            center.getWorld().strikeLightningEffect(center);
            center.getWorld().playSound(center, Sound.ENTITY_WITHER_SPAWN, 1f, 0.5f);
        }
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
            ItemStack mainHand = stand.getEquipment().getItemInMainHand();

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
            } else if (type == Material.DIAMOND_SWORD && (mainHand == null || mainHand.getType() == Material.AIR)) {
                stand.getEquipment().setItemInMainHand(hand.clone());
                stand.setRightArmPose(new EulerAngle(Math.toRadians(-90), 0, 0));
                accepted = true;
            }

            if (accepted) {
                hand.setAmount(hand.getAmount() - 1);
                stand.getWorld().playSound(stand.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 1.1f);
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
                    updateBossBar(standUuid, mobs.size());
                }
            }
        }

        // Handle Boss Group tracking
        if (bossGroup.contains(entity.getUniqueId())) {
            bossGroup.remove(entity.getUniqueId());

            BossBar bar = activeBars.get(entity.getUniqueId());
            if (bar != null) {
                bar.removeAll();
                activeBars.remove(entity.getUniqueId());
            }

            if (bossGroup.isEmpty()) {
                Location deathLoc = entity.getLocation();
                playerBroadcast(deathLoc.getWorld(),
                        Component.text("THE OVERLORDS ARE DEFEATED! PROCEED TO THE PORTAL!", NamedTextColor.GOLD,
                                TextDecoration.BOLD));

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        startProgressionTwoTransition(deathLoc);
                    }
                }.runTaskLater(SkillsBoss.getInstance(), 100);
            }
        }
    }

    @EventHandler
    public void onBossDamage(EntityDamageByEntityEvent event) {
        if (bossGroup.contains(event.getDamager().getUniqueId())
                && bossGroup.contains(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private void updateBossBar(UUID standUuid, int currentMobs) {
        BossBar bar = activeBars.get(standUuid);
        if (bar != null) {
            bar.setProgress(Math.clamp(currentMobs / 12.0, 0.0, 1.0));
        }
    }

    private void checkActivation(ArmorStand stand) {
        ItemStack helmet = stand.getEquipment().getHelmet();
        ItemStack chest = stand.getEquipment().getChestplate();
        ItemStack legs = stand.getEquipment().getLeggings();
        ItemStack boots = stand.getEquipment().getBoots();
        ItemStack sword = stand.getEquipment().getItemInMainHand();

        boolean full = helmet != null && helmet.getType() != Material.AIR &&
                chest != null && chest.getType() != Material.AIR &&
                legs != null && legs.getType() != Material.AIR &&
                boots != null && boots.getType() != Material.AIR &&
                sword != null && sword.getType() != Material.AIR;

        if (full) {
            UUID standUuid = stand.getUniqueId();
            activeWaveMobs.put(standUuid, Collections.synchronizedSet(new HashSet<>()));

            BossBar bar = Bukkit.createBossBar("§4§lThe Avernus Ritual", BarColor.RED, BarStyle.SEGMENTED_10);
            for (Player p : stand.getWorld().getPlayers()) {
                bar.addPlayer(p);
            }
            activeBars.put(standUuid, bar);

            new BukkitRunnable() {
                int stage = 0;
                boolean inTransition = false;
                int transitionTicks = 0;

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

                    if (inTransition) {
                        transitionTicks--;
                        if (transitionTicks <= 0) {
                            inTransition = false;
                            spawnWave(stand, stage + 1);
                        }
                        return;
                    }

                    if (mobs != null && !mobs.isEmpty()) {
                        mobs.removeIf(id -> Bukkit.getEntity(id) == null || !Bukkit.getEntity(id).isValid());
                        if (mobs.isEmpty()) {
                            stage++;
                            stand.getWorld().playSound(stand.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.2f, 1.8f);
                            if (stage < 3) {
                                inTransition = true;
                                transitionTicks = 40;
                            }
                        } else {
                            return;
                        }
                    }

                    if (stage == 0 && !inTransition && (mobs == null || mobs.isEmpty())) {
                        bar.setTitle("§c§lWave 1: Soul Stalkers");
                        playerBroadcast(stand.getWorld(),
                                Component.text("The air grows cold with the whispers of souls...", NamedTextColor.RED));
                        inTransition = true;
                        transitionTicks = 40;
                    } else if (stage == 1 && !inTransition) {
                        bar.setTitle("§6§lWave 2: Infernal Phalanx");
                        playerBroadcast(stand.getWorld(),
                                Component.text("The nether erupts from below!", NamedTextColor.GOLD));
                        inTransition = true;
                        transitionTicks = 40;
                    } else if (stage == 2 && !inTransition) {
                        bar.setTitle("§4§lWave 3: The Pit Lords");
                        playerBroadcast(stand.getWorld(), Component
                                .text("The elite guardians of the pit have come for you!", NamedTextColor.DARK_RED));
                        inTransition = true;
                        transitionTicks = 40;
                    } else if (stage == 3) {
                        bar.removeAll();
                        activeBars.remove(standUuid);
                        spawnBosses(stand.getLocation());
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
            for (int i = 0; i < 10; i++) {
                Location spawn = loc.clone().add(Math.random() * 8 - 4, 0, Math.random() * 8 - 4);
                WitherSkeleton s = (WitherSkeleton) loc.getWorld().spawnEntity(spawn, EntityType.WITHER_SKELETON);
                s.setCustomName("§7Soul Stalker");
                s.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
                s.getPersistentDataContainer().set(WAVE_MOB_KEY, PersistentDataType.STRING,
                        stand.getUniqueId().toString());
                mobs.add(s.getUniqueId());
            }
        } else if (waveNum == 2) {
            for (int i = 0; i < 8; i++) {
                Location spawn = loc.clone().add(Math.random() * 10 - 5, 0, Math.random() * 10 - 5);
                PiglinBrute b = (PiglinBrute) loc.getWorld().spawnEntity(spawn, EntityType.PIGLIN_BRUTE);
                b.setCustomName("§6Infernal Brute");
                b.getPersistentDataContainer().set(WAVE_MOB_KEY, PersistentDataType.STRING,
                        stand.getUniqueId().toString());
                mobs.add(b.getUniqueId());

                Blaze f = (Blaze) loc.getWorld().spawnEntity(spawn.clone().add(0, 3, 0), EntityType.BLAZE);
                f.getPersistentDataContainer().set(WAVE_MOB_KEY, PersistentDataType.STRING,
                        stand.getUniqueId().toString());
                mobs.add(f.getUniqueId());
            }
        } else if (waveNum == 3) {
            for (int i = 0; i < 4; i++) {
                Location spawn = loc.clone().add(Math.random() * 6 - 3, 0, Math.random() * 6 - 3);
                MagmaCube m = (MagmaCube) loc.getWorld().spawnEntity(spawn, EntityType.MAGMA_CUBE);
                m.setSize(6);
                m.setCustomName("§4Pit Lord");
                m.getPersistentDataContainer().set(WAVE_MOB_KEY, PersistentDataType.STRING,
                        stand.getUniqueId().toString());
                mobs.add(m.getUniqueId());
            }
        }
    }

    private void spawnBosses(Location loc) {
        playerBroadcast(loc.getWorld(),
                Component.text("THE OVERLORDS OF AVERNUS HAVE ARRIVED!", NamedTextColor.DARK_RED, TextDecoration.BOLD));

        String[] titles = { "§4§lIgnis", "§4§lSoul", "§4§lVoid", "§4§lKane" };
        BarColor[] colors = { BarColor.RED, BarColor.PURPLE, BarColor.BLUE, BarColor.WHITE };

        for (int i = 0; i < 4; i++) {
            Location spawn = loc.clone().add(Math.cos(i * Math.PI / 2) * 5, 0, Math.sin(i * Math.PI / 2) * 5);
            WitherSkeleton boss = (WitherSkeleton) loc.getWorld().spawnEntity(spawn, EntityType.WITHER_SKELETON);
            boss.setCustomName(titles[i] + " - Avernus Overlord");
            boss.setCustomNameVisible(true);
            boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(800);
            boss.setHealth(800);
            boss.getAttribute(Attribute.SCALE).setBaseValue(2.5);

            boss.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
            boss.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
            boss.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
            boss.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
            boss.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));

            bossTeam.addEntry(boss.getUniqueId().toString());
            bossGroup.add(boss.getUniqueId());

            BossBar bar = Bukkit.createBossBar(titles[i], colors[i], BarStyle.SOLID);
            for (Player p : boss.getWorld().getPlayers()) {
                bar.addPlayer(p);
            }
            activeBars.put(boss.getUniqueId(), bar);

            final int type = i;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!boss.isValid()) {
                        bar.removeAll();
                        activeBars.remove(boss.getUniqueId());
                        cancel();
                        return;
                    }
                    bar.setProgress(Math.clamp(boss.getHealth() / 800.0, 0, 1));

                    // Specialized Abilities
                    double rand = Math.random();
                    if (rand < 0.1) {
                        if (type == 0) { // Ignis: Fire Blast
                            boss.getWorld().spawnParticle(Particle.FLAME, boss.getLocation(), 100, 3, 1, 3, 0.1);
                            for (Entity e : boss.getNearbyEntities(6, 6, 6)) {
                                if (e instanceof Player p && !p.isOp())
                                    p.setFireTicks(100);
                            }
                        } else if (type == 1) { // Soul: Healing pulse
                            boss.getWorld().spawnParticle(Particle.SOUL, boss.getLocation(), 50, 2, 2, 2, 0.05);
                            for (UUID id : bossGroup) {
                                LivingEntity b = (LivingEntity) Bukkit.getEntity(id);
                                if (b != null && b.isValid())
                                    b.setHealth(Math.min(800, b.getHealth() + 30));
                            }
                        } else if (type == 2) { // Void: Massive Pull
                            boss.getWorld().spawnParticle(Particle.PORTAL, boss.getLocation(), 200, 10, 2, 10, 0);
                            for (Entity e : boss.getNearbyEntities(12, 12, 12)) {
                                if (e instanceof Player p && !p.isOp())
                                    p.setVelocity(boss.getLocation().subtract(p.getLocation()).toVector().normalize()
                                            .multiply(1.5));
                            }
                        } else if (type == 3) { // Kane: Earthquake
                            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1f, 0.5f);
                            for (Entity e : boss.getNearbyEntities(8, 5, 8)) {
                                if (e instanceof Player p && !p.isOp())
                                    p.setVelocity(new Vector(0, 1.5, 0));
                            }
                        }
                    }
                }
            }.runTaskTimer(SkillsBoss.getInstance(), 20, 20);
        }
    }

    private void generateSmallAltar(Location center) {
        // Simple 3x3 Nether Altar
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Location l = center.clone().add(x, -1, z);
                if (x == 0 && z == 0)
                    l.getBlock().setType(Material.NETHERITE_BLOCK);
                else
                    l.getBlock().setType(Material.CRYING_OBSIDIAN);

                if (Math.abs(x) == 1 && Math.abs(z) == 1) {
                    center.clone().add(x, 0, z).getBlock().setType(Material.SOUL_LANTERN);
                }
            }
        }
    }

    private void startProgressionTwoTransition(Location loc) {
        SkillsBoss.setProgressionLevel(2);
        transitionActive = true;
        transitionPortal = loc.clone().add(0, 1, 0);
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                loc.clone().add(x, y + 1.5, 0).getBlock().setType(Material.NETHER_PORTAL);
            }
        }
        Title title = Title.title(Component.text("PROGRESSION II", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("ENTER THE AVERNUS", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(3)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
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
                dir.normalize().multiply(0.4);
                Location next = pLoc.clone().add(dir);
                for (int x = -1; x <= 1; x++) {
                    for (int y = 0; y <= 2; y++) {
                        for (int z = -1; z <= 1; z++) {
                            Block b = next.clone().add(x, y, z).getBlock();
                            if (b.getType() != Material.AIR && b.getType() != Material.NETHER_PORTAL)
                                b.breakNaturally();
                        }
                    }
                }
                p.teleport(next);
                p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, pLoc, 10, 0.4, 0.4, 0.4, 0.05);
                ticks++;
            }
        }.runTaskTimer(SkillsBoss.getInstance(), 0, 1);
    }

    private void spawnAltarArmorStand(Location loc) {
        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setBasePlate(false);
        stand.setArms(true);
        stand.setCustomName("§4§lAltar of the Four");
        stand.setCustomNameVisible(true);
        stand.getPersistentDataContainer().set(ALTAR_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    private void playerBroadcast(World world, Component msg) {
        for (Player p : world.getPlayers()) {
            p.sendMessage(msg);
        }
    }
}
