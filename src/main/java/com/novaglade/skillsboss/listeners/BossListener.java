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

    private static BossListener instance;
    private static final NamespacedKey ALTAR_KEY = new NamespacedKey(SkillsBoss.getInstance(), "boss_altar");
    private static final NamespacedKey WAVE_MOB_KEY = new NamespacedKey(SkillsBoss.getInstance(), "wave_mob");
    private static final NamespacedKey FINAL_BOSS_KEY = new NamespacedKey(SkillsBoss.getInstance(), "final_boss");
    private static final NamespacedKey BOSS_PHASE_KEY = new NamespacedKey(SkillsBoss.getInstance(), "boss_phase");
    private static final NamespacedKey DROP_ITEM_KEY = new NamespacedKey(SkillsBoss.getInstance(), "drop_item");

    private static boolean transitionActive = false;

    private final Map<UUID, Set<UUID>> activeWaveMobs = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> activeBars = new ConcurrentHashMap<>();
    private final Set<UUID> bossGroup = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> activatingStands = Collections.synchronizedSet(new HashSet<>());
    private final Map<UUID, Set<UUID>> bossMinions = new ConcurrentHashMap<>();
    private final Set<UUID> shieldedBosses = Collections.synchronizedSet(new HashSet<>());

    private Team ritualTeam;

    public BossListener() {
        instance = this;
        org.bukkit.scoreboard.Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        ritualTeam = sb.getTeam("AvernusRitual");
        if (ritualTeam == null) {
            ritualTeam = sb.registerNewTeam("AvernusRitual");
        }
        ritualTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        ritualTeam.setAllowFriendlyFire(false);
    }

    public static BossListener getInstance() {
        return instance;
    }

    public static boolean isTransitionActive() {
        return transitionActive;
    }

    @EventHandler
    public void onAltarPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!ItemManager.isBossSpawnItem(item))
            return;

        Block block = event.getBlock();
        Location center = block.getLocation().add(0.5, 0.1, 0.5);

        spawnManualRitual(center);
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

        if (bossGroup.contains(entity.getUniqueId())) {
            bossGroup.remove(entity.getUniqueId());
            BossBar bar = activeBars.remove(entity.getUniqueId());
            if (bar != null)
                bar.removeAll();

            Set<UUID> minions = bossMinions.remove(entity.getUniqueId());
            if (minions != null) {
                minions.forEach(mId -> {
                    Entity m = Bukkit.getEntity(mId);
                    if (m != null)
                        m.remove();
                });
            }

            Location deathLoc = entity.getLocation();
            playerBroadcast(deathLoc.getWorld(),
                    Component.text("THE CATACLYSM IS AVERTED!", NamedTextColor.GOLD, TextDecoration.BOLD));

            deathLoc.getWorld().dropItemNaturally(deathLoc, ItemManager.createPortalIgniter());
            deathLoc.getWorld().dropItemNaturally(deathLoc, ItemManager.createCustomItem(Material.DIAMOND_SWORD));

            playerBroadcast(deathLoc.getWorld(),
                    Component.text("The Portal Igniter has been dropped!", NamedTextColor.LIGHT_PURPLE,
                            TextDecoration.BOLD));

            deathLoc.getWorld().getNearbyEntities(deathLoc, 50, 50, 50).forEach(e -> {
                if (e.getType() == EntityType.ARMOR_STAND && e.customName() != null &&
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                                .serialize(e.customName()).contains("Altar")) {
                    e.remove();
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

            deathLoc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, deathLoc, 50, 2, 2, 2, 0);
            deathLoc.getWorld().playSound(deathLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        for (Map.Entry<UUID, Set<UUID>> entry : bossMinions.entrySet()) {
            if (entry.getValue().remove(entity.getUniqueId())) {
                event.getDrops().clear();
                event.setDroppedExp(0);

                if (entity.getPersistentDataContainer().has(DROP_ITEM_KEY, PersistentDataType.STRING)) {
                    String matName = entity.getPersistentDataContainer().get(DROP_ITEM_KEY, PersistentDataType.STRING);
                    if (matName != null) {
                        try {
                            Material mat = Material.valueOf(matName);
                            ItemStack drop = ItemManager.createCustomItem(mat);
                            entity.getWorld().dropItemNaturally(entity.getLocation(), drop);
                        } catch (Exception ignored) {
                        }
                    }
                }

                if (entry.getValue().isEmpty() && shieldedBosses.contains(entry.getKey())) {
                    Entity bossEntity = Bukkit.getEntity(entry.getKey());
                    if (bossEntity instanceof LivingEntity) {
                        LivingEntity boss = (LivingEntity) bossEntity;
                        shieldedBosses.remove(entry.getKey());
                        boss.setInvulnerable(false);
                        boss.setAI(true);
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
            double progress = currentMobs / 8.0;
            bar.setProgress(Math.max(0, Math.min(1, progress)));
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

        startRitual(stand);
    }

    private void startRitual(Entity anchor) {
        UUID anchorUuid = anchor.getUniqueId();
        if (activatingStands.contains(anchorUuid))
            return;
        activatingStands.add(anchorUuid);

        activeWaveMobs.put(anchorUuid, Collections.synchronizedSet(new HashSet<>()));
        BossBar ritualBar = Bukkit.createBossBar("§4§lThe Descent", BarColor.RED, BarStyle.SEGMENTED_10);
        Bukkit.getOnlinePlayers().forEach(ritualBar::addPlayer);
        activeBars.put(anchorUuid, ritualBar);

        new BukkitRunnable() {
            int waveNum = 0;
            boolean waiting = false;
            int waveTicks = 0;

            @Override
            public void run() {
                if (!anchor.isValid()) {
                    endRitual(anchorUuid, ritualBar);
                    cancel();
                    return;
                }

                Set<UUID> mobs = activeWaveMobs.get(anchorUuid);
                if (mobs == null) {
                    cancel();
                    return;
                }

                if (waiting) {
                    waveTicks++;
                    mobs.removeIf(id -> {
                        Entity ent = Bukkit.getEntity(id);
                        return ent == null || !ent.isValid() || ent.isDead();
                    });

                    if (mobs.isEmpty() && waveTicks >= 40) {
                        waiting = false;
                        waveTicks = 0;
                        anchor.getWorld().playSound(anchor.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 2f);
                    }
                    return;
                }

                if (waveNum < 4) {
                    waveNum++;
                    startWave(anchor, ritualBar, waveNum);
                    waiting = true;
                } else {
                    ritualBar.removeAll();
                    activeBars.remove(anchorUuid);
                    spawnBosses(anchor.getLocation());
                    anchor.remove();
                    activatingStands.remove(anchorUuid);
                    activeWaveMobs.remove(anchorUuid);
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

    private void startWave(Entity stand, BossBar bar, int waveId) {
        String[] titles = { "", "§e§lTrial I: The Fallen Sentries", "§9§lTrial II: The Undead Sentinels",
                "§c§lTrial III: The Avernus Guards", "§4§lTrial IV: The Final Gate" };
        Component[] msgs = { null,
                Component.text("The fallen rise in diamond plate...", NamedTextColor.YELLOW),
                Component.text("The undead manifest from the depths...", NamedTextColor.BLUE),
                Component.text("The Avernus Guards have arrived.", NamedTextColor.RED),
                Component.text("THE GATEKEEPER HAS AWAKENED!", NamedTextColor.DARK_RED, TextDecoration.BOLD)
        };

        bar.setTitle(titles[waveId]);
        playerBroadcast(stand.getWorld(), msgs[waveId]);
        spawnWave(stand, waveId);
    }

    private void spawnWave(Entity stand, int waveId) {
        Location loc = stand.getLocation();
        Set<UUID> mobs = activeWaveMobs.get(stand.getUniqueId());
        if (waveId == 1) {
            for (int i = 0; i < 8; i++) {
                LivingEntity e = spawnMob(loc, EntityType.SKELETON, "§eFallen Sentry", Material.BOW,
                        stand.getUniqueId(), mobs);
                if (e != null)
                    applyDiamondGear(e, 40);
            }
        } else if (waveId == 2) {
            for (int i = 0; i < 8; i++) {
                LivingEntity e = spawnMob(loc, EntityType.ZOMBIE, "§9Undead Sentinel", Material.IRON_SWORD,
                        stand.getUniqueId(), mobs);
                if (e != null)
                    applyDiamondGear(e, 50);
            }
        } else if (waveId == 3) {
            for (int i = 0; i < 8; i++) {
                LivingEntity e = spawnMob(loc, EntityType.WITHER_SKELETON, "§cAvernus Guard", Material.IRON_SWORD,
                        stand.getUniqueId(), mobs);
                if (e != null)
                    applyDiamondGear(e, 75);
            }
        } else if (waveId == 4) {
            for (int i = 0; i < 4; i++) {
                LivingEntity s = spawnMob(loc, EntityType.SKELETON, "§eFallen Sentry", Material.BOW,
                        stand.getUniqueId(), mobs);
                if (s != null)
                    applyDiamondGear(s, 40);
                LivingEntity z = spawnMob(loc, EntityType.ZOMBIE, "§9Undead Sentinel", Material.IRON_SWORD,
                        stand.getUniqueId(), mobs);
                if (z != null)
                    applyDiamondGear(z, 50);
                LivingEntity w = spawnMob(loc, EntityType.WITHER_SKELETON, "§cAvernus Guard", Material.IRON_SWORD,
                        stand.getUniqueId(), mobs);
                if (w != null)
                    applyDiamondGear(w, 75);
            }
            Skeleton archer = (Skeleton) spawnMob(loc, EntityType.SKELETON, "§6§lThe Gatekeeper (Archer)", Material.BOW,
                    stand.getUniqueId(), mobs);
            if (archer != null) {
                applyDiamondGear(archer, 150);
                archer.getPersistentDataContainer().set(new NamespacedKey(SkillsBoss.getInstance(), "explosive_arrow"),
                        PersistentDataType.BYTE, (byte) 1);
            }
            WitherSkeleton warrior = (WitherSkeleton) spawnMob(loc, EntityType.WITHER_SKELETON,
                    "§6§lThe Gatekeeper (Warrior)", Material.DIAMOND_SWORD, stand.getUniqueId(), mobs);
            if (warrior != null) {
                applyDiamondGear(warrior, 150);
                if (warrior.getAttribute(Attribute.MOVEMENT_SPEED) != null)
                    warrior.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.40);
                setupWarriorLogic(warrior);
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
                List<Player> targets = warrior.getWorld().getNearbyEntities(warrior.getLocation(), 15, 15, 15).stream()
                        .filter(e -> e instanceof Player && ((Player) e).getGameMode() == GameMode.SURVIVAL)
                        .map(e -> (Player) e).collect(Collectors.toList());

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
                            warrior.getWorld().playSound(warrior.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR,
                                    1f, 0.5f);
                            target.damage(25, warrior);
                            target.setVelocity(warrior.getLocation().getDirection().multiply(2.5).setY(0.8));
                            target.sendMessage(Component.text("The Gatekeeper SMASHES you away!", NamedTextColor.GOLD,
                                    TextDecoration.BOLD));
                        }
                    }.runTaskLater(SkillsBoss.getInstance(), 10);
                }
            }
        }.runTaskTimer(SkillsBoss.getInstance(), 100, 300);
    }

    private void applyDiamondGear(LivingEntity e, double health) {
        if (e.getAttribute(Attribute.MAX_HEALTH) != null) {
            e.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
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

    public static void spawnManualRitual(Location loc) {
        if (instance == null)
            return;
        Location markerLoc = loc.clone().getBlock().getLocation().add(0.5, 0.1, 0.5);
        Marker marker = (Marker) loc.getWorld().spawnEntity(markerLoc, EntityType.MARKER);
        marker.getPersistentDataContainer().set(ALTAR_KEY, PersistentDataType.BYTE, (byte) 1);

        marker.getWorld().strikeLightningEffect(markerLoc);
        marker.getWorld().playSound(markerLoc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 0.5f);
        instance.playerBroadcast(loc.getWorld(), Component.text("The Administrator has forced a Manifestation!",
                NamedTextColor.RED, TextDecoration.BOLD));
        instance.startRitual(marker);
    }

    private void spawnBosses(Location loc) {
        playerBroadcast(loc.getWorld(),
                Component.text("SUPREMUS AND HIS GUARD HAVE AWAKENED!", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        Location spawn = loc.clone().add(0, 2, 0);
        WitherSkeleton boss = (WitherSkeleton) loc.getWorld().spawnEntity(spawn, EntityType.WITHER_SKELETON);
        boss.customName(Component.text("§4§lSUPREMUS"));
        boss.setCustomNameVisible(true);

        if (boss.getAttribute(Attribute.MAX_HEALTH) != null) {
            boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1000);
            boss.setHealth(1000);
        }
        if (boss.getAttribute(Attribute.SCALE) != null)
            boss.getAttribute(Attribute.SCALE).setBaseValue(3.0);
        if (boss.getAttribute(Attribute.ATTACK_DAMAGE) != null)
            boss.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(25.0);

        boss.getPersistentDataContainer().set(FINAL_BOSS_KEY, PersistentDataType.BYTE, (byte) 2);
        boss.getPersistentDataContainer().set(BOSS_PHASE_KEY, PersistentDataType.INTEGER, 1);

        Set<UUID> minions = Collections.synchronizedSet(new HashSet<>());
        bossMinions.put(boss.getUniqueId(), minions);
        shieldedBosses.add(boss.getUniqueId());
        boss.setInvulnerable(true);
        boss.setAI(false);

        boss.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
        boss.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
        boss.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
        boss.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
        boss.getEquipment().setItemInMainHand(new ItemStack(Material.MACE));

        ritualTeam.addEntry(boss.getUniqueId().toString());
        bossGroup.add(boss.getUniqueId());

        BossBar suBar = Bukkit.createBossBar("§4§lSUPREMUS", BarColor.RED, BarStyle.SEGMENTED_20);
        Bukkit.getOnlinePlayers().forEach(suBar::addPlayer);
        activeBars.put(boss.getUniqueId(), suBar);

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
            if (sentinel.getAttribute(Attribute.ATTACK_DAMAGE) != null)
                sentinel.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(40.0);

            Material dropMaterial = null;
            if (i == 0)
                dropMaterial = Material.DIAMOND_HELMET;
            else if (i == 1)
                dropMaterial = Material.DIAMOND_CHESTPLATE;
            else if (i == 2)
                dropMaterial = Material.DIAMOND_LEGGINGS;
            else if (i == 3)
                dropMaterial = Material.DIAMOND_BOOTS;
            else if (i == 4)
                dropMaterial = Material.DIAMOND_SWORD;

            if (dropMaterial != null)
                sentinel.getPersistentDataContainer().set(DROP_ITEM_KEY, PersistentDataType.STRING,
                        dropMaterial.name());

            ritualTeam.addEntry(sentinel.getUniqueId().toString());
            minions.add(sentinel.getUniqueId());

            final int type = i;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!sentinel.isValid()) {
                        cancel();
                        return;
                    }
                    if (Math.random() < 0.12) {
                        try {
                            triggerBossAbility(sentinel, type, false);
                        } catch (Exception ignored) {
                        }
                    }
                    if (sentinel.getLocation().distance(spawn) > 20)
                        sentinel.teleport(spawn);
                }
            }.runTaskTimer(SkillsBoss.getInstance(), 20, 20);
        }

        playerBroadcast(loc.getWorld(), Component.text("Supremus is shielded by his Guard!", NamedTextColor.GOLD));

        new BukkitRunnable() {
            int ticks = 0;
            int phase = 1;

            @Override
            public void run() {
                if (!boss.isValid()) {
                    suBar.removeAll();
                    activeBars.remove(boss.getUniqueId());
                    cancel();
                    return;
                }
                suBar.setProgress(Math.max(0, Math.min(1, boss.getHealth() / 1000.0)));

                if (boss.getHealth() <= 666 && phase == 1) {
                    phase = 2;
                    enterPhase2(boss, 0);
                }
                if (boss.getHealth() <= 333 && phase == 2) {
                    phase = 3;
                    enterPhase3(boss, 0);
                }

                if (shieldedBosses.contains(boss.getUniqueId())) {
                    if (ticks % 20 == 0) {
                        boss.getWorld().spawnParticle(Particle.WITCH, boss.getLocation().add(0, 1, 0), 10, 0.5, 1, 0.5,
                                0);
                        Set<UUID> mSet = bossMinions.get(boss.getUniqueId());
                        if (mSet != null) {
                            mSet.forEach(mId -> {
                                Entity m = Bukkit.getEntity(mId);
                                if (m != null && m.isValid()) {
                                    Location s = boss.getLocation().add(0, 1.5, 0);
                                    Location e = m.getLocation().add(0, 1.5, 0);
                                    Vector dir = e.toVector().subtract(s.toVector()).normalize();
                                    for (double d = 0; d < s.distance(e); d++)
                                        boss.getWorld().spawnParticle(Particle.DUST,
                                                s.clone().add(dir.clone().multiply(d)), 1,
                                                new Particle.DustOptions(org.bukkit.Color.MAROON, 1f));
                                }
                            });
                        }
                    }
                    ticks++;
                    return;
                }

                if (ticks % 100 == 0) {
                    boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.5f);
                    for (Entity e : boss.getNearbyEntities(15, 10, 15)) {
                        if (e instanceof Player && !e.isOp()) {
                            ((LivingEntity) e).damage(phase >= 2 ? 35 : 25, boss);
                            e.setVelocity(e.getLocation().subtract(boss.getLocation()).toVector().normalize()
                                    .multiply(1.5).setY(0.5));
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(SkillsBoss.getInstance(), 20, 20);
    }

    @EventHandler
    public void onPortalPlace(BlockPlaceEvent event) {
        if (ItemManager.isPortalObsidian(event.getItemInHand())) {
            event.setCancelled(true);
            if (event.getPlayer().getGameMode() != GameMode.CREATIVE)
                event.getItemInHand().setAmount(event.getItemInHand().getAmount() - 1);
            generateNetherPortalFrame(event.getBlock().getLocation());
            event.getPlayer().sendMessage(
                    Component.text("The Portal Frame manifests before you...", NamedTextColor.DARK_PURPLE));
        }
    }

    private void generateNetherPortalFrame(Location center) {
        for (int x = -2; x <= 2; x++)
            for (int y = 0; y <= 5; y++)
                center.clone().add(x, y, 0).getBlock().setType(Material.AIR);
        for (int x = -2; x <= 2; x++) {
            center.clone().add(x, 0, 0).getBlock().setType(Material.CRYING_OBSIDIAN);
            center.clone().add(x, 5, 0).getBlock().setType(Material.CRYING_OBSIDIAN);
        }
        for (int y = 1; y <= 4; y++) {
            center.clone().add(-2, y, 0).getBlock().setType(Material.CRYING_OBSIDIAN);
            center.clone().add(2, y, 0).getBlock().setType(Material.CRYING_OBSIDIAN);
        }
    }

    @EventHandler
    public void onPortalIgniterUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND)
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
                    Component.text("THE PORTAL AWAKENS", NamedTextColor.DARK_RED, TextDecoration.BOLD));
            Bukkit.getOnlinePlayers().forEach(p -> p.showTitle(title));
        }
    }

    private Location findNearbyPortalFrame(Location center, int radius) {
        Location best = null;
        for (int x = -radius; x <= radius; x++)
            for (int y = -radius; y <= radius; y++)
                for (int z = -radius; z <= radius; z++) {
                    Location check = center.clone().add(x, y, z);
                    if (check.getBlock().getType() == Material.CRYING_OBSIDIAN)
                        if (best == null || check.getY() < best.getY())
                            best = check;
                }
        return best;
    }

    private void lightPortalFrame(Location base) {
        for (int y = 1; y <= 4; y++)
            for (int x = -1; x <= 1; x++) {
                Location check = base.clone().add(x, y, 0);
                if (check.getBlock().getType() == Material.AIR)
                    check.getBlock().setType(Material.NETHER_PORTAL);
            }
    }

    private void playerBroadcast(World world, Component msg) {
        if (msg != null)
            world.getPlayers().forEach(p -> p.sendMessage(msg));
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
        playerBroadcast(boss.getWorld(),
                Component.text("Supremus enters Phase 2!", NamedTextColor.YELLOW, TextDecoration.BOLD));
        if (boss.getAttribute(Attribute.MOVEMENT_SPEED) != null)
            boss.getAttribute(Attribute.MOVEMENT_SPEED)
                    .setBaseValue(boss.getAttribute(Attribute.MOVEMENT_SPEED).getBaseValue() * 1.2);
    }

    private void enterPhase3(LivingEntity boss, int type) {
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WITHER_SPAWN, 2f, 0.5f);
        playerBroadcast(boss.getWorld(),
                Component.text("Supremus enters FINAL PHASE!", NamedTextColor.RED, TextDecoration.BOLD));
        boss.setInvulnerable(true);
        shieldedBosses.add(boss.getUniqueId());
        Set<UUID> minions = bossMinions.get(boss.getUniqueId());
        String[] minionNames = { "§4Crimson Guard", "§5Void Guard", "§1Frost Guard", "§cWar Guard" };
        for (int i = 0; i < 4; i++) {
            Location spawnLoc = boss.getLocation().clone().add(Math.cos(i * Math.PI / 2) * 4, 0,
                    Math.sin(i * Math.PI / 2) * 4);
            WitherSkeleton minion = (WitherSkeleton) boss.getWorld().spawnEntity(spawnLoc, EntityType.WITHER_SKELETON);
            minion.customName(Component.text(minionNames[i]));
            minion.setCustomNameVisible(true);
            if (minion.getAttribute(Attribute.MAX_HEALTH) != null) {
                minion.getAttribute(Attribute.MAX_HEALTH).setBaseValue(80);
                minion.setHealth(80);
            }
            minions.add(minion.getUniqueId());
            ritualTeam.addEntry(minion.getUniqueId().toString());
        }
    }

    private void triggerBossAbility(LivingEntity boss, int type, boolean enhanced) {
        double multiplier = enhanced ? 1.5 : 1.0;
        if (type == 0) {
            boss.getWorld().spawnParticle(Particle.FLAME, boss.getLocation(), (int) (200 * multiplier), 4, 1, 4, 0.2);
            boss.getNearbyEntities(8, 6, 8).forEach(e -> {
                if (e instanceof Player && !e.isOp())
                    e.setFireTicks((int) (160 * multiplier));
            });
        } else if (type == 1) {
            boss.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, boss.getLocation(), (int) (100 * multiplier), 3, 2,
                    3, 0.05);
            boss.getNearbyEntities(10, 10, 10).forEach(e -> {
                if (e instanceof Player && !e.isOp())
                    ((Player) e).addPotionEffect(
                            new PotionEffect(PotionEffectType.WITHER, (int) (100 * multiplier), enhanced ? 2 : 1));
            });
        } else if (type == 2) {
            boss.getWorld().spawnParticle(Particle.PORTAL, boss.getLocation(), (int) (200 * multiplier), 10, 2, 10, 0);
            boss.getNearbyEntities(12, 12, 12).forEach(e -> {
                if (e instanceof Player && !e.isOp())
                    e.setVelocity(boss.getLocation().subtract(e.getLocation()).toVector().normalize()
                            .multiply(1.6 * multiplier));
            });
        } else if (type == 3) {
            boss.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, boss.getLocation(), (int) (5 * multiplier), 4,
                    0.5, 4, 0);
            boss.getNearbyEntities(10, 5, 10).forEach(e -> {
                if (e instanceof Player && !e.isOp()) {
                    e.setVelocity(new Vector(0, 1.8 * multiplier, 0));
                    ((LivingEntity) e).damage(8 * multiplier, boss);
                }
            });
        }
    }

    private LivingEntity spawnMob(Location loc, EntityType type, String name, Material hand, UUID standUuid,
            Set<UUID> mobs) {
        double rx = (new Random().nextDouble() * 3) - 1.5;
        double rz = (new Random().nextDouble() * 3) - 1.5;
        Location spawnLoc = loc.clone().add(rx, 0, rz);
        boolean found = false;
        for (int dy = 2; dy >= -2; dy--) {
            Block b = spawnLoc.clone().add(0, dy, 0).getBlock();
            if (b.getType() == Material.CRYING_OBSIDIAN || b.getType().isSolid()) {
                spawnLoc.add(0, dy + 1, 0);
                found = true;
                break;
            }
        }
        if (!found)
            spawnLoc = loc.clone().add(rx, 0.1, rz);
        LivingEntity e = (LivingEntity) loc.getWorld().spawnEntity(spawnLoc, type);
        if (e == null)
            return null;
        e.customName(Component.text(name));
        e.setCustomNameVisible(true);
        if (hand != null)
            e.getEquipment().setItemInMainHand(new ItemStack(hand));
        e.getPersistentDataContainer().set(WAVE_MOB_KEY, PersistentDataType.STRING, standUuid.toString());
        if (ritualTeam != null)
            ritualTeam.addEntry(e.getUniqueId().toString());
        mobs.add(e.getUniqueId());
        return e;
    }
}
