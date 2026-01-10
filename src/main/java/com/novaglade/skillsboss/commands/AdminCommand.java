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
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;

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
                handleProgression(sender, args);
                break;
            case "give":
                handleGive(sender, args);
                break;
            case "reset":
                handleReset(sender);
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
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }
        Player player = (Player) sender;

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
            int seconds = 10;
            int subticks = 0;

            @Override
            public void run() {
                try {
                    if (seconds > 0) {
                        // Show title at the start of each second
                        if (subticks == 0) {
                            Component mainTitle = Component.text(String.valueOf(seconds), NamedTextColor.RED,
                                    TextDecoration.BOLD);
                            Component subTitle = Component.text("A New Beginning Approaches...",
                                    NamedTextColor.YELLOW);
                            Title title = Title.title(mainTitle, subTitle,
                                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(700),
                                            Duration.ofMillis(100)));

                            for (Player online : Bukkit.getOnlinePlayers()) {
                                if (online.getWorld().equals(world)) {
                                    online.showTitle(title);
                                    online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.5f,
                                            0.5f + (seconds * 0.1f));
                                }
                            }
                        }

                        // Epic particle effects every few ticks
                        if (subticks % 3 == 0) {
                            double progress = (10 - seconds + (subticks / 20.0)) / 10.0;

                            // Double helix spiral
                            for (int h = 0; h < 2; h++) {
                                double helixAngle = (subticks * 0.3) + (h * Math.PI);
                                double radius = 3 - (progress * 2);
                                double height = progress * 6;
                                double x = Math.cos(helixAngle) * radius;
                                double z = Math.sin(helixAngle) * radius;

                                world.spawnParticle(Particle.SOUL_FIRE_FLAME, center.clone().add(x, height, z), 2, 0.1,
                                        0.1, 0.1, 0);
                                world.spawnParticle(Particle.FLAME, center.clone().add(-x, height, -z), 2, 0.1, 0.1,
                                        0.1, 0);
                            }

                            // Ground ring pulses
                            double ringRadius = 2 + Math.sin(subticks * 0.2) * 0.5;
                            for (int i = 0; i < 16; i++) {
                                double angle = (i * 2 * Math.PI / 16);
                                double x = Math.cos(angle) * ringRadius;
                                double z = Math.sin(angle) * ringRadius;
                                world.spawnParticle(Particle.DRAGON_BREATH, center.clone().add(x, 0.1, z), 1, 0, 0, 0,
                                        0);
                            }
                        }

                        // Major pulse every second
                        if (subticks == 0) {
                            for (int i = 0; i < 40; i++) {
                                double angle = (i * 2 * Math.PI / 40);
                                double x = Math.cos(angle) * 5;
                                double z = Math.sin(angle) * 5;
                                world.spawnParticle(Particle.ENCHANT, center.clone().add(x, 0.5, z), 5, 0.2, 0.5, 0.2,
                                        0);
                            }
                            world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f,
                                    1.0f + (0.1f * (10 - seconds)));
                        }

                        subticks++;
                        if (subticks >= 20) {
                            subticks = 0;
                            seconds--;
                        }
                    } else {
                        // EPIC FINALE
                        SkillsBoss.setProgressionLevel(1);
                        Component mainTitle = Component.text("PROGRESSION I", NamedTextColor.GREEN,
                                TextDecoration.BOLD);
                        Component subTitle = Component.text("A NEW BEGINNING", NamedTextColor.DARK_AQUA,
                                TextDecoration.BOLD);
                        Title finalTitle = Title.title(mainTitle, subTitle,
                                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3),
                                        Duration.ofSeconds(1)));

                        // Multi-layered explosion
                        new BukkitRunnable() {
                            int explosionTick = 0;

                            @Override
                            public void run() {
                                if (explosionTick == 0) {
                                    // Initial massive burst
                                    for (int r = 0; r < 15; r++) {
                                        world.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 60, r * 0.8, 1, r * 0.8,
                                                0.2);
                                        world.spawnParticle(Particle.FLAME, center, 60, r * 0.8, 1, r * 0.8, 0.2);
                                    }
                                    world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 30, 4, 2, 4, 0);

                                    world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 4.0f, 0.4f);
                                    world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3.0f, 1.2f);
                                } else if (explosionTick == 5) {
                                    // Expanding ring shockwave
                                    for (int ring = 0; ring < 3; ring++) {
                                        for (int i = 0; i < 60; i++) {
                                            double angle = (i * 2 * Math.PI / 60);
                                            double radius = 6 + (ring * 3);
                                            double x = Math.cos(angle) * radius;
                                            double z = Math.sin(angle) * radius;
                                            world.spawnParticle(Particle.CRIT, center.clone().add(x, 0.1, z), 8, 0.3,
                                                    0.3, 0.3, 0);
                                            world.spawnParticle(Particle.END_ROD, center.clone().add(x, 0.1, z), 3, 0,
                                                    0.5, 0, 0.05);
                                        }
                                    }

                                    world.playSound(center, Sound.ENTITY_WITHER_DEATH, 3.5f, 0.4f);
                                    world.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.5f, 0.5f);
                                } else if (explosionTick == 10) {
                                    // Rising pillars
                                    for (int pillar = 0; pillar < 8; pillar++) {
                                        double angle = (pillar * 2 * Math.PI / 8);
                                        double x = Math.cos(angle) * 4;
                                        double z = Math.sin(angle) * 4;
                                        for (int h = 0; h < 10; h++) {
                                            world.spawnParticle(Particle.TOTEM_OF_UNDYING,
                                                    center.clone().add(x, h * 0.5, z), 3, 0.2, 0.2, 0.2, 0);
                                        }
                                    }

                                    world.playSound(center, Sound.UI_TOAST_CHALLENGE_COMPLETE, 3.0f, 1.0f);
                                    world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 1.5f);
                                } else if (explosionTick >= 15) {
                                    cancel();
                                }
                                explosionTick++;
                            }
                        }.runTaskTimer(SkillsBoss.getInstance(), 0, 2);

                        world.getWorldBorder().setCenter(center);
                        world.getWorldBorder().setSize(750);

                        for (Player online : Bukkit.getOnlinePlayers()) {
                            if (online.getWorld().equals(world)) {
                                online.sendMessage(Component
                                        .text("PROGRESSION I: ", NamedTextColor.GREEN, TextDecoration.BOLD)
                                        .append(Component
                                                .text("A New Beginning", NamedTextColor.DARK_AQUA,
                                                        TextDecoration.BOLD)));
                                online.showTitle(finalTitle);
                            }
                        }
                        cancel();
                    }
                } catch (Exception e) {
                    Bukkit.getLogger().severe("[SkillsBoss] Error in Progression I countdown: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskTimer(SkillsBoss.getInstance(), 0, 1);
    }

    private void handleReset(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return;
        }
        Player player = (Player) sender;

        SkillsBoss.setProgressionLevel(0);
        player.getWorld().setSpawnLocation(player.getLocation());
        player.getWorld().getWorldBorder().setCenter(player.getLocation());
        player.getWorld().getWorldBorder().setSize(19);

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.teleport(player.getLocation());
        }

        sender.sendMessage(
                Component.text("World reset! Progression set to 0, all players teleported to spawn, border set to 19.",
                        NamedTextColor.GREEN));
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
        } else if (args[1].equalsIgnoreCase("bossspawn1") || args[1].equalsIgnoreCase("waveboss")) {
            player.getInventory().addItem(ItemManager.createBossSpawnItem());
            sender.sendMessage(Component.text("Received Boss Spawn Altar!", NamedTextColor.LIGHT_PURPLE));
        } else if (args[1].equalsIgnoreCase("portal")) {
            player.getInventory().addItem(ItemManager.createPortalObsidian());
            sender.sendMessage(Component.text("Received 16x Portal Obsidian!", NamedTextColor.DARK_PURPLE));
        } else {
            sender.sendMessage(Component.text("Unknown item.", NamedTextColor.RED));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(
                Component.text("--- SkillsBoss Admin ---", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.text("/admin progression <0|1> ", NamedTextColor.YELLOW)
                .append(Component.text("- Start progression stages", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/admin give <diamondarmor|waveboss|portal> ", NamedTextColor.YELLOW)
                .append(Component.text("- Give legendary gear, boss altar, or portal obsidian", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/admin reset ", NamedTextColor.YELLOW)
                .append(Component.text("- Reset world: TP all to spawn, set progression to 0", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/admin reload ", NamedTextColor.YELLOW)
                .append(Component.text("- Reload plugin config", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/admin version ", NamedTextColor.YELLOW)
                .append(Component.text("- Show plugin version", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("------------------------", NamedTextColor.GOLD));
    }
}
