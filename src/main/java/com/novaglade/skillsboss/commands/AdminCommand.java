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

    private static BukkitRunnable activeCountdown = null;

    private void startProgressionOneCountdown(org.bukkit.World world) {
        // Cancel any existing countdown
        if (activeCountdown != null) {
            activeCountdown.cancel();
            activeCountdown = null;
        }

        Location center = world.getSpawnLocation();

        activeCountdown = new BukkitRunnable() {
            int countdown = 10;

            @Override
            public void run() {
                if (countdown > 0) {
                    // Display countdown title
                    Component mainTitle = Component.text(String.valueOf(countdown), NamedTextColor.RED,
                            TextDecoration.BOLD);
                    Component subTitle = Component.text("A New Beginning Approaches...", NamedTextColor.YELLOW);
                    Title title = Title.title(mainTitle, subTitle,
                            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(800),
                                    Duration.ofMillis(200)));

                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (online.getWorld().equals(world)) {
                            online.showTitle(title);
                            online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.5f,
                                    0.5f + (countdown * 0.1f));
                        }
                    }

                    // Epic particles each second
                    double progress = (10 - countdown) / 10.0;

                    // Spiral particles rising
                    for (int i = 0; i < 20; i++) {
                        double angle = (countdown * 36 + i * 18) * Math.PI / 180;
                        double radius = 3 - (progress * 1.5);
                        double height = progress * 5;
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME, center.clone().add(x, height, z), 3, 0.1, 0.1,
                                0.1, 0);
                        world.spawnParticle(Particle.FLAME, center.clone().add(-x, height * 0.7, -z), 3, 0.1, 0.1,
                                0.1, 0);
                    }

                    // Pulsing ground ring
                    for (int i = 0; i < 32; i++) {
                        double angle = i * 2 * Math.PI / 32;
                        double radius = 4 + Math.sin(countdown * 0.5) * 0.8;
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;
                        world.spawnParticle(Particle.DRAGON_BREATH, center.clone().add(x, 0.2, z), 2, 0, 0, 0, 0);
                        world.spawnParticle(Particle.ENCHANT, center.clone().add(x, 0.5, z), 3, 0.2, 0.3, 0.2, 0);
                    }

                    // Sound effect
                    world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f,
                            1.0f + (0.05f * (10 - countdown)));

                    countdown--;
                } else {
                    // FINALE - Progression starts!
                    SkillsBoss.setProgressionLevel(1);

                    Component mainTitle = Component.text("PROGRESSION I", NamedTextColor.GREEN,
                            TextDecoration.BOLD);
                    Component subTitle = Component.text("A NEW BEGINNING", NamedTextColor.DARK_AQUA,
                            TextDecoration.BOLD);
                    Title finalTitle = Title.title(mainTitle, subTitle,
                            Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3),
                                    Duration.ofSeconds(1)));

                    // Multi-stage explosion animation
                    new BukkitRunnable() {
                        int stage = 0;

                        @Override
                        public void run() {
                            if (stage == 0) {
                                // Massive initial explosion
                                for (int r = 0; r < 12; r++) {
                                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 80, r, 1.5, r, 0.3);
                                    world.spawnParticle(Particle.FLAME, center, 80, r, 1.5, r, 0.3);
                                }
                                world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 40, 5, 2, 5, 0);

                                world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 4.0f, 0.4f);
                                world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3.5f, 1.2f);
                            } else if (stage == 3) {
                                // Expanding shockwave rings
                                for (int ring = 0; ring < 4; ring++) {
                                    for (int i = 0; i < 64; i++) {
                                        double angle = i * 2 * Math.PI / 64;
                                        double radius = 5 + (ring * 3);
                                        double x = Math.cos(angle) * radius;
                                        double z = Math.sin(angle) * radius;
                                        world.spawnParticle(Particle.CRIT, center.clone().add(x, 0.2, z), 10, 0.3, 0.3,
                                                0.3, 0);
                                        world.spawnParticle(Particle.END_ROD, center.clone().add(x, 0.2, z), 4, 0, 0.6,
                                                0, 0.05);
                                    }
                                }

                                world.playSound(center, Sound.ENTITY_WITHER_DEATH, 3.5f, 0.4f);
                                world.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.5f, 0.5f);
                            } else if (stage == 6) {
                                // Rising pillars of light
                                for (int pillar = 0; pillar < 8; pillar++) {
                                    double angle = pillar * 2 * Math.PI / 8;
                                    double x = Math.cos(angle) * 5;
                                    double z = Math.sin(angle) * 5;
                                    for (int h = 0; h < 12; h++) {
                                        world.spawnParticle(Particle.TOTEM_OF_UNDYING,
                                                center.clone().add(x, h * 0.6, z),
                                                4, 0.3, 0.3, 0.3, 0);
                                    }
                                }

                                world.playSound(center, Sound.UI_TOAST_CHALLENGE_COMPLETE, 3.0f, 1.0f);
                                world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 2.5f, 1.5f);
                            } else if (stage >= 10) {
                                cancel();
                            }
                            stage++;
                        }
                    }.runTaskTimer(SkillsBoss.getInstance(), 0, 2);

                    // Set world border
                    world.getWorldBorder().setCenter(center);
                    world.getWorldBorder().setSize(750);

                    // Notify all players
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (online.getWorld().equals(world)) {
                            online.sendMessage(Component.text("PROGRESSION I: ", NamedTextColor.GREEN,
                                    TextDecoration.BOLD)
                                    .append(Component.text("A New Beginning", NamedTextColor.DARK_AQUA,
                                            TextDecoration.BOLD)));
                            online.showTitle(finalTitle);
                        }
                    }
                    activeCountdown = null;
                    cancel();
                }
            }
        };
        activeCountdown.runTaskTimer(SkillsBoss.getInstance(), 0, 20); // Run every second (20 ticks)
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
