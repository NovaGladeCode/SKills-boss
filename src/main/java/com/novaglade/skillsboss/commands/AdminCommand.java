package com.novaglade.skillsboss.commands;

import com.novaglade.skillsboss.SkillsBoss;
import com.novaglade.skillsboss.items.ItemManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

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
                            startProgression1At(((Player) sender).getWorld(), null);
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
                    Component.text("PROGRESSION 0", NamedTextColor.WHITE, TextDecoration.BOLD),
                    Component.text("A NEW BEGINNING", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofSeconds(1)));
            online.showTitle(startTitle);
        }

        player.sendMessage(
                Component.text("World has been reset to Progression 0.", NamedTextColor.GREEN, TextDecoration.BOLD));
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

                    // Massive Rising Vortex Animation
                    double radius = 1.0 + (maxTicks - ticks) * 0.08;
                    double angle = ticks * 0.4;
                    for (int i = 0; i < 4; i++) {
                        double subAngle = angle + (i * (Math.PI / 2));
                        double x = Math.cos(subAngle) * radius;
                        double z = Math.sin(subAngle) * radius;
                        double y = (maxTicks - ticks) * 0.1;
                        if (y > 30)
                            y = 30; // Cap height

                        Location partLoc = center.clone().add(x, y, z);
                        world.spawnParticle(Particle.END_ROD, partLoc, 3, 0.1, 0.1, 0.1, 0.01);
                        world.spawnParticle(Particle.WITCH, partLoc, 2, 0.1, 0.1, 0.1, 0.01);

                        // Vertical beams
                        if (ticks % 5 == 0) {
                            world.spawnParticle(Particle.SOUL_FIRE_FLAME, center.clone().add(0, y, 0), 10, 0.5, 1, 0.5,
                                    0.05);
                        }
                    }

                    if (ticks % 20 == 0 && seconds > 0) {
                        Component mainTitle = Component.text(String.valueOf(seconds), NamedTextColor.GOLD,
                                TextDecoration.BOLD);
                        Component subTitle = Component.text("A New Era Approaches...",
                                NamedTextColor.YELLOW, TextDecoration.ITALIC);
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
                        Component mainTitle = Component.text("PROGRESSION I", NamedTextColor.GREEN,
                                TextDecoration.BOLD);
                        Component subTitle = Component.text("A NEW BEGINNING", NamedTextColor.DARK_RED,
                                TextDecoration.BOLD);

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

                        world.getWorldBorder().setCenter(center);
                        world.getWorldBorder().setSize(1000);
                        for (Player online : Bukkit.getOnlinePlayers()) {
                            if (online.getWorld().equals(world)) {
                                online.sendMessage(Component
                                        .text("PROGRESSION I: ", NamedTextColor.GREEN, TextDecoration.BOLD)
                                        .append(Component
                                                .text("A New Beginning", NamedTextColor.DARK_RED,
                                                        TextDecoration.BOLD)));
                                online.showTitle(finalTitle);
                                online.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 2));
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
