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

                // Every tick: HYPER-INTENSE Ground Ritual (Dense but structured)
                double progress = 1.0 - (ticks / 100.0);

                // 1. Triple-Layered Counter-Rotating Rings
                for (int layer = 1; layer <= 3; layer++) {
                    double rSize = (3.0 * layer) * (layer == 1 ? 1.0 - progress : 1.0);
                    double speed = (layer % 2 == 0 ? 0.3 : -0.3);
                    int density = 24 * layer;

                    for (int i = 0; i < density; i++) {
                        double angle = (ticks * speed + i * (2 * Math.PI / density));
                        double x = Math.cos(angle) * rSize;
                        double z = Math.sin(angle) * rSize;

                        Particle p = (layer == 1 ? Particle.WITCH : (layer == 2 ? Particle.SOUL : Particle.END_ROD));
                        world.spawnParticle(p, center.clone().add(x, 0.1, z), 1, 0, 0, 0, 0);

                        // Vertical "Energy Walls" for the rings
                        if (ticks % 10 == 0) {
                            world.spawnParticle(Particle.ENCHANT, center.clone().add(x, progress * 2.5, z), 1, 0, 0, 0,
                                    0.05);
                        }
                    }
                }

                // 2. Electrical Arcs between Ritual Points
                if (ticks % 3 == 0) {
                    for (int i = 0; i < 4; i++) {
                        double angle = (ticks * 0.05 + i * (Math.PI / 2));
                        double r = 6.0;
                        Location p1 = center.clone().add(Math.cos(angle) * r, 0.1, Math.sin(angle) * r);
                        Location p2 = center.clone().add(Math.cos(angle + 0.5) * r, 0.1, Math.sin(angle + 0.5) * r);

                        for (double d = 0; d < 1.0; d += 0.15) {
                            Location arc = p1.clone().add(p2.clone().subtract(p1).toVector().multiply(d));
                            world.spawnParticle(Particle.ELECTRIC_SPARK, arc.add(0, Math.sin(d * Math.PI) * 0.5, 0), 1,
                                    0, 0.1, 0, 0.01);
                        }
                    }
                }

                // 3. Vertical Soul Vortex (Inward pull & Rising energy)
                for (int i = 0; i < 6; i++) {
                    double angle = (ticks * 0.8 + i * (Math.PI * (2.0 / 6.0)));
                    double vRadius = 10.0 * (1.0 - progress);
                    double vx = Math.cos(angle) * vRadius;
                    double vz = Math.sin(angle) * vRadius;
                    double vy = Math.sin(ticks * 0.1 + i) + 1.2;

                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, center.clone().add(vx, vy, vz), 1, 0.1, 0.1, 0.1,
                            0.02);
                    world.spawnParticle(Particle.DRAGON_BREATH, center.clone().add(vx * 0.7, vy * 0.8, vz * 0.7), 1, 0,
                            0, 0, 0.01);
                }

                // 4. Ground Rune Pulse
                if (ticks % 10 == 0) {
                    world.spawnParticle(Particle.GLOW, center, 60, 6, 0.1, 6, 0.05);
                }

                // Localized Focus
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!online.getWorld().equals(world))
                        continue;
                    double distance = online.getLocation().distance(center);
                    if (distance <= 40.0) {
                        online.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 30, 0, false, false, false));
                        online.spawnParticle(Particle.END_ROD,
                                online.getEyeLocation().add(online.getLocation().getDirection().multiply(0.4)), 3, 0.2,
                                0.2, 0.2, 0.01);
                        online.playSound(online.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f,
                                0.6f + (float) progress * 0.4f);
                    }
                }

                if (ticks <= 0) {
                    SkillsBoss.setProgressionLevel(1);

                    Component mainTitle = Component.text("PROGRESSION I", NamedTextColor.GREEN, TextDecoration.BOLD);
                    Component subTitle = Component.text("A NEW BEGINNING", NamedTextColor.DARK_AQUA,
                            TextDecoration.BOLD);
                    Component chatMsg = Component.text("PROGRESSION I: ", NamedTextColor.GREEN, TextDecoration.BOLD)
                            .append(Component.text("A New Beginning", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));

                    Title.Times times = Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(4),
                            Duration.ofSeconds(2));
                    Title finalTitle = Title.title(mainTitle, subTitle, times);

                    // Final Hyper-Shockwave (Clean spheres and rays)
                    for (int r = 0; r < 8; r++) {
                        world.spawnParticle(Particle.FLASH, center, 70, r * 2.5, 0.1, r * 2.5, 0);
                        world.spawnParticle(Particle.SONIC_BOOM, center, 8, r * 1.5, 0.1, r * 1.5, 0);
                    }

                    // Shimmering Sphere of Light
                    for (int i = 0; i < 150; i++) {
                        double phi = Math.acos(-1.0 + (2.0 * i) / 150.0);
                        double theta = Math.sqrt(150.0 * Math.PI) * phi;
                        double x = Math.cos(theta) * Math.sin(phi) * 6;
                        double y = Math.sin(theta) * Math.sin(phi) * 6;
                        double z = Math.cos(phi) * 6;
                        world.spawnParticle(Particle.END_ROD, center.clone().add(x, y + 2, z), 2, 0, 0, 0, 0.05);
                    }

                    world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.7f);
                    world.playSound(center, Sound.ENTITY_WARDEN_DEATH, 2.0f, 0.9f);
                    world.playSound(center, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2.2f, 1.0f);
                    world.spawnParticle(Particle.END_ROD, center, 100, 0.5, 0.5, 0.5, 0.2);

                    // Set world border center to spawn and size to 750
                    world.getWorldBorder().setCenter(center);
                    world.getWorldBorder().setSize(750);

                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (online.getWorld().equals(world) && online.getLocation().distance(center) <= 50.0) {
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
