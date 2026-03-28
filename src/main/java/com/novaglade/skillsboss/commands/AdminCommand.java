package com.novaglade.skillsboss.commands;

import com.novaglade.skillsboss.SkillsBoss;
import com.novaglade.skillsboss.items.ItemManager;
import com.novaglade.skillsboss.listeners.BossListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class AdminCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "progression":
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /admin progression <0|1|2|reset>", NamedTextColor.RED));
                    return true;
                }
                if (args[1].equalsIgnoreCase("reset")) {
                    SkillsBoss.setProgressionLevel(-1);
                    sender.sendMessage(Component.text("Progression reset to -1.", NamedTextColor.GREEN));
                    return true;
                }
                try {
                    int level = Integer.parseInt(args[1]);
                    if (level == 0) {
                        if (sender instanceof Player) {
                            Player p = (Player) sender;
                            Location beaconLoc = findNearbyProgression1Beacon(p.getLocation(), 20);
                            if (beaconLoc != null) {
                                startProgression0At(p.getWorld(), beaconLoc.clone().add(0.5, 0, 0.5));
                            } else {
                                startProgressionZeroTransition(p); // Fallback to player location if no beacon
                            }
                        } else {
                            sender.sendMessage(Component.text("Must be run by player.", NamedTextColor.RED));
                        }
                    } else if (level == 1) {
                        if (sender instanceof Player) {
                            Player p = (Player) sender;
                            Location beaconLoc = findNearbyProgression1Beacon(p.getLocation(), 20); // Reduced radius to
                                                                                                    // 20 for
                                                                                                    // performance
                            if (beaconLoc != null) {
                                startProgression1At(p.getWorld(), beaconLoc.clone().add(0.5, 0, 0.5));
                            } else {
                                sender.sendMessage(
                                        Component.text(
                                                "No Progression I Catalyst found nearby (20 blocks)! Place one first.",
                                                NamedTextColor.RED));
                            }
                        } else {
                            sender.sendMessage(
                                    Component.text("Must be run by player to determine world.", NamedTextColor.RED));
                        }
                    } else if (level == 2) {
                        if (sender instanceof Player) {
                            Player p = (Player) sender;
                            SkillsBoss.setProgressionLevel(2);
                            String worldName = "world_nether";
                            World targetWorld = Bukkit.getWorld(worldName);
                            if (targetWorld == null) {
                                targetWorld = Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == World.Environment.NETHER).findFirst().orElse(null);
                            }
                            if (targetWorld != null) {
                                com.novaglade.skillsboss.listeners.BossListener.getInstance().startWarlordEvent(targetWorld);
                                sender.sendMessage(Component.text("Progression II and Warlord Event started in " + targetWorld.getName(), NamedTextColor.GREEN));
                            } else {
                                sender.sendMessage(Component.text("Could not find target dimension.", NamedTextColor.YELLOW));
                            }
                        } else {
                            sender.sendMessage(Component.text("Must be run by player.", NamedTextColor.RED));
                        }
                    } else {
                        sender.sendMessage(Component.text("Invalid level.", NamedTextColor.RED));
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Invalid number.", NamedTextColor.RED));
                }
                break;
            case "test":
                if (args.length >= 3 && args[1].equalsIgnoreCase("progression")) {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(Component.text("Must be run by a player.", NamedTextColor.RED));
                        return true;
                    }
                    Player p = (Player) sender;
                    try {
                        int level = Integer.parseInt(args[2]);
                        SkillsBoss.setProgressionLevel(level);
                        String worldName = "world";
                        if (level >= 2) {
                            worldName = "world_nether";
                        }
                        World targetWorld = Bukkit.getWorld(worldName);
                        if (targetWorld == null && level >= 2) {
                            // Fallback if generic nether name isn't found
                            targetWorld = Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == World.Environment.NETHER).findFirst().orElse(null);
                        }
                        if (targetWorld != null) {
                            p.teleport(targetWorld.getSpawnLocation());
                            sender.sendMessage(Component.text("Set Progression to " + level + " and teleported to " + targetWorld.getName(), NamedTextColor.GREEN));
                        } else {
                            sender.sendMessage(Component.text("Set Progression to " + level + " but could not find target dimension.", NamedTextColor.YELLOW));
                        }
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Component.text("Invalid number.", NamedTextColor.RED));
                    }
                } else {
                    sender.sendMessage(Component.text("Usage: /admin test progression <0|1|2>", NamedTextColor.RED));
                }
                break;
            case "setprog":
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /admin setprog <level>", NamedTextColor.RED));
                    return true;
                }
                try {
                    int level = Integer.parseInt(args[1]);
                    SkillsBoss.setProgressionLevel(level);
                    sender.sendMessage(Component.text("Progression set abruptly to " + level + ".", NamedTextColor.GREEN));
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Invalid number.", NamedTextColor.RED));
                }
                break;
            case "altar":
                if (args.length >= 2 && args[1].equalsIgnoreCase("reset")) {
                    com.novaglade.skillsboss.listeners.BossListener.getInstance().resetRitualSystem();
                    sender.sendMessage(Component.text("Ritual system has been reset!", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Usage: /admin altar reset", NamedTextColor.RED));
                }
                break;
            case "give":
                handleGive(sender, args);
                break;
            case "boss":
                handleBossSpawn(sender, args);
                break;
            case "warlord":
                handleWarlord(sender, args);
                break;
            case "trader":
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    com.novaglade.skillsboss.listeners.ProgressionListener.spawnPiglinTrader(p.getLocation());
                    sender.sendMessage(Component.text("Piglin Trader spawned!", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Must be run by player.", NamedTextColor.RED));
                }
                break;
            case "killall":
                handleKillAll(sender, args);
                break;
            case "heal":
                handleHeal(sender, args);
                break;
            case "tp":
                handleTeleport(sender, args);
                break;
            case "status":
                handleStatus(sender);
                break;
            case "worldborder":
            case "wb":
                handleWorldBorder(sender, args);
                break;
            case "god":
                handleGodMode(sender);
                break;
            case "time":
                handleTime(sender, args);
                break;
            case "reload":
                SkillsBoss.getInstance().reloadConfig();
                sender.sendMessage(Component.text("Config reloaded.", NamedTextColor.GREEN));
                break;
            case "version":
                sender.sendMessage(Component.text(
                        "SkillsBoss v" + SkillsBoss.getInstance().getDescription().getVersion(), NamedTextColor.GREEN));
                break;
            default:
                sendHelp(sender);
                break;
        }
        return true;
    }

    private void startProgressionZeroTransition(Player player) {
        startProgression0At(player.getWorld(), player.getLocation());
    }

    public static void startProgression0At(org.bukkit.World world, Location center) {
        SkillsBoss.setProgressionLevel(0);
        world.setSpawnLocation(center);
        world.getWorldBorder().setCenter(center);
        world.getWorldBorder().setSize(19);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getWorld().equals(world)) {
                online.teleport(center);
                online.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                online.spawnParticle(Particle.EXPLOSION_EMITTER, center, 20, 2, 2, 2, 0);

                Title startTitle = Title.title(
                        Component.text("PROGRESSION 0", NamedTextColor.WHITE)
                                .decorate(TextDecoration.BOLD),
                        Component.text("A NEW BEGINNING", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4),
                                Duration.ofSeconds(1)));
                online.showTitle(startTitle);
            }
        }

        // Delete the beacon catalyst if present
        if (center.getBlock().getType() == Material.BEACON) {
            center.getBlock().setType(Material.AIR);
        }
    }

    private static void startProgressionOneCountdown(org.bukkit.World world) {
        startProgression1At(world, null);
    }

    public static void startProgression1At(org.bukkit.World world, Location customCenter) {
        Location center = customCenter != null ? customCenter : world.getSpawnLocation();
        Random random = new Random();

        new BukkitRunnable() {
            int maxTicks = 10 * 20; // 10 seconds
            int ticks = maxTicks;

            @Override
            public void run() {
                try {
                    int seconds = (int) Math.ceil(ticks / 20.0);

                    // MASSIVE VORTEX - 10 Streams (Stable)
                    double radius = 1.0 + (maxTicks - ticks) * 0.08;
                    double angle = ticks * 0.4;
                    for (int i = 0; i < 10; i++) {
                        double subAngle = angle + (i * (Math.PI * 2 / 10));
                        double x = Math.cos(subAngle) * radius;
                        double z = Math.sin(subAngle) * radius;
                        double y = (maxTicks - ticks) * 0.15;
                        if (y > 40)
                            y = 40;

                        Location partLoc = center.clone().add(x, y, z);
                        world.spawnParticle(Particle.END_ROD, partLoc, 5, 0.1, 0.1, 0.1, 0.01);
                        world.spawnParticle(Particle.WITCH, partLoc, 3, 0.1, 0.1, 0.1, 0.01);
                        world.spawnParticle(Particle.GLOW, partLoc, 2, 0.1, 0.1, 0.1, 0.01);

                        // Cyan Energy Laser Threads
                        if (ticks % 2 == 0) {
                            for (int j = 0; j < 20; j++) {
                                double ratio = j / 20.0;
                                Location laserPoint = center.clone().add(x * ratio, y * ratio, z * ratio);
                                world.spawnParticle(Particle.DUST, laserPoint, 1,
                                        new Particle.DustOptions(org.bukkit.Color.AQUA, 0.5f));
                            }
                        }

                        // Ender Dragon Lasers (End Crystal Beams from Vortex to Center)
                        if (ticks % 3 == 0) {
                            for (int j = 0; j < 15; j++) {
                                double ratio = j / 15.0;
                                Location laserPoint = center.clone().add(x * ratio, y * ratio, z * ratio);
                                world.spawnParticle(Particle.DUST, laserPoint, 1,
                                        new Particle.DustOptions(org.bukkit.Color.fromRGB(200, 0, 255), 1.0f));
                            }
                        }
                    }

                    // ROTATING SCANNING LASERS (Yellow)
                    if (ticks % 2 == 0) {
                        for (int i = 0; i < 3; i++) {
                            double lAngle = ticks * 0.1 + (i * Math.PI * 2 / 3);
                            for (double d = 0; d < 15; d += 0.5) {
                                double lx = Math.cos(lAngle) * d;
                                double lz = Math.sin(lAngle) * d;
                                double ly = Math.sin(ticks * 0.05) * 5 + 5;
                                world.spawnParticle(Particle.DUST, center.clone().add(lx, ly, lz), 1,
                                        new Particle.DustOptions(org.bukkit.Color.YELLOW, 0.8f));
                            }
                        }
                    }

                    // END CRYSTAL LASERS (Purple dense beams)
                    if (ticks % 5 == 0) {
                        for (int i = 0; i < 4; i++) {
                            double pAngle = (ticks * 0.05) + (i * Math.PI / 2);
                            double px = Math.cos(pAngle) * 15;
                            double pz = Math.sin(pAngle) * 15;
                            Location beamStart = center.clone().add(px, 35, pz);
                            Vector dir = center.clone().add(0, 1.5, 0).toVector().subtract(beamStart.toVector());
                            double len = dir.length();
                            dir.normalize();
                            for (double d = 0; d < len; d += 0.3) {
                                world.spawnParticle(Particle.DUST, beamStart.clone().add(dir.clone().multiply(d)), 3,
                                        new Particle.DustOptions(org.bukkit.Color.fromRGB(200, 0, 255), 1.5f));
                            }
                        }
                    }

                    if (ticks % 15 == 0) {
                        world.spawnParticle(Particle.REVERSE_PORTAL, center, 100, 10, 5, 10, 0.01);
                        world.spawnParticle(Particle.SOUL, center, 50, 8, 2, 8, 0.02);
                        world.playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 2f, 0.5f);
                    }

                    if (ticks % 20 == 0 && seconds > 0) {
                        Component mainTitle = Component.text(String.valueOf(seconds), NamedTextColor.GOLD)
                                .decorate(TextDecoration.BOLD);
                        Component subTitle = Component.text("A New Era Approaches...",
                                NamedTextColor.YELLOW).decorate(TextDecoration.ITALIC);
                        Title title = Title.title(mainTitle, subTitle,
                                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(800),
                                        Duration.ofMillis(100)));

                        world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 2f,
                                0.5f + ((float) (maxTicks - ticks) / maxTicks));
                        world.playSound(center, Sound.BLOCK_BEACON_AMBIENT, 1f, 0.5f);

                        for (Player online : Bukkit.getOnlinePlayers()) {
                            if (online.getWorld().equals(world)) {
                                online.showTitle(title);
                                online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f,
                                        0.5f + (seconds * 0.1f));
                            }
                        }

                        // Expanding Golden Rings
                        for (int i = 0; i < 360; i += 10) {
                            double rad = Math.toRadians(i);
                            double rx = Math.cos(rad) * (5 + (15 - seconds) * 2);
                            double rz = Math.sin(rad) * (5 + (15 - seconds) * 2);
                            world.spawnParticle(Particle.GLOW, center.clone().add(rx, 1.0, rz), 2, 0.1, 0.1, 0.1, 0.01);
                        }
                    }

                    if (ticks <= 0) {
                        SkillsBoss.setProgressionLevel(1);
                        Component mainTitle = Component.text("PROGRESSION I", NamedTextColor.GREEN)
                                .decorate(TextDecoration.BOLD);
                        Component subTitle = Component.text("A NEW BEGINNING", NamedTextColor.DARK_RED)
                                .decorate(TextDecoration.BOLD);

                        Title finalTitle = Title.title(mainTitle, subTitle,
                                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(3000),
                                        Duration.ofMillis(1000)));

                        world.spawnParticle(Particle.EXPLOSION_EMITTER, center.clone().add(0, 5, 0), 100, 5, 10, 5, 0);
                        world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 1.0f);
                        world.playSound(center, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2.0f, 1.0f);
                        world.playSound(center, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);

                        // Massive Shockwave
                        for (double r = 0; r < 40; r += 2.0) {
                            final double currentR = r;
                            int delay = (int) (r / 2.0);
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    for (int i = 0; i < 360; i += 4) {
                                        double rad = Math.toRadians(i);
                                        double rx = Math.cos(rad) * currentR;
                                        double rz = Math.sin(rad) * currentR;
                                        world.spawnParticle(Particle.SOUL_FIRE_FLAME, center.clone().add(rx, 1.0, rz),
                                                2, 0, 0.1, 0, 0.01);
                                        world.spawnParticle(Particle.DRAGON_BREATH, center.clone().add(rx, 2.0, rz), 2,
                                                0, 0.1, 0, 0.01);
                                        if (currentR % 10 == 0) {
                                            world.spawnParticle(Particle.EXPLOSION, center.clone().add(rx, 0.5, rz), 1,
                                                    0, 0, 0, 0);
                                        }
                                    }
                                    world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 0.5f);
                                }
                            }.runTaskLater(SkillsBoss.getInstance(), delay);
                        }

                        // Delete the beacon catalyst
                        if (center.getBlock().getType() == Material.BEACON) {
                            center.getBlock().setType(Material.AIR);
                            world.spawnParticle(Particle.EXPLOSION_EMITTER, center.clone().add(0, 1, 0), 50);
                            world.spawnParticle(Particle.FLASH, center.clone().add(0, 1, 0), 20);
                            world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
                            world.playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 2f, 0.5f);
                            world.strikeLightningEffect(center);
                        }

                        world.getWorldBorder().setCenter(center);
                        world.getWorldBorder().setSize(1000);
                        for (Player online : Bukkit.getOnlinePlayers()) {
                            if (online.getWorld().equals(world)) {
                                online.sendMessage(Component
                                        .text("PROGRESSION I: ", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                                        .append(Component
                                                .text("A New Beginning", NamedTextColor.DARK_RED)
                                                .decorate(TextDecoration.BOLD)));
                                online.showTitle(finalTitle);
                                online.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 2));
                                online.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 1)); // Added
                                                                                                               // more
                                                                                                               // effects
                            }
                        }
                        cancel();
                    }
                    ticks--;
                } catch (Exception e) {
                    Bukkit.getLogger().severe("[SkillsBoss] Error in Progression I countdown: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskTimer(SkillsBoss.getInstance(), 0, 1);

    }

    private static Location findNearbyProgression1Beacon(Location playerLoc, int radius) {
        World world = playerLoc.getWorld();
        int px = playerLoc.getBlockX();
        int py = playerLoc.getBlockY();
        int pz = playerLoc.getBlockZ();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -10; y <= 10; y++) { // Optimized Y scan
                    Block block = world.getBlockAt(px + x, py + y, pz + z);
                    if (block.getType() == Material.BEACON) {
                        // Check if this beacon has the Progression 1 marker
                        if (block.getState() instanceof TileState) {
                            TileState state = (TileState) block.getState();
                            if (state.getPersistentDataContainer().has(
                                    new NamespacedKey(SkillsBoss.getInstance(), "progression_1_beacon"),
                                    PersistentDataType.BYTE)) {
                                return block.getLocation();
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }
        Player player = (Player) sender;

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /admin give <diamondarmor|netheritearmor|waveboss|portal|allitems>", NamedTextColor.RED));
            return;
        }

        if (args[1].equalsIgnoreCase("diamondarmor")) {
            player.getInventory().addItem(ItemManager.createCustomItem(Material.DIAMOND_HELMET));
            player.getInventory().addItem(ItemManager.createCustomItem(Material.DIAMOND_CHESTPLATE));
            player.getInventory().addItem(ItemManager.createCustomItem(Material.DIAMOND_LEGGINGS));
            player.getInventory().addItem(ItemManager.createCustomItem(Material.DIAMOND_BOOTS));
            player.getInventory().addItem(ItemManager.createCustomItem(Material.DIAMOND_SWORD));
            sender.sendMessage(Component.text("Received custom diamond armor and sword!", NamedTextColor.GREEN));
        } else if (args[1].equalsIgnoreCase("netheritearmor")) {
            player.getInventory().addItem(new ItemStack(Material.NETHERITE_HELMET));
            player.getInventory().addItem(new ItemStack(Material.NETHERITE_CHESTPLATE));
            player.getInventory().addItem(new ItemStack(Material.NETHERITE_LEGGINGS));
            player.getInventory().addItem(new ItemStack(Material.NETHERITE_BOOTS));
            player.getInventory().addItem(new ItemStack(Material.NETHERITE_SWORD));
            sender.sendMessage(Component.text("Received netherite armor and sword!", NamedTextColor.GREEN));
        } else if (args[1].equalsIgnoreCase("bossspawn1") || args[1].equalsIgnoreCase("waveboss")
                || args[1].equalsIgnoreCase("wavespawn")) {
            player.getInventory().addItem(ItemManager.createBossSpawnItem());
            player.getInventory().addItem(ItemManager.createBossSpawnerItem());
            sender.sendMessage(Component.text("Received Ritual Core and Ritual Spawner!", NamedTextColor.LIGHT_PURPLE));
        } else if (args[1].equalsIgnoreCase("portal")) {
            player.getInventory().addItem(new ItemStack(Material.BARRIER, 64));
            player.getInventory().addItem(ItemManager.createPortalCoreBlock());
            sender.sendMessage(
                    Component.text("Received Portal Frame Blocks and Ignition Core!", NamedTextColor.LIGHT_PURPLE));
        } else if (args[1].equalsIgnoreCase("prog1")) {
            player.getInventory().addItem(ItemManager.createProgression1Item());
            sender.sendMessage(Component.text("Received Progression 1 Catalyst!", NamedTextColor.GOLD));
        } else if (args[1].equalsIgnoreCase("turn")) {
            player.getInventory().addItem(ItemManager.createAltarTurnerItem());
            sender.sendMessage(Component.text("Received Altar Turner stick!", NamedTextColor.YELLOW));
        } else if (args[1].equalsIgnoreCase("traderegg")) {
            player.getInventory().addItem(ItemManager.createTraderSpawnItem());
            sender.sendMessage(Component.text("Received Piglin Trader Spawn Egg!", NamedTextColor.GOLD));
        } else if (args[1].equalsIgnoreCase("traderspawner")) {
            player.getInventory().addItem(ItemManager.createTraderSpawnerItem());
            sender.sendMessage(Component.text("Received Piglin Trader Spawner!", NamedTextColor.GOLD));
        } else if (args[1].equalsIgnoreCase("food")) {
            player.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 64));
            player.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 64));
            sender.sendMessage(Component.text("Received food supplies!", NamedTextColor.GREEN));
        } else if (args[1].equalsIgnoreCase("allitems")) {
            player.getInventory().addItem(ItemManager.createBossSpawnItem());
            player.getInventory().addItem(ItemManager.createBossSpawnerItem());
            player.getInventory().addItem(ItemManager.createPortalCoreBlock());
            player.getInventory().addItem(new ItemStack(Material.BARRIER, 64));
            player.getInventory().addItem(ItemManager.createProgression1Item());
            player.getInventory().addItem(ItemManager.createAltarTurnerItem());
            player.getInventory().addItem(ItemManager.createTraderSpawnItem());
            player.getInventory().addItem(ItemManager.createTraderSpawnerItem());
            sender.sendMessage(Component.text("Received all admin items!", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Unknown item. Options: diamondarmor, netheritearmor, wavespawn, portal, prog1, turn, traderegg, traderspawner, food, allitems", NamedTextColor.RED));
        }
    }

    // ===========================
    //   BOSS SPAWNING
    // ===========================
    private void handleBossSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Must be run by player.", NamedTextColor.RED));
            return;
        }
        Player p = (Player) sender;

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /admin boss <supremus|warlord>", NamedTextColor.RED));
            return;
        }

        String bossType = args[1].toLowerCase();
        switch (bossType) {
            case "supremus":
                // Spawn Supremus directly at player location (skips ritual)
                BossListener bl = BossListener.getInstance();
                if (bl != null) {
                    // Use reflection-free approach: call spawnBosses through the listener 
                    // Since spawnBosses is private, we trigger via a public mechanism
                    // For now, spawn a simulated direct boss
                    sender.sendMessage(Component.text("Spawning Supremus at your location...", NamedTextColor.GOLD));
                    BossListener.spawnSupremusDirect(p.getLocation());
                } else {
                    sender.sendMessage(Component.text("BossListener not initialized!", NamedTextColor.RED));
                }
                break;
            case "warlord":
                BossListener.spawnWarlordBoss(p.getLocation());
                sender.sendMessage(Component.text("Warlord Boss spawned at your location!", NamedTextColor.GREEN));
                break;
            default:
                sender.sendMessage(Component.text("Unknown boss type. Options: supremus, warlord", NamedTextColor.RED));
                break;
        }
    }

    // ===========================
    //   WARLORD COMMAND
    // ===========================
    private void handleWarlord(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Must be run by player.", NamedTextColor.RED));
            return;
        }
        Player p = (Player) sender;

        if (args.length >= 2 && args[1].equalsIgnoreCase("event")) {
            // Start the full warlord event with spawners + countdown
            World targetWorld = p.getWorld();
            if (args.length >= 3) {
                World named = Bukkit.getWorld(args[2]);
                if (named != null) targetWorld = named;
            }
            BossListener.getInstance().startWarlordEvent(targetWorld);
            sender.sendMessage(Component.text("Warlord Event started in " + targetWorld.getName() + "!", NamedTextColor.GREEN));
        } else {
            // Direct spawn
            BossListener.spawnWarlordBoss(p.getLocation());
            sender.sendMessage(Component.text("Warlord Boss spawned!", NamedTextColor.GREEN));
        }
    }

    // ===========================
    //   KILL ALL
    // ===========================
    private void handleKillAll(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /admin killall <bosses|mobs|all|hostile>", NamedTextColor.RED));
            return;
        }

        World world;
        if (sender instanceof Player) {
            world = ((Player) sender).getWorld();
        } else {
            world = Bukkit.getWorlds().get(0);
        }

        String type = args[1].toLowerCase();
        int count = 0;

        switch (type) {
            case "bosses":
                // Reset the entire ritual/boss system
                BossListener.getInstance().resetRitualSystem();
                sender.sendMessage(Component.text("All bosses, bars, and ritual entities cleared!", NamedTextColor.GREEN));
                return;
            case "mobs":
                // Kill all non-player living entities with custom names (wave mobs, sentinels, etc.)
                for (Entity e : world.getEntities()) {
                    if (e instanceof LivingEntity && !(e instanceof Player) && e.customName() != null) {
                        ((LivingEntity) e).setHealth(0);
                        count++;
                    }
                }
                sender.sendMessage(Component.text("Killed " + count + " named mobs in " + world.getName() + "!", NamedTextColor.GREEN));
                break;
            case "hostile":
                // Kill all hostile mobs
                for (Entity e : world.getEntities()) {
                    if (e instanceof Monster) {
                        ((LivingEntity) e).setHealth(0);
                        count++;
                    }
                }
                sender.sendMessage(Component.text("Killed " + count + " hostile mobs in " + world.getName() + "!", NamedTextColor.GREEN));
                break;
            case "all":
                // Kill all non-player living entities
                for (Entity e : world.getEntities()) {
                    if (e instanceof LivingEntity && !(e instanceof Player)) {
                        ((LivingEntity) e).setHealth(0);
                        count++;
                    }
                }
                // Also reset bosses
                BossListener.getInstance().resetRitualSystem();
                sender.sendMessage(Component.text("Killed " + count + " entities and cleared boss state in " + world.getName() + "!", NamedTextColor.GREEN));
                break;
            default:
                sender.sendMessage(Component.text("Unknown type. Options: bosses, mobs, hostile, all", NamedTextColor.RED));
                break;
        }
    }

    // ===========================
    //   HEAL
    // ===========================
    private void handleHeal(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            // Heal specific player
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target != null) {
                healPlayer(target);
                sender.sendMessage(Component.text("Healed " + target.getName() + "!", NamedTextColor.GREEN));
            } else if (args[1].equalsIgnoreCase("all")) {
                int count = 0;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    healPlayer(p);
                    count++;
                }
                sender.sendMessage(Component.text("Healed " + count + " players!", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            }
        } else if (sender instanceof Player) {
            healPlayer((Player) sender);
            sender.sendMessage(Component.text("Healed!", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Usage: /admin heal [player|all]", NamedTextColor.RED));
        }
    }

    private void healPlayer(Player p) {
        if (p.getAttribute(Attribute.MAX_HEALTH) != null) {
            p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
        }
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 2));
    }

    // ===========================
    //   TELEPORT
    // ===========================
    private void handleTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Must be run by player.", NamedTextColor.RED));
            return;
        }
        Player p = (Player) sender;

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /admin tp <nether|overworld|end|spawn>", NamedTextColor.RED));
            return;
        }

        String dest = args[1].toLowerCase();
        World targetWorld = null;

        switch (dest) {
            case "nether":
                targetWorld = Bukkit.getWorld("world_nether");
                if (targetWorld == null) {
                    targetWorld = Bukkit.getWorlds().stream()
                            .filter(w -> w.getEnvironment() == World.Environment.NETHER)
                            .findFirst().orElse(null);
                }
                break;
            case "overworld":
            case "world":
                targetWorld = Bukkit.getWorld("world");
                if (targetWorld == null) {
                    targetWorld = Bukkit.getWorlds().stream()
                            .filter(w -> w.getEnvironment() == World.Environment.NORMAL)
                            .findFirst().orElse(null);
                }
                break;
            case "end":
                targetWorld = Bukkit.getWorld("world_the_end");
                if (targetWorld == null) {
                    targetWorld = Bukkit.getWorlds().stream()
                            .filter(w -> w.getEnvironment() == World.Environment.THE_END)
                            .findFirst().orElse(null);
                }
                break;
            case "spawn":
                p.teleport(p.getWorld().getSpawnLocation());
                sender.sendMessage(Component.text("Teleported to spawn!", NamedTextColor.GREEN));
                return;
            default:
                // Try to find world by name
                targetWorld = Bukkit.getWorld(args[1]);
                break;
        }

        if (targetWorld != null) {
            p.teleport(targetWorld.getSpawnLocation());
            sender.sendMessage(Component.text("Teleported to " + targetWorld.getName() + "!", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Could not find world: " + args[1], NamedTextColor.RED));
        }
    }

    // ===========================
    //   STATUS
    // ===========================
    private void handleStatus(CommandSender sender) {
        sender.sendMessage(Component.text("═══════ SkillsBoss Status ═══════", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

        // Progression Level
        int level = SkillsBoss.getProgressionLevel();
        NamedTextColor levelColor;
        switch (level) {
            case -1: levelColor = NamedTextColor.GRAY; break;
            case 0: levelColor = NamedTextColor.WHITE; break;
            case 1: levelColor = NamedTextColor.GREEN; break;
            case 2: levelColor = NamedTextColor.RED; break;
            default: levelColor = NamedTextColor.YELLOW; break;
        }
        sender.sendMessage(Component.text("  Progression Level: ", NamedTextColor.YELLOW)
                .append(Component.text(String.valueOf(level), levelColor).decorate(TextDecoration.BOLD)));

        // Online Players
        sender.sendMessage(Component.text("  Players Online: ", NamedTextColor.YELLOW)
                .append(Component.text(String.valueOf(Bukkit.getOnlinePlayers().size()), NamedTextColor.AQUA)));

        // World Info
        if (sender instanceof Player) {
            Player p = (Player) sender;
            World w = p.getWorld();
            sender.sendMessage(Component.text("  Current World: ", NamedTextColor.YELLOW)
                    .append(Component.text(w.getName() + " (" + w.getEnvironment().name() + ")", NamedTextColor.AQUA)));
            sender.sendMessage(Component.text("  World Border: ", NamedTextColor.YELLOW)
                    .append(Component.text(String.format("%.0f blocks", w.getWorldBorder().getSize()), NamedTextColor.AQUA)));

            // Count nearby entities
            int hostileCount = 0, bossCount = 0, namedCount = 0;
            for (Entity e : w.getEntities()) {
                if (e instanceof Monster) hostileCount++;
                if (e.customName() != null && e instanceof LivingEntity && !(e instanceof Player)) namedCount++;
            }
            sender.sendMessage(Component.text("  Hostile Mobs: ", NamedTextColor.YELLOW)
                    .append(Component.text(String.valueOf(hostileCount), NamedTextColor.RED)));
            sender.sendMessage(Component.text("  Named Entities: ", NamedTextColor.YELLOW)
                    .append(Component.text(String.valueOf(namedCount), NamedTextColor.LIGHT_PURPLE)));
        }

        // Warlord Event Status
        BossListener bl = BossListener.getInstance();
        if (bl != null) {
            int spawnerCount = bl.warlordSpawners.size();
            boolean countdown = bl.warlordCountdownActive;
            if (spawnerCount > 0 || countdown) {
                sender.sendMessage(Component.text("  Warlord Event: ", NamedTextColor.YELLOW)
                        .append(Component.text("ACTIVE", NamedTextColor.RED).decorate(TextDecoration.BOLD)));
                sender.sendMessage(Component.text("    Spawners Remaining: ", NamedTextColor.GRAY)
                        .append(Component.text(String.valueOf(spawnerCount), NamedTextColor.RED)));
                sender.sendMessage(Component.text("    Countdown Active: ", NamedTextColor.GRAY)
                        .append(Component.text(countdown ? "Yes" : "No", countdown ? NamedTextColor.GREEN : NamedTextColor.RED)));
            } else {
                sender.sendMessage(Component.text("  Warlord Event: ", NamedTextColor.YELLOW)
                        .append(Component.text("Inactive", NamedTextColor.GRAY)));
            }
        }

        sender.sendMessage(Component.text("═════════════════════════════════", NamedTextColor.GOLD));
    }

    // ===========================
    //   WORLD BORDER
    // ===========================
    private void handleWorldBorder(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Must be run by player.", NamedTextColor.RED));
            return;
        }
        Player p = (Player) sender;

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /admin wb <size|reset|center>", NamedTextColor.RED));
            return;
        }

        if (args[1].equalsIgnoreCase("reset")) {
            p.getWorld().getWorldBorder().reset();
            sender.sendMessage(Component.text("World border reset!", NamedTextColor.GREEN));
        } else if (args[1].equalsIgnoreCase("center")) {
            p.getWorld().getWorldBorder().setCenter(p.getLocation());
            sender.sendMessage(Component.text("World border centered on you!", NamedTextColor.GREEN));
        } else {
            try {
                double size = Double.parseDouble(args[1]);
                p.getWorld().getWorldBorder().setCenter(p.getWorld().getSpawnLocation());
                p.getWorld().getWorldBorder().setSize(size);
                sender.sendMessage(Component.text("World border set to " + size + " blocks!", NamedTextColor.GREEN));
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid size. Usage: /admin wb <number|reset|center>", NamedTextColor.RED));
            }
        }
    }

    // ===========================
    //   GOD MODE
    // ===========================
    private void handleGodMode(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Must be run by player.", NamedTextColor.RED));
            return;
        }
        Player p = (Player) sender;
        boolean isInvulnerable = p.isInvulnerable();
        p.setInvulnerable(!isInvulnerable);

        if (!isInvulnerable) {
            p.sendMessage(Component.text("God Mode: ", NamedTextColor.YELLOW)
                    .append(Component.text("ENABLED", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 0, true, false));
        } else {
            p.sendMessage(Component.text("God Mode: ", NamedTextColor.YELLOW)
                    .append(Component.text("DISABLED", NamedTextColor.RED).decorate(TextDecoration.BOLD)));
            p.removePotionEffect(PotionEffectType.SATURATION);
        }
    }

    // ===========================
    //   TIME
    // ===========================
    private void handleTime(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Must be run by player.", NamedTextColor.RED));
            return;
        }
        Player p = (Player) sender;

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /admin time <day|night|noon|midnight|<ticks>>", NamedTextColor.RED));
            return;
        }

        String t = args[1].toLowerCase();
        switch (t) {
            case "day":
                p.getWorld().setTime(1000);
                break;
            case "noon":
                p.getWorld().setTime(6000);
                break;
            case "night":
                p.getWorld().setTime(13000);
                break;
            case "midnight":
                p.getWorld().setTime(18000);
                break;
            default:
                try {
                    long ticks = Long.parseLong(t);
                    p.getWorld().setTime(ticks);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Invalid time. Options: day, noon, night, midnight, or a tick value.", NamedTextColor.RED));
                    return;
                }
                break;
        }
        sender.sendMessage(Component.text("Time set to " + t + "!", NamedTextColor.GREEN));
    }

    // ===========================
    //   HELP
    // ===========================
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("═══════ SkillsBoss Admin ═══════", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

        sendHelpLine(sender, "/admin progression <0|1|2|reset>", "Start/reset progression stages");
        sendHelpLine(sender, "/admin setprog <level>", "Immediately set progression level");
        sendHelpLine(sender, "/admin test progression <level>", "Set progression + teleport to dimension");
        sendHelpLine(sender, "/admin altar reset", "Clear all ritual entities and bars");
        sendHelpLine(sender, "/admin boss <supremus|warlord>", "Spawn a boss at your location");
        sendHelpLine(sender, "/admin warlord [event]", "Spawn warlord or start full event");
        sendHelpLine(sender, "/admin trader", "Spawn a Piglin Trader");
        sendHelpLine(sender, "/admin killall <bosses|mobs|hostile|all>", "Kill entities by category");
        sendHelpLine(sender, "/admin heal [player|all]", "Heal yourself, a player, or everyone");
        sendHelpLine(sender, "/admin tp <nether|overworld|end|spawn>", "Teleport to a dimension or spawn");
        sendHelpLine(sender, "/admin give <item>", "Give items (diamondarmor, netheritearmor, wavespawn, portal, prog1, turn, food, allitems)");
        sendHelpLine(sender, "/admin status", "Show game state and active events");
        sendHelpLine(sender, "/admin wb <size|reset|center>", "Adjust world border");
        sendHelpLine(sender, "/admin god", "Toggle god mode (invulnerable + saturation)");
        sendHelpLine(sender, "/admin time <day|night|noon|midnight>", "Set world time");
        sendHelpLine(sender, "/admin reload", "Reload plugin config");
        sendHelpLine(sender, "/admin version", "Show plugin version");

        sender.sendMessage(Component.text("════════════════════════════════", NamedTextColor.GOLD));
    }

    private void sendHelpLine(CommandSender sender, String cmd, String desc) {
        sender.sendMessage(Component.text(cmd + " ", NamedTextColor.YELLOW)
                .append(Component.text("- " + desc, NamedTextColor.GRAY)));
    }

    // ===========================
    //   TAB COMPLETION
    // ===========================
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.isOp()) return Collections.emptyList();

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList(
                    "progression", "setprog", "test", "altar", "boss", "warlord",
                    "trader", "killall", "heal", "tp", "give", "status",
                    "wb", "god", "time", "reload", "version"
            ));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "progression":
                    completions.addAll(Arrays.asList("0", "1", "2", "reset"));
                    break;
                case "test":
                    completions.add("progression");
                    break;
                case "altar":
                    completions.add("reset");
                    break;
                case "boss":
                    completions.addAll(Arrays.asList("supremus", "warlord"));
                    break;
                case "warlord":
                    completions.add("event");
                    break;
                case "killall":
                    completions.addAll(Arrays.asList("bosses", "mobs", "hostile", "all"));
                    break;
                case "heal":
                    completions.add("all");
                    Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
                    break;
                case "tp":
                    completions.addAll(Arrays.asList("nether", "overworld", "end", "spawn"));
                    break;
                case "give":
                    completions.addAll(Arrays.asList(
                            "diamondarmor", "netheritearmor", "wavespawn", "portal",
                            "prog1", "turn", "traderegg", "traderspawner", "food", "allitems"
                    ));
                    break;
                case "wb":
                case "worldborder":
                    completions.addAll(Arrays.asList("reset", "center", "19", "100", "500", "1000"));
                    break;
                case "time":
                    completions.addAll(Arrays.asList("day", "noon", "night", "midnight"));
                    break;
                case "setprog":
                    completions.addAll(Arrays.asList("-1", "0", "1", "2"));
                    break;
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("test") && args[1].equalsIgnoreCase("progression")) {
                completions.addAll(Arrays.asList("0", "1", "2"));
            }
        }

        // Filter by what the user has typed
        String input = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(input))
                .sorted()
                .collect(Collectors.toList());
    }
}
