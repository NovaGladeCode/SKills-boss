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
import org.bukkit.event.block.BlockPlaceEvent;
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
import java.util.Random;
import java.util.stream.Collectors;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BossListener implements Listener {

    private static final NamespacedKey ALTAR_KEY = new NamespacedKey(SkillsBoss.getInstance(), "boss_altar");
    private static final NamespacedKey WAVE_MOB_KEY = new NamespacedKey(SkillsBoss.getInstance(), "wave_mob");
    private static final NamespacedKey FINAL_BOSS_KEY = new NamespacedKey(SkillsBoss.getInstance(), "final_boss");
    private static final NamespacedKey BOSS_PHASE_KEY = new NamespacedKey(SkillsBoss.getInstance(), "boss_phase");
    private static final NamespacedKey DROP_ITEM_KEY = new NamespacedKey(SkillsBoss.getInstance(), "drop_item");

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

            // Drop Legendary Diamond Sword
            deathLoc.getWorld().dropItemNaturally(deathLoc, ItemManager.createCustomItem(Material.DIAMOND_SWORD));

            playerBroadcast(deathLoc.getWorld(),
                    Component.text("The Portal Igniter has been dropped!", NamedTextColor.LIGHT_PURPLE,
                            TextDecoration.BOLD));

            // Clean up Altar
            deathLoc.getWorld().getNearbyEntities(deathLoc, 50, 50, 50).forEach(e -> {
                if (e.getType() == EntityType.ARMOR_STAND && e.customName() != null
                        && net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                                .serialize(e.customName()).contains("Altar")) {
                    e.remove();
                    // Change nearby Crying Obsidian to Obsidian (Deactivate)
                    for (int x = -2; x <= 2; x++) {
                        for (int z = -2; z <= 2; z++) {
                            Block b = e.getLocation().add(x, -1, z).getBlock();
                            if (b.getType() == Material.CRYING_OBSIDIAN || b.getType() == Material.NETHERITE_BLOCK) {
                                b.setType(Material.OBSIDIAN);
                            }
                        }
                    }
                }
            });

            // Visual effects
            deathLoc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, deathLoc, 50, 2, 2, 2, 0);
            deathLoc.getWorld().playSound(deathLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        // Handle boss minion deaths
        for (Map.Entry<UUID, Set<UUID>> entry : bossMinions.entrySet()) {
            if (entry.getValue().remove(entity.getUniqueId())) {
                event.getDrops().clear();
                event.setDroppedExp(0);

                // Handle Custom Drop
                if (entity.getPersistentDataContainer().has(DROP_ITEM_KEY, PersistentDataType.STRING)) {
                    String matName = entity.getPersistentDataContainer().get(DROP_ITEM_KEY, PersistentDataType.STRING);
                    if (matName != null) {
                        Material mat = Material.valueOf(matName);
                        ItemStack drop = ItemManager.createCustomItem(mat);
                        entity.getWorld().dropItemNaturally(entity.getLocation(), drop);
                    }
                }

                // Check if all minions are dead and boss is shielded
                if (entry.getValue().isEmpty() && shieldedBosses.contains(entry.getKey())) {
                    Entity bossEntity = Bukkit.getEntity(entry.getKey());
                    if (bossEntity instanceof LivingEntity) {
                        LivingEntity boss = (LivingEntity) bossEntity;
                        shieldedBosses.remove(entry.getKey());
                        boss.setInvulnerable(false);
                        boss.setAI(true); // Re-enable movement/AI
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

    @EventHandler
    public void onAltarDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getEntity() instanceof ArmorStand) {
            if (event.getEntity().getPersistentDataContainer().has(ALTAR_KEY, PersistentDataType.BYTE)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBossHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof WitherSkeleton && event.getEntity() instanceof Player) {
            WitherSkeleton boss = (WitherSkeleton) event.getDamager();
            if (boss.getPersistentDataContainer().has(FINAL_BOSS_KEY, PersistentDataType.BYTE)) {
                Player p = (Player) event.getEntity();
                p.setVelocity(
                        p.getLocation().subtract(boss.getLocation()).toVector().normalize().multiply(1.8).setY(0.5));
                p.getWorld().spawnParticle(Particle.SONIC_BOOM, p.getEyeLocation(), 1);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 2f);
            }
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
            int waveTicks = 0;

            @Override
            public void run() {
                if (!stand.isValid()) {
                    endRitual(standUuid, ritualBar);
                    cancel();
                    return;
                }

                Set<UUID> mobs = activeWaveMobs.get(standUuid);
                if (mobs == null) {
                    SkillsBoss.getInstance().getLogger().warning("Altar " + standUuid + " lost its mob tracking set!");
                    cancel();
                    return;
                }

                if (waiting) {
                    waveTicks++;

                    // Clean up invalid entities from the tracking set
                    mobs.removeIf(id -> {
                        Entity ent = Bukkit.getEntity(id);
                        return ent == null || !ent.isValid() || ent.isDead();
                    });

                    if (mobs.isEmpty()) {
                        // If everything is dead, and we've waited at least 2 ticks (safety)
                        if (waveTicks >= 40) { // 2 seconds safety delay
                            waiting = false;
                            waveTicks = 0;
                            stand.getWorld().playSound(stand.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 2f);
                            playerBroadcast(stand.getWorld(), Component.text("The Trial continues...",
                                    NamedTextColor.DARK_GRAY, TextDecoration.ITALIC));
                        }
                    } else {
                        // Mobs are still alive, keep waiting
                        return;
                    }
                }

                // If not waiting, spawn the next thing
                SkillsBoss.getInstance().getLogger()
                        .info("Transitioning Altar " + standUuid + " to wave " + (waveNum + 1));
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
            for (int i = 0; i < 8; i++) { // Doubled (8)
                Skeleton e = (Skeleton) spawnMob(loc, EntityType.SKELETON, "§eFallen Sentry", Material.BOW,
                        stand.getUniqueId(), mobs);
                if (e != null)
                    applyDiamondGear(e, 40);
            }
        } else if (waveId == 2) {
            for (int i = 0; i < 8; i++) { // Doubled (8)
                Zombie e = (Zombie) spawnMob(loc, EntityType.ZOMBIE, "§9Undead Sentinel", Material.IRON_SWORD,
                        stand.getUniqueId(), mobs);
                if (e != null)
                    applyDiamondGear(e, 50);
            }
        } else if (waveId == 3) {
            for (int i = 0; i < 8; i++) { // Doubled (8)
                WitherSkeleton e = (WitherSkeleton) spawnMob(loc, EntityType.WITHER_SKELETON, "§cAvernus Guard",
                        Material.IRON_SWORD, stand.getUniqueId(), mobs);
                if (e != null)
                    applyDiamondGear(e, 75);
            }
        } else if (waveId == 4) {
            // Doubled Mobs
            for (int i = 0; i < 4; i++) {
                Skeleton s = (Skeleton) spawnMob(loc, EntityType.SKELETON, "§eFallen Sentry", Material.BOW,
                        stand.getUniqueId(), mobs);
                if (s != null)
                    applyDiamondGear(s, 40);
                Zombie z = (Zombie) spawnMob(loc, EntityType.ZOMBIE, "§9Undead Sentinel", Material.IRON_SWORD,
                        stand.getUniqueId(), mobs);
                if (z != null)
                    applyDiamondGear(z, 50);
                WitherSkeleton w = (WitherSkeleton) spawnMob(loc, EntityType.WITHER_SKELETON, "§cAvernus Guard",
                        Material.IRON_SWORD, stand.getUniqueId(), mobs);
                if (w != null)
                    applyDiamondGear(w, 75);
            }

            // 1. Bow Gatekeeper (Archer)
            Skeleton archer = (Skeleton) spawnMob(loc, EntityType.SKELETON, "§6§lThe Gatekeeper (Archer)",
                    Material.BOW, stand.getUniqueId(), mobs);
            if (archer != null)
                applyDiamondGear(archer, 150); // Buff HP

            // 2. Sword Gatekeeper (Warrior)
            WitherSkeleton warrior = (WitherSkeleton) spawnMob(loc, EntityType.WITHER_SKELETON,
                    "§6§lThe Gatekeeper (Warrior)",
                    Material.DIAMOND_SWORD, stand.getUniqueId(), mobs);
            if (warrior != null) {
                applyDiamondGear(warrior, 150);
                // Warrior Stats (Fast, No Fire Argument removed)
                if (warrior.getAttribute(Attribute.MOVEMENT_SPEED) != null)
                    warrior.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.40); // Fast

                // Warrior Ability: Grip of the Gatekeeper (Pull & Smash)
                setupWarriorLogic(warrior);
            }

            if (archer != null) {
                // Archer Name Tag for Listener
                archer.getPersistentDataContainer().set(new NamespacedKey(SkillsBoss.getInstance(), "explosive_arrow"),
                        PersistentDataType.BYTE, (byte) 1);
            }
        }
    }

    private void setupWarriorLogic(WitherSkeleton warrior) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!warrior.isValid()) {
                    cancel();
                    return;
                }
                List<Player> targets = warrior.getWorld().getNearbyEntities(warrior.getLocation(), 15, 15, 15)
                        .stream()
                        .filter(e -> e instanceof Player && ((Player) e).getGameMode() == GameMode.SURVIVAL)
                        .map(e -> (Player) e)
                        .collect(Collectors.toList());

                if (!targets.isEmpty()) {
                    Player target = targets.get(new Random().nextInt(targets.size()));
                    warrior.getWorld().playSound(warrior.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
                    target.teleport(warrior.getLocation().add(warrior.getLocation().getDirection()));

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (!target.isOnline() || !warrior.isValid())
                                return;
                            warrior.swingMainHand();
                            warrior.getWorld().playSound(warrior.getLocation(),
                                    Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 0.5f);
                            target.damage(25, warrior);
                            target.setVelocity(warrior.getLocation().getDirection().multiply(2.5).setY(0.8));
                            target.sendMessage(Component.text("The Gatekeeper SMASHES you away!",
                                    NamedTextColor.GOLD, TextDecoration.BOLD));
                        }
                    }.runTaskLater(SkillsBoss.getInstance(), 10);
                }
            }
        }.runTaskTimer(SkillsBoss.getInstance(), 100, 300); // Every 15s (300 ticks)
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
        // Randomize spawn location within 8 blocks
        double rx = (Math.random() * 16) - 8;
        double rz = (Math.random() * 16) - 8;
        Location spawnLoc = loc.clone().add(rx, 0, rz);

        // Find a safe Y level (scan from +3 down to -3)
        boolean foundFloor = false;
        for (double dy = 3; dy >= -3; dy--) {
            Block b = spawnLoc.clone().add(0, dy, 0).getBlock();
            if (b.getType().isSolid()) {
                spawnLoc.add(0, dy + 1.1, 0); // Spawning slightly above floor
                foundFloor = true;
                break;
            }
        }

        // If no solid ground found in range, just spawn at altar level + 1
        if (!foundFloor) {
            spawnLoc = loc.clone().add(rx, 1.1, rz);
        }

        LivingEntity e = (LivingEntity) loc.getWorld().spawnEntity(spawnLoc, type);

        if (e == null) {
            SkillsBoss.getInstance().getLogger().warning("[Ritual] FAILED to spawn " + type.name() + " at " + spawnLoc);
            playerBroadcast(loc.getWorld(), Component.text("A dark energy failed to manifest...", NamedTextColor.RED));
            return null;
        }

        e.customName(Component.text(name));
        e.setCustomNameVisible(true);
        if (hand != null)
            e.getEquipment().setItemInMainHand(new ItemStack(hand));

        // Mark the mob
        e.getPersistentDataContainer().set(WAVE_MOB_KEY, PersistentDataType.STRING, standUuid.toString());

        if (ritualTeam != null) {
            ritualTeam.addEntry(e.getUniqueId().toString());
        }

        mobs.add(e.getUniqueId());
        return e;
    }

    private void spawnBosses(Location loc) {
        playerBroadcast(loc.getWorld(), Component.text("SUPREMUS AND HIS GUARD HAVE AWAKENED!",
                NamedTextColor.DARK_RED, TextDecoration.BOLD));

        // 1. Spawn Supremus (Main Boss)
        Location spawn = loc.clone().add(0, 2, 0);
        WitherSkeleton boss = (WitherSkeleton) loc.getWorld().spawnEntity(spawn, EntityType.WITHER_SKELETON);
        boss.customName(Component.text("§4§lSUPREMUS"));
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
            boss.getAttribute(dmgAttr).setBaseValue(25.0); // Reduced from 40 to 25
        }

        boss.getPersistentDataContainer().set(FINAL_BOSS_KEY, PersistentDataType.BYTE, (byte) 2);
        boss.getPersistentDataContainer().set(BOSS_PHASE_KEY, PersistentDataType.INTEGER, 1);

        // Setup Minions/Shielding
        Set<UUID> minions = Collections.synchronizedSet(new HashSet<>());
        bossMinions.put(boss.getUniqueId(), minions);
        shieldedBosses.add(boss.getUniqueId());
        boss.setInvulnerable(true);
        boss.setAI(false); // Freeze the boss

        // Netherite Gear
        boss.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
        boss.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
        boss.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
        boss.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
        boss.getEquipment().setItemInMainHand(new ItemStack(Material.MACE));
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

        // 2. Spawn 5 Sentinels (Bodyguards)
        String[] titles = { "§4§lCrimson Sentinel", "§5§lVoid Sentinel", "§1§lFrost Sentinel", "§c§lWar Sentinel",
                "§8§lShadow Sentinel" };

        for (int i = 0; i < 5; i++) {
            Location sLoc = loc.clone().add(Math.cos(i * Math.PI * 2 / 5) * 6, 0, Math.sin(i * Math.PI * 2 / 5) * 6);
            WitherSkeleton sentinel = (WitherSkeleton) loc.getWorld().spawnEntity(sLoc, EntityType.WITHER_SKELETON);
            sentinel.customName(Component.text(titles[i]));
            sentinel.setCustomNameVisible(true);

            if (sentinel.getAttribute(Attribute.MAX_HEALTH) != null) {
                sentinel.getAttribute(Attribute.MAX_HEALTH).setBaseValue(150);
                sentinel.setHealth(150);
            }
            if (sentinel.getAttribute(Attribute.SCALE) != null) {
                sentinel.getAttribute(Attribute.SCALE).setBaseValue(0.8);
            }
            if (sentinel.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
                sentinel.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(40.0); // Doubled AGAIN (Way more damage)
            }

            // Mixed Gear (1 Diamond piece)
            ItemStack helm = new ItemStack(Material.IRON_HELMET);
            ItemStack chest = new ItemStack(Material.IRON_CHESTPLATE);
            ItemStack leg = new ItemStack(Material.IRON_LEGGINGS);
            ItemStack boot = new ItemStack(Material.IRON_BOOTS);
            Material dropMaterial = null;

            if (i == 0) {
                helm = new ItemStack(Material.DIAMOND_HELMET);
                dropMaterial = Material.DIAMOND_HELMET;
            } else if (i == 1) {
                chest = new ItemStack(Material.DIAMOND_CHESTPLATE);
                dropMaterial = Material.DIAMOND_CHESTPLATE;
            } else if (i == 2) {
                leg = new ItemStack(Material.DIAMOND_LEGGINGS);
                dropMaterial = Material.DIAMOND_LEGGINGS;
            } else if (i == 3) {
                boot = new ItemStack(Material.DIAMOND_BOOTS);
                dropMaterial = Material.DIAMOND_BOOTS;
            } else if (i == 4) {
                // 5th Guard gets full iron + held sword, drops Sword
                dropMaterial = Material.DIAMOND_SWORD;
            }

            sentinel.getEquipment().setHelmet(helm);
            sentinel.getEquipment().setChestplate(chest);
            sentinel.getEquipment().setLeggings(leg);
            sentinel.getEquipment().setBoots(boot);

            sentinel.getEquipment()
                    .setItemInMainHand(
                            new ItemStack((i == 3 || i == 4) ? Material.DIAMOND_SWORD : Material.DIAMOND_SWORD));

            // Save drop item
            if (dropMaterial != null) {
                sentinel.getPersistentDataContainer().set(DROP_ITEM_KEY, PersistentDataType.STRING,
                        dropMaterial.name());
            }

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

                // If shielded, just play idle stasis effects and skip attacks
                if (shieldedBosses.contains(boss.getUniqueId())) {
                    if (ticks % 20 == 0) {
                        boss.getWorld().spawnParticle(Particle.WITCH, boss.getLocation().add(0, 1, 0), 10, 0.5, 1, 0.5,
                                0);
                        // Draw lines to minions
                        Set<UUID> myMinions = bossMinions.get(boss.getUniqueId());
                        if (myMinions != null) {
                            for (UUID mId : myMinions) {
                                Entity m = Bukkit.getEntity(mId);
                                if (m != null && m.isValid()) {
                                    Location start = boss.getLocation().add(0, 1.5, 0);
                                    Location end = m.getLocation().add(0, 1.5, 0);
                                    double dist = start.distance(end);
                                    Vector dir = end.toVector().subtract(start.toVector()).normalize();
                                    for (double d = 0; d < dist; d += 1.0) {
                                        boss.getWorld().spawnParticle(Particle.DUST,
                                                start.clone().add(dir.clone().multiply(d)), 1,
                                                new Particle.DustOptions(org.bukkit.Color.MAROON, 1.0f));
                                    }
                                }
                            }
                        }
                    }

                    // Force orientation to center or look at players continuously?
                    // Currently just skipping attacks.
                    ticks++;
                    return;
                }

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
    public void onPortalPlace(BlockPlaceEvent event) {
        if (ItemManager.isPortalObsidian(event.getItemInHand())) {
            event.setCancelled(true);

            // Consume 1 item
            if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
                event.getItemInHand().setAmount(event.getItemInHand().getAmount() - 1);
            }

            Location loc = event.getBlock().getLocation();
            generateNetherPortalFrame(loc);

            event.getPlayer().sendMessage(
                    Component.text("The Portal Frame manifests before you...", NamedTextColor.DARK_PURPLE));
            loc.getWorld().playSound(loc, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 0.5f);
        }
    }

    private void generateNetherPortalFrame(Location center) {
        // Clear area (safety)
        for (int x = -2; x <= 2; x++) {
            for (int y = 0; y <= 5; y++) {
                center.clone().add(x, y, 0).getBlock().setType(Material.AIR);
            }
        }

        // Build Frame
        // Base
        for (int x = -2; x <= 2; x++)
            center.clone().add(x, 0, 0).getBlock().setType(Material.CRYING_OBSIDIAN);
        // Top
        for (int x = -2; x <= 2; x++)
            center.clone().add(x, 5, 0).getBlock().setType(Material.CRYING_OBSIDIAN);
        // Sides
        for (int y = 1; y <= 4; y++) {
            center.clone().add(-2, y, 0).getBlock().setType(Material.CRYING_OBSIDIAN);
            center.clone().add(2, y, 0).getBlock().setType(Material.CRYING_OBSIDIAN);
        }
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
        Location best = null;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Location check = center.clone().add(x, y, z);
                    if (check.getBlock().getType() == Material.CRYING_OBSIDIAN) {
                        // Return the LOWEST Y block (likely base)
                        if (best == null || check.getY() < best.getY()) {
                            best = check;
                        }
                    }
                }
            }
        }
        return best;
    }

    private void lightPortalFrame(Location base) {
        // base is the lowest Crying Obsidian (y=0 in our generation)
        // Light the area INSIDE the frame (y=1 to y=4)
        for (int y = 1; y <= 4; y++) {
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
        stand.setInvulnerable(true); // Fix: Entities cannot destroy it
        stand.getPersistentDataContainer().set(ALTAR_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    private void playerBroadcast(World world, Component msg) {
        if (msg != null) {
            for (Player p : world.getPlayers()) {
                p.sendMessage(msg);
            }
        }
    }

    @EventHandler
    public void onProjectileHit(org.bukkit.event.entity.ProjectileHitEvent event) {
        if (event.getEntity() instanceof Arrow && event.getEntity().getShooter() instanceof Skeleton) {
            Skeleton shooter = (Skeleton) event.getEntity().getShooter();
            if (shooter.getPersistentDataContainer().has(new NamespacedKey(SkillsBoss.getInstance(), "explosive_arrow"),
                    PersistentDataType.BYTE)) {
                Location hit = event.getHitBlock() != null ? event.getHitBlock().getLocation()
                        : event.getEntity().getLocation();
                hit.getWorld().createExplosion(hit, 2.0f, false, false);
                event.getEntity().remove();
            }
        }
    }

    private void enterPhase2(LivingEntity boss, int type) {
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, 0.8f);
        boss.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, boss.getLocation(), 10, 2, 1, 2, 0);

        String currentName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(boss.customName());
        playerBroadcast(boss.getWorld(),
                Component.text(currentName + " enters Phase 2!", NamedTextColor.YELLOW, TextDecoration.BOLD));

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

        String currentName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(boss.customName());
        playerBroadcast(boss.getWorld(),
                Component.text(currentName + " enters FINAL PHASE!", NamedTextColor.RED, TextDecoration.BOLD));

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
            minion.customName(Component.text(minionNames[i]));
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
