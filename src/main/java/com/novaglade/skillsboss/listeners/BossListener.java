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
import org.bukkit.block.TileState;
import org.bukkit.block.BlockFace;
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
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.event.entity.EntityShootBowEvent;

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
    
    // Warlord Event State
    public final List<UUID> warlordSpawners = new ArrayList<>();
    public boolean warlordCountdownActive = false;

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
        if (ItemManager.isBossSpawnItem(event.getPlayer().getInventory().getItemInMainHand())) {
            // Check for nearby spawner
            if (findNearbySpawner(event.getBlock().getLocation()) == null) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Component
                        .text("No Ritual Spawner detected nearby! Place a Spawner first.", NamedTextColor.RED));
                return;
            }
            spawnManualRitual(event.getBlock().getLocation().add(0.5, 0.1, 0.5));
        }
    }

    private Location findNearbySpawner(Location loc) {
        for (int x = -15; x <= 15; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -15; z <= 15; z++) {
                    Block b = loc.clone().add(x, y, z).getBlock();
                    if (b.getType() == Material.SPAWNER) { // Usually custom spawner or just checking for the block
                        // In practice, we'd check NBT/Metadata, but for simplicity:
                        return b.getLocation().add(0.5, 0.1, 0.5);
                    }
                }
            }
        }
        return null;
    }

    public void resetRitualSystem() {
        activeBars.values().forEach(BossBar::removeAll);
        activeBars.clear();
        activeWaveMobs.values().forEach(uids -> uids.forEach(id -> {
            Entity e = Bukkit.getEntity(id);
            if (e != null)
                e.remove();
        }));
        activeWaveMobs.clear();
        bossGroup.forEach(id -> {
            Entity e = Bukkit.getEntity(id);
            if (e != null)
                e.remove();
        });
        bossGroup.clear();
        activatingStands.forEach(id -> {
            Entity e = Bukkit.getEntity(id);
            if (e != null)
                e.remove();
        });
        activatingStands.clear();
        shieldedBosses.clear();
        bossMinions.clear();

        // Remove any Marker or ArmorStand entities with ALTAR_KEY
        Bukkit.getWorlds().forEach(world -> {
            world.getEntitiesByClass(Marker.class).stream()
                    .filter(m -> m.getPersistentDataContainer().has(ALTAR_KEY, PersistentDataType.BYTE))
                    .forEach(Entity::remove);
            world.getEntitiesByClass(ArmorStand.class).stream()
                    .filter(as -> as.getPersistentDataContainer().has(ALTAR_KEY, PersistentDataType.BYTE))
                    .forEach(Entity::remove);
        });
    }

    @EventHandler
    public void onAltarInteract(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND)
            return;
        Entity entity = event.getRightClicked();
        if (!(entity instanceof ArmorStand))
            return;
        ArmorStand stand = (ArmorStand) entity;
        if (!stand.getPersistentDataContainer().has(ALTAR_KEY, PersistentDataType.BYTE))
            return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (ItemManager.isAltarTurnerItem(player.getInventory().getItemInMainHand())) {
            stand.setRotation(stand.getLocation().getYaw() + 45, 0);
            player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 1f, 1f);
            player.sendMessage(Component.text("Altar rotated!", NamedTextColor.YELLOW));
            return;
        }

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
                    Component.text("THE CATACLYSM IS AVERTED!", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

            // Epic death effects
            deathLoc.getWorld().strikeLightningEffect(deathLoc);
            for (int i = 0; i < 360; i += 15) {
                double angle = Math.toRadians(i);
                Location pLoc = deathLoc.clone().add(Math.cos(angle) * 3, 0, Math.sin(angle) * 3);
                deathLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, pLoc, 10, 0.1, 2, 0.1, 0.1);
            }

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
            deathLoc.getWorld().playSound(deathLoc, Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 0.5f);

            // 30-second countdown then portal pull
            startBossDeathPortalSequence(deathLoc);
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
                                Component.text("The shield has shattered!", NamedTextColor.YELLOW)
                                        .decorate(TextDecoration.BOLD));
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
    public void onSpawnerDeath(EntityDeathEvent event) {
        if (warlordSpawners.contains(event.getEntity().getUniqueId())) {
            warlordSpawners.remove(event.getEntity().getUniqueId());
            event.getDrops().clear();
            event.setDroppedExp(0);
            
            Location deathLoc = event.getEntity().getLocation();
            deathLoc.getWorld().strikeLightningEffect(deathLoc);
            deathLoc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, deathLoc, 10, 2, 2, 2, 0);
            playerBroadcast(deathLoc.getWorld(), Component.text("A Demonic Spawner has been destroyed! " + warlordSpawners.size() + " remaining.", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
            
            if (warlordSpawners.isEmpty() && !warlordCountdownActive) {
                startWarlordCountdown(deathLoc.getWorld());
            }
        }
    }

    @EventHandler
    public void onSentryShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Skeleton))
            return;
        Skeleton sentry = (Skeleton) event.getEntity();
        String name = PlainTextComponentSerializer.plainText()
                .serialize(sentry.customName() != null ? sentry.customName() : Component.empty());

        if (name.contains("Fallen Sentry")) {
            event.setCancelled(true);
            Entity target = sentry.getTarget();
            if (target == null) {
                target = sentry.getWorld().getNearbyEntities(sentry.getLocation(), 15, 15, 15).stream()
                        .filter(e -> e instanceof Player && ((Player) e).getGameMode() == GameMode.SURVIVAL)
                        .findFirst().orElse(null);
            }

            if (target instanceof LivingEntity) {
                LivingEntity victim = (LivingEntity) target;
                Location start = sentry.getEyeLocation();
                Location end = victim.getEyeLocation();
                Vector dir = end.toVector().subtract(start.toVector()).normalize();
                double dist = start.distance(end);

                // Laser effect
                for (double d = 0; d < dist; d += 0.5) {
                    sentry.getWorld().spawnParticle(Particle.DUST,
                            start.clone().add(dir.clone().multiply(d)), 1,
                            new Particle.DustOptions(org.bukkit.Color.RED, 1.2f));
                }
                sentry.getWorld().playSound(start, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.5f, 2f);
                victim.damage(6, sentry); // 3 hearts
            }
        } else if (sentry.getPersistentDataContainer()
                .has(new NamespacedKey(SkillsBoss.getInstance(), "explosive_arrow"), PersistentDataType.BYTE)) {
            // Existing explosive arrow logic if needed, but wasn't implemented before.
            // We can leave it for now or implement it if the user asks.
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
            // Scale bar based on expected mob counts (approximate 8-15)
            double progress = currentMobs / 15.0;
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
            boolean finished = false;

            @Override
            public void run() {
                if (finished)
                    return;

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

                    if (mobs.isEmpty() && waveTicks >= 5) { // Reduced delay to 5s
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
                    finished = true;
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
                Component.text("THE GATEKEEPER HAS AWAKENED!", NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD)
        };

        bar.setTitle(titles[waveId]);
        playerBroadcast(stand.getWorld(), msgs[waveId]);

        // Visual Shockwave
        stand.getWorld().playSound(stand.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 1.5f);
        for (int i = 0; i < 360; i += 15) {
            double angle = Math.toRadians(i);
            Vector dir = new Vector(Math.cos(angle), 0, Math.sin(angle));
            for (double d = 1; d < 10; d += 0.5) {
                stand.getWorld().spawnParticle(Particle.CLOUD, stand.getLocation().clone().add(dir.clone().multiply(d)),
                        1, 0, 0.1, 0, 0.02);
            }
        }

        spawnWave(stand, waveId);
    }

    private void spawnWave(Entity stand, int waveId) {
        Location altarLoc = stand.getLocation();
        Location baseSpawnLoc = findNearbySpawner(altarLoc);
        if (baseSpawnLoc == null)
            baseSpawnLoc = altarLoc; // Fallback
            
        final Location spawnLoc = baseSpawnLoc;

        // Spawn shockwave animation
        spawnLoc.getWorld().strikeLightningEffect(spawnLoc);
        spawnLoc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, spawnLoc, 2, 1, 1, 1, 0);

        int mobsToSpawn = (int) spawnLoc.getWorld().getPlayers().stream()
                .filter(p -> p.getLocation().distanceSquared(spawnLoc) <= 2500)
                .count();
        if (mobsToSpawn < 1) {
            mobsToSpawn = 1;
        }

        Set<UUID> mobs = activeWaveMobs.get(stand.getUniqueId());
        String B64_CORRUPT_SKELETON = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGY5NWZkYTdkZmRjNDVhYTQ0MDY1OGM5NjQxMGQ0YjMyOTBkMzI2ZmNiNjYzNDEwNmU2MzBmZGYxNmJkNTcyIn19fQ==";
        String B64_CORRUPT_PALADIN = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDAyNjgzMzFhNzkxODA5OTQyNGUzN2IyNjYwOWY3OGE1ZTE1MmU0NjZlZDBkNTQ2ZmRlZjI2NDczZDYyYzdhZiJ9fX0=";
        String B64_DARK_KNIGHT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTk1MmM2YjAyM2I3ZjE5YjA4Yjc3NDlkZGMzNGQ5NTc4ZDAyODc3N2JiZGQ2MDAxMmUwMTgzZTY5NjUxYWQ4OSJ9fX0=";
        String B64_BLOOD_CULTIST = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzViMjM2YmFlNDdhMjJlZDU3NzA0ZDliNDhjZmE3ODdhZWQ4NzIyMTFlZWNmODZlMDBiM2MwZDA4Nzg3NWFjNSJ9fX0=";
        String B64_BLACK_KNIGHT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTBhYjg5NWM2M2U5MGZkNTY4MDM5NGVhZWRhYTFiNTRiMTM4ZWZlYmIzMTg0ZDI4OGM3NmU1M2IyM2I5MWQyZCJ9fX0=";
        
        if (waveId == 1) {
            for (int i = 0; i < mobsToSpawn; i++) {
                LivingEntity e = spawnMob(spawnLoc, EntityType.SKELETON, "§eFallen Sentry", Material.BOW,
                        stand.getUniqueId(), mobs);
                if (e != null) {
                    applyCorruptedArmor(e, 30);
                    applyBase64Skin(e, B64_CORRUPT_SKELETON);
                }
            }
        } else if (waveId == 2) {
            for (int i = 0; i < mobsToSpawn; i++) {
                LivingEntity e = spawnMob(spawnLoc, EntityType.ZOMBIE, "§9Undead Sentinel", Material.DIAMOND_SWORD,
                        stand.getUniqueId(), mobs);
                if (e != null) {
                    applyCorruptedArmor(e, 30);
                    applyBase64Skin(e, B64_CORRUPT_PALADIN);
                    if (e.getAttribute(Attribute.ATTACK_DAMAGE) != null)
                        e.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(1.0);
                }
            }
        } else if (waveId == 3) {
            for (int i = 0; i < mobsToSpawn; i++) {
                LivingEntity e = spawnMob(spawnLoc, EntityType.WITHER_SKELETON, "§cAvernus Guard",
                        Material.DIAMOND_SWORD,
                        stand.getUniqueId(), mobs);
                if (e != null) {
                    applyCorruptedArmor(e, 50);
                    applyBase64Skin(e, B64_BLACK_KNIGHT);
                    if (e.getAttribute(Attribute.ATTACK_DAMAGE) != null)
                        e.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(1.5);
                    if (e.getAttribute(Attribute.MOVEMENT_SPEED) != null)
                        e.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.30);
                }
            }
        } else if (waveId == 4) {
            for (int i = 0; i < mobsToSpawn; i++) {
                LivingEntity s = spawnMob(spawnLoc, EntityType.SKELETON, "§eFallen Sentry", Material.BOW,
                        stand.getUniqueId(), mobs);
                if (s != null) {
                    applyCorruptedArmor(s, 30);
                    applyBase64Skin(s, B64_CORRUPT_SKELETON);
                }
                LivingEntity z = spawnMob(spawnLoc, EntityType.ZOMBIE, "§9Undead Sentinel", Material.DIAMOND_SWORD,
                        stand.getUniqueId(), mobs);
                if (z != null) {
                    applyCorruptedArmor(z, 30);
                    applyBase64Skin(z, B64_CORRUPT_PALADIN);
                    if (z.getAttribute(Attribute.ATTACK_DAMAGE) != null)
                        z.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(1.0);
                }
                LivingEntity w = spawnMob(spawnLoc, EntityType.WITHER_SKELETON, "§cAvernus Guard",
                        Material.DIAMOND_SWORD,
                        stand.getUniqueId(), mobs);
                if (w != null) {
                    applyCorruptedArmor(w, 50);
                    applyBase64Skin(w, B64_BLACK_KNIGHT);
                    if (w.getAttribute(Attribute.ATTACK_DAMAGE) != null)
                        w.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(1.5);
                }
            }
            Skeleton archer = (Skeleton) spawnMob(spawnLoc, EntityType.SKELETON, "§6§lThe Gatekeeper (Archer)",
                    Material.BOW,
                    stand.getUniqueId(), mobs);
            if (archer != null) {
                applyCorruptedArmor(archer, 200);
                applyBase64Skin(archer, B64_BLOOD_CULTIST);
                archer.getPersistentDataContainer().set(new NamespacedKey(SkillsBoss.getInstance(), "explosive_arrow"),
                        PersistentDataType.BYTE, (byte) 1);
                ItemStack bow = archer.getEquipment().getItemInMainHand();
                bow.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.POWER, 10);
                bow.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PUNCH, 5);
            }
            WitherSkeleton warrior = (WitherSkeleton) spawnMob(spawnLoc, EntityType.WITHER_SKELETON,
                    "§6§lThe Gatekeeper (Warrior)", Material.CHAIN, stand.getUniqueId(), mobs);
            if (warrior != null) {
                applyCorruptedArmor(warrior, 150);
                applyBase64Skin(warrior, B64_DARK_KNIGHT);
                if (warrior.getAttribute(Attribute.MOVEMENT_SPEED) != null)
                    warrior.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.40);
                if (warrior.getAttribute(Attribute.ATTACK_DAMAGE) != null)
                    warrior.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(7.0);
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
                            target.damage(12, warrior);
                            target.setVelocity(warrior.getLocation().getDirection().multiply(2.5).setY(0.8));
                            target.sendMessage(Component.text("The Gatekeeper SMASHES you away!", NamedTextColor.GOLD)
                                    .decorate(TextDecoration.BOLD));
                        }
                    }.runTaskLater(SkillsBoss.getInstance(), 10);
                }
            }
        }.runTaskTimer(SkillsBoss.getInstance(), 100, 300);
    }

    private void applyChainmailGear(LivingEntity e, double health) {
        if (e.getAttribute(Attribute.MAX_HEALTH) != null) {
            e.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
            e.setHealth(health);
        }
        e.getEquipment().setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
        e.getEquipment().setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
        e.getEquipment().setLeggings(new ItemStack(Material.CHAINMAIL_LEGGINGS));
        e.getEquipment().setBoots(new ItemStack(Material.CHAINMAIL_BOOTS));

        e.getEquipment().setHelmetDropChance(0);
        e.getEquipment().setChestplateDropChance(0);
        e.getEquipment().setLeggingsDropChance(0);
        e.getEquipment().setBootsDropChance(0);
        e.getEquipment().setItemInMainHandDropChance(0);
    }

    private void applyBase64Skin(LivingEntity e, String base64) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
        org.bukkit.profile.PlayerProfile profile = org.bukkit.Bukkit.createPlayerProfile(java.util.UUID.randomUUID());
        try {
            String decoded = new String(java.util.Base64.getDecoder().decode(base64));
            String url = decoded.substring(decoded.indexOf("\"url\":\"") + 7);
            url = url.substring(0, url.indexOf("\""));
            org.bukkit.profile.PlayerTextures textures = profile.getTextures();
            textures.setSkin(new java.net.URL(url));
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
            head.setItemMeta(meta);
            e.getEquipment().setHelmet(head);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void applyCorruptedArmor(LivingEntity e, double health) {
        if (e.getAttribute(Attribute.MAX_HEALTH) != null) {
            e.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
            e.setHealth(health);
        }
        
        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
        ItemStack legs = new ItemStack(Material.LEATHER_LEGGINGS);
        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        
        org.bukkit.inventory.meta.LeatherArmorMeta armMeta;
        
        for (ItemStack item : new ItemStack[]{chest, legs, boots}) {
            armMeta = (org.bukkit.inventory.meta.LeatherArmorMeta) item.getItemMeta();
            armMeta.setColor(org.bukkit.Color.fromRGB(30, 30, 30));
            org.bukkit.inventory.meta.ArmorMeta trimMeta = (org.bukkit.inventory.meta.ArmorMeta) armMeta;
            trimMeta.setTrim(new org.bukkit.inventory.meta.trim.ArmorTrim(org.bukkit.inventory.meta.trim.TrimMaterial.REDSTONE, org.bukkit.inventory.meta.trim.TrimPattern.SILENCE));
            item.setItemMeta(armMeta);
        }
        
        e.getEquipment().setChestplate(chest);
        e.getEquipment().setLeggings(legs);
        e.getEquipment().setBoots(boots);

        e.getEquipment().setHelmetDropChance(0);
        e.getEquipment().setChestplateDropChance(0);
        e.getEquipment().setLeggingsDropChance(0);
        e.getEquipment().setBootsDropChance(0);
        e.getEquipment().setItemInMainHandDropChance(0);

        if (e instanceof Skeleton) {
            ItemStack bow = e.getEquipment().getItemInMainHand();
            if (bow != null && bow.getType() == Material.BOW) {
                bow.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.POWER, 3);
                bow.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.FLAME, 1);
            }
        }
    }

    public static void spawnManualRitual(Location loc) {
        if (instance == null)
            return;
        Location standLoc = loc.clone().getBlock().getLocation().add(0.5, 0.1, 0.5);
        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(standLoc, EntityType.ARMOR_STAND);
        stand.setBasePlate(false);
        stand.setArms(true);
        stand.customName(Component.text("§4§lThe Avernus Altar"));
        stand.setCustomNameVisible(true);
        stand.setInvulnerable(true);
        stand.getPersistentDataContainer().set(ALTAR_KEY, PersistentDataType.BYTE, (byte) 1);

        stand.getWorld().strikeLightningEffect(standLoc);
        stand.getWorld().playSound(standLoc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 0.5f);
        instance.playerBroadcast(loc.getWorld(), Component.text("A Ritual Altar has been anchored!",
                NamedTextColor.RED).decorate(TextDecoration.BOLD));
    }

    private Location findSafeSpawnLocation(Location base, double offsetX, double offsetZ) {
        Location loc = base.clone().add(offsetX, 0, offsetZ);
        // Search upward from Y-2 to Y+5 for a safe location (solid below, air at feet
        // and head)
        for (int dy = -2; dy <= 5; dy++) {
            Block feet = loc.clone().add(0, dy, 0).getBlock();
            Block head = loc.clone().add(0, dy + 1, 0).getBlock();
            Block ground = loc.clone().add(0, dy - 1, 0).getBlock();
            if (ground.getType().isSolid() && !feet.getType().isSolid() && !head.getType().isSolid()) {
                return loc.clone().add(0, dy, 0);
            }
        }
        // Fallback: just spawn above the base
        return base.clone().add(offsetX, 1, offsetZ);
    }

    public static void spawnSupremusDirect(Location loc) {
        if (instance == null) return;
        instance.spawnBosses(loc);
    }

    private void spawnBosses(Location loc) {
        Location spawnPoint = findNearbySpawner(loc);
        if (spawnPoint == null)
            spawnPoint = loc;

        playerBroadcast(loc.getWorld(),
                Component.text("SUPREMUS HAS AWAKENED!", NamedTextColor.DARK_RED)
                        .decorate(TextDecoration.BOLD));

        // Cinematic Spawn - bigger and better
        loc.getWorld().strikeLightningEffect(loc);
        loc.getWorld().strikeLightningEffect(loc.clone().add(3, 0, 0));
        loc.getWorld().strikeLightningEffect(loc.clone().add(-3, 0, 0));
        loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_SPAWN, 2f, 0.5f);
        loc.getWorld().playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, 0.3f);
        for (int i = 0; i < 360; i += 5) {
            double angle = Math.toRadians(i);
            for (double r = 2; r <= 8; r += 2) {
                Location pLoc = loc.clone().add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
                loc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, pLoc, 5, 0.1, 2, 0.1, 0.05);
            }
        }
        loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 5, 1, 1, 1, 0);

        // Supremus spawns ALONE - no guards yet
        Location spawn = findSafeSpawnLocation(spawnPoint, 0, 0).add(0, 1, 0);
        Bukkit.getLogger().info("[SkillsBoss] Spawning Supremus at " + spawn.toString());
        WitherSkeleton boss = (WitherSkeleton) loc.getWorld().spawnEntity(spawn, EntityType.WITHER_SKELETON);
        boss.customName(Component.text("§4§lSUPREMUS"));
        boss.setCustomNameVisible(true);
        boss.setRemoveWhenFarAway(false);

        try {
            // Apply attributes with modern 1.21 names
            if (boss.getAttribute(Attribute.MAX_HEALTH) != null) {
                boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1500);
                boss.setHealth(1500);
            }

            org.bukkit.attribute.AttributeInstance scaleAttr = boss.getAttribute(Attribute.SCALE);
            if (scaleAttr != null) {
                scaleAttr.setBaseValue(4.0);
                Bukkit.getLogger().info("[SkillsBoss] Applied SCALE: 4.0");
            }

            if (boss.getAttribute(Attribute.ATTACK_DAMAGE) != null)
                boss.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(200.0);
            if (boss.getAttribute(Attribute.MOVEMENT_SPEED) != null)
                boss.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.38);
        } catch (Exception e) {
            Bukkit.getLogger().severe("[SkillsBoss] Error setting boss attributes: " + e.getMessage());
        }

        // Force full health 1 tick later to prevent spawn damage reducing it
        new BukkitRunnable() {
            @Override
            public void run() {
                if (boss.isValid() && boss.getAttribute(Attribute.MAX_HEALTH) != null) {
                    boss.setHealth(boss.getAttribute(Attribute.MAX_HEALTH).getValue());
                }
            }
        }.runTaskLater(SkillsBoss.getInstance(), 1);

        boss.getPersistentDataContainer().set(FINAL_BOSS_KEY, PersistentDataType.BYTE, (byte) 2);
        boss.getPersistentDataContainer().set(BOSS_PHASE_KEY, PersistentDataType.INTEGER, 1);

        // Equipment
        boss.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
        boss.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
        boss.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
        boss.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
        boss.getEquipment().setItemInMainHand(new ItemStack(Material.MACE));

        boss.getEquipment().setItemInMainHandDropChance(0f);
        boss.getEquipment().setHelmetDropChance(0f);
        boss.getEquipment().setChestplateDropChance(0f);
        boss.getEquipment().setLeggingsDropChance(0f);
        boss.getEquipment().setBootsDropChance(0f);

        ritualTeam.addEntry(boss.getUniqueId().toString());
        bossGroup.add(boss.getUniqueId());

        Set<UUID> minions = Collections.synchronizedSet(new HashSet<>());
        bossMinions.put(boss.getUniqueId(), minions);

        BossBar suBar = Bukkit.createBossBar("§4§lSUPREMUS", BarColor.RED, BarStyle.SEGMENTED_20);
        Bukkit.getOnlinePlayers().forEach(suBar::addPlayer);
        activeBars.put(boss.getUniqueId(), suBar);

        final Location bossSpawnPoint = spawnPoint.clone();

        setupSupremusSpinAttack(boss);

        // Boss behavior loop
        new BukkitRunnable() {
            int ticks = 0;
            double[] thresholds = {1200, 900, 600, 300, 150};
            boolean[] spawnedSentinels = new boolean[5];

            @Override
            public void run() {
                if (!boss.isValid()) {
                    suBar.removeAll();
                    activeBars.remove(boss.getUniqueId());
                    cancel();
                    return;
                }
                suBar.setProgress(Math.max(0, Math.min(1, boss.getHealth() / 1500.0)));

                // At 5 thresholds: shield + freeze + spawn one sentinel
                for (int i = 0; i < 5; i++) {
                    if (!spawnedSentinels[i] && boss.getHealth() <= thresholds[i]) {
                        spawnedSentinels[i] = true;
                        
                        Bukkit.getLogger().info("[SkillsBoss] Supremus below threshold " + thresholds[i] + "!");
                        shieldedBosses.add(boss.getUniqueId());
                        boss.setInvulnerable(true);
                        boss.setAI(false);

                        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WITHER_SPAWN, 2f, 0.3f);
                        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, 0.5f);
                        boss.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, boss.getLocation(), 10, 2, 2, 2, 0);
                        boss.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, boss.getLocation(), 200, 3, 3, 3, 0.1);

                        Title guardTitle = Title.title(
                                Component.text("A SENTINEL AWAKENS!", NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD),
                                Component.text("Destroy it to break the shield!", NamedTextColor.GOLD));
                        Bukkit.getOnlinePlayers().forEach(p -> p.showTitle(guardTitle));
                        playerBroadcast(boss.getWorld(),
                                Component.text("SUPREMUS IS SHIELDED! DESTROY HIS SENTINEL!", NamedTextColor.GOLD)
                                        .decorate(TextDecoration.BOLD));

                        spawnSentinel(boss, i, minions);
                        break;
                    }
                }

                // Shield visual effects
                if (shieldedBosses.contains(boss.getUniqueId())) {
                    if (ticks % 5 == 0) {
                        // Rotating shield particles
                        double shieldAngle = Math.toRadians(ticks * 9);
                        for (int i = 0; i < 4; i++) {
                            double a = shieldAngle + (i * Math.PI / 2);
                            Location sp = boss.getLocation().add(Math.cos(a) * 2, 1.5 + Math.sin(ticks * 0.1),
                                    Math.sin(a) * 2);
                            boss.getWorld().spawnParticle(Particle.WITCH, sp, 3, 0.1, 0.1, 0.1, 0);
                            boss.getWorld().spawnParticle(Particle.END_ROD, sp, 1, 0, 0, 0, 0.01);
                        }
                    }
                    if (ticks % 20 == 0) {
                        // Energy tethers to sentinels
                        Set<UUID> mSet = bossMinions.get(boss.getUniqueId());
                        if (mSet != null) {
                            mSet.forEach(mId -> {
                                Entity m = Bukkit.getEntity(mId);
                                if (m != null && m.isValid()) {
                                    Location s = boss.getLocation().add(0, 1.5, 0);
                                    Location e = m.getLocation().add(0, 1.5, 0);
                                    Vector dir = e.toVector().subtract(s.toVector()).normalize();
                                    double dist = s.distance(e);
                                    for (double d = 0; d < dist; d += 0.5) {
                                        boss.getWorld().spawnParticle(Particle.DUST,
                                                s.clone().add(dir.clone().multiply(d)), 1,
                                                new Particle.DustOptions(org.bukkit.Color.MAROON, 1.2f));
                                        boss.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                                                s.clone().add(dir.clone().multiply(d)), 1, 0.05, 0.05, 0.05, 0);
                                    }
                                }
                            });
                        }
                    }
                    ticks++;
                    return;
                }

                // Ambient boss particles when fighting
                if (ticks % 5 == 0) {
                    boss.getWorld().spawnParticle(Particle.SOUL, boss.getEyeLocation(), 5, 0.5, 0.5, 0.5, 0.05);
                    boss.getWorld().spawnParticle(Particle.DRAGON_BREATH, boss.getLocation(), 3, 1, 0.5, 1, 0.02);
                    boss.getWorld().spawnParticle(Particle.FLAME, boss.getLocation().add(0, 0.5, 0), 3, 0.5, 0.5, 0.5,
                            0.01);
                }

                // Ground slam attack every 5 seconds
                if (ticks % 100 == 0) {
                    boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.5f);
                    boss.getWorld().spawnParticle(Particle.SONIC_BOOM, boss.getLocation(), 3, 2, 2, 2, 0);
                    // Expanding shockwave ring
                    for (int ring = 0; ring < 360; ring += 10) {
                        double a = Math.toRadians(ring);
                        Location ringLoc = boss.getLocation().add(Math.cos(a) * 5, 0.5, Math.sin(a) * 5);
                        boss.getWorld().spawnParticle(Particle.EXPLOSION, ringLoc, 1, 0, 0, 0, 0);
                    }
                    for (Entity e : boss.getNearbyEntities(15, 10, 15)) {
                        if (e instanceof Player && !e.isOp()) {
                            ((LivingEntity) e).damage(80, boss);
                            e.setVelocity(e.getLocation().subtract(boss.getLocation()).toVector().normalize()
                                    .multiply(2.5).setY(0.8));
                        }
                    }
                }

                // Warp Hammer Attack: Teleport above player and smash down
                if (ticks > 0 && ticks % 200 == 150) {
                    List<Player> nearby = boss.getWorld().getNearbyEntities(boss.getLocation(), 25, 15, 25).stream()
                            .filter(e -> e instanceof Player && ((Player) e).getGameMode() == GameMode.SURVIVAL)
                            .map(e -> (Player) e).collect(Collectors.toList());
                    if (!nearby.isEmpty()) {
                        Player target = nearby.get(new Random().nextInt(nearby.size()));
                        Location above = target.getLocation().add(0, 4, 0);

                        // Warp Animation
                        boss.getWorld().spawnParticle(Particle.REVERSE_PORTAL, boss.getLocation(), 80, 1, 2, 1, 0.2);
                        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.5f);

                        boss.teleport(above);

                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (!boss.isValid() || !target.isOnline())
                                    return;
                                boss.setVelocity(new Vector(0, -2.5, 0)); // Smash down
                                boss.getWorld().spawnParticle(Particle.SONIC_BOOM, boss.getLocation(), 10, 0.5, 0.5,
                                        0.5, 0);
                                boss.getWorld().playSound(boss.getLocation(), Sound.ITEM_MACE_SMASH_GROUND, 2f,
                                        0.5f);

                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        if (!boss.isValid())
                                            return;
                                        boss.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, boss.getLocation(), 5,
                                                2, 0.5, 2, 0);
                                        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_GENERIC_EXPLODE,
                                                1.5f, 0.7f);
                                        target.damage(80, boss);
                                        target.setVelocity(new Vector(0, 0.8, 0));
                                    }
                                }.runTaskLater(SkillsBoss.getInstance(), 5);
                            }
                        }.runTaskLater(SkillsBoss.getInstance(), 10);
                    }
                }
                ticks++;

            }
        }.runTaskTimer(SkillsBoss.getInstance(), 20, 5);
    }

    @EventHandler
    public void onPortalPlace(BlockPlaceEvent event) {
        if (ItemManager.isProgression1Item(event.getItemInHand())) {
            // Don't cancel - let the beacon place normally
            if (event.getPlayer().getGameMode() != GameMode.CREATIVE)
                event.getItemInHand().setAmount(event.getItemInHand().getAmount() - 1);

            // Mark the beacon with a persistent data tag
            Block placedBlock = event.getBlock();
            if (placedBlock.getState() instanceof TileState) {
                TileState state = (TileState) placedBlock.getState();
                state.getPersistentDataContainer().set(
                        new NamespacedKey(SkillsBoss.getInstance(), "progression_1_beacon"),
                        PersistentDataType.BYTE, (byte) 1);
                state.update();
            }

            event.getPlayer().sendMessage(
                    Component.text("Progression I Catalyst placed! Use ", NamedTextColor.GOLD)
                            .append(Component.text("/admin progression 1", NamedTextColor.YELLOW)
                                    .decorate(TextDecoration.BOLD))
                            .append(Component.text(" to activate it.", NamedTextColor.GOLD)));
        } else if (ItemManager.isPortalCoreBlock(event.getItemInHand())) {
            event.getPlayer().sendMessage(
                    Component.text("Portal Ignition Core placed. Build your shape with Barrier blocks around it!",
                            NamedTextColor.GOLD));
        } else if (event.getItemInHand().getType() == Material.BARRIER) {
            // Already handled by being a barrier, but we can add a message
            if (event.getPlayer().getInventory().getItemInMainHand().getType() == Material.BARRIER) {
                // Just let it place
            }
        }
    }

    private void igniteCustomPortal(Block coreBlock) {
        Set<Block> portalBlocks = new HashSet<>();
        Queue<Block> toCheck = new LinkedList<>();

        // Start by checking all neighbors of the core for barriers
        for (BlockFace face : BlockFace.values()) {
            if (face.isCartesian()) {
                Block neighbor = coreBlock.getRelative(face);
                if (neighbor.getType() == Material.BARRIER) {
                    toCheck.add(neighbor);
                }
            }
        }

        while (!toCheck.isEmpty()) {
            Block current = toCheck.poll();
            if (current.getType() == Material.BARRIER && !portalBlocks.contains(current)) {
                portalBlocks.add(current);
                // Check all 6 neighbors
                for (BlockFace face : BlockFace.values()) {
                    if (face.isCartesian()) {
                        toCheck.add(current.getRelative(face));
                    }
                }
            }
        }

        if (portalBlocks.isEmpty())
            return;

        Location center = coreBlock.getLocation();
        for (Block b : portalBlocks) {
            b.setType(Material.NETHER_PORTAL);
        }

        SkillsBoss.setProgressionLevel(2);
        Title title = Title.title(
                Component.text("PROGRESSION II", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                Component.text("THE PORTAL AWAKENS", NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD));
        Bukkit.getOnlinePlayers().forEach(p -> p.showTitle(title));

        // Global Portal Pull Animation
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks > 200) {
                    org.bukkit.World nether = Bukkit.getWorlds().stream()
                            .filter(w -> w.getEnvironment() == org.bukkit.World.Environment.NETHER)
                            .findFirst().orElse(null);
                    if (nether != null) {
                        startWarlordEvent(nether);
                    }
                    cancel();
                    return;
                }

                // Visual effects at the portal
                for (Block b : portalBlocks) {
                    if (ticks % 5 == 0) {
                        b.getWorld().spawnParticle(Particle.PORTAL, b.getLocation().add(0.5, 0.5, 0.5), 3, 0.3, 0.3,
                                0.3,
                                0.1);
                    }
                }

                // Global pull
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.getWorld().equals(center.getWorld()))
                        continue;
                    double dist = p.getLocation().distance(center);
                    if (dist > 500)
                        dist = 500; // Cap distance for force calculation

                    Vector pull = center.toVector().subtract(p.getLocation().toVector()).normalize();

                    // Stronger pull that scales with distance to ensure everyone arrives
                    double force = 0.1 + (dist * 0.005);
                    if (force > 0.8)
                        force = 0.8;

                    if (dist < 1.5) {
                        // Teleport to Nether
                        org.bukkit.World nether = Bukkit.getWorlds().stream()
                                .filter(w -> w.getEnvironment() == org.bukkit.World.Environment.NETHER)
                                .findFirst().orElse(null);
                        if (nether != null) {
                            p.teleport(findRandomNetherLocation(nether));
                            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
                            p.sendMessage(
                                    Component.text("You have been consumed by the Avernus!", NamedTextColor.DARK_RED));
                        }
                        continue;
                    }

                    p.setVelocity(p.getVelocity().add(pull.multiply(force)));

                    if (ticks % 20 == 0) {
                        p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, 0.3f, 0.5f);
                        p.sendMessage(
                                Component.text("The dimensional rift pulls at your soul...", NamedTextColor.DARK_RED)
                                        .decorate(TextDecoration.ITALIC));
                    }
                }
                ticks++;
            }
        }.runTaskTimer(SkillsBoss.getInstance(), 0, 1);
    }

    private void playerBroadcast(World world, Component msg) {
        if (msg != null)
            world.getPlayers().forEach(p -> p.sendMessage(msg));
    }

    private Location findRandomNetherLocation(org.bukkit.World nether) {
        Random rand = new Random();
        Location spawnBase = nether.getSpawnLocation();
        int attempts = 0;
        while (attempts < 50) {
            int rx = spawnBase.getBlockX() + rand.nextInt(401) - 200;
            int rz = spawnBase.getBlockZ() + rand.nextInt(401) - 200;
            // Search for safe Y from 32 to 100
            for (int y = 32; y < 100; y++) {
                Block ground = nether.getBlockAt(rx, y - 1, rz);
                Block feet = nether.getBlockAt(rx, y, rz);
                Block head = nether.getBlockAt(rx, y + 1, rz);
                if (ground.getType().isSolid() && !ground.getType().toString().contains("LAVA")
                        && !feet.getType().isSolid() && !feet.isLiquid()
                        && !head.getType().isSolid() && !head.isLiquid()) {
                    return new Location(nether, rx + 0.5, y, rz + 0.5);
                }
            }
            attempts++;
        }
        // Fallback to nether spawn if no safe block found
        return spawnBase;
    }

    private void triggerBossAbility(LivingEntity boss, int type, boolean enhanced) {
        double multiplier = enhanced ? 1.5 : 1.0;
        if (type == 0) {
            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.5f);
            boss.getWorld().spawnParticle(Particle.FLAME, boss.getLocation(), (int) (200 * multiplier), 4, 1, 4, 0.2);
            boss.getWorld().spawnParticle(Particle.LAVA, boss.getLocation(), (int) (50 * multiplier), 3, 1, 3, 0.1);
            boss.getNearbyEntities(8, 6, 8).forEach(e -> {
                if (e instanceof Player && !e.isOp())
                    e.setFireTicks((int) (160 * multiplier));
            });
        } else if (type == 1) {
            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1f, 0.5f);
            boss.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, boss.getLocation(), (int) (100 * multiplier), 3, 2,
                    3, 0.05);
            boss.getWorld().spawnParticle(Particle.SOUL, boss.getLocation(), (int) (40 * multiplier), 2, 2, 2, 0.02);
            boss.getNearbyEntities(10, 10, 10).forEach(e -> {
                if (e instanceof Player && !e.isOp())
                    ((Player) e).addPotionEffect(
                            new PotionEffect(PotionEffectType.WITHER, (int) (100 * multiplier), enhanced ? 2 : 1));
            });
        } else if (type == 2) {
            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.2f);
            boss.getWorld().spawnParticle(Particle.REVERSE_PORTAL, boss.getLocation(), (int) (200 * multiplier), 10, 2,
                    10, 0);
            boss.getNearbyEntities(12, 12, 12).forEach(e -> {
                if (e instanceof Player && !e.isOp())
                    e.setVelocity(boss.getLocation().subtract(e.getLocation()).toVector().normalize()
                            .multiply(1.6 * multiplier));
            });
        } else if (type == 3) {
            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.5f);
            boss.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, boss.getLocation(), (int) (5 * multiplier), 4,
                    0.5, 4, 0);
            boss.getWorld().spawnParticle(Particle.FLASH, boss.getLocation(), (int) (3 * multiplier), 2, 1, 2, 0);
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
        double rx = (new Random().nextDouble() * 6) - 3;
        double rz = (new Random().nextDouble() * 6) - 3;
        Location spawnLoc = findSafeSpawnLocation(loc, rx, rz);
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

        // Spawn particle effect
        e.getWorld().spawnParticle(Particle.SMOKE, spawnLoc, 15, 0.3, 0.5, 0.3, 0.05);
        e.getWorld().spawnParticle(Particle.FLAME, spawnLoc, 5, 0.2, 0.2, 0.2, 0.02);
        return e;
    }

    public static void spawnWarlordBoss(Location loc) {
        if (instance == null) return;
        loc.getWorld().strikeLightningEffect(loc);
        loc.getWorld().spawnParticle(Particle.FLAME, loc, 100, 1, 1, 1, 0.1);
        loc.getWorld().playSound(loc, Sound.ENTITY_PIGLIN_BRUTE_ANGRY, 2f, 0.5f);

        PiglinBrute boss = (PiglinBrute) loc.getWorld().spawnEntity(loc, EntityType.PIGLIN_BRUTE);
        boss.customName(Component.text("§c§lMassive Warlord"));
        boss.setCustomNameVisible(true);
        boss.setRemoveWhenFarAway(false);
        boss.setImmuneToZombification(true);

        if (boss.getAttribute(Attribute.MAX_HEALTH) != null) {
            boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1500.0);
            boss.setHealth(1500.0);
        }
        
        org.bukkit.attribute.AttributeInstance scaleAttr = boss.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(3.0);
        }

        if (boss.getAttribute(Attribute.ATTACK_DAMAGE) != null) boss.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(120.0);
        if (boss.getAttribute(Attribute.MOVEMENT_SPEED) != null) boss.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.40);

        boss.getEquipment().setHelmet(null);
        boss.getEquipment().setChestplate(null);
        boss.getEquipment().setLeggings(null);
        boss.getEquipment().setBoots(null);
        ItemStack ax = new ItemStack(Material.NETHERITE_AXE);
        ax.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.FIRE_ASPECT, 3);
        ax.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.KNOCKBACK, 2);
        boss.getEquipment().setItemInMainHand(ax);

        boss.getEquipment().setItemInMainHandDropChance(0f);
        boss.getEquipment().setHelmetDropChance(0f);
        boss.getEquipment().setChestplateDropChance(0f);
        boss.getEquipment().setLeggingsDropChance(0f);
        boss.getEquipment().setBootsDropChance(0f);

        BossBar wBar = Bukkit.createBossBar("§c§lMassive Warlord", BarColor.RED, BarStyle.SEGMENTED_12);
        Bukkit.getOnlinePlayers().forEach(wBar::addPlayer);
        instance.activeBars.put(boss.getUniqueId(), wBar);
        instance.bossGroup.add(boss.getUniqueId());

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!boss.isValid()) {
                    wBar.removeAll();
                    instance.activeBars.remove(boss.getUniqueId());
                    cancel();
                    return;
                }
                wBar.setProgress(Math.max(0, Math.min(1, boss.getHealth() / 1500.0)));

                if (ticks % 5 == 0) {
                    boss.getWorld().spawnParticle(Particle.FLAME, boss.getLocation().add(0, 1, 0), 5, 0.5, 0.5, 0.5, 0.05);
                    boss.getWorld().spawnParticle(Particle.LAVA, boss.getLocation().add(0, 1, 0), 2, 0.3, 0.3, 0.3, 0.02);
                }

                // Fire attack
                if (ticks > 0 && ticks % 100 == 0) {
                    boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_GHAST_SHOOT, 1.5f, 0.5f);
                    for (int i = 0; i < 360; i += 15) {
                        double a = Math.toRadians(i);
                        Location pLoc = boss.getLocation().add(Math.cos(a) * 6, 0.5, Math.sin(a) * 6);
                        boss.getWorld().spawnParticle(Particle.FLAME, pLoc, 5, 0.2, 0.2, 0.2, 0.1);
                        boss.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, pLoc, 2, 0.1, 0.1, 0.1, 0.05);
                    }
                    boss.getWorld().getNearbyEntities(boss.getLocation(), 10, 10, 10).forEach(e -> {
                        if (e instanceof Player && !e.isOp()) {
                            e.setFireTicks(160);
                            ((LivingEntity) e).damage(40, boss);
                        }
                    });
                }

                // Massive pull and cleave attack specifically for large groups
                if (ticks > 0 && ticks % 160 == 80) {
                    boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 2f, 0.5f);
                    boss.getWorld().spawnParticle(Particle.REVERSE_PORTAL, boss.getLocation(), 200, 8, 2, 8, 0.1);
                    boss.getWorld().getNearbyEntities(boss.getLocation(), 20, 15, 20).forEach(e -> {
                        if (e instanceof Player && ((Player) e).getGameMode() == GameMode.SURVIVAL) {
                            Vector pull = boss.getLocation().toVector().subtract(e.getLocation().toVector()).normalize().multiply(1.5).setY(0.3);
                            e.setVelocity(pull);
                            ((Player) e).sendMessage(Component.text("The Warlord pulls you in!", NamedTextColor.RED).decorate(TextDecoration.BOLD));
                        }
                    });

                    // Follow up with a huge cleave
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (!boss.isValid()) return;
                            boss.swingMainHand();
                            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 2f, 0.5f);
                            boss.getWorld().spawnParticle(Particle.SWEEP_ATTACK, boss.getLocation().add(0, 1, 0), 20, 3, 0.5, 3, 0);
                            boss.getWorld().spawnParticle(Particle.SONIC_BOOM, boss.getLocation(), 3, 0, 0, 0, 0);
                            
                            boss.getWorld().getNearbyEntities(boss.getLocation(), 8, 5, 8).forEach(e -> {
                                if (e instanceof Player && !e.isOp()) {
                                    ((LivingEntity) e).damage(60, boss);
                                    e.setVelocity(e.getLocation().toVector().subtract(boss.getLocation().toVector()).normalize().multiply(2.0).setY(0.5));
                                }
                            });
                        }
                    }.runTaskLater(SkillsBoss.getInstance(), 15);
                }

                ticks++;
            }
        }.runTaskTimer(SkillsBoss.getInstance(), 20, 5);
    }
    
    private void setupSupremusSpinAttack(WitherSkeleton boss) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!boss.isValid()) {
                    cancel();
                    return;
                }
                if (shieldedBosses.contains(boss.getUniqueId())) return;
                
                // Switch target randomly
                List<Player> targets = boss.getWorld().getNearbyEntities(boss.getLocation(), 20, 20, 20).stream()
                        .filter(e -> e instanceof Player && ((Player) e).getGameMode() == GameMode.SURVIVAL)
                        .map(e -> (Player) e).collect(Collectors.toList());
                if (!targets.isEmpty()) {
                    boss.setTarget(targets.get(new Random().nextInt(targets.size())));
                }

                // Periodic spin attack
                if (Math.random() < 0.25) {
                    boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 2f, 0.5f);
                    boss.getWorld().spawnParticle(Particle.SWEEP_ATTACK, boss.getLocation().add(0, 1, 0), 30, 4, 1, 4, 0);
                    
                    // Sword landing in a circle
                    for (int i = 0; i < 8; i++) {
                        double angle = Math.toRadians(i * 45);
                        Location dropLoc = boss.getLocation().clone().add(Math.cos(angle) * 5, 5, Math.sin(angle) * 5);
                        ArmorStand swordStand = (ArmorStand) boss.getWorld().spawnEntity(dropLoc, EntityType.ARMOR_STAND);
                        swordStand.setVisible(false);
                        swordStand.setInvulnerable(true);
                        swordStand.setGravity(true);
                        swordStand.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
                        swordStand.setRightArmPose(new EulerAngle(Math.toRadians(180), 0, 0));
                        
                        new BukkitRunnable() {
                            int t = 0;
                            @Override
                            public void run() {
                                if (!swordStand.isValid()) {
                                    cancel();
                                    return;
                                }
                                if (t++ > 20 || swordStand.isOnGround()) {
                                    swordStand.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, swordStand.getLocation(), 1, 0.5, 0.5, 0.5, 0);
                                    swordStand.getWorld().playSound(swordStand.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.5f);
                                    swordStand.remove();
                                    cancel();
                                }
                            }
                        }.runTaskTimer(SkillsBoss.getInstance(), 1, 1);
                    }

                    boss.getNearbyEntities(6, 6, 6).forEach(e -> {
                        if (e instanceof Player && !e.isOp()) {
                            ((LivingEntity) e).damage(40, boss);
                            e.setVelocity(e.getLocation().toVector().subtract(boss.getLocation().toVector()).normalize().multiply(1.5).setY(0.5));
                        }
                    });
                }
            }
        }.runTaskTimer(SkillsBoss.getInstance(), 60, 60);
    }
    
    private void spawnSentinel(WitherSkeleton boss, int i, Set<UUID> minions) {
        String[] sentinelNames = { "§4§lCrimson Sentinel", "§5§lVoid Sentinel", "§1§lFrost Sentinel",
                "§c§lWar Sentinel", "§8§lShadow Sentinel" };
        String[] skinNames = { 
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGY5NWZkYTdkZmRjNDVhYTQ0MDY1OGM5NjQxMGQ0YjMyOTBkMzI2ZmNiNjYzNDEwNmU2MzBmZGYxNmJkNTcyIn19fQ==", 
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDAyNjgzMzFhNzkxODA5OTQyNGUzN2IyNjYwOWY3OGE1ZTE1MmU0NjZlZDBkNTQ2ZmRlZjI2NDczZDYyYzdhZiJ9fX0=", 
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTk1MmM2YjAyM2I3ZjE5YjA4Yjc3NDlkZGMzNGQ5NTc4ZDAyODc3N2JiZGQ2MDAxMmUwMTgzZTY5NjUxYWQ4OSJ9fX0=", 
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzViMjM2YmFlNDdhMjJlZDU3NzA0ZDliNDhjZmE3ODdhZWQ4NzIyMTFlZWNmODZlMDBiM2MwZDA4Nzg3NWFjNSJ9fX0=", 
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTBhYjg5NWM2M2U5MGZkNTY4MDM5NGVhZWRhYTFiNTRiMTM4ZWZlYmIzMTg0ZDI4OGM3NmU1M2IyM2I5MWQyZCJ9fX0=" 
        };
        Material[] sentinelDrops = { Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
                Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS, Material.DIAMOND_SWORD };
                
        double angle = i * Math.PI * 2 / 5;
        Location sLoc = findSafeSpawnLocation(boss.getLocation(), Math.cos(angle) * 6,
                Math.sin(angle) * 6);

        // Lightning at spawn point
        boss.getWorld().strikeLightningEffect(sLoc);

        WitherSkeleton sentinel = (WitherSkeleton) boss.getWorld().spawnEntity(sLoc,
                EntityType.WITHER_SKELETON);
        sentinel.customName(Component.text(sentinelNames[i]));
        sentinel.setCustomNameVisible(true);

        applyCorruptedArmor(sentinel, 300);

        if (sentinel.getAttribute(Attribute.ATTACK_DAMAGE) != null)
            sentinel.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(20.0);
        if (sentinel.getAttribute(Attribute.MOVEMENT_SPEED) != null)
            sentinel.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.35);

        sentinel.getEquipment().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        applyBase64Skin(sentinel, skinNames[i]);

        // Tag sentinel to drop legendary item
        sentinel.getPersistentDataContainer().set(DROP_ITEM_KEY, PersistentDataType.STRING,
                sentinelDrops[i].name());

        ritualTeam.addEntry(sentinel.getUniqueId().toString());
        minions.add(sentinel.getUniqueId());

        // Sentinel ability loop
        final int type = i;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!sentinel.isValid()) {
                    cancel();
                    return;
                }
                // Ambient particles
                sentinel.getWorld().spawnParticle(Particle.ENCHANT, sentinel.getLocation().add(0, 1, 0),
                        5, 0.3, 0.5, 0.3, 0.5);
                if (Math.random() < 0.15) {
                    try {
                        triggerBossAbility(sentinel, type, false);
                    } catch (Exception ignored) {
                    }
                }

                // Sentinel Laser Attack
                Player laserTarget = sentinel.getWorld()
                        .getNearbyEntities(sentinel.getLocation(), 15, 15, 15).stream()
                        .filter(e -> e instanceof Player
                                && ((Player) e).getGameMode() == GameMode.SURVIVAL)
                        .map(e -> (Player) e).findFirst().orElse(null);

                if (laserTarget != null) {
                    Location start = sentinel.getEyeLocation();
                    Location end = laserTarget.getEyeLocation();
                    Vector dir = end.toVector().subtract(start.toVector()).normalize();
                    double dist = start.distance(end);

                    // Visual Beam
                    for (double d = 0; d < dist; d += 0.5) {
                        sentinel.getWorld().spawnParticle(Particle.DUST,
                                start.clone().add(dir.clone().multiply(d)), 1,
                                new Particle.DustOptions(org.bukkit.Color.RED, 1.5f));
                    }
                    sentinel.getWorld().playSound(start, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.5f,
                            2f);
                    laserTarget.damage(25, sentinel);
                }
                // Leash to boss area
                if (sentinel.getLocation().distance(boss.getLocation()) > 25)
                    sentinel.teleport(boss.getLocation());
            }
        }.runTaskTimer(SkillsBoss.getInstance(), 20, 20);
    }

    public void startWarlordEvent(org.bukkit.World world) {
        if (!world.getEnvironment().equals(org.bukkit.World.Environment.NETHER)) {
            playerBroadcast(world, Component.text("The Warlord event can only begin in the Nether!", NamedTextColor.RED));
            return;
        }
        
        warlordSpawners.clear();
        warlordCountdownActive = false;
        Location center = world.getSpawnLocation();
        
        // Spawn 4 demonic spawners
        double[][] offsets = {{40, 40}, {-40, 40}, {40, -40}, {-40, -40}};
        for (int i = 0; i < 4; i++) {
            Location sLoc = findSafeSpawnLocation(center, offsets[i][0], offsets[i][1]);
            MagmaCube spawner = (MagmaCube) world.spawnEntity(sLoc, EntityType.MAGMA_CUBE);
            spawner.setSize(4);
            spawner.customName(Component.text("§5§lDemonic Spawner"));
            spawner.setCustomNameVisible(true);
            spawner.setAI(false);
            if (spawner.getAttribute(Attribute.MAX_HEALTH) != null) {
                spawner.getAttribute(Attribute.MAX_HEALTH).setBaseValue(200);
                spawner.setHealth(200);
            }
            warlordSpawners.add(spawner.getUniqueId());
            world.strikeLightningEffect(sLoc);
        }
        
        playerBroadcast(world, Component.text("THE CATACLYSM BEGINS!", NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD));
        playerBroadcast(world, Component.text("Follow your compass to find and destroy the 4 Demonic Spawners!", NamedTextColor.GOLD));
        
        // Give everyone a compass
        ItemStack compass = new ItemStack(Material.COMPASS);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(world)) {
                if (!p.getInventory().contains(Material.COMPASS)) {
                    p.getInventory().addItem(compass);
                }
            }
        }
        
        // Compass updating & mob spawning task
        new BukkitRunnable() {
            @Override
            public void run() {
                if (warlordSpawners.isEmpty() || warlordCountdownActive) {
                    cancel();
                    return;
                }
                
                // Spawn mobs around spawners occasionally
                if (Math.random() < 0.2) {
                    for (UUID id : warlordSpawners) {
                        Entity e = Bukkit.getEntity(id);
                        if (e != null && e.isValid()) {
                            e.getWorld().spawnParticle(Particle.PORTAL, e.getLocation(), 50, 4, 4, 4, 0.1);
                            if (Math.random() < 0.3) {
                                e.getWorld().spawnEntity(findSafeSpawnLocation(e.getLocation(), (Math.random()*6)-3, (Math.random()*6)-3), EntityType.WITHER_SKELETON);
                            }
                        }
                    }
                }
                
                // Update compasses
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getWorld().equals(world) && p.getInventory().contains(Material.COMPASS)) {
                        Entity closest = null;
                        double minDist = Double.MAX_VALUE;
                        for (UUID id : warlordSpawners) {
                            Entity e = Bukkit.getEntity(id);
                            if (e != null && e.isValid()) {
                                double d = e.getLocation().distanceSquared(p.getLocation());
                                if (d < minDist) {
                                    minDist = d;
                                    closest = e;
                                }
                            }
                        }
                        if (closest != null) {
                            p.setCompassTarget(closest.getLocation());
                        }
                    }
                }
            }
        }.runTaskTimer(SkillsBoss.getInstance(), 20, 20);
    }
    
    private void startBossDeathPortalSequence(Location deathLoc) {
        transitionActive = true;
        final World world = deathLoc.getWorld();

        // 30-second countdown with warnings
        new BukkitRunnable() {
            int secondsLeft = 30;

            @Override
            public void run() {
                if (secondsLeft <= 0) {
                    // Time's up — spawn portal and pull players
                    spawnPortalAndPull(deathLoc);
                    cancel();
                    return;
                }

                // Periodic warnings
                if (secondsLeft == 30 || secondsLeft == 20 || secondsLeft == 10 || secondsLeft <= 5) {
                    playerBroadcast(world,
                            Component.text("A dimensional rift is forming... " + secondsLeft + " seconds!",
                                    NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD));
                    for (Player p : world.getPlayers()) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f,
                                secondsLeft <= 5 ? 2f : 1f);
                    }
                }

                // Ambient rumbling effects during countdown
                if (secondsLeft % 5 == 0) {
                    world.strikeLightningEffect(deathLoc.clone().add(
                            (Math.random() * 10) - 5, 0, (Math.random() * 10) - 5));
                    world.playSound(deathLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 0.3f);
                }

                // Growing particle vortex at death location
                double radius = 3.0 + ((30 - secondsLeft) * 0.3);
                for (int i = 0; i < 360; i += 20) {
                    double angle = Math.toRadians(i + (secondsLeft * 12));
                    Location pLoc = deathLoc.clone().add(Math.cos(angle) * radius, 0.5, Math.sin(angle) * radius);
                    world.spawnParticle(Particle.PORTAL, pLoc, 5, 0.1, 0.5, 0.1, 0.1);
                    world.spawnParticle(Particle.REVERSE_PORTAL, pLoc, 3, 0.1, 0.3, 0.1, 0.05);
                }

                secondsLeft--;
            }
        }.runTaskTimer(SkillsBoss.getInstance(), 0, 20); // Every second
    }

    private void spawnPortalAndPull(Location portalLoc) {
        World world = portalLoc.getWorld();

        // Advance progression
        SkillsBoss.setProgressionLevel(2);

        // === DRAMATIC LIGHTNING SHOW ===
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45);
            Location strikePos = portalLoc.clone().add(Math.cos(angle) * 5, 0, Math.sin(angle) * 5);
            world.strikeLightningEffect(strikePos);
        }
        world.strikeLightningEffect(portalLoc);
        world.playSound(portalLoc, Sound.ENTITY_WITHER_SPAWN, 2f, 0.3f);
        world.playSound(portalLoc, Sound.BLOCK_END_PORTAL_SPAWN, 2f, 0.5f);
        world.playSound(portalLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, 0.3f);

        // Massive particle explosion
        world.spawnParticle(Particle.EXPLOSION_EMITTER, portalLoc, 10, 2, 2, 2, 0);
        world.spawnParticle(Particle.REVERSE_PORTAL, portalLoc, 500, 3, 3, 3, 0.5);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, portalLoc, 200, 5, 3, 5, 0.1);

        // Show title to all players
        Title portalTitle = Title.title(
                Component.text("THE RIFT OPENS", NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD),
                Component.text("You are being pulled into the Avernus!", NamedTextColor.LIGHT_PURPLE));
        Bukkit.getOnlinePlayers().forEach(p -> p.showTitle(portalTitle));

        // === BUILD NETHER PORTAL at death location ===
        Location basePortal = portalLoc.clone();
        basePortal.setY(Math.floor(basePortal.getY()));

        // Build a 5-wide, 5-tall portal frame
        for (int x = -2; x <= 2; x++) {
            for (int y = 0; y <= 5; y++) {
                Block b = basePortal.clone().add(x, y, 0).getBlock();
                if (x == -2 || x == 2 || y == 0 || y == 5) {
                    b.setType(Material.OBSIDIAN);
                } else {
                    b.setType(Material.NETHER_PORTAL);
                }
            }
        }

        // Set of portal blocks for proximity detection
        Set<Location> portalBlockLocs = new HashSet<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = 1; y <= 4; y++) {
                portalBlockLocs.add(basePortal.clone().add(x, y, 0));
            }
        }

        // === PULL PLAYERS INTO THE PORTAL ===
        final Location pullCenter = basePortal.clone().add(0, 2, 0);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks > 900) { // 45 seconds max pull time
                    // Force teleport anyone still in the world
                    org.bukkit.World nether = Bukkit.getWorlds().stream()
                            .filter(w -> w.getEnvironment() == org.bukkit.World.Environment.NETHER)
                            .findFirst().orElse(null);
                    if (nether != null) {
                        for (Player p : world.getPlayers()) {
                            p.teleport(findRandomNetherLocation(nether));
                            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
                            p.sendMessage(
                                    Component.text("You have been consumed by the Avernus!", NamedTextColor.DARK_RED));
                        }
                        startWarlordEvent(nether);
                    }
                    transitionActive = false;
                    cancel();
                    return;
                }

                // Continuous lightning around the portal
                if (ticks % 40 == 0) {
                    world.strikeLightningEffect(portalLoc.clone().add(
                            (Math.random() * 16) - 8, 0, (Math.random() * 16) - 8));
                }

                // Portal ambient effects
                if (ticks % 5 == 0) {
                    world.spawnParticle(Particle.PORTAL, pullCenter, 30, 1, 2, 1, 0.5);
                    world.spawnParticle(Particle.REVERSE_PORTAL, pullCenter, 20, 2, 2, 2, 0.2);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, pullCenter, 5, 0.5, 1, 0.5, 0.05);
                    world.playSound(pullCenter, Sound.BLOCK_PORTAL_AMBIENT, 0.5f, 0.5f);
                }

                // Pull all players toward the portal
                for (Player p : world.getPlayers()) {
                    double dist = p.getLocation().distance(pullCenter);

                    // Check if close enough to teleport
                    if (dist < 1.5) {
                        org.bukkit.World nether = Bukkit.getWorlds().stream()
                                .filter(w -> w.getEnvironment() == org.bukkit.World.Environment.NETHER)
                                .findFirst().orElse(null);
                        if (nether != null) {
                            p.teleport(findRandomNetherLocation(nether));
                            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
                            p.sendMessage(
                                    Component.text("You have been consumed by the Avernus!", NamedTextColor.DARK_RED));
                        }
                        continue;
                    }

                    // Pull force — slow cinematic pull so you can see it happening
                    Vector pull = pullCenter.toVector().subtract(p.getLocation().toVector()).normalize();
                    double force = 0.04 + (Math.max(0, 100 - dist) * 0.002);
                    if (force > 0.25) force = 0.25;

                    p.setVelocity(p.getVelocity().add(pull.multiply(force)));

                    // Periodic warnings
                    if (ticks % 40 == 0) {
                        p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, 0.5f, 0.5f);
                        p.sendMessage(
                                Component.text("The dimensional rift pulls at your soul...", NamedTextColor.DARK_PURPLE)
                                        .decorate(TextDecoration.ITALIC));
                    }
                }

                ticks++;
            }
        }.runTaskTimer(SkillsBoss.getInstance(), 0, 1);
    }

    private void startWarlordCountdown(org.bukkit.World world) {
        warlordCountdownActive = true;
        playerBroadcast(world, Component.text("All Demonic Spawners are destroyed! The Warlord arrives in 2 minutes!", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        
        new BukkitRunnable() {
            int ticksLeft = 120 * 20; // 2 minutes
            @Override
            public void run() {
                if (ticksLeft <= 0) {
                    playerBroadcast(world, Component.text("THE MASSIVE WARLORD HAS ARRIVED!", NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD));
                    spawnWarlordBoss(world.getSpawnLocation());
                    cancel();
                    return;
                }
                
                if (ticksLeft % 20 == 0) {
                    int seconds = ticksLeft / 20;
                    if (seconds == 60 || seconds == 30 || seconds == 10 || seconds <= 5) {
                        playerBroadcast(world, Component.text("Warlord spawns in " + seconds + " seconds!", NamedTextColor.YELLOW));
                        for (Player p : world.getPlayers()) {
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                        }
                    }
                }
                ticksLeft--;
            }
        }.runTaskTimer(SkillsBoss.getInstance(), 0, 1);
    }
}
