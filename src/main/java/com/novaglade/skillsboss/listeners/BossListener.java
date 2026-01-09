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
        if (item == null || !ItemManager.isBossSpawnItem(item))
            return;

        event.setCancelled(true);
        Block block = event.getClickedBlock();
        if (block == null)
            return;

        Location center = block.getLocation().add(0.5, 1, 0.5);
        item.setAmount(item.getAmount() - 1);

        playerBroadcast(center.getWorld(),
                Component.text("A rift in the Avernus opens...", NamedTextColor.RED, TextDecoration.BOLD));
        generateSmallAltar(center);
        spawnAltarArmorStand(center.clone().add(0, 0.1, 0));

        center.getWorld().strikeLightningEffect(center);
        center.getWorld().playSound(center, Sound.ENTITY_WITHER_SPAWN, 1f, 0.5f);
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
        if (entity.getPersistentDataContainer().has(WAVE_MOB_KEY, PersistentDataType.STRING)) {
            UUID standUuid = UUID
                    .fromString(entity.getPersistentDataContainer().get(WAVE_MOB_KEY, PersistentDataType.STRING));
            Set<UUID> mobs = activeWaveMobs.get(standUuid);
            if (mobs != null) {
                mobs.remove(entity.getUniqueId());
                updateBossBar(standUuid, mobs.size());
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
                        Component.text("THE OVERLORDS ARE DEFEATED!", NamedTextColor.GOLD, TextDecoration.BOLD));
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
            bar.setProgress(Math.clamp(currentMobs / 12.0, 0.0, 1.0));
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
        BossBar ritualBar = Bukkit.createBossBar("§4§lThe Avernus Ritual", BarColor.RED, BarStyle.SEGMENTED_10);
        for (Player p : stand.getWorld().getPlayers()) {
            ritualBar.addPlayer(p);
        }
        activeBars.put(standUuid, ritualBar);

        new BukkitRunnable() {
            int wave = 0; // Current active wave (1, 2, 3) or 4 (boss)
            boolean waitingForWave = false;

            @Override
            public void run() {
                if (!stand.isValid()) {
                    endRitual(standUuid, ritualBar);
                    cancel();
                    return;
                }

                Set<UUID> mobs = activeWaveMobs.get(standUuid);
                if (waitingForWave) {
                    mobs.removeIf(id -> Bukkit.getEntity(id) == null || !Bukkit.getEntity(id).isValid());
                    if (mobs.isEmpty()) {
                        waitingForWave = false;
                        wave++;
                        stand.getWorld().playSound(stand.getLocation(), Sound.ENTITY_ENDER_EYE_DEATH, 1.5f, 0.5f);
                    } else {
                        return;
                    }
                }

                if (wave == 0) { // Initial Trigger
                    startWave(stand, ritualBar, 1);
                    wave = 1;
                    waitingForWave = true;
                } else if (wave == 1) { // Wave 1 cleared
                    startWave(stand, ritualBar, 2);
                    wave = 2;
                    waitingForWave = true;
                } else if (wave == 2) { // Wave 2 cleared
                    startWave(stand, ritualBar, 3);
                    wave = 3;
                    waitingForWave = true;
                } else if (wave == 3) { // Wave 3 cleared
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

    private void startWave(ArmorStand stand, BossBar bar, int waveNum) {
        String[] titles = { "", "§c§lWave 1: Soul Stalkers", "§6§lWave 2: Infernal Phalanx",
                "§4§lWave 3: Pit Guardians" };
        Component[] msgs = { null, Component.text("The air grows cold...", NamedTextColor.RED),
                Component.text("The nether erupts!", NamedTextColor.GOLD),
                Component.text("The elite have come!", NamedTextColor.DARK_RED) };

        bar.setTitle(titles[waveNum]);
        playerBroadcast(stand.getWorld(), msgs[waveNum]);
        spawnWave(stand, waveNum);
    }

    private void spawnWave(ArmorStand stand, int waveNum) {
        Location loc = stand.getLocation();
        Set<UUID> mobs = activeWaveMobs.get(stand.getUniqueId());
        if (waveNum == 1) {
            for (int i = 0; i < 10; i++)
                spawnMob(loc, EntityType.WITHER_SKELETON, "§7Soul Stalker", Material.BOW, stand.getUniqueId(), mobs);
        } else if (waveNum == 2) {
            for (int i = 0; i < 6; i++) {
                spawnMob(loc, EntityType.PIGLIN_BRUTE, "§6Infernal Brute", Material.GOLDEN_AXE, stand.getUniqueId(),
                        mobs);
                spawnMob(loc.clone().add(0, 3, 0), EntityType.BLAZE, "§6Avernus Ember", null, stand.getUniqueId(),
                        mobs);
            }
        } else if (waveNum == 3) {
            for (int i = 0; i < 4; i++) {
                MagmaCube m = (MagmaCube) spawnMob(loc, EntityType.MAGMA_CUBE, "§4Pit Lord", null, stand.getUniqueId(),
                        mobs);
                m.setSize(6);
            }
        }
    }

    private LivingEntity spawnMob(Location loc, EntityType type, String name, Material hand, UUID standUuid,
            Set<UUID> mobs) {
        Location spawn = loc.clone().add(Math.random() * 10 - 5, 0, Math.random() * 10 - 5);
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
        playerBroadcast(loc.getWorld(),
                Component.text("THE OVERLORDS HAVE ARRIVED!", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        String[] titles = { "§4§lIgnis", "§4§lSoul", "§4§lVoid", "§4§lKane" };
        BarColor[] colors = { BarColor.RED, BarColor.PURPLE, BarColor.BLUE, BarColor.WHITE };
        for (int i = 0; i < 4; i++) {
            Location spawn = loc.clone().add(Math.cos(i * Math.PI / 2) * 5, 0, Math.sin(i * Math.PI / 2) * 5);
            WitherSkeleton boss = (WitherSkeleton) loc.getWorld().spawnEntity(spawn, EntityType.WITHER_SKELETON);
            boss.setCustomName(titles[i]);
            boss.setCustomNameVisible(true);
            boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(800);
            boss.setHealth(800);
            boss.getAttribute(Attribute.SCALE).setBaseValue(2.0);
            boss.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
            boss.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
            boss.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
            boss.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
            boss.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));

            if (i == 0)
                boss.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.4);
            if (i == 3)
                boss.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1.0);

            ritualTeam.addEntry(boss.getUniqueId().toString());
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
                    double rand = Math.random();
                    if (rand < 0.12) {
                        if (type == 0) { // Ignis
                            boss.getWorld().spawnParticle(Particle.FLAME, boss.getLocation(), 200, 4, 1, 4, 0.2);
                            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1f, 1f);
                            for (Entity e : boss.getNearbyEntities(8, 6, 8)) {
                                if (e instanceof Player p && !p.isOp())
                                    p.setFireTicks(160);
                            }
                        } else if (type == 1) { // Soul
                            boss.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, boss.getLocation(), 100, 3, 2, 3,
                                    0.05);
                            for (UUID id : bossGroup) {
                                LivingEntity b = (LivingEntity) Bukkit.getEntity(id);
                                if (b != null && b.isValid())
                                    b.setHealth(Math.min(800, b.getHealth() + 40));
                            }
                            for (Entity e : boss.getNearbyEntities(10, 10, 10)) {
                                if (e instanceof Player p && !p.isOp())
                                    p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1));
                            }
                        } else if (type == 2) { // Void
                            boss.getWorld().spawnParticle(Particle.PORTAL, boss.getLocation(), 200, 10, 2, 10, 0);
                            for (Entity e : boss.getNearbyEntities(12, 12, 12)) {
                                if (e instanceof Player p && !p.isOp())
                                    p.setVelocity(boss.getLocation().subtract(p.getLocation()).toVector().normalize()
                                            .multiply(1.6));
                            }
                        } else if (type == 3) { // Kane
                            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 2f,
                                    0.5f);
                            boss.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, boss.getLocation(), 5, 4, 0.5, 4,
                                    0);
                            for (Entity e : boss.getNearbyEntities(10, 5, 10)) {
                                if (e instanceof Player p && !p.isOp()) {
                                    p.setVelocity(new Vector(0, 1.8, 0));
                                    p.damage(8, boss);
                                }
                            }
                        }
                    }
                }
            }.runTaskTimer(SkillsBoss.getInstance(), 20, 20);
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
        if (msg != null) {
            for (Player p : world.getPlayers()) {
                p.sendMessage(msg);
            }
        }
    }
}
