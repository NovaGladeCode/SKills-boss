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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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

    private static boolean transitionActive = false;
    private static Location transitionPortal = null;

    private final Map<UUID, Set<UUID>> activeWaveMobs = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> activeBars = new ConcurrentHashMap<>();
    private final Set<UUID> bossGroup = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> activatingStands = Collections.synchronizedSet(new HashSet<>());

    private Team ritualTeam;

    public BossListener() {
        org.bukkit.scoreboard.Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        ritualTeam = sb.getTeam("AvernusRitual");
        if (ritualTeam == null) {
            ritualTeam = sb.registerNewTeam("AvernusRitual");
        }
        ritualTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        ritualTeam.setAllowFriendlyFire(false);
    }

    public static boolean isTransitionActive() {
        return transitionActive;
    }

    @EventHandler
    public void onAltarPlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        if (event.getHand() != EquipmentSlot.HAND)
            return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR)
            return;
        if (!ItemManager.isBossSpawnItem(item))
            return;

        event.setCancelled(true);
        Block block = event.getClickedBlock();
        if (block == null)
            return;

        Location center = block.getLocation().add(0.5, 1, 0.5);
        item.setAmount(item.getAmount() - 1);

        playerBroadcast(center.getWorld(),
                Component.text("The Avernus Core has been anchored.", NamedTextColor.DARK_RED, TextDecoration.BOLD));

        // Directional Altar
        Vector direction = event.getPlayer().getLocation().getDirection().setY(0).normalize();
        generateDirectionalAltar(center, direction);

        spawnAltarArmorStand(center.clone().add(0, 0.1, 0));

        center.getWorld().setBlockData(center.clone().add(0, -1, 0), Material.NETHERITE_BLOCK.createBlockData());
        center.getWorld().strikeLightningEffect(center);
        center.getWorld().playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 0.5f);
    }

    @EventHandler
    public void onAltarInteract(PlayerInteractAtEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof ArmorStand))
            return;
        ArmorStand stand = (ArmorStand) entity;
        if (!stand.getPersistentDataContainer().has(ALTAR_KEY, PersistentDataType.BYTE))
            return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR)
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
                stand.getWorld().playSound(stand.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1.2f);
                checkActivation(stand);
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.getPersistentDataContainer().has(WAVE_MOB_KEY, PersistentDataType.STRING)) {
            String uuidStr = entity.getPersistentDataContainer().get(WAVE_MOB_KEY, PersistentDataType.STRING);
            if (uuidStr != null) {
                UUID standUuid = UUID.fromString(uuidStr);
                Set<UUID> mobs = activeWaveMobs.get(standUuid);
                if (mobs != null) {
                    mobs.remove(entity.getUniqueId());
                    updateBossBar(standUuid, mobs.size());
                }
            }
        }
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
                        Component.text("THE CATACLYSM IS AVERTED!", NamedTextColor.GOLD, TextDecoration.BOLD));
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
    public void onRitualFriendlyFire(EntityDamageByEntityEvent event) {
        if (ritualTeam.hasEntry(event.getDamager().getUniqueId().toString())
                && ritualTeam.hasEntry(event.getEntity().getUniqueId().toString())) {
            event.setCancelled(true);
        }
    }

    private void updateBossBar(UUID standUuid, int currentMobs) {
        BossBar bar = activeBars.get(standUuid);
        if (bar != null) {
            double progress = currentMobs / 6.0;
            if (progress < 0)
                progress = 0;
            if (progress > 1)
                progress = 1;
            bar.setProgress(progress);
        }
    }

    private void checkActivation(ArmorStand stand) {
        if (stand.getEquipment().getHelmet() == null || stand.getEquipment().getHelmet().getType() == Material.AIR ||
                stand.getEquipment().getChestplate() == null
                || stand.getEquipment().getChestplate().getType() == Material.AIR ||
                stand.getEquipment().getLeggings() == null
                || stand.getEquipment().getLeggings().getType() == Material.AIR ||
                stand.getEquipment().getBoots() == null || stand.getEquipment().getBoots().getType() == Material.AIR ||
                stand.getEquipment().getItemInMainHand() == null
                || stand.getEquipment().getItemInMainHand().getType() == Material.AIR)
            return;

        UUID standUuid = stand.getUniqueId();
        if (activatingStands.contains(standUuid))
            return;
        activatingStands.add(standUuid);

        activeWaveMobs.put(standUuid, Collections.synchronizedSet(new HashSet<>()));
        BossBar ritualBar = Bukkit.createBossBar("§4§lThe Descent", BarColor.RED, BarStyle.SEGMENTED_10);
        for (Player p : stand.getWorld().getPlayers()) {
            ritualBar.addPlayer(p);
        }
        activeBars.put(standUuid, ritualBar);

        new BukkitRunnable() {
            int waveNum = 0; // Current wave we are waiting on
            boolean waiting = false;

            @Override
            public void run() {
                if (!stand.isValid()) {
                    endRitual(standUuid, ritualBar);
                    cancel();
                    return;
                }

                Set<UUID> mobs = activeWaveMobs.get(standUuid);
                if (waiting) {
                    mobs.removeIf(id -> Bukkit.getEntity(id) == null || !Bukkit.getEntity(id).isValid());
                    if (mobs.isEmpty()) {
                        waiting = false;
                        stand.getWorld().playSound(stand.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 2f);
                    } else {
                        return;
                    }
                }

                // If not waiting, spawn the next thing
                if (waveNum == 0) {
                    startWave(stand, ritualBar, 1);
                    waveNum = 1;
                    waiting = true;
                } else if (waveNum == 1) {
                    startWave(stand, ritualBar, 2);
                    waveNum = 2;
                    waiting = true;
                } else if (waveNum == 2) {
                    startWave(stand, ritualBar, 3);
                    waveNum = 3;
                    waiting = true;
                } else if (waveNum == 3) {
                    startWave(stand, ritualBar, 4); // Combined + Mini Boss
                    waveNum = 4;
                    waiting = true;
                } else if (waveNum == 4) {
                    ritualBar.removeAll();
                    activeBars.remove(standUuid);
                    spawnBosses(stand.getLocation());
                    stand.remove();
                    activatingStands.remove(standUuid);
                    activeWaveMobs.remove(standUuid);
                    cancel();
                }
            }
        }.runTaskTimer(SkillsBoss.getInstance(), 10, 20);
    }

    private void endRitual(UUID uuid, BossBar bar) {
        bar.removeAll();
        activeBars.remove(uuid);
        activeWaveMobs.remove(uuid);
        activatingStands.remove(uuid);
    }

    private void startWave(ArmorStand stand, BossBar bar, int waveId) {
        String[] titles = { "", "§e§lTrial I: The Fallen Sentries", "§9§lTrial II: The Undead Sentinels",
                "§c§lTrial III: The Avernus Guards", "§4§lTrial IV: The Final Gate" };
        Component[] msgs = { null, Component.text("The fallen rise in diamond plate...", NamedTextColor.YELLOW),
                Component.text("The undead manifest from the depths...", NamedTextColor.BLUE),
                Component.text("The Avernus Guards have arrived.", NamedTextColor.RED),
                Component.text("THE GATEKEEPER HAS AWAKENED!", NamedTextColor.DARK_RED, TextDecoration.BOLD) };

        bar.setTitle(titles[waveId]);
        playerBroadcast(stand.getWorld(), msgs[waveId]);
        spawnWave(stand, waveId);
    }

    private void spawnWave(ArmorStand stand, int waveId) {
        Location loc = stand.getLocation();
        Set<UUID> mobs = activeWaveMobs.get(stand.getUniqueId());
        if (waveId == 1) {
            for (int i = 0; i < 4; i++) {
                Skeleton e = (Skeleton) spawnMob(loc, EntityType.SKELETON, "§eFallen Sentry", Material.BOW,
                        stand.getUniqueId(), mobs);
                applyDiamondGear(e, 40);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!e.isValid()) {
                            cancel();
                            return;
                        }
                        shootCustomBeam(e, Particle.SOUL_FIRE_FLAME, 3.0, false);
                    }
                }.runTaskTimer(SkillsBoss.getInstance(), 60, 80);
            }
        } else if (waveId == 2) {
            for (int i = 0; i < 4; i++) {
                Zombie e = (Zombie) spawnMob(loc, EntityType.ZOMBIE, "§9Undead Sentinel", Material.IRON_SWORD,
                        stand.getUniqueId(), mobs);
                applyDiamondGear(e, 50);
            }
        } else if (waveId == 3) {
            for (int i = 0; i < 4; i++) {
                WitherSkeleton e = (WitherSkeleton) spawnMob(loc, EntityType.WITHER_SKELETON, "§cAvernus Guard",
                        Material.DIAMOND_AXE, stand.getUniqueId(), mobs);
                applyDiamondGear(e, 75);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!e.isValid()) {
                            cancel();
                            return;
                        }
                        shootCustomBeam(e, Particle.PORTAL, 6.0, false);
                    }
                }.runTaskTimer(SkillsBoss.getInstance(), 50, 70);
            }
        } else if (waveId == 4) {
            for (int i = 0; i < 2; i++) {
                Skeleton s = (Skeleton) spawnMob(loc, EntityType.SKELETON, "§eFallen Sentry", Material.BOW,
                        stand.getUniqueId(), mobs);
                applyDiamondGear(s, 40);
                Zombie z = (Zombie) spawnMob(loc, EntityType.ZOMBIE, "§9Undead Sentinel", Material.IRON_SWORD,
                        stand.getUniqueId(), mobs);
                applyDiamondGear(z, 50);
                WitherSkeleton w = (WitherSkeleton) spawnMob(loc, EntityType.WITHER_SKELETON, "§cAvernus Guard",
                        Material.DIAMOND_AXE, stand.getUniqueId(), mobs);
                applyDiamondGear(w, 75);
            }
            WitherSkeleton mini = (WitherSkeleton) spawnMob(loc, EntityType.WITHER_SKELETON, "§0§lThe Gatekeeper",
                    Material.DIAMOND_SWORD, stand.getUniqueId(), mobs);
            applyDiamondGear(mini, 250);
            if (mini.getAttribute(Attribute.SCALE) != null)
                mini.getAttribute(Attribute.SCALE).setBaseValue(2.5);

            if (mini.getAttribute(Attribute.MOVEMENT_SPEED) != null)
                mini.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.35);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!mini.isValid()) {
                        cancel();
                        return;
                    }
                    shootCustomBeam(mini, Particle.DRAGON_BREATH, 10.0, true);
                }
            }.runTaskTimer(SkillsBoss.getInstance(), 40, 40);
        }
    }

    private void applyDiamondGear(LivingEntity e, double health) {
        Attribute hpAttr = Attribute.MAX_HEALTH;
        if (e.getAttribute(hpAttr) != null) {
            e.getAttribute(hpAttr).setBaseValue(health);
            e.setHealth(health);
        }
        e.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
        e.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        e.getEquipment().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        e.getEquipment().setBoots(new ItemStack(Material.IRON_BOOTS));
        e.getEquipment().setHelmetDropChance(0);
        e.getEquipment().setChestplateDropChance(0);
        e.getEquipment().setLeggingsDropChance(0);
        e.getEquipment().setBootsDropChance(0);
    }

    private void shootCustomBeam(Mob shooter, Particle particle, double damage, boolean fire) {
        LivingEntity target = shooter.getTarget();
        if (target == null)
            return;

        Location start = shooter.getEyeLocation();
        Location end = target.getEyeLocation();
        double dist = start.distance(end);
        if (dist > 20)
            return;

        Vector dir = end.toVector().subtract(start.toVector()).normalize();
        shooter.getWorld().playSound(start, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.2f);

        for (double d = 0; d < dist; d += 0.5) {
            Location point = start.clone().add(dir.clone().multiply(d));
            shooter.getWorld().spawnParticle(particle, point, 3, 0.05, 0.05, 0.05, 0.01);

            if (d > 1.0 && point.distance(target.getLocation().add(0, 1, 0)) < 1.2) {
                target.damage(damage, shooter);
                if (fire)
                    target.setFireTicks(60);
                break;
            }
        }
    }

    private LivingEntity spawnMob(Location loc, EntityType type, String name, Material hand, UUID standUuid,
            Set<UUID> mobs) {
        Location spawn = loc.clone().add(Math.random() * 8 - 4, 0, Math.random() * 8 - 4);
        LivingEntity e = (LivingEntity) loc.getWorld().spawnEntity(spawn, type);
        e.setCustomName(name);
        e.setCustomNameVisible(true);
        if (hand != null)
            e.getEquipment().setItemInMainHand(new ItemStack(hand));
        e.getPersistentDataContainer().set(WAVE_MOB_KEY, PersistentDataType.STRING, standUuid.toString());
        ritualTeam.addEntry(e.getUniqueId().toString());
        mobs.add(e.getUniqueId());
        return e;
    }

    private void spawnBosses(Location loc) {
        playerBroadcast(loc.getWorld(), Component.text("THE FOUR SENTINELS AWAKEN!",
                NamedTextColor.DARK_RED, TextDecoration.BOLD));
        String[] titles = { "§4§lCrimson Sentinel", "§5§lVoid Sentinel", "§1§lFrost Sentinel", "§c§lWar Sentinel" };
        BarColor[] colors = { BarColor.RED, BarColor.PURPLE, BarColor.BLUE, BarColor.RED };

        for (int i = 0; i < 4; i++) {
            Location spawn = loc.clone().add(Math.cos(i * Math.PI / 2) * 5, 0, Math.sin(i * Math.PI / 2) * 5);
            WitherSkeleton boss = (WitherSkeleton) loc.getWorld().spawnEntity(spawn, EntityType.WITHER_SKELETON);
            boss.setCustomName(titles[i]);
            boss.setCustomNameVisible(true);

            Attribute hpAttr = Attribute.MAX_HEALTH;
            if (boss.getAttribute(hpAttr) != null) {
                boss.getAttribute(hpAttr).setBaseValue(300);
                boss.setHealth(300);
            }
            Attribute scaleAttr = Attribute.SCALE;
            if (boss.getAttribute(scaleAttr) != null) {
                boss.getAttribute(scaleAttr).setBaseValue(2.0);
            }

            boss.getEquipment().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
            boss.getEquipment().setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
            boss.getEquipment().setBoots(new ItemStack(Material.DIAMOND_BOOTS));

            Material weapon = i == 3 ? Material.DIAMOND_AXE : Material.DIAMOND_SWORD;
            boss.getEquipment().setItemInMainHand(new ItemStack(weapon));

            Attribute speedAttr = Attribute.MOVEMENT_SPEED;
            if (i == 0 && boss.getAttribute(speedAttr) != null)
                boss.getAttribute(speedAttr).setBaseValue(0.40);

            Attribute kbAttr = Attribute.KNOCKBACK_RESISTANCE;
            if (i == 3 && boss.getAttribute(kbAttr) != null)
                boss.getAttribute(kbAttr).setBaseValue(1.0);

            ritualTeam.addEntry(boss.getUniqueId().toString());
            bossGroup.add(boss.getUniqueId());
            BossBar bar = Bukkit.createBossBar(titles[i], colors[i], BarStyle.SOLID);
            for (Player p : boss.getWorld().getPlayers()) {
                bar.addPlayer(p);
            }
            activeBars.put(boss.getUniqueId(), bar);

            final int type = i;
            new BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    if (!boss.isValid()) {
                        bar.removeAll();
                        activeBars.remove(boss.getUniqueId());
                        cancel();
                        return;
                    }
                    double progress = boss.getHealth() / 300.0;
                    if (progress < 0)
                        progress = 0;
                    if (progress > 1)
                        progress = 1;
                    bar.setProgress(progress);

                    Location bLoc = boss.getLocation();
                    if (type == 0)
                        bLoc.getWorld().spawnParticle(Particle.FLAME, bLoc.add(0, 1, 0), 3, 0.2, 0.5, 0.2, 0.02);
                    else if (type == 1)
                        bLoc.getWorld().spawnParticle(Particle.SOUL, bLoc.add(0, 1, 0), 3, 0.2, 0.5, 0.2, 0.02);
                    else if (type == 2)
                        bLoc.getWorld().spawnParticle(Particle.PORTAL, bLoc.add(0, 1, 0), 3, 0.2, 0.5, 0.2, 0.02);
                    else if (type == 3)
                        bLoc.getWorld().spawnParticle(Particle.SMOKE, bLoc.add(0, 1, 0), 3, 0.2, 0.5, 0.2, 0.02);

                    double rand = Math.random();
                    if (rand < 0.12) {
                        if (type == 0) { // Ignis
                            boss.getWorld().spawnParticle(Particle.FLAME, boss.getLocation(), 200, 4, 1, 4, 0.2);
                            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1f, 1f);
                            for (Entity e : boss.getNearbyEntities(8, 6, 8)) {
                                if (e instanceof Player) {
                                    Player target = (Player) e;
                                    if (!target.isOp())
                                        target.setFireTicks(160);
                                }
                            }
                        } else if (type == 1) { // Anima
                            boss.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, boss.getLocation(), 100, 3, 2, 3,
                                    0.05);
                            for (UUID id : bossGroup) {
                                Entity bEnt = Bukkit.getEntity(id);
                                if (bEnt instanceof LivingEntity) {
                                    LivingEntity b = (LivingEntity) bEnt;
                                    if (b.isValid())
                                        b.setHealth(Math.min(300, b.getHealth() + 20));
                                }
                            }
                            for (Entity e : boss.getNearbyEntities(10, 10, 10)) {
                                if (e instanceof Player) {
                                    Player target = (Player) e;
                                    if (!target.isOp())
                                        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1));
                                }
                            }
                        } else if (type == 2) { // Abyss
                            boss.getWorld().spawnParticle(Particle.PORTAL, boss.getLocation(), 200, 10, 2, 10, 0);
                            for (Entity e : boss.getNearbyEntities(12, 12, 12)) {
                                if (e instanceof Player) {
                                    Player target = (Player) e;
                                    if (!target.isOp())
                                        target.setVelocity(boss.getLocation().subtract(target.getLocation()).toVector()
                                                .normalize().multiply(1.6));
                                }
                            }
                        } else if (type == 3) { // Ares
                            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 2f,
                                    0.5f);
                            boss.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, boss.getLocation(), 5, 4, 0.5, 4,
                                    0);
                            for (Entity e : boss.getNearbyEntities(10, 5, 10)) {
                                if (e instanceof Player) {
                                    Player target = (Player) e;
                                    if (!target.isOp()) {
                                        target.setVelocity(new Vector(0, 1.8, 0));
                                        target.damage(8, boss);
                                    }
                                }
                            }
                        }
                    }

                    // NEW: Boss Reinforcements
                    if (ticks % 200 == 0) {
                        Zombie minion = (Zombie) boss.getWorld().spawnEntity(boss.getLocation(), EntityType.ZOMBIE);
                        minion.setCustomName("§4Ritual Slave");
                        minion.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
                        minion.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
                        ritualTeam.addEntry(minion.getUniqueId().toString());
                    }
                    ticks++;
                }
            }.runTaskTimer(SkillsBoss.getInstance(), 20, 20);
        }
    }

    private void generateDirectionalAltar(Location center, Vector dir) {
        Vector cross = new Vector(0, 1, 0).crossProduct(dir).normalize();
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                Location l = center.clone().add(dir.clone().multiply(i)).add(cross.clone().multiply(j)).add(0, -1, 0);
                if (i == 0 && j == 0)
                    l.getBlock().setType(Material.NETHERITE_BLOCK);
                else
                    l.getBlock().setType(Material.CRYING_OBSIDIAN);

                if (Math.abs(i) == 1 && Math.abs(j) == 1) {
                    center.clone().add(dir.clone().multiply(i)).add(cross.clone().multiply(j)).getBlock()
                            .setType(Material.SOUL_LANTERN);
                }
            }
        }
    }

    private void generateSmallAltar(Location center) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Location l = center.clone().add(x, -1, z);
                if (x == 0 && z == 0)
                    l.getBlock().setType(Material.NETHERITE_BLOCK);
                else
                    l.getBlock().setType(Material.CRYING_OBSIDIAN);
                if (Math.abs(x) == 1 && Math.abs(z) == 1)
                    center.clone().add(x, 0, z).getBlock().setType(Material.SOUL_LANTERN);
            }
        }
    }

    private void startProgressionTwoTransition(Location loc) {
        SkillsBoss.setProgressionLevel(2);
        transitionActive = true;
        transitionPortal = loc.clone().add(0, 1, 0);

        // Just light the portal, don't create blocks
        Location portalBase = findNearbyPortalFrame(loc, 20);
        if (portalBase != null) {
            lightPortalFrame(portalBase);
        }

        Title title = Title.title(Component.text("PROGRESSION II", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("THE PORTAL AWAKENS", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(3)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
        }
    }

    private Location findNearbyPortalFrame(Location center, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Location check = center.clone().add(x, y, z);
                    if (check.getBlock().getType() == Material.CRYING_OBSIDIAN) {
                        return check;
                    }
                }
            }
        }
        return null;
    }

    private void lightPortalFrame(Location frameBlock) {
        // Find the portal frame and light it
        Location base = frameBlock.clone();
        for (int y = 0; y < 5; y++) {
            for (int x = -1; x <= 1; x++) {
                Location check = base.clone().add(x, y, 0);
                if (check.getBlock().getType() == Material.AIR) {
                    check.getBlock().setType(Material.NETHER_PORTAL);
                }
            }
        }
    }

    private void spawnAltarArmorStand(Location loc) {
        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setBasePlate(false);
        stand.setArms(true);
        stand.setCustomName("§4§lThe Avernus Anchor");
        stand.setCustomNameVisible(true);
        stand.getPersistentDataContainer().set(ALTAR_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    private void playerBroadcast(World world, Component msg) {
        if (msg != null) {
            for (Player p : world.getPlayers()) {
                p.sendMessage(msg);
            }
        }
    }
}
