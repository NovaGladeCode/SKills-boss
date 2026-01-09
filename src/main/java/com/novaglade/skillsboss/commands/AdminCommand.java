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
            int maxTicks = 10 * 20; // 10 seconds
            int ticks = maxTicks;

            @Override
            public void run() {
                int seconds = (int) Math.ceil(ticks / 20.0);
                double progress = 1.0 - ((double) ticks / maxTicks);

                // Once per second: Show Localized Title & Play Sound
                if (ticks % 20 == 0) {
                    Component mainTitle = Component.text(String.valueOf(seconds), NamedTextColor.RED,
                            TextDecoration.BOLD);
                    Component subTitle = Component.text("Starting Progression I: A New Beginning...",
                            NamedTextColor.YELLOW);
                    Title title = Title.title(mainTitle, subTitle,
                            Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(800), Duration.ofMillis(100)));

                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (online.getWorld().equals(world) && online.getLocation().distance(center) <= 60.0) {
                            online.showTitle(title);
                            online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f,
                                    0.4f + (float) progress * 1.6f);
                        }
                    }
                }

                // 1. Eight Counter-Rotating Ground Rings (Maximum Geometry)
                int layersToShow = Math.min(8, (int) (progress * 10) + 1);
                for (int layer = 1; layer <= layersToShow; layer++) {
                    double rSize = (2.2 * layer);
                    double speed = (layer % 2 == 0 ? 0.35 : -0.35);
                    int density = 20 + (layer * 10);

                    for (int i = 0; i < density; i++) {
                        double angle = (ticks * speed + i * (2 * Math.PI / density));
                        double x = Math.cos(angle) * rSize;
                        double z = Math.sin(angle) * rSize;

                        Particle p = (layer % 3 == 0 ? Particle.WITCH
                                : (layer % 3 == 1 ? Particle.SOUL_FIRE_FLAME : Particle.END_ROD));
                        world.spawnParticle(p, center.clone().add(x, 0.1, z), 2, 0.05, 0, 0.05, 0);

                        // Escalating Energy Beams (Center-focused)
                        if (ticks % 3 == 0) {
                            world.spawnParticle(Particle.ENCHANT, center.clone().add(x, progress * 15, z), 1, 0, 0, 0,
                                    0.05);
                        }
                    }
                }

                // 2. Rising Soul Chains (8 compass points)
                if (progress > 0.2) {
                    for (int i = 0; i < 8; i++) {
                        double angle = i * (Math.PI / 4);
                        double r = 18.0;
                        Location chainBase = center.clone().add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
                        for (int h = 0; h < 20; h++) {
                            if ((ticks + h) % 10 == 0) {
                                world.spawnParticle(Particle.SOUL, chainBase.clone().add(0, h * progress, 0), 1, 0, 0,
                                        0, 0);
                            }
                        }
                    }
                }

                // 3. Horizontal Lightning Arcs (Ground Network)
                if (progress > 0.4 && ticks % 3 == 0) {
                    for (int i = 0; i < 6; i++) {
                        double angle = Math.random() * 2 * Math.PI;
                        double startR = 4.0;
                        double endR = 15.0 * progress;
                        Location p1 = center.clone().add(Math.cos(angle) * startR, 0.1, Math.sin(angle) * startR);
                        Location p2 = center.clone().add(Math.cos(angle + 0.2) * endR, 0.1,
                                Math.sin(angle + 0.2) * endR);

                        for (double d = 0; d < 1.0; d += 0.1) {
                            Location arc = p1.clone().add(p2.clone().subtract(p1).toVector().multiply(d));
                            world.spawnParticle(Particle.ELECTRIC_SPARK, arc.add(0, Math.random() * 0.2, 0), 1, 0, 0, 0,
                                    0);
                        }
                    }
                }

                // 4. The Sky Funnel (Massive Escalation)
                if (progress > 0.6) {
                    for (int i = 0; i < 16; i++) {
                        double angle = (ticks * 0.7 + i * (Math.PI / 8));
                        double vRadius = 20.0 - (progress * 15.0);
                        double vy = (i * 1.8);
                        double vx = Math.cos(angle) * vRadius;
                        double vz = Math.sin(angle) * vRadius;
                        world.spawnParticle(Particle.DRAGON_BREATH, center.clone().add(vx, vy, vz), 2, 0.1, 0.1, 0.1,
                                0.02);
                    }
                }

                // 5. Environmental Glow Ripples
                if (ticks % 15 == 0) {
                    world.spawnParticle(Particle.GLOW, center, 100, 10, 0.1, 10, 0.02);
                }

                // Localized Audio (NO SCREEN SHAKE EFFECTS)
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!online.getWorld().equals(world))
                        continue;
                    double distance = online.getLocation().distance(center);
                    if (distance <= 60.0) {
                        online.playSound(online.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f,
                                0.3f + (float) progress * 0.9f);
                        online.playSound(online.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.6f,
                                0.4f + (float) progress);
                    }
                }

                if (ticks <= 0) {
                    SkillsBoss.setProgressionLevel(1);

                    Component mainTitle = Component.text("PROGRESSION I", NamedTextColor.GREEN, TextDecoration.BOLD);
                    Component subTitle = Component.text("A NEW BEGINNING", NamedTextColor.DARK_AQUA,
                            TextDecoration.BOLD);
                    Component chatMsg = Component.text("PROGRESSION I: ", NamedTextColor.GREEN, TextDecoration.BOLD)
                            .append(Component.text("A New Beginning", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));

                    Title.Times times = Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(6),
                            Duration.ofSeconds(3));
                    Title finalTitle = Title.title(mainTitle, subTitle, times);

                    // Final OMNI-IMPACT (Massive sphere and residue)
                    for (int r = 0; r < 20; r++) {
                        world.spawnParticle(Particle.FLASH, center, 100, r * 2, 0.5, r * 2, 0);
                        world.spawnParticle(Particle.SONIC_BOOM, center, 10, r, r, r, 0);
                    }

                    // Super-Sphere of Light (Higher density)
                    for (int i = 0; i < 400; i++) {
                        double phi = Math.acos(-1.0 + (2.0 * i) / 400.0);
                        double theta = Math.sqrt(400.0 * Math.PI) * phi;
                        double x = Math.cos(theta) * Math.sin(phi) * 15;
                        double y = Math.sin(theta) * Math.sin(phi) * 15;
                        double z = Math.cos(phi) * 15;
                        world.spawnParticle(Particle.END_ROD, center.clone().add(x, y + 5, z), 2, 0, 0, 0, 0.05);
                    }

                    // Lingering Residue (Falling stars)
                    new BukkitRunnable() {
                        int residueTicks = 100;

                        @Override
                        public void run() {
                            world.spawnParticle(Particle.END_ROD, center, 10, 15, 10, 15, 0.01);
                            if (residueTicks-- <= 0)
                                cancel();
                        }
                    }.runTaskTimer(SkillsBoss.getInstance(), 10, 2);

                    world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 3.5f, 0.4f);
                    world.playSound(center, Sound.ENTITY_WITHER_DEATH, 3.5f, 0.4f);
                    world.playSound(center, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2.5f, 1.0f);
                    world.spawnParticle(Particle.END_ROD, center, 600, 3, 3, 3, 0.6);

                    world.getWorldBorder().setCenter(center);
                    world.getWorldBorder().setSize(750);

                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (online.getWorld().equals(world) && online.getLocation().distance(center) <= 80.0) {
                            online.sendMessage(chatMsg);
                            online.showTitle(finalTitle);
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
            sender.sendMessage(Component.text("Usage: /admin give <diamondarmor|waveboss>", NamedTextColor.RED));
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
        } else {
            sender.sendMessage(Component.text("Unknown item.", NamedTextColor.RED));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(
                Component.text("--- SkillsBoss Admin ---", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.text("/admin progression <0|1> ", NamedTextColor.YELLOW)
                .append(Component.text("- Start progression stages", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/admin give <diamondarmor|waveboss> ", NamedTextColor.YELLOW)
                .append(Component.text("- Give legendary gear or boss altar", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/admin reload ", NamedTextColor.YELLOW)
                .append(Component.text("- Reload plugin config", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/admin version ", NamedTextColor.YELLOW)
                .append(Component.text("- Show plugin version", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("------------------------", NamedTextColor.GOLD));
    }
}
