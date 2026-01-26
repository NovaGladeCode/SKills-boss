package com.novaglade.skillsboss.commands;

import com.novaglade.skillsboss.SkillsBoss;
import com.novaglade.skillsboss.items.ItemManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.Random;

public class AdminCommand implements CommandExecutor {

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
                    sender.sendMessage(Component.text("Usage: /admin progression <0|1>", NamedTextColor.RED));
                    return true;
                }
                try {
                    int level = Integer.parseInt(args[1]);
                    if (level == 0) {
                        if (sender instanceof Player) {
                            startProgressionZeroTransition((Player) sender);
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
                    } else {
                        sender.sendMessage(Component.text("Invalid level.", NamedTextColor.RED));
                    }
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
        World world = player.getWorld();
        Location center = player.getLocation();

        SkillsBoss.setProgressionLevel(0);
        world.setSpawnLocation(center);
        world.getWorldBorder().setCenter(center);
        world.getWorldBorder().setSize(19);

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.teleport(center);
            online.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
            online.spawnParticle(Particle.EXPLOSION_EMITTER, center, 20, 2, 2, 2, 0);

            Title startTitle = Title.title(
                    Component.text("PROGRESSION 0", NamedTextColor.WHITE).decorate(TextDecoration.BOLD),
                    Component.text("A NEW BEGINNING", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofSeconds(1)));
            online.showTitle(startTitle);
        }

        player.sendMessage(
                Component.text("World has been reset to Progression 0.", NamedTextColor.GREEN)
                        .decorate(TextDecoration.BOLD));
    }

    private static void startProgressionOneCountdown(org.bukkit.World world) {
        startProgression1At(world, null);
    }

    public static void startProgression1At(org.bukkit.World world, Location customCenter) {
        Location center = customCenter != null ? customCenter : world.getSpawnLocation();
        Random random = new Random();

        new BukkitRunnable() {
            int maxTicks = 15 * 20; // 15 seconds (made it longer for more epicness)
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
            sender.sendMessage(Component.text("Usage: /admin give <diamondarmor|waveboss|portal>", NamedTextColor.RED));
            return;
        }

        if (args[1].equalsIgnoreCase("diamondarmor")) {
            player.getInventory().addItem(ItemManager.createCustomItem(Material.DIAMOND_HELMET));
            player.getInventory().addItem(ItemManager.createCustomItem(Material.DIAMOND_CHESTPLATE));
            player.getInventory().addItem(ItemManager.createCustomItem(Material.DIAMOND_LEGGINGS));
            player.getInventory().addItem(ItemManager.createCustomItem(Material.DIAMOND_BOOTS));
            player.getInventory().addItem(ItemManager.createCustomItem(Material.DIAMOND_SWORD));
            sender.sendMessage(Component.text("Received custom diamond armor and sword!", NamedTextColor.GREEN));
        } else if (args[1].equalsIgnoreCase("bossspawn1") || args[1].equalsIgnoreCase("waveboss")
                || args[1].equalsIgnoreCase("wavespawn")) {
            player.getInventory().addItem(ItemManager.createBossSpawnItem());
            player.getInventory().addItem(ItemManager.createBossSpawnerItem());
            sender.sendMessage(Component.text("Received Ritual Core and Ritual Spawner!", NamedTextColor.LIGHT_PURPLE));
        } else if (args[1].equalsIgnoreCase("portal")) {
            player.getInventory().addItem(ItemManager.createPortalObsidian());
            sender.sendMessage(Component.text("Received Portal Obsidian!", NamedTextColor.DARK_PURPLE));
        } else if (args[1].equalsIgnoreCase("prog1")) {
            player.getInventory().addItem(ItemManager.createProgression1Item());
            sender.sendMessage(Component.text("Received Progression 1 Catalyst!", NamedTextColor.GOLD));
        } else {
            sender.sendMessage(Component.text("Unknown item.", NamedTextColor.RED));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(
                Component.text("--- SkillsBoss Admin ---", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.text("/admin progression <0|1> ", NamedTextColor.YELLOW)
                .append(Component.text("- Start progression stages", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/admin altar reset ", NamedTextColor.YELLOW)
                .append(Component.text("- Clear all ritual entities and bars", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/admin give <diamondarmor|wavespawn|portal|prog1> ", NamedTextColor.YELLOW)
                .append(Component.text("- Give legendary gear, ritual items, portal obsidian, or progression catalyst",
                        NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/admin reload ", NamedTextColor.YELLOW)
                .append(Component.text("- Reload plugin config", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/admin version ", NamedTextColor.YELLOW)
                .append(Component.text("- Show plugin version", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("------------------------", NamedTextColor.GOLD));
    }
}
