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
    private static final NamespacedKey FINAL_BOSS_KEY = new NamespacedKey(SkillsBoss.getInstance(), "final_boss");
    private static final NamespacedKey BOSS_PHASE_KEY = new NamespacedKey(SkillsBoss.getInstance(), "boss_phase");

    private static boolean transitionActive = false;
    private static Location transitionPortal = null;

    private final Map<UUID, Set<UUID>> activeWaveMobs = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> activeBars = new ConcurrentHashMap<>();
    private final Set<UUID> bossGroup = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> activatingStands = Collections.synchronizedSet(new HashSet<>());
    private final Map<UUID, Set<UUID>> bossMinions = new ConcurrentHashMap<>();
    private final Set<UUID> shieldedBosses = Collections.synchronizedSet(new HashSet<>());

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

        // Removed Netherite Block placement
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

        // Clear drops for all wave mobs and minions
        if (entity.getPersistentDataContainer().has(WAVE_MOB_KEY, PersistentDataType.STRING)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
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

        // Handle final boss deaths
        // Handle final boss deaths (Supremus)
        if (bossGroup.contains(entity.getUniqueId())) {
            bossGroup.remove(entity.getUniqueId());
            BossBar bar = activeBars.get(entity.getUniqueId());
            if (bar != null) {
                bar.removeAll();
                activeBars.remove(entity.getUniqueId());
            }

            // Clean up minions
            Set<UUID> minions = bossMinions.remove(entity.getUniqueId());
            if (minions != null) {
                minions.forEach(mId -> {
                    Entity m = Bukkit.getEntity(mId);
                    if (m != null)
                        m.remove();
                });
            }

            // WIN CONDITION
            Location deathLoc = entity.getLocation();
            playerBroadcast(deathLoc.getWorld(),
                    Component.text("THE CATACLYSM IS AVERTED!", NamedTextColor.GOLD, TextDecoration.BOLD));

            // Drop Portal Igniter
            ItemStack igniter = ItemManager.createPortalIgniter();
            deathLoc.getWorld().dropItemNaturally(deathLoc, igniter);

            playerBroadcast(deathLoc.getWorld(),
                    Component.text("The Portal Igniter has been dropped!", NamedTextColor.LIGHT_PURPLE,
                            TextDecoration.BOLD));

            // Visual effects
            deathLoc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, deathLoc, 50, 2, 2, 2, 0);
            deathLoc.getWorld().playSound(deathLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        // Handle boss minion deaths
        for (Map.Entry<UUID, Set<UUID>> entry : bossMinions.entrySet()) {
            if (entry.getValue().remove(entity.getUniqueId())) {
                event.getDrops().clear();
                event.setDroppedExp(0);

                // Check if all minions are dead and boss is shielded
                if (entry.getValue().isEmpty() && shieldedBosses.contains(entry.getKey())) {
                    Entity bossEntity = Bukkit.getEntity(entry.getKey());
                    if (bossEntity instanceof LivingEntity) {
                        LivingEntity boss = (LivingEntity) bossEntity;
                        shieldedBosses.remove(entry.getKey());
                        boss.setInvulnerable(false);
                        boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_GLASS_BREAK, 2f, 0.5f);
                        playerBroadcast(boss.getWorld(),
                                Component.text("The shield has shattered!", NamedTextColor.YELLOW,
                                        TextDecoration.BOLD));
                    }
                }
                break;
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
                        Material.IRON_SWORD, stand.getUniqueId(), mobs);
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
                        Material.IRON_SWORD, stand.getUniqueId(), mobs);
                applyDiamondGear(w, 75);
            }
            WitherSkeleton mini = (WitherSkeleton) spawnMob(loc, EntityType.WITHER_SKELETON, "§6§lThe Gatekeeper",
                    Material.IRON_SWORD, stand.getUniqueId(), mobs);
            applyDiamondGear(mini, 120);
            if (mini.getAttribute(Attribute.SCALE) != null)
                mini.getAttribute(Attribute.SCALE).setBaseValue(1.5);

            if (mini.getAttribute(Attribute.MOVEMENT_SPEED) != null)
                mini.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.3);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!mini.isValid()) {
                        cancel();
                        return;
                    }
                    shootCustomBeam(mini, Particle.DRAGON_BREATH, 6.0, false);
                }
            }.runTaskTimer(SkillsBoss.getInstance(), 60, 80);
        }
    }

    private void applyDiamondGear(LivingEntity e, double health) {
        Attribute hpAttr = Attribute.MAX_HEALTH;
        if (e.getAttribute(hpAttr) != null) {
            e.getAttribute(hpAttr).setBaseValue(health);
            e.setHealth(health);
        }
        e.getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        e.getEquipment().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
        e.getEquipment().setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
        e.getEquipment().setBoots(new ItemStack(Material.DIAMOND_BOOTS));
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
        playerBroadcast(loc.getWorld(), Component.text("SUPREMUS AND HIS GUARD HAVE AWAKENED!",
                NamedTextColor.DARK_RED, TextDecoration.BOLD));

        // 1. Spawn Supremus (Main Boss)
        Location spawn = loc.clone().add(0, 2, 0);
        WitherSkeleton boss = (WitherSkeleton) loc.getWorld().spawnEntity(spawn, EntityType.WITHER_SKELETON);
        boss.setCustomName("§4§lSUPREMUS");
        boss.setCustomNameVisible(true);

        Attribute hpAttr = Attribute.MAX_HEALTH;
        if (boss.getAttribute(hpAttr) != null) {
            boss.getAttribute(hpAttr).setBaseValue(1000);
            boss.setHealth(1000);
        }
        Attribute scaleAttr = Attribute.SCALE;
        if (boss.getAttribute(scaleAttr) != null) {
            boss.getAttribute(scaleAttr).setBaseValue(3.0);
        }
        Attribute dmgAttr = Attribute.ATTACK_DAMAGE;
        if (boss.getAttribute(dmgAttr) != null) {
            boss.getAttribute(dmgAttr).setBaseValue(40.0); // Way more damage
        }

        boss.getPersistentDataContainer().set(FINAL_BOSS_KEY, PersistentDataType.BYTE, (byte) 2);
        boss.getPersistentDataContainer().set(BOSS_PHASE_KEY, PersistentDataType.INTEGER, 1);

        // Setup Minions/Shielding
        Set<UUID> minions = Collections.synchronizedSet(new HashSet<>());
        bossMinions.put(boss.getUniqueId(), minions);
        shieldedBosses.add(boss.getUniqueId());
        boss.setInvulnerable(true);

        // Netherite Gear
        boss.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
        boss.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
        boss.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
        boss.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
        boss.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_AXE));
        boss.getEquipment().setHelmetDropChance(0);
        boss.getEquipment().setChestplateDropChance(0);
        boss.getEquipment().setLeggingsDropChance(0);
        boss.getEquipment().setBootsDropChance(0);
        boss.getEquipment().setItemInMainHandDropChance(0);

        ritualTeam.addEntry(boss.getUniqueId().toString());
        bossGroup.add(boss.getUniqueId());

        BossBar suBar = Bukkit.createBossBar("§4§lSUPREMUS", BarColor.RED, BarStyle.SEGMENTED_20);
        for (Player p : boss.getWorld().getPlayers())
            suBar.addPlayer(p);
        activeBars.put(boss.getUniqueId(), suBar);

        // 2. Spawn 4 Sentinels (Bodyguards)
        String[] titles = { "§4§lCrimson Sentinel", "§5§lVoid Sentinel", "§1§lFrost Sentinel", "§c§lWar Sentinel" };

        for (int i = 0; i < 4; i++) {
            Location sLoc = loc.clone().add(Math.cos(i * Math.PI / 2) * 6, 0, Math.sin(i * Math.PI / 2) * 6);
            WitherSkeleton sentinel = (WitherSkeleton) loc.getWorld().spawnEntity(sLoc, EntityType.WITHER_SKELETON);
            sentinel.setCustomName(titles[i]);
            sentinel.setCustomNameVisible(true);

            if (sentinel.getAttribute(Attribute.MAX_HEALTH) != null) {
                sentinel.getAttribute(Attribute.MAX_HEALTH).setBaseValue(150);
                sentinel.setHealth(150);
            }
            if (sentinel.getAttribute(Attribute.SCALE) != null) {
                sentinel.getAttribute(Attribute.SCALE).setBaseValue(0.8);
            }
            if (sentinel.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
                sentinel.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(20.0); // Buffed Sentinel Damage
            }

            sentinel.getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
            sentinel.getEquipment().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
            sentinel.getEquipment().setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
            sentinel.getEquipment().setBoots(new ItemStack(Material.DIAMOND_BOOTS));
            sentinel.getEquipment()
                    .setItemInMainHand(new ItemStack(i == 3 ? Material.DIAMOND_AXE : Material.DIAMOND_SWORD));

            ritualTeam.addEntry(sentinel.getUniqueId().toString());
            minions.add(sentinel.getUniqueId()); // Add to MINIONS, not bossGroup

            // Sentinel Logic
            final int type = i;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!sentinel.isValid()) {
                        cancel();
                        return;
                    }

                    double rand = Math.random();
                    if (rand < 0.12) {
                        try {
                            triggerBossAbility(sentinel, type, false);
                        } catch (Exception e) {
                        }
                    }
                    if (sentinel.getLocation().distance(spawn) > 20) {
                        sentinel.teleport(spawn);
                        sentinel.getWorld().spawnParticle(Particle.PORTAL, sentinel.getLocation(), 20, 0.5, 1, 0.5, 0);
                    }
                }
            }.runTaskTimer(SkillsBoss.getInstance(), 20, 20);
        }

        playerBroadcast(loc.getWorld(),
                Component.text("Supremus is shielded by his Guard! Destroy them!", NamedTextColor.GOLD));

        // 3. Supremus Logic
        new BukkitRunnable() {
            int ticks = 0;
            int phase = 1;
            boolean phase2 = false;
            boolean phase3 = false;

            @Override
            public void run() {
                if (!boss.isValid()) {
                    suBar.removeAll();
                    activeBars.remove(boss.getUniqueId());
                    cancel();
                    return;
                }

                double progress = boss.getHealth() / 1000.0;
                if (progress < 0)
                    progress = 0;
                if (progress > 1)
                    progress = 1;
                suBar.setProgress(progress);

                // Phase Transitions
                if (!phase2 && boss.getHealth() <= 666) {
                    phase2 = true;
                    phase = 2;
                    enterPhase2(boss, 0); // Reuse visual
                    boss.getPersistentDataContainer().set(BOSS_PHASE_KEY, PersistentDataType.INTEGER, 2);
                }
                if (!phase3 && boss.getHealth() <= 333) {
                    phase3 = true;
                    phase = 3;
                    enterPhase3(boss, 0); // Reuse visual (adds minions? No, shield logic handled separately)
                    boss.getPersistentDataContainer().set(BOSS_PHASE_KEY, PersistentDataType.INTEGER, 3);
                }

                // Abilities
                // Phase 1: Obliterate (Shockwave)
                if (ticks % 100 == 0) {
                    boss.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, boss.getLocation(), 10, 3, 1, 3, 0);
                    boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.5f);
                    for (Entity e : boss.getNearbyEntities(15, 10, 15)) {
                        if (e instanceof Player && !e.isOp()) {
                            ((LivingEntity) e).damage(phase >= 2 ? 35 : 25, boss); // Buffed Damage
                            e.setVelocity(e.getLocation().subtract(boss.getLocation()).toVector().normalize()
                                    .multiply(1.5).setY(0.5));
                            ((Player) e).sendMessage(Component.text("Supremus uses OBLITERATE!",
                                    NamedTextColor.DARK_RED, TextDecoration.BOLD));
                        }
                    }
                }

                // New Ability: Soul Siphon (Cool Heal Spell)
                if (ticks % 400 == 0 && boss.getHealth() < 1000) {
                    boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 2f, 0.5f);
                    boss.getWorld().spawnParticle(Particle.SCULK_SOUL, boss.getLocation(), 50, 1, 2, 1, 0.1);

                    boolean drained = false;
                    for (Entity e : boss.getNearbyEntities(20, 10, 20)) {
                        if (e instanceof Player && !e.isOp()) {
                            drained = true;
                            LivingEntity target = (LivingEntity) e;
                            target.damage(12, boss);

                            // Visual trail: Hearts from player to boss
                            Location pLoc = target.getLocation().add(0, 1, 0);
                            Location bLoc = boss.getLocation().add(0, 2, 0);
                            double dist = pLoc.distance(bLoc);
                            Vector dir = bLoc.toVector().subtract(pLoc.toVector()).normalize();
                            for (double d = 0; d < dist; d += 0.5) {
                                target.getWorld().spawnParticle(Particle.HEART,
                                        pLoc.clone().add(dir.clone().multiply(d)), 1, 0, 0, 0, 0);
                            }

                            double heal = 60.0;
                            boss.setHealth(Math.min(1000, boss.getHealth() + heal));
                        }
                    }
                    if (drained) {
                        playerBroadcast(boss.getWorld(), Component.text("Supremus SIPHONS your soul to heal!",
                                NamedTextColor.DARK_GREEN, TextDecoration.BOLD));
                        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.5f);
                    }
                }

                // Phase 2: Cataclysm (Meteor)
                if (phase >= 2 && ticks % 160 == 0) {
                    boss.getWorld().playSound(boss.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 2f, 0.5f);
                    for (Entity e : boss.getNearbyEntities(20, 10, 20)) {
                        if (e instanceof Player && !e.isOp()) {
                            Location target = e.getLocation();
                            // Delay strike
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    target.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, target, 1);
                                    target.getWorld().playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                                    for (Entity hit : target.getWorld().getNearbyEntities(target, 3, 3, 3)) {
                                        if (hit instanceof Player)
                                            ((LivingEntity) hit).damage(25, boss); // Buffed Damage
                                    }
                                }
                            }.runTaskLater(SkillsBoss.getInstance(), 20);
                        }
                    }
                    playerBroadcast(boss.getWorld(), Component.text("Supremus summons CATACLYSM!", NamedTextColor.RED));
                }

                // Phase 3: Void Rift (Pull)
                if (phase >= 3 && ticks % 200 == 0) {
                    boss.getWorld().spawnParticle(Particle.PORTAL, boss.getLocation(), 100, 5, 5, 5, 0.1);
                    boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 1f, 0.5f);
                    for (Entity e : boss.getNearbyEntities(25, 25, 25)) {
                        if (e instanceof Player && !e.isOp()) {
                            e.setVelocity(
                                    boss.getLocation().subtract(e.getLocation()).toVector().normalize().multiply(2.0));
                            ((LivingEntity) e).damage(5, boss);
                        }
                    }
                    playerBroadcast(boss.getWorld(),
                            Component.text("The VOID RIFT consumes all!", NamedTextColor.DARK_PURPLE));
                }

                // Tethering
                if (boss.getLocation().distance(spawn) > 30) {
                    boss.teleport(spawn);
                }

                ticks++;
            }
        }.runTaskTimer(SkillsBoss.getInstance(), 20, 20);
    }

    private void generateDirectionalAltar(Location center, Vector dir) {
        Vector cross = new Vector(0, 1, 0).crossProduct(dir).normalize();
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                Location l = center.clone().add(dir.clone().multiply(i)).add(cross.clone().multiply(j)).add(0, -1, 0);
                if (i == 0 && j == 0)
                    l.getBlock().setType(Material.CRYING_OBSIDIAN);
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
                    l.getBlock().setType(Material.CRYING_OBSIDIAN);
                else
                    l.getBlock().setType(Material.CRYING_OBSIDIAN);
                if (Math.abs(x) == 1 && Math.abs(z) == 1)
                    center.clone().add(x, 0, z).getBlock().setType(Material.SOUL_LANTERN);
            }
        }
    }

    private void startProgressionTwoTransition(Location loc) {
        // Portal will be lit when player uses Portal Igniter
        transitionActive = true;
        transitionPortal = loc.clone().add(0, 1, 0);
    }

    @EventHandler
    public void onPortalIgniterUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        if (event.getHand() != EquipmentSlot.HAND)
            return;
        ItemStack item = event.getItem();
        if (item == null || !ItemManager.isPortalIgniter(item))
            return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CRYING_OBSIDIAN)
            return;

        event.setCancelled(true);

        Location portalBase = findNearbyPortalFrame(block.getLocation(), 5);
        if (portalBase != null) {
            item.setAmount(item.getAmount() - 1);
            lightPortalFrame(portalBase);

            SkillsBoss.setProgressionLevel(2);

            Title title = Title.title(Component.text("PROGRESSION II", NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text("THE PORTAL AWAKENS", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofSeconds(2)));
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showTitle(title);
            }

            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 2f, 0.5f);
            block.getWorld().spawnParticle(Particle.PORTAL, block.getLocation().add(0.5, 0.5, 0.5), 200, 2, 2, 2, 0.5);
        } else {
            event.getPlayer().sendMessage(Component.text("This is not a valid portal frame!", NamedTextColor.RED));
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
        stand.setCustomName("§4§lThe Avernus Altar");
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

    private void enterPhase2(LivingEntity boss, int type) {
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, 0.8f);
        boss.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, boss.getLocation(), 10, 2, 1, 2, 0);

        playerBroadcast(boss.getWorld(),
                Component.text(boss.getCustomName() + " enters Phase 2!", NamedTextColor.YELLOW, TextDecoration.BOLD));

        // Increase speed and damage
        if (boss.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
            double currentSpeed = boss.getAttribute(Attribute.MOVEMENT_SPEED).getBaseValue();
            boss.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(currentSpeed * 1.2);
        }
        if (boss.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
            double currentDmg = boss.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue();
            boss.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(currentDmg * 1.3);
        }
    }

    private void enterPhase3(LivingEntity boss, int type) {
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WITHER_SPAWN, 2f, 0.5f);
        boss.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, boss.getLocation(), 20, 3, 2, 3, 0);

        playerBroadcast(boss.getWorld(),
                Component.text(boss.getCustomName() + " enters FINAL PHASE!", NamedTextColor.RED, TextDecoration.BOLD));

        // Shield boss
        boss.setInvulnerable(true);
        shieldedBosses.add(boss.getUniqueId());

        // Spawn 4 minions
        Set<UUID> minions = bossMinions.get(boss.getUniqueId());
        String[] minionNames = { "§4Crimson Guard", "§5Void Guard", "§1Frost Guard", "§cWar Guard" };

        for (int i = 0; i < 4; i++) {
            double angle = (i * Math.PI / 2);
            Location spawnLoc = boss.getLocation().clone().add(
                    Math.cos(angle) * 4, 0, Math.sin(angle) * 4);

            WitherSkeleton minion = (WitherSkeleton) boss.getWorld().spawnEntity(spawnLoc, EntityType.WITHER_SKELETON);
            minion.setCustomName(minionNames[i]);
            minion.setCustomNameVisible(true);

            if (minion.getAttribute(Attribute.MAX_HEALTH) != null) {
                minion.getAttribute(Attribute.MAX_HEALTH).setBaseValue(80);
                minion.setHealth(80);
            }

            minion.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            minion.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
            minion.getEquipment().setHelmetDropChance(0);
            minion.getEquipment().setChestplateDropChance(0);
            minion.getEquipment().setItemInMainHandDropChance(0);

            ritualTeam.addEntry(minion.getUniqueId().toString());
            minions.add(minion.getUniqueId());
        }

        playerBroadcast(boss.getWorld(),
                Component.text("Destroy the guards to break the shield!", NamedTextColor.GOLD));

        // Further stat boost
        if (boss.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
            double currentSpeed = boss.getAttribute(Attribute.MOVEMENT_SPEED).getBaseValue();
            boss.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(currentSpeed * 1.3);
        }
        if (boss.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
            double currentDmg = boss.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue();
            boss.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(currentDmg * 1.5);
        }
    }

    private void triggerBossAbility(LivingEntity boss, int type, boolean enhanced) {
        double multiplier = enhanced ? 1.5 : 1.0;

        if (type == 0) { // Crimson - Fire
            boss.getWorld().spawnParticle(Particle.FLAME, boss.getLocation(), (int) (200 * multiplier), 4, 1, 4, 0.2);
            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1f, 1f);
            for (Entity e : boss.getNearbyEntities(8, 6, 8)) {
                if (e instanceof Player) {
                    Player target = (Player) e;
                    if (!target.isOp())
                        target.setFireTicks((int) (160 * multiplier));
                }
            }
        } else if (type == 1) { // Void - Wither
            boss.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, boss.getLocation(), (int) (100 * multiplier), 3, 2,
                    3, 0.05);
            for (UUID id : bossGroup) {
                Entity bEnt = Bukkit.getEntity(id);
                if (bEnt instanceof LivingEntity) {
                    LivingEntity b = (LivingEntity) bEnt;
                    if (b.isValid())
                        b.setHealth(Math.min(450, b.getHealth() + 20 * multiplier));
                }
            }
            for (Entity e : boss.getNearbyEntities(10, 10, 10)) {
                if (e instanceof Player) {
                    Player target = (Player) e;
                    if (!target.isOp())
                        target.addPotionEffect(
                                new PotionEffect(PotionEffectType.WITHER, (int) (100 * multiplier), enhanced ? 2 : 1));
                }
            }
        } else if (type == 2) { // Frost - Pull
            boss.getWorld().spawnParticle(Particle.PORTAL, boss.getLocation(), (int) (200 * multiplier), 10, 2, 10, 0);
            for (Entity e : boss.getNearbyEntities(12, 12, 12)) {
                if (e instanceof Player) {
                    Player target = (Player) e;
                    if (!target.isOp())
                        target.setVelocity(boss.getLocation().subtract(target.getLocation()).toVector()
                                .normalize().multiply(1.6 * multiplier));
                }
            }
        } else if (type == 3) { // War - Slam
            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 2f, 0.5f);
            boss.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, boss.getLocation(), (int) (5 * multiplier), 4,
                    0.5, 4, 0);
            for (Entity e : boss.getNearbyEntities(10, 5, 10)) {
                if (e instanceof Player) {
                    Player target = (Player) e;
                    if (!target.isOp()) {
                        target.setVelocity(new Vector(0, 1.8 * multiplier, 0));
                        target.damage(8 * multiplier, boss);
                    }
                }
            }
        }
    }
}
