package com.novaglade.skillsboss.commands;

import com.novaglade.skillsboss.SkillsBoss;
import com.novaglade.skillsboss.items.ItemManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;

public class AdminCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label,
            String[] args) {

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
                handleProgression(sender, args);
                break;
            case "give":
                handleGive(sender, args);
                break;
            case "reload":
                sender.sendMessage(Component.text("SkillsBoss configuration reloaded!", NamedTextColor.GREEN));
                break;
            case "version":
                sender.sendMessage(
                        Component.text("SkillsBoss Version: 1.0-SNAPSHOT (Paper 1.21.11)", NamedTextColor.AQUA));
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void handleProgression(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /admin progression <0|1>", NamedTextColor.RED));
            return;
        }

        int level;
        try {
            level = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid level.", NamedTextColor.RED));
            return;
        }

        if (level == 0) {
            SkillsBoss.setProgressionLevel(0);
            player.getWorld().setSpawnLocation(player.getLocation());
            player.getWorld().getWorldBorder().setCenter(player.getLocation());
            player.getWorld().getWorldBorder().setSize(19);

            for (Player online : Bukkit.getOnlinePlayers()) {
                online.teleport(player.getLocation());
            }

            sender.sendMessage(Component.text("Progression 0 set: Spawn updated, All players TPed, Border set to 19.",
                    NamedTextColor.GREEN));
        } else if (level == 1) {
            startProgressionOneCountdown(player.getWorld());
        } else {
            sender.sendMessage(Component.text("Unknown progression level.", NamedTextColor.RED));
        }
    }

    private void startProgressionOneCountdown(org.bukkit.World world) {
        Location center = world.getSpawnLocation();

        new BukkitRunnable() {
            int ticks = 5 * 20; // 5 seconds in ticks

            @Override
            public void run() {
                int seconds = (int) Math.ceil(ticks / 20.0);

                // Once per second: Show Localized Title & Play Sound
                if (ticks % 20 == 0) {
                    Component mainTitle = Component.text(String.valueOf(seconds), NamedTextColor.RED,
                            TextDecoration.BOLD);
                    Component subTitle = Component.text("Starting Progression I: A New Beginning...",
                            NamedTextColor.YELLOW);
                    Title title = Title.title(mainTitle, subTitle,
                            Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(800), Duration.ofMillis(100)));

                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (online.getWorld().equals(world) && online.getLocation().distance(center) <= 50.0) {
                            online.showTitle(title);
                            online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f,
                                    0.5f + (5 - seconds) * 0.2f);
                        }
                    }
                }

                // Every tick: GOD-TIER Ground Ritual (Maximum Density)
                double progress = 1.0 - (ticks / 100.0);

                // 1. Five Counter-Rotating Rings (Geometric Core)
                for (int layer = 1; layer <= 5; layer++) {
                    double rSize = (2.5 * layer) * (layer == 1 ? 1.0 : 1.0);
                    double speed = (layer % 2 == 0 ? 0.4 : -0.4);
                    int density = 30 * layer;

                    for (int i = 0; i < density; i++) {
                        double angle = (ticks * speed + i * (2 * Math.PI / density));
                        double x = Math.cos(angle) * rSize;
                        double z = Math.sin(angle) * rSize;

                        Particle p = (layer % 2 == 0 ? Particle.WITCH : Particle.SOUL_FIRE_FLAME);
                        world.spawnParticle(p, center.clone().add(x, 0.1, z), 2, 0.05, 0.05, 0.05, 0);

                        // Vertical Beams for every layer
                        if (ticks % 4 == 0) {
                            world.spawnParticle(Particle.END_ROD, center.clone().add(x, progress * 10, z), 1, 0, 0, 0,
                                    0.02);
                        }
                    }
                }

                // 2. Chaotic Electrical Network
                if (ticks % 2 == 0) {
                    for (int i = 0; i < 8; i++) {
                        double angle = (ticks * 0.08 + i * (Math.PI / 4));
                        double r = 12.0 * progress;
                        Location p1 = center.clone().add(Math.cos(angle) * r, 0.1, Math.sin(angle) * r);
                        Location p2 = center.clone().add(Math.cos(angle + 1.2) * (r + 2), 0.5,
                                Math.sin(angle + 1.2) * (r + 2));

                        for (double d = 0; d < 1.0; d += 0.1) {
                            Location arc = p1.clone().add(p2.clone().subtract(p1).toVector().multiply(d));
                            world.spawnParticle(Particle.ELECTRIC_SPARK, arc.add(0, Math.random() * 0.5, 0), 2, 0.05,
                                    0.05, 0.05, 0.01);
                        }
                    }
                }

                // 3. The Sky-to-Ground Funnel (Massive Vortex)
                for (int i = 0; i < 12; i++) {
                    double angle = (ticks * 0.5 + i * (Math.PI / 6));
                    double vRadius = 15.0 - (progress * 12.0);
                    double vy = (i * 1.5) + (Math.sin(ticks * 0.2) * 2);
                    double vx = Math.cos(angle) * vRadius;
                    double vz = Math.sin(angle) * vRadius;

                    world.spawnParticle(Particle.DRAGON_BREATH, center.clone().add(vx, vy, vz), 3, 0.1, 0.1, 0.1, 0.02);
                    world.spawnParticle(Particle.SOUL, center.clone().add(vx, vy, vz), 2, 0, 0, 0, 0.01);
                }

                // 4. Violent Ground Tremors (Block Dust)
                double shakeRadius = 12.0 * (1.0 - progress);
                world.spawnParticle(Particle.BLOCK, center, 40, shakeRadius + 2, 0.2, shakeRadius + 2, 0.1,
                        Material.DIRT.createBlockData());
                if (ticks % 5 == 0) {
                    world.spawnParticle(Particle.LARGE_SMOKE, center, 20, 10, 0.1, 10, 0.02);
                }

                // Localized Focus (Maximum Intensity Shake)
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!online.getWorld().equals(world))
                        continue;
                    double distance = online.getLocation().distance(center);
                    if (distance <= 50.0) {
                        // Strong Nausea and Blindness pulse
                        online.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 1, false, false, false));
                        if (ticks % 30 == 0)
                            online.addPotionEffect(
                                    new PotionEffect(PotionEffectType.BLINDNESS, 20, 0, false, false, false));

                        // violent vibration particles
                        online.spawnParticle(Particle.CRIT, online.getEyeLocation(), 20, 0.5, 0.5, 0.5, 0.2);
                        online.spawnParticle(Particle.FLASH,
                                online.getEyeLocation().add(online.getLocation().getDirection().multiply(0.2)), 1, 0, 0,
                                0, 0);

                        online.playSound(online.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f,
                                0.4f + (float) progress * 0.6f);
                        online.playSound(online.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.5f,
                                0.5f + (float) progress);
                    }
                }

                if (ticks <= 0) {
                    SkillsBoss.setProgressionLevel(1);

                    Component mainTitle = Component.text("PROGRESSION I", NamedTextColor.GREEN, TextDecoration.BOLD);
                    Component subTitle = Component.text("A NEW BEGINNING", NamedTextColor.DARK_AQUA,
                            TextDecoration.BOLD);
                    Component chatMsg = Component.text("PROGRESSION I: ", NamedTextColor.GREEN, TextDecoration.BOLD)
                            .append(Component.text("A New Beginning", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));

                    Title.Times times = Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(5),
                            Duration.ofSeconds(2));
                    Title finalTitle = Title.title(mainTitle, subTitle, times);

                    // Final OMNI-BLAST (Environmental Erasure Look)
                    for (int r = 0; r < 12; r++) {
                        world.spawnParticle(Particle.FLASH, center, 100, r * 3, 0.5, r * 3, 0);
                        world.spawnParticle(Particle.SONIC_BOOM, center, 15, r * 2, 0.5, r * 2, 0);
                        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 10, r, r, r, 0.1);
                    }

                    // Golden/Blue Shimmering Super-Sphere
                    for (int i = 0; i < 300; i++) {
                        double phi = Math.acos(-1.0 + (2.0 * i) / 300.0);
                        double theta = Math.sqrt(300.0 * Math.PI) * phi;
                        double x = Math.cos(theta) * Math.sin(phi) * 12;
                        double y = Math.sin(theta) * Math.sin(phi) * 12;
                        double z = Math.cos(phi) * 12;
                        world.spawnParticle(Particle.END_ROD, center.clone().add(x, y + 4, z), 2, 0, 0, 0, 0.05);
                        if (i % 2 == 0)
                            world.spawnParticle(Particle.SOUL_FIRE_FLAME,
                                    center.clone().add(x * 0.8, y * 0.8 + 4, z * 0.8), 1, 0, 0, 0, 0.02);
                    }

                    world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.5f);
                    world.playSound(center, Sound.ENTITY_WARDEN_DEATH, 3.0f, 0.8f);
                    world.playSound(center, Sound.ENTITY_WITHER_DEATH, 2.0f, 0.5f);
                    world.playSound(center, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2.5f, 1.0f);
                    world.spawnParticle(Particle.END_ROD, center, 500, 2, 2, 2, 0.5);

                    // Set world border center to spawn and size to 750
                    world.getWorldBorder().setCenter(center);
                    world.getWorldBorder().setSize(750);

                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (online.getWorld().equals(world) && online.getLocation().distance(center) <= 60.0) {
                            online.sendMessage(chatMsg);
                            online.showTitle(finalTitle);
                            online.removePotionEffect(PotionEffectType.NAUSEA);
                        }
                    }
                    cancel();
                }
                ticks--;
            }
        }.runTaskTimer(SkillsBoss.getInstance(), 0, 1);
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /admin give <diamondarmor>", NamedTextColor.RED));
            return;
        }

        if (args[1].equalsIgnoreCase("diamondarmor")) {
            player.getInventory().addItem(ItemManager.createCustomItem(Material.DIAMOND_HELMET));
            player.getInventory().addItem(ItemManager.createCustomItem(Material.DIAMOND_CHESTPLATE));
            player.getInventory().addItem(ItemManager.createCustomItem(Material.DIAMOND_LEGGINGS));
            player.getInventory().addItem(ItemManager.createCustomItem(Material.DIAMOND_BOOTS));
            player.getInventory().addItem(ItemManager.createCustomItem(Material.DIAMOND_SWORD));
            sender.sendMessage(Component.text("Received custom diamond armor and sword!", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Unknown item.", NamedTextColor.RED));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(
                Component.text("--- SkillsBoss Admin ---", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.text("/admin progression <0|1> ", NamedTextColor.YELLOW)
                .append(Component.text("- Start progression stages", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/admin give diamondarmor ", NamedTextColor.YELLOW)
                .append(Component.text("- Give legendary armor", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/admin reload ", NamedTextColor.YELLOW)
                .append(Component.text("- Reload plugin config", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/admin version ", NamedTextColor.YELLOW)
                .append(Component.text("- Show plugin version", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("------------------------", NamedTextColor.GOLD));
    }
}
