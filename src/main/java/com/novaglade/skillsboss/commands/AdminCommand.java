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

                // Every tick: Area Animation
                double progress = 1.0 - (ticks / 100.0);
                double radius = 6.0 * (1.0 - progress);

                // Swirling energy beams
                for (int i = 0; i < 4; i++) {
                    double angle = (ticks * 0.4 + i * (Math.PI / 2));
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;

                    // Main swirling ring
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, center.clone().add(x, 0.5, z), 3, 0.1,
                            0.1, 0.1, 0.02);
                    world.spawnParticle(Particle.DRAGON_BREATH,
                            center.clone().add(x, 0.5 + progress * 4, z), 3, 0.1, 0.1, 0.1, 0.02);

                    // Rising pillars that close in
                    world.spawnParticle(Particle.END_ROD, center.clone().add(x, progress * 15, z), 2, 0, 0,
                            0, 0.01);
                }

                // Static Sky Beams (Outer perimeter)
                for (int i = 0; i < 8; i++) {
                    double angle = i * (Math.PI / 4);
                    double bx = Math.cos(angle) * 7.0;
                    double bz = Math.sin(angle) * 7.0;
                    Location beamLoc = center.clone().add(bx, 0, bz);

                    // Particle Beams shooting up
                    for (int h = 0; h < 20; h++) {
                        if (ticks % 5 == 0) {
                            world.spawnParticle(Particle.SOUL, beamLoc.clone().add(0, h, 0), 1, 0, 0, 0, 0.02);
                        }
                    }
                }

                // Ground Tremors
                if (ticks % 2 == 0) {
                    world.spawnParticle(Particle.BLOCK, center, 15, radius + 1, 0.1, radius + 1, 0.05,
                            Material.GRASS_BLOCK.createBlockData());
                }

                // Localized Focus: Shake players
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!online.getWorld().equals(world))
                        continue;
                    double distance = online.getLocation().distance(center);
                    if (distance <= 30.0) {
                        online.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 40, 0, false, false, false));
                        online.spawnParticle(Particle.CRIT, online.getEyeLocation(), 12, 0.3, 0.3, 0.3, 0.1);
                        online.playSound(online.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f,
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

                    Title.Times times = Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(4),
                            Duration.ofSeconds(2));
                    Title finalTitle = Title.title(mainTitle, subTitle, times);

                    // Impact Explosion Beams
                    for (int i = 0; i < 16; i++) {
                        double angle = i * (Math.PI / 8);
                        double x = Math.cos(angle) * 2;
                        double z = Math.sin(angle) * 2;
                        for (int h = 0; h < 50; h++) {
                            world.spawnParticle(Particle.FLASH, center.clone().add(x, h, z), 1, 0, 0, 0, 0);
                        }
                    }

                    world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 20, 2, 2, 2, 0.1);
                    world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2f, 1f);
                    world.playSound(center, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2f, 1f);

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
