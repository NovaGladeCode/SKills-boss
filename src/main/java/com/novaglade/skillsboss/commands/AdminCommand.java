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
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

public class AdminCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {

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
            player.getWorld().getWorldBorder().setSize(10);

            for (Player online : Bukkit.getOnlinePlayers()) {
                online.teleport(player.getLocation());
            }

            sender.sendMessage(Component.text("Progression 0 set: Spawn updated, All players TPed, Border set to 10.",
                    NamedTextColor.GREEN));
        } else if (level == 1) {
            startProgressionOneCountdown(player);
        } else {
            sender.sendMessage(Component.text("Unknown progression level.", NamedTextColor.RED));
        }
    }

    private void startProgressionOneCountdown(Player starter) {
        Location center = starter.getLocation();

        new BukkitRunnable() {
            int ticks = 5 * 20; // 5 seconds in ticks

            @Override
            public void run() {
                int seconds = (int) Math.ceil(ticks / 20.0);

                // Once per second: Show Title & Play Sound
                if (ticks % 20 == 0) {
                    Component mainTitle = Component.text(String.valueOf(seconds), NamedTextColor.RED,
                            TextDecoration.BOLD);
                    Component subTitle = Component.text("Starting Progression I: A New Beginning...",
                            NamedTextColor.YELLOW);
                    Title title = Title.title(mainTitle, subTitle,
                            Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(800), Duration.ofMillis(100)));

                    for (Player online : Bukkit.getOnlinePlayers()) {
                        online.showTitle(title);
                        online.playSound(online.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f,
                                0.5f + (5 - seconds) * 0.2f);
                    }
                }

                // Every tick: Animation & Shake
                double radius = 5.0 - (ticks / 100.0 * 5.0); // Ring closes in
                for (int i = 0; i < 3; i++) {
                    double angle = (ticks * 0.5 + i * (2 * Math.PI / 3));
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    center.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, center.clone().add(x, 0.5, z), 1, 0, 0, 0,
                            0);
                    center.getWorld().spawnParticle(Particle.DRAGON_BREATH, center.clone().add(x, 1.5, z), 1, 0, 0, 0,
                            0);
                }

                // Shake effect for players near or online
                for (Player online : Bukkit.getOnlinePlayers()) {
                    // Apply brief nausea to simulate shake
                    online.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 40, 0, false, false, false));

                    // Spawn vibration particles around player head
                    online.spawnParticle(Particle.CRIT, online.getEyeLocation(), 5, 0.5, 0.5, 0.5, 0.1);
                }

                if (ticks <= 0) {
                    SkillsBoss.setProgressionLevel(1);
                    Component startMsg = Component.text("PROGRESSION I: A NEW BEGINNING", NamedTextColor.GREEN,
                            TextDecoration.BOLD);

                    // Final impact effects
                    center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 10, 2, 2, 2, 0.1);
                    center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2f, 1f);
                    center.getWorld().playSound(center, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2f, 1f);

                    // Set world border to 500
                    center.getWorld().getWorldBorder().setSize(500);

                    for (Player online : Bukkit.getOnlinePlayers()) {
                        online.sendMessage(startMsg);
                        online.showTitle(Title.title(startMsg, Component.empty()));
                        online.removePotionEffect(PotionEffectType.NAUSEA);
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
